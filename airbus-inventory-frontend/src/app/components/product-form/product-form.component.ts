import { Component, Inject } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { HttpErrorResponse } from '@angular/common/http';
import { Product, ProductRequest } from '../../models/product.model';
import { ProductService } from '../../services/product.service';
import { ErrorResponse } from '../../models/error-response.model';

export interface ProductFormData {
  product?: Product;
}

export const PRODUCT_CATEGORIES = [
  'Avionics',
  'Landing Gear',
  'Engine Components',
  'Cabin Interiors',
  'Hydraulics'
];

@Component({
  selector: 'app-product-form',
  templateUrl: './product-form.component.html',
  styleUrls: ['./product-form.component.scss']
})
export class ProductFormComponent {

  form: FormGroup;
  isEdit: boolean;
  submitting = false;
  errorMessage: string | null = null;
  categories = PRODUCT_CATEGORIES;

  constructor(
    private fb: FormBuilder,
    private productService: ProductService,
    private dialogRef: MatDialogRef<ProductFormComponent>,
    @Inject(MAT_DIALOG_DATA) public data: ProductFormData
  ) {
    this.isEdit = !!data.product;

    const p = data.product;
    this.form = this.fb.group({
      name: [p?.name ?? '', [Validators.required, Validators.maxLength(150)]],
      category: [p?.category ?? '', [Validators.required]],
      quantity: [p?.quantity ?? 0, [Validators.required, Validators.min(0)]],
      unitPrice: [p?.unitPrice ?? 0, [Validators.required, Validators.min(0)]],
      supplier: [p?.supplier ?? '', [Validators.maxLength(150)]],
      reorderLevel: [p?.reorderLevel ?? 0, [Validators.required, Validators.min(0)]]
    });
  }

  submit(): void {
    if (this.form.invalid || this.submitting) {
      this.form.markAllAsTouched();
      return;
    }

    this.submitting = true;
    this.errorMessage = null;
    const request: ProductRequest = this.form.value;

    const obs = this.isEdit && this.data.product
      ? this.productService.update(this.data.product.id, request)
      : this.productService.create(request);

    obs.subscribe({
      next: product => {
        this.submitting = false;
        this.dialogRef.close(product);
      },
      error: (err: HttpErrorResponse) => {
        this.submitting = false;
        const body = err.error as ErrorResponse | undefined;
        this.errorMessage = body?.details?.join(', ') ?? body?.message ?? 'Save failed. Please try again.';
      }
    });
  }

  cancel(): void {
    this.dialogRef.close();
  }
}
