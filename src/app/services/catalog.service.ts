import { Injectable } from '@angular/core';
import { Observable, map } from 'rxjs';
import { Category, Provider, Group, Tag, Product } from '../models/domain.model';
import { StorageService } from './storage.service';

@Injectable({
  providedIn: 'root'
})
export class CatalogService {
  constructor(private storageService: StorageService) {}

  // ================= CATEGORIES =================
  get categories$(): Observable<Category[]> { return this.storageService.categories$; }
  getCategories(): Category[] { return this.storageService.getCategories(); }

  addCategory(category: Omit<Category, 'id' | 'createdAt'>): Category {
    const list = this.getCategories();
    const cleanName = category.name.trim();
    if (!cleanName) throw new Error('El nombre de la categoría es obligatorio.');
    if (list.some(c => c.name.toLowerCase() === cleanName.toLowerCase())) {
      throw new Error(`Ya existe una categoría con el nombre "${cleanName}".`);
    }

    const newCat: Category = {
      ...category,
      id: 'cat-' + Date.now(),
      name: cleanName,
      createdAt: new Date().toISOString()
    };
    this.storageService.saveCategories([...list, newCat]);
    return newCat;
  }

  updateCategory(id: string, updates: Partial<Omit<Category, 'id' | 'createdAt'>>): Category {
    const list = this.getCategories();
    const index = list.findIndex(c => c.id === id);
    if (index === -1) throw new Error('Categoría no encontrada.');

    if (updates.name) {
      const cleanName = updates.name.trim();
      if (list.some(c => c.id !== id && c.name.toLowerCase() === cleanName.toLowerCase())) {
        throw new Error(`El nombre de categoría "${cleanName}" ya está en uso.`);
      }
      updates.name = cleanName;
    }

    const updated = { ...list[index], ...updates };
    list[index] = updated;
    this.storageService.saveCategories([...list]);
    return updated;
  }

  deleteCategory(id: string): void {
    const list = this.getCategories().filter(c => c.id !== id);
    this.storageService.saveCategories(list);
  }

  // ================= PROVIDERS =================
  get providers$(): Observable<Provider[]> { return this.storageService.providers$; }
  getProviders(): Provider[] { return this.storageService.getProviders(); }

  addProvider(provider: Omit<Provider, 'id' | 'createdAt'>): Provider {
    const list = this.getProviders();
    const cleanCode = provider.code.trim().toUpperCase();
    const cleanName = provider.name.trim();

    if (!cleanCode) throw new Error('El código del proveedor es obligatorio.');
    if (!cleanName) throw new Error('El nombre del proveedor es obligatorio.');

    if (list.some(p => p.code.toUpperCase() === cleanCode)) {
      throw new Error(`El código de proveedor "${cleanCode}" ya existe.`);
    }
    if (list.some(p => p.name.toLowerCase() === cleanName.toLowerCase())) {
      throw new Error(`El nombre de proveedor "${cleanName}" ya existe.`);
    }

    const newProvider: Provider = {
      ...provider,
      id: 'prov-' + Date.now(),
      code: cleanCode,
      name: cleanName,
      createdAt: new Date().toISOString()
    };
    this.storageService.saveProviders([...list, newProvider]);
    return newProvider;
  }

  updateProvider(id: string, updates: Partial<Omit<Provider, 'id' | 'createdAt'>>): Provider {
    const list = this.getProviders();
    const index = list.findIndex(p => p.id === id);
    if (index === -1) throw new Error('Proveedor no encontrado.');

    if (updates.code) {
      const cleanCode = updates.code.trim().toUpperCase();
      if (list.some(p => p.id !== id && p.code.toUpperCase() === cleanCode)) {
        throw new Error(`El código "${cleanCode}" ya está registrado por otro proveedor.`);
      }
      updates.code = cleanCode;
    }
    if (updates.name) {
      const cleanName = updates.name.trim();
      if (list.some(p => p.id !== id && p.name.toLowerCase() === cleanName.toLowerCase())) {
        throw new Error(`El nombre "${cleanName}" ya está registrado por otro proveedor.`);
      }
      updates.name = cleanName;
    }

    const updated = { ...list[index], ...updates };
    list[index] = updated;
    this.storageService.saveProviders([...list]);
    return updated;
  }

