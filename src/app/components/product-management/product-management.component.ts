import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { CatalogService } from '../../services/catalog.service';
import { Product, Group, Provider, Tag, SaleType } from '../../models/domain.model';
import { ConfirmModalComponent } from '../confirm-modal/confirm-modal.component';

@Component({
  selector: 'app-product-management',
  standalone: true,
  imports: [CommonModule, FormsModule, ConfirmModalComponent],
  template: `
    <div class="p-4 sm:p-6 max-w-7xl mx-auto space-y-6">
      
      <!-- Header -->
      <div class="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4">
        <div>
          <h1 class="text-2xl font-bold font-headline text-[#1E2519] flex items-center gap-2">
            <span class="material-symbols-outlined text-[#3B6C2B] text-3xl">inventory_2</span>
            Gestión de Productos
          </h1>
          <p class="text-xs sm:text-sm text-[#7D8774] mt-0.5">
            Alta, edición, márgenes de ganancia, control de pesables y códigos de barras
          </p>
        </div>

        <button
          (click)="openCreateModal()"
          class="px-4 py-2.5 rounded-xl font-bold text-xs sm:text-sm bg-[#3B6C2B] hover:bg-[#4E7A3E] text-white shadow-xs transition-colors flex items-center gap-2">
          <span class="material-symbols-outlined text-base">add</span>
          Nuevo Producto
        </button>
      </div>

      <!-- Search & Filters -->
      <div class="bg-white p-4 rounded-2xl border border-[#D8D3C1] shadow-2xs flex flex-col md:flex-row gap-3 items-center justify-between">
        <div class="relative flex-1 w-full">
          <span class="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none text-[#7D8774]">
            <span class="material-symbols-outlined text-lg">search</span>
          </span>
          <input
            type="text"
            [(ngModel)]="searchQuery"
            placeholder="Buscar por nombre, código, código de barras, grupo o proveedor..."
            class="w-full pl-9 pr-4 py-2 text-xs sm:text-sm bg-[#FAF8F0] border border-[#D8D3C1] rounded-xl focus:ring-2 focus:ring-[#3B6C2B]"
          />
        </div>

        <div class="flex items-center gap-2 w-full md:w-auto">
          <select [(ngModel)]="selectedFilterType" class="text-xs p-2 rounded-xl border border-[#D8D3C1] bg-[#FAF8F0]">
            <option value="ALL">Todos los tipos</option>
            <option value="unit">Por Unidad</option>
            <option value="weight">Por Peso (Gramos)</option>
            <option value="frequent">Frecuentes / Rápidos</option>
          </select>

          <span class="text-xs font-semibold text-[#4C5544] px-2 py-1 bg-[#EDE7D4] rounded-lg">
            {{ filteredProducts.length }} productos
          </span>
        </div>
      </div>

      <!-- Products Table -->
      <div class="bg-white rounded-2xl border border-[#D8D3C1] shadow-xs overflow-hidden">
        <div class="overflow-x-auto">
          <table class="w-full text-left text-xs sm:text-sm">
            <thead class="bg-[#FAF8F0] border-b border-[#D8D3C1] text-[#4C5544] uppercase tracking-wider font-semibold">
              <tr>
                <th class="p-3.5 pl-4">Código / Barras</th>
                <th class="p-3.5">Nombre</th>
                <th class="p-3.5">Grupo & Proveedor</th>
                <th class="p-3.5">Costo</th>
                <th class="p-3.5">Precio Venta</th>
                <th class="p-3.5">Utilidad</th>
                <th class="p-3.5">Tipo / Frecuente</th>
                <th class="p-3.5">Estado</th>
                <th class="p-3.5 pr-4 text-right">Acciones</th>
              </tr>
            </thead>
            <tbody class="divide-y divide-[#EDE7D4]">
              <tr *ngFor="let p of filteredProducts" class="hover:bg-[#FAF8F0] transition-colors">
                <td class="p-3.5 pl-4 font-mono">
                  <span class="font-bold text-[#1E2519] block">{{ p.code }}</span>
                  <span class="text-[10px] text-[#7D8774]">{{ p.barcode || 'Sin código barras' }}</span>
                </td>
                <td class="p-3.5">
                  <span class="font-bold text-[#1E2519] block">{{ p.name }}</span>
                  <div class="flex items-center gap-1 mt-0.5">
                    <span *ngFor="let tagId of p.tagIds" class="text-[9px] font-bold px-1.5 py-0.2 rounded-sm bg-[#EDE7D4] text-[#4C5544]">
                      {{ getTagName(tagId) }}
                    </span>
                  </div>
                </td>
                <td class="p-3.5 text-[#4C5544]">
                  <span class="block font-semibold text-[#1E2519]">{{ getGroupName(p.groupId) }}</span>
                  <span class="text-[11px] text-[#7D8774]">{{ getProviderName(p.providerId) }}</span>
                </td>
                <td class="p-3.5 font-mono text-[#7D8774]">
                  Bs {{ p.cost | number:'1.2-2' }}
                </td>
                <td class="p-3.5 font-mono font-bold text-[#3B6C2B]">
                  Bs {{ p.salePrice | number:'1.2-2' }}
                </td>
                <td class="p-3.5 font-mono">
                  <span class="font-bold text-[#1E3B0B] block">Bs {{ (p.salePrice - p.cost) | number:'1.2-2' }}</span>
                  <span class="text-[10px] text-[#7EB53F] font-bold">
                    +{{ ((p.salePrice - p.cost) / (p.cost || 1) * 100) | number:'1.1-1' }}%
                  </span>
                </td>
                <td class="p-3.5">
                  <span
                    [ngClass]="p.saleType === 'weight' ? 'bg-[#EBF5DE] text-[#1E3B0B]' : 'bg-[#EDE7D4] text-[#4C5544]'"
                    class="px-2 py-0.5 rounded-md font-bold text-[10px] block w-max">
                    {{ p.saleType === 'weight' ? '⚖️ Balanza (g)' : 'Unidad' }}
                  </span>
                  <span *ngIf="p.isFrequent" class="text-[9px] font-bold text-[#6B4C00] bg-[#FEF6DC] px-1.5 py-0.2 rounded-sm mt-1 inline-block">
                    ⚡ Frecuente
                  </span>
                </td>
                <td class="p-3.5">
                  <button
                    type="button"
                    (click)="toggleActive(p)"
                    [ngClass]="p.isActive ? 'bg-[#EBF5DE] text-[#1E3B0B]' : 'bg-gray-100 text-gray-500'"
                    class="px-2 py-0.5 rounded-full text-[10px] font-bold transition-colors">
                    {{ p.isActive ? 'Activo' : 'Inactivo' }}
                  </button>
                </td>
                <td class="p-3.5 pr-4 text-right">
                  <div class="flex items-center justify-end gap-1.5">
                    <button
                      (click)="openEditModal(p)"
                      class="p-1.5 rounded-lg text-[#4C5544] hover:text-[#1E2519] hover:bg-[#EDE7D4] transition-colors"
                      title="Editar Producto">
                      <span class="material-symbols-outlined text-base">edit</span>
                    </button>
                    <button
                      (click)="openDeleteModal(p)"
                      class="p-1.5 rounded-lg text-red-600 hover:text-red-800 hover:bg-red-50 transition-colors"
                      title="Eliminar Producto">
                      <span class="material-symbols-outlined text-base">delete</span>
                    </button>
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

    </div>

    <!-- Create / Edit Product Modal -->
    <div *ngIf="showFormModal" class="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-xs overflow-y-auto">
      <div class="bg-white rounded-2xl shadow-2xl border border-[#D8D3C1] max-w-2xl w-full my-8 overflow-hidden p-6 space-y-4">
        <div class="flex items-center justify-between border-b border-[#D8D3C1] pb-3">
          <h3 class="text-lg font-bold font-headline text-[#1E2519]">
            {{ isEditing ? 'Editar Producto' : 'Registrar Nuevo Producto' }}
          </h3>
          <button (click)="showFormModal = false" class="text-[#7D8774] hover:text-[#1E2519]">
            <span class="material-symbols-outlined">close</span>
          </button>
        </div>

        <div class="grid grid-cols-1 sm:grid-cols-2 gap-4">
          <!-- Code (Unique, Required) -->
          <div>
            <label class="block text-xs font-bold text-[#4C5544] uppercase mb-1">Código del Producto *</label>
            <input
              type="text"
              [(ngModel)]="formData.code"
              placeholder="Ej: ABA-005"
              class="w-full p-2.5 rounded-xl border border-[#D8D3C1] text-xs font-mono uppercase bg-[#FAF8F0]"
            />
          </div>

          <!-- Barcode (Unique, Optional) -->
          <div>
            <label class="block text-xs font-bold text-[#4C5544] uppercase mb-1">Código de Barras (Opcional)</label>
            <input
              type="text"
              [(ngModel)]="formData.barcode"
              placeholder="Ej: 7771234099"
              class="w-full p-2.5 rounded-xl border border-[#D8D3C1] text-xs font-mono bg-[#FAF8F0]"
            />
          </div>

          <!-- Name (Required) -->
          <div class="sm:col-span-2">
            <label class="block text-xs font-bold text-[#4C5544] uppercase mb-1">Nombre del Producto *</label>
            <input
              type="text"
              [(ngModel)]="formData.name"
              placeholder="Ej: Aceite de Oliva Extra Virgen 1L"
              class="w-full p-2.5 rounded-xl border border-[#D8D3C1] text-sm bg-[#FAF8F0]"
            />
          </div>

          <!-- Group (Required) -->
          <div>
            <label class="block text-xs font-bold text-[#4C5544] uppercase mb-1">Grupo de Almacén *</label>
            <select [(ngModel)]="formData.groupId" class="w-full p-2.5 rounded-xl border border-[#D8D3C1] text-xs bg-[#FAF8F0]">
              <option *ngFor="let g of groups" [value]="g.id">{{ g.name }}</option>
            </select>
          </div>

          <!-- Provider (Required) -->
          <div>
            <label class="block text-xs font-bold text-[#4C5544] uppercase mb-1">Proveedor *</label>
            <select [(ngModel)]="formData.providerId" class="w-full p-2.5 rounded-xl border border-[#D8D3C1] text-xs bg-[#FAF8F0]">
              <option *ngFor="let p of providers" [value]="p.id">{{ p.name }} ({{ p.code }})</option>
            </select>
          </div>

          <!-- Cost (Required) -->
          <div>
            <label class="block text-xs font-bold text-[#4C5544] uppercase mb-1">Costo de Compra (Bs) *</label>
            <input
              type="number"
              min="0"
              step="0.1"
              [(ngModel)]="formData.cost"
              (ngModelChange)="recalcUtility()"
              class="w-full p-2.5 rounded-xl border border-[#D8D3C1] text-sm font-mono bg-[#FAF8F0]"
            />
          </div>

          <!-- Sale Price (Required, Must be > Cost) -->
          <div>
            <label class="block text-xs font-bold text-[#4C5544] uppercase mb-1">Precio de Venta (Bs) *</label>
            <input
              type="number"
              min="0"
              step="0.1"
              [(ngModel)]="formData.salePrice"
              (ngModelChange)="recalcUtility()"
              class="w-full p-2.5 rounded-xl border border-[#D8D3C1] text-sm font-mono font-bold bg-[#FAF8F0]"
            />
          </div>

          <!-- Live Utility & Margin % (Disabled Fields required by lock.md) -->
          <div>
            <label class="block text-xs font-bold text-[#7D8774] uppercase mb-1">Utilidad (Precio - Costo)</label>
            <input
              type="text"
              disabled
              [value]="'Bs ' + utility.toFixed(2)"
              class="w-full p-2.5 rounded-xl border border-[#D8D3C1] text-sm font-mono font-bold bg-[#EDE7D4] text-[#1E3B0B]"
            />
          </div>

          <div>
            <label class="block text-xs font-bold text-[#7D8774] uppercase mb-1">% de Margen de Utilidad</label>
            <input
              type="text"
              disabled
              [value]="utilityMargin.toFixed(1) + '%'"
              class="w-full p-2.5 rounded-xl border border-[#D8D3C1] text-sm font-mono font-bold bg-[#EDE7D4] text-[#1E3B0B]"
            />
          </div>

          <!-- Warning if Sale Price <= Cost (lock.md requirement) -->
          <div *ngIf="formData.salePrice <= formData.cost" class="sm:col-span-2 p-2.5 rounded-xl bg-red-50 border border-red-300 text-xs text-red-800 font-semibold flex items-center gap-1.5">
            <span class="material-symbols-outlined text-base text-error">error</span>
            Advertencia: El precio de venta debe ser obligatoriamente mayor al costo de compra.
          </div>

          <!-- Sale Type & Frequent Checkbox -->
          <div>
            <label class="block text-xs font-bold text-[#4C5544] uppercase mb-1">Tipo de Venta *</label>
            <select [(ngModel)]="formData.saleType" class="w-full p-2.5 rounded-xl border border-[#D8D3C1] text-xs bg-[#FAF8F0]">
              <option value="unit">Por Unidad</option>
              <option value="weight">Por Peso (Gramos - Balanza)</option>
            </select>
          </div>

          <div class="flex items-center gap-2 pt-6">
            <input
              type="checkbox"
              id="frequentCheck"
              [(ngModel)]="formData.isFrequent"
              class="w-4 h-4 text-[#3B6C2B] rounded-sm focus:ring-[#3B6C2B]"
            />
            <label for="frequentCheck" class="text-xs font-bold text-[#1E2519] cursor-pointer">
              Producto Frecuente (Aparece en Acceso Rápido del POS)
            </label>
          </div>

        </div>

        <div class="pt-4 flex justify-end gap-2 border-t border-[#D8D3C1]">
          <button (click)="showFormModal = false" class="px-4 py-2 text-xs font-semibold rounded-lg bg-[#EDE7D4]">
            Cancelar
          </button>
          <button
            (click)="triggerConfirmSaveModal()"
            [disabled]="!isFormValid"
            class="px-5 py-2 text-xs font-bold rounded-lg bg-[#3B6C2B] hover:bg-[#4E7A3E] text-white shadow-xs disabled:opacity-50 disabled:cursor-not-allowed">
            {{ isEditing ? 'Guardar Cambios' : 'Crear Producto' }}
          </button>
        </div>
      </div>
    </div>

    <!-- Confirmation Modal on Create/Edit (lock.md requirement) -->
    <app-confirm-modal
      [isOpen]="showConfirmSaveModal"
      [title]="isEditing ? 'Confirmar Modificación de Producto' : 'Confirmar Registro de Producto'"
      [message]="isEditing ? '¿Confirma guardar las modificaciones de ' + formData.name + '?' : '¿Confirma la creación del nuevo producto ' + formData.name + '?'"
      confirmText="Confirmar y Guardar"
      cancelText="Cancelar"
      (confirmed)="executeSaveProduct()"
      (cancelled)="showConfirmSaveModal = false">
    </app-confirm-modal>

    <!-- Confirmation Modal on Delete (lock.md requirement) -->
    <app-confirm-modal
      [isOpen]="showConfirmDeleteModal"
      title="Confirmar Eliminación de Producto"
      [message]="'¿Está seguro de que desea eliminar el producto ' + (selectedProduct?.name || '') + '? Esta acción no se puede deshacer.'"
      confirmText="Sí, Eliminar"
      cancelText="Cancelar"
      [isDanger]="true"
      (confirmed)="executeDeleteProduct()"
      (cancelled)="showConfirmDeleteModal = false">
    </app-confirm-modal>
  `
})
export class ProductManagementComponent implements OnInit {
  products: Product[] = [];
  groups: Group[] = [];
  providers: Provider[] = [];
  tags: Tag[] = [];

