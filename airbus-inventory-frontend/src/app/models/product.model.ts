export interface Product {
  id: number;
  name: string;
  category: string;
  quantity: number;
  unitPrice: number;
  supplier: string;
  reorderLevel: number;
  lastUpdated: string;
}

export type ProductRequest = Omit<Product, 'id' | 'lastUpdated'>;