  deleteProvider(id: string): void {
    const list = this.getProviders().filter(p => p.id !== id);
    this.storageService.saveProviders(list);
  }

  // ================= GROUPS =================
  get groups$(): Observable<Group[]> { return this.storageService.groups$; }
  getGroups(): Group[] { return this.storageService.getGroups(); }

  addGroup(group: Omit<Group, 'id' | 'createdAt'>): Group {
    const list = this.getGroups();
    const cleanName = group.name.trim();
    if (!cleanName) throw new Error('El nombre del grupo es obligatorio.');
    if (list.some(g => g.name.toLowerCase() === cleanName.toLowerCase())) {
      throw new Error(`El grupo "${cleanName}" ya existe.`);
    }

    const newGroup: Group = {
      ...group,
      id: 'grp-' + Date.now(),
      name: cleanName,
      createdAt: new Date().toISOString()
    };
    this.storageService.saveGroups([...list, newGroup]);
    return newGroup;
  }

  updateGroup(id: string, updates: Partial<Omit<Group, 'id' | 'createdAt'>>): Group {
    const list = this.getGroups();
    const index = list.findIndex(g => g.id === id);
    if (index === -1) throw new Error('Grupo no encontrado.');

    if (updates.name) {
      const cleanName = updates.name.trim();
      if (list.some(g => g.id !== id && g.name.toLowerCase() === cleanName.toLowerCase())) {
        throw new Error(`El nombre de grupo "${cleanName}" ya está registrado.`);
      }
      updates.name = cleanName;
    }

    const updated = { ...list[index], ...updates };
    list[index] = updated;
    this.storageService.saveGroups([...list]);
    return updated;
  }

  deleteGroup(id: string): void {
    const list = this.getGroups().filter(g => g.id !== id);
    this.storageService.saveGroups(list);
  }

  // ================= TAGS =================
  get tags$(): Observable<Tag[]> { return this.storageService.tags$; }
  getTags(): Tag[] { return this.storageService.getTags(); }

  addTag(tag: Omit<Tag, 'id' | 'createdAt'>): Tag {
    const list = this.getTags();
    const cleanName = tag.name.trim();
    if (!cleanName) throw new Error('El nombre de la etiqueta es obligatorio.');
    if (list.some(t => t.name.toLowerCase() === cleanName.toLowerCase())) {
      throw new Error(`La etiqueta "${cleanName}" ya existe.`);
    }

    const newTag: Tag = {
      ...tag,
      id: 'tag-' + Date.now(),
      name: cleanName,
      createdAt: new Date().toISOString()
    };
    this.storageService.saveTags([...list, newTag]);
    return newTag;
  }

  updateTag(id: string, updates: Partial<Omit<Tag, 'id' | 'createdAt'>>): Tag {
    const list = this.getTags();
    const index = list.findIndex(t => t.id === id);
    if (index === -1) throw new Error('Etiqueta no encontrada.');

    if (updates.name) {
      const cleanName = updates.name.trim();
      if (list.some(t => t.id !== id && t.name.toLowerCase() === cleanName.toLowerCase())) {
        throw new Error(`La etiqueta "${cleanName}" ya está registrada.`);
      }
      updates.name = cleanName;
    }

    const updated = { ...list[index], ...updates };
    list[index] = updated;
    this.storageService.saveTags([...list]);
    return updated;
  }

  deleteTag(id: string): void {
    const list = this.getTags().filter(t => t.id !== id);
    this.storageService.saveTags(list);
  }