  searchQuery = '';
  selectedFilterType = 'ALL';

  // Form State
  showFormModal = false;
  isEditing = false;
  selectedProduct: Product | null = null;

  formData: {
    code: string;
    barcode: string;
    name: string;
    groupId: string;
    providerId: string;
    tagIds: string[];
    cost: number;
    salePrice: number;
    saleType: SaleType;
    isFrequent: boolean;
    isActive: boolean;
  } = this.getEmptyForm();

  utility = 0;
  utilityMargin = 0;

  // Confirm Modals
  showConfirmSaveModal = false;
  showConfirmDeleteModal = false;

  constructor(private catalogService: CatalogService) {}

  ngOnInit(): void {
    this.catalogService.products$.subscribe(prods => this.products = prods);
    this.catalogService.groups$.subscribe(grps => this.groups = grps);
    this.catalogService.providers$.subscribe(provs => this.providers = provs);
    this.catalogService.tags$.subscribe(t => this.tags = t);
  }

  getEmptyForm() {
    return {
      code: '',
      barcode: '',
      name: '',
      groupId: this.groups[0]?.id || '',
      providerId: this.providers[0]?.id || '',
      tagIds: [],
      cost: 1.0,
      salePrice: 2.0,
      saleType: 'unit' as SaleType,
      isFrequent: false,
      isActive: true
    };
  }

