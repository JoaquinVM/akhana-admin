import { Component, OnInit, effect, inject, input, output, signal, untracked } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { CdkDragDrop, DragDropModule, moveItemInArray } from '@angular/cdk/drag-drop';
import { Observable } from 'rxjs';
import { QuickProductService } from '../../../../core/quick-product/quick-product.service';
import { ProductService } from '../../../../core/product/product.service';
import { QuickProductGroup } from '../../../../core/quick-product/models/quick-product.models';
import { Product, isSpecialProduct } from '../../../../core/product/models/product.models';
import { ModalComponent } from '../../../../shared/components/modal/modal.component';
import { ConfirmModalComponent } from '../../../../shared/components/confirm-modal/confirm-modal.component';
import { ToastService } from '../../../../shared/components/toast/toast.service';
import { searchProducts } from '../../../../shared/utils/product-search';

// Without a search the grid only suggests a few products; a search shows every match (POS-02)
const SUGGESTIONS_WITHOUT_SEARCH = 10;

import { PluralPipe } from '../../../../shared/utils/plural.pipe';

@Component({
  selector: 'app-quick-products-config-modal',
  standalone: true,
  imports: [PluralPipe, CommonModule, FormsModule, DragDropModule, ModalComponent, ConfirmModalComponent],
  templateUrl: './quick-products-config-modal.component.html',
  styleUrls: ['./quick-products-config-modal.component.css']
})
export class QuickProductsConfigModalComponent implements OnInit {
  readonly isOpen = input<boolean>(false);
  readonly close = output<void>();

  readonly quickProductService = inject(QuickProductService);
  private readonly productService = inject(ProductService);
  private readonly toast = inject(ToastService);

  catalogProducts = signal<Product[]>([]);
  productSearchTerm = signal<string>('');
  newGroupName = signal<string>('');
  selectedGroupId = signal<string | null>(null);
  // One change at a time: each one is built from the group as it is now, so a second one sent before the first
  // answers would overwrite it
  isSaving = signal<boolean>(false);
  groupToDelete = signal<QuickProductGroup | null>(null);

  constructor() {
    // The modal stays mounted in register-sale: reload the catalog on each opening so new products show up
    effect(() => {
      if (this.isOpen()) {
        untracked(() => this.loadCatalog());
      }
    });
  }

  ngOnInit(): void {
    const groups = this.quickProductService.groups();
    if (groups.length > 0 && !this.selectedGroupId()) {
      this.selectedGroupId.set(groups[0].id);
    }
  }

  private loadCatalog(): void {
    this.productService.getProducts(undefined, 'ACTIVO').subscribe({
      // POS-11: special products ask for name and price; they are not quick products
      next: (products) => this.catalogProducts.set(products.filter((p) => !isSpecialProduct(p))),
      error: () => {
        this.catalogProducts.set([]);
        this.toast.error('No se pudo cargar el catálogo de productos. Cierra y vuelve a abrir para reintentar.');
      }
    });
  }

  private save<T>(operation: Observable<T>, failure: string, done?: (result: T) => void): void {
    this.isSaving.set(true);
    operation.subscribe({
      next: (result) => {
        this.isSaving.set(false);
        done?.(result);
      },
      error: (err) => {
        this.isSaving.set(false);
        this.toast.error(err?.error?.message || failure);
      }
    });
  }

  get selectedGroup(): QuickProductGroup | null {
    const id = this.selectedGroupId();
    const groups = this.quickProductService.groups();
    if (!id && groups.length > 0) {
      return groups[0];
    }
    return groups.find((g) => g.id === id) ?? null;
  }

  selectGroup(id: string): void {
    this.selectedGroupId.set(id);
  }

  handleCreateGroup(): void {
    const name = this.newGroupName().trim();
    if (!name || this.isSaving()) return;

    this.save(this.quickProductService.createGroup({ name, productIds: [] }), 'No se pudo crear el grupo.', (created) => {
      this.newGroupName.set('');
      this.selectedGroupId.set(created.id);
    });
  }

  handleDeleteGroup(group: QuickProductGroup, event: MouseEvent): void {
    event.stopPropagation();
    this.groupToDelete.set(group);
  }

  cancelDeleteGroup(): void {
    if (!this.isSaving()) {
      this.groupToDelete.set(null);
    }
  }

  confirmDeleteGroup(): void {
    const group = this.groupToDelete();
    if (!group || this.isSaving()) return;
    this.save(this.quickProductService.deleteGroup(group.id), 'No se pudo eliminar el grupo.', () => {
      this.groupToDelete.set(null);
      if (this.selectedGroupId() === group.id) {
        this.selectedGroupId.set(null);
      }
    });
  }

  onDropGroup(event: CdkDragDrop<QuickProductGroup[]>): void {
    if (this.isSaving()) return;
    const groups = [...this.quickProductService.groups()];
    moveItemInArray(groups, event.previousIndex, event.currentIndex);
    const orderedIds = groups.map((g) => g.id);
    this.save(this.quickProductService.reorderGroups(orderedIds), 'No se pudo guardar el orden de los grupos.');
  }

  onDropItem(event: CdkDragDrop<Product[]>): void {
    const group = this.selectedGroup;
    if (!group || this.isSaving()) return;

    const products = [...group.products];
    moveItemInArray(products, event.previousIndex, event.currentIndex);
    const orderedIds = products.map((p) => p.id);
    this.save(this.quickProductService.reorderGroupItems(group.id, orderedIds), 'No se pudo guardar el orden de los productos.');
  }

  handleAddProductToGroup(product: Product): void {
    const group = this.selectedGroup;
    if (!group || this.isSaving()) return;

    if (group.products.some((p) => p.id === product.id)) {
      return; // Ya está en el grupo
    }

    const updatedProductIds = [...group.products.map((p) => p.id), product.id];
    this.save(this.quickProductService.updateGroup(group.id, {
      name: group.name,
      productIds: updatedProductIds
    }), `No se pudo añadir ${product.name} al grupo.`);
  }

  handleRemoveProductFromGroup(productId: string): void {
    const group = this.selectedGroup;
    if (!group || this.isSaving()) return;

    const updatedProductIds = group.products
      .filter((p) => p.id !== productId)
      .map((p) => p.id);

    this.save(this.quickProductService.updateGroup(group.id, {
      name: group.name,
      productIds: updatedProductIds
    }), 'No se pudo quitar el producto del grupo.');
  }

  isProductInCurrentGroup(productId: string): boolean {
    const group = this.selectedGroup;
    if (!group) return false;
    return group.products.some((p) => p.id === productId);
  }

  get filteredCatalogProducts(): Product[] {
    const all = this.catalogProducts();
    if (!this.productSearchTerm().trim()) return all.slice(0, SUGGESTIONS_WITHOUT_SEARCH);
    return searchProducts(this.productSearchTerm(), all);
  }

  handleClose(): void {
    this.close.emit();
  }
}
