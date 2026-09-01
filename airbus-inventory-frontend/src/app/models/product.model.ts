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