  get isFormValid(): boolean {
    return !!(
      this.formData.code.trim() &&
      this.formData.name.trim() &&
      this.formData.groupId &&
      this.formData.providerId &&
      this.formData.salePrice > this.formData.cost
    );
  }

  get filteredProducts(): Product[] {
    let list = this.products;

    if (this.selectedFilterType === 'unit') {
      list = list.filter(p => p.saleType === 'unit');
    } else if (this.selectedFilterType === 'weight') {
      list = list.filter(p => p.saleType === 'weight');
    } else if (this.selectedFilterType === 'frequent') {
      list = list.filter(p => p.isFrequent);
    }

    if (this.searchQuery.trim()) {
      const q = this.searchQuery.trim().toLowerCase();
      list = list.filter(p =>
        p.name.toLowerCase().includes(q) ||
        p.code.toLowerCase().includes(q) ||
        (p.barcode && p.barcode.toLowerCase().includes(q)) ||
        this.getGroupName(p.groupId).toLowerCase().includes(q) ||
        this.getProviderName(p.providerId).toLowerCase().includes(q)
      );
    }

    return list;
  }

  getGroupName(id: string): string {
    return this.groups.find(g => g.id === id)?.name || id;
  }

  getProviderName(id: string): string {
    return this.providers.find(p => p.id === id)?.name || id;
  }

