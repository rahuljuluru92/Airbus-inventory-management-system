import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { ProductService } from './product.service';
import { PageResponse, Product } from '../models/product.model';
import { environment } from '../../environments/environment';

describe('ProductService', () => {
  let service: ProductService;
  let httpMock: HttpTestingController;
  const apiUrl = `${environment.apiUrl}/products`;

  const sampleProduct: Product = {
    id: 1,
    name: 'Turbine Disc',
    category: 'Engine Components',
    quantity: 5,
    unitPrice: 98000,
    supplier: 'CFM International',
    reorderLevel: 6,
    createdBy: 'admin',
    updatedBy: 'admin',
    lastUpdated: '2026-08-29T10:00:00'
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule]
    });
    service = TestBed.inject(ProductService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('getAll requests the given page/size as query params and returns the page response', () => {
    const page: PageResponse<Product> = {
      content: [sampleProduct],
      page: 1,
      size: 10,
      totalElements: 38,
      totalPages: 4
    };

    service.getAll(1, 10).subscribe(result => {
      expect(result).toEqual(page);
    });

    const req = httpMock.expectOne(r => r.url === apiUrl);
    expect(req.request.params.get('page')).toBe('1');
    expect(req.request.params.get('size')).toBe('10');
    req.flush(page);
  });

  it('getByCategory URL-encodes the category and returns a flat list', () => {
    service.getByCategory('Landing Gear').subscribe(result => {
      expect(result).toEqual([sampleProduct]);
    });

    const req = httpMock.expectOne(`${apiUrl}/category/Landing%20Gear`);
    expect(req.request.method).toBe('GET');
    req.flush([sampleProduct]);
  });

  it('getLowStock hits /low-stock', () => {
    service.getLowStock().subscribe(result => {
      expect(result).toEqual([sampleProduct]);
    });

    httpMock.expectOne(`${apiUrl}/low-stock`).flush([sampleProduct]);
  });

  it('getSummary hits /summary', () => {
    const summary = {
      totalProducts: 38,
      totalInventoryValue: 10208470,
      lowStockCount: 3,
      byCategory: [{ category: 'Avionics', count: 8 }]
    };

    service.getSummary().subscribe(result => {
      expect(result).toEqual(summary);
    });

    httpMock.expectOne(`${apiUrl}/summary`).flush(summary);
  });

  it('create POSTs the request body without id/audit/lastUpdated fields', () => {
    const request = {
      name: 'New Part', category: 'Avionics', quantity: 1, unitPrice: 1, supplier: '', reorderLevel: 1
    };

    service.create(request).subscribe();

    const req = httpMock.expectOne(apiUrl);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(request);
    req.flush(sampleProduct);
  });

  it('update PUTs to /:id', () => {
    const request = {
      name: 'Turbine Disc', category: 'Engine Components', quantity: 8, unitPrice: 98000,
      supplier: 'CFM International', reorderLevel: 6
    };

    service.update(1, request).subscribe();

    const req = httpMock.expectOne(`${apiUrl}/1`);
    expect(req.request.method).toBe('PUT');
    req.flush(sampleProduct);
  });

  it('delete DELETEs /:id', () => {
    service.delete(1).subscribe();

    const req = httpMock.expectOne(`${apiUrl}/1`);
    expect(req.request.method).toBe('DELETE');
    req.flush(null);
  });
});