  // ================= PRODUCTS =================
  get products$(): Observable<Product[]> { return this.storageService.products$; }
  getProducts(): Product[] { return this.storageService.getProducts(); }

  getFrequentProducts(): Observable<Product[]> {
    return this.products$.pipe(
      map(prods => prods.filter(p => p.isActive && p.isFrequent))
    );
  }

  addProduct(product: Omit<Product, 'id' | 'createdAt' | 'updatedAt'>): Product {
    const list = this.getProducts();
    const cleanCode = product.code.trim().toUpperCase();
    const cleanBarcode = product.barcode?.trim();

    if (!cleanCode) throw new Error('El código del producto es obligatorio.');
    if (!product.name.trim()) throw new Error('El nombre del producto es obligatorio.');
    if (!product.groupId) throw new Error('Debe asignar un grupo al producto.');
    if (!product.providerId) throw new Error('Debe asignar un proveedor al producto.');

    if (product.salePrice <= product.cost) {
      throw new Error(`El precio de venta (Bs ${product.salePrice}) debe ser mayor al costo (Bs ${product.cost}).`);
    }

    if (list.some(p => p.code.toUpperCase() === cleanCode)) {
      throw new Error(`El código de producto "${cleanCode}" ya existe.`);
    }

    if (cleanBarcode && list.some(p => p.barcode && p.barcode === cleanBarcode)) {
      throw new Error(`El código de barras "${cleanBarcode}" ya está registrado en otro producto.`);
    }

    const newProd: Product = {
      ...product,
      id: 'prod-' + Date.now(),
      code: cleanCode,
      barcode: cleanBarcode || undefined,
      createdAt: new Date().toISOString()
    };
    this.storageService.saveProducts([...list, newProd]);
    return newProd;
  }

  updateProduct(id: string, updates: Partial<Omit<Product, 'id' | 'createdAt'>>): Product {
    const list = this.getProducts();
    const index = list.findIndex(p => p.id === id);
    if (index === -1) throw new Error('Producto no encontrado.');

    const current = list[index];
    const newCost = updates.cost !== undefined ? updates.cost : current.cost;
    const newPrice = updates.salePrice !== undefined ? updates.salePrice : current.salePrice;

    if (newPrice <= newCost) {
      throw new Error(`El precio de venta (Bs ${newPrice}) debe ser mayor al costo (Bs ${newCost}).`);
    }

    if (updates.code) {
      const cleanCode = updates.code.trim().toUpperCase();
      if (list.some(p => p.id !== id && p.code.toUpperCase() === cleanCode)) {
        throw new Error(`El código de producto "${cleanCode}" ya existe.`);
      }
      updates.code = cleanCode;
    }

    if (updates.barcode) {
      const cleanBarcode = updates.barcode.trim();
      if (list.some(p => p.id !== id && p.barcode && p.barcode === cleanBarcode)) {
        throw new Error(`El código de barras "${cleanBarcode}" ya está asignado a otro producto.`);
      }
      updates.barcode = cleanBarcode;
    }

    const updated: Product = {
      ...current,
      ...updates,
      updatedAt: new Date().toISOString()
    };
    list[index] = updated;
    this.storageService.saveProducts([...list]);
    return updated;
  }

  toggleProductActive(id: string): boolean {
    const list = this.getProducts();
    const index = list.findIndex(p => p.id === id);
    if (index === -1) return false;
    list[index].isActive = !list[index].isActive;
    list[index].updatedAt = new Date().toISOString();
    this.storageService.saveProducts([...list]);
    return list[index].isActive;
  }

  deleteProduct(id: string): void {
    const list = this.getProducts().filter(p => p.id !== id);
    this.storageService.saveProducts(list);
  }

  calculateProfit(cost: number, salePrice: number): { profit: number; marginPercent: number } {
    const profit = Number((salePrice - cost).toFixed(2));
    const marginPercent = cost > 0 ? Number(((profit / cost) * 100).toFixed(1)) : 0;
    return { profit, marginPercent };
  }
}