  getTagName(id: string): string {
    return this.tags.find(t => t.id === id)?.name || id;
  }

  recalcUtility(): void {
    const cost = Number(this.formData.cost) || 0;
    const price = Number(this.formData.salePrice) || 0;
    this.utility = Number((price - cost).toFixed(2));
    this.utilityMargin = cost > 0 ? Number(((this.utility / cost) * 100).toFixed(1)) : 0;
  }

  openCreateModal(): void {
    this.isEditing = false;
    this.formData = this.getEmptyForm();
    if (this.groups.length > 0) this.formData.groupId = this.groups[0].id;
    if (this.providers.length > 0) this.formData.providerId = this.providers[0].id;
    this.recalcUtility();
    this.showFormModal = true;
  }

  openEditModal(p: Product): void {
    this.isEditing = true;
    this.selectedProduct = p;
    this.formData = {
      code: p.code,
      barcode: p.barcode || '',
      name: p.name,
      groupId: p.groupId,
      providerId: p.providerId,
      tagIds: [...p.tagIds],
      cost: p.cost,
      salePrice: p.salePrice,
      saleType: p.saleType,
      isFrequent: p.isFrequent,
      isActive: p.isActive
    };
    this.recalcUtility();
    this.showFormModal = true;
  }

  triggerConfirmSaveModal(): void {
    if (!this.isFormValid) return;
    this.showConfirmSaveModal = true;
  }

  executeSaveProduct(): void {
    try {
      if (this.isEditing && this.selectedProduct) {
        this.catalogService.updateProduct(this.selectedProduct.id, this.formData);
      } else {
        this.catalogService.addProduct(this.formData);
      }
      this.showConfirmSaveModal = false;
      this.showFormModal = false;
    } catch (e: any) {
      alert('Error: ' + e.message);
      this.showConfirmSaveModal = false;
    }
  }

  toggleActive(p: Product): void {
    this.catalogService.toggleProductActive(p.id);
  }

  openDeleteModal(p: Product): void {
    this.selectedProduct = p;
    this.showConfirmDeleteModal = true;
  }

  executeDeleteProduct(): void {
    if (!this.selectedProduct) return;
    this.catalogService.deleteProduct(this.selectedProduct.id);
    this.showConfirmDeleteModal = false;
    this.selectedProduct = null;
  }
}
