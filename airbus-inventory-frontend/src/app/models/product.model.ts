export interface Product {
  id: number;
  name: string;
  category: string;
  quantity: number;
  unitPrice: number;
  supplier: string;
  reorderLevel: number;
  createdBy: string;
  updatedBy: string;
  lastUpdated: string;
}

export type ProductRequest = Omit<Product, 'id' | 'createdBy' | 'updatedBy' | 'lastUpdated'>;

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface CategoryCount {
  category: string;
  count: number;
}

export interface InventorySummary {
  totalProducts: number;
  totalInventoryValue: number;
  lowStockCount: number;
  byCategory: CategoryCount[];
}
