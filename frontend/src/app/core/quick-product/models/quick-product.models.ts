import { Product } from '../../product/models/product.models';

export interface QuickProductGroup {
  id: string;
  name: string;
  displayOrder: number;
  createdAt: string;
  updatedAt: string;
  products: Product[];
}

export interface QuickProductGroupRequest {
  name: string;
  productIds: string[];
}

export interface ReorderGroupsRequest {
  groupIds: string[];
}

export interface ReorderItemsRequest {
  productIds: string[];
}
