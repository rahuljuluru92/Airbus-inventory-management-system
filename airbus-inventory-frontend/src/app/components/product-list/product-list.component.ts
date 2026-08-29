import { AfterViewInit, Component, OnInit, ViewChild } from '@angular/core';
import { MatTableDataSource } from '@angular/material/table';
import { MatSort } from '@angular/material/sort';
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

  displayedColumns = ['name', 'category', 'quantity', 'unitPrice', 'supplier', 'reorderLevel', 'actions'];
  dataSource = new MatTableDataSource<Product>([]);
  categories = PRODUCT_CATEGORIES;
  selectedCategory = '';
  loading = false;
  errorMessage: string | null = null;

  @ViewChild(MatSort) sort!: MatSort;

  constructor(
    private productService: ProductService,
    private authService: AuthService,
    private router: Router,
    private dialog: MatDialog,
    private snackBar: MatSnackBar
  ) {}

  ngOnInit(): void {
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

  loadProducts(): void {
    this.loading = true;
    this.errorMessage = null;

    const request = this.selectedCategory
      ? this.productService.getByCategory(this.selectedCategory)
      : this.productService.getAll();

    request.subscribe({
      next: products => {
        this.dataSource.data = products;
        this.loading = false;
      },
      error: () => {
        this.errorMessage = 'Failed to load products.';
        this.loading = false;
      }
    });
  }

  onCategoryChange(): void {
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
}
