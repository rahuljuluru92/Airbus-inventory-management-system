import { AfterViewInit, Component, OnInit, ViewChild } from '@angular/core';
import { MatTableDataSource } from '@angular/material/table';
import { MatSort } from '@angular/material/sort';
import { MatPaginator, PageEvent } from '@angular/material/paginator';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Router } from '@angular/router';
import { Product } from '../../models/product.model';
import { ProductService } from '../../services/product.service';
import { AuthService } from '../../services/auth.service';
import { ProductFormComponent, PRODUCT_CATEGORIES } from '../product-form/product-form.component';
import { ConfirmDialogComponent } from '../confirm-dialog/confirm-dialog.component';

@Component({
  selector: 'app-product-list',
  templateUrl: './product-list.component.html',
  styleUrls: ['./product-list.component.scss']
})
export class ProductListComponent implements OnInit, AfterViewInit {

  dataSource = new MatTableDataSource<Product>([]);
  categories = PRODUCT_CATEGORIES;
  selectedCategory = '';
  showLowStockOnly = false;
  loading = false;
  errorMessage: string | null = null;
  isAdmin = false;
  displayedColumns: string[] = [];

  // Pagination only applies to the "all products, no filters" view — /category and /low-stock
  // are smaller, targeted result sets returned unpaginated by the backend (see decisions.md).
  pageIndex = 0;
  pageSize = 50;
  pageSizeOptions = [10, 20, 50];
  totalElements = 0;

  @ViewChild(MatSort) sort!: MatSort;
  @ViewChild(MatPaginator) paginator!: MatPaginator;

  constructor(
    private productService: ProductService,
    private authService: AuthService,
    private router: Router,
    private dialog: MatDialog,
    private snackBar: MatSnackBar
  ) {}

  ngOnInit(): void {
    this.isAdmin = this.authService.isAdmin();
    const baseColumns = ['name', 'category', 'quantity', 'unitPrice', 'supplier', 'reorderLevel'];
    this.displayedColumns = this.isAdmin ? [...baseColumns, 'actions'] : baseColumns;
    this.loadProducts();
  }

  ngAfterViewInit(): void {
    this.dataSource.sort = this.sort;
    this.dataSource.filterPredicate = (product, filter) =>
      product.name.toLowerCase().includes(filter);
  }

  get username(): string | null {
    return this.authService.getUsername();
  }

  /** True when the currently loaded list is server-side paginated (the plain "all products" view). */
  get isServerPaginated(): boolean {
    return !this.showLowStockOnly && !this.selectedCategory;
  }

  loadProducts(): void {
    this.loading = true;
    this.errorMessage = null;

    if (this.showLowStockOnly) {
      this.productService.getLowStock().subscribe({
        next: products => this.applyUnpagedResult(products),
        error: () => this.handleLoadError()
      });
    } else if (this.selectedCategory) {
      this.productService.getByCategory(this.selectedCategory).subscribe({
        next: products => this.applyUnpagedResult(products),
        error: () => this.handleLoadError()
      });
    } else {
      this.productService.getAll(this.pageIndex, this.pageSize).subscribe({
        next: page => {
          this.dataSource.data = page.content;
          this.totalElements = page.totalElements;
          this.loading = false;
        },
        error: () => this.handleLoadError()
      });
    }
  }

  onCategoryChange(): void {
    this.pageIndex = 0;
    this.loadProducts();
  }

  onLowStockToggle(): void {
    if (this.showLowStockOnly) {
      this.selectedCategory = '';
    }
    this.pageIndex = 0;
    this.loadProducts();
  }

  onPageChange(event: PageEvent): void {
    this.pageIndex = event.pageIndex;
    this.pageSize = event.pageSize;
    this.loadProducts();
  }

  applyNameFilter(value: string): void {
    this.dataSource.filter = value.trim().toLowerCase();
  }

  openAddDialog(): void {
    const ref = this.dialog.open(ProductFormComponent, { data: {}, width: '540px' });
    ref.afterClosed().subscribe(result => {
      if (result) {
        this.snackBar.open('Product added', 'Close', { duration: 3000 });
        this.loadProducts();
      }
    });
  }

  openEditDialog(product: Product): void {
    const ref = this.dialog.open(ProductFormComponent, { data: { product }, width: '540px' });
    ref.afterClosed().subscribe(result => {
      if (result) {
        this.snackBar.open('Product updated', 'Close', { duration: 3000 });
        this.loadProducts();
      }
    });
  }

  openDeleteDialog(product: Product): void {
    const ref = this.dialog.open(ConfirmDialogComponent, {
      data: {
        title: 'Delete Product',
        message: `Are you sure you want to delete "${product.name}"? This cannot be undone.`
      }
    });

    ref.afterClosed().subscribe(confirmed => {
      if (confirmed) {
        this.productService.delete(product.id).subscribe({
          next: () => {
            this.snackBar.open('Product deleted', 'Close', { duration: 3000 });
            this.loadProducts();
          },
          error: () => {
            this.snackBar.open('Failed to delete product', 'Close', { duration: 3000 });
          }
        });
      }
    });
  }

  logout(): void {
    this.authService.logout();
    this.router.navigate(['/login']);
  }

  private applyUnpagedResult(products: Product[]): void {
    this.dataSource.data = products;
    this.totalElements = products.length;
    this.loading = false;
  }

  private handleLoadError(): void {
    this.errorMessage = 'Failed to load products.';
    this.loading = false;
  }
}
