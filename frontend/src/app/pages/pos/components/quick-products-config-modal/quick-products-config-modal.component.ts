import { Component, EventEmitter, Input, OnInit, Output, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { CdkDragDrop, DragDropModule, moveItemInArray } from '@angular/cdk/drag-drop';
import { QuickProductService } from '../../../../core/quick-product/quick-product.service';
import { ProductService } from '../../../../core/product/product.service';
import { QuickProductGroup } from '../../../../core/quick-product/models/quick-product.models';
import { Product } from '../../../../core/product/models/product.models';
import { ModalComponent } from '../../../../shared/components/modal/modal.component';

@Component({
  selector: 'app-quick-products-config-modal',
  standalone: true,
  imports: [CommonModule, FormsModule, DragDropModule, ModalComponent],
  templateUrl: './quick-products-config-modal.component.html',
  styleUrls: ['./quick-products-config-modal.component.css']
})
export class QuickProductsConfigModalComponent implements OnInit {
  @Input() isOpen = false;
  @Output() close = new EventEmitter<void>();

  readonly quickProductService = inject(QuickProductService);
  private readonly productService = inject(ProductService);

  catalogProducts = signal<Product[]>([]);
  productSearchTerm = signal<string>('');
  newGroupName = signal<string>('');
  selectedGroupId = signal<string | null>(null);

  ngOnInit(): void {
    this.productService.getProducts(undefined, 'ACTIVO').subscribe({
      next: (products) => this.catalogProducts.set(products),
      error: () => this.catalogProducts.set([])
    });

    const groups = this.quickProductService.groups();
    if (groups.length > 0 && !this.selectedGroupId()) {
      this.selectedGroupId.set(groups[0].id);
    }
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
    if (!name) return;

    this.quickProductService.createGroup({ name, productIds: [] }).subscribe({
      next: (created) => {
        this.newGroupName.set('');
        this.selectedGroupId.set(created.id);
      }
    });
  }

  handleDeleteGroup(groupId: string, event: MouseEvent): void {
    event.stopPropagation();
    if (confirm('¿Estás seguro de eliminar este grupo de productos rápidos?')) {
      this.quickProductService.deleteGroup(groupId).subscribe();
    }
  }

  onDropGroup(event: CdkDragDrop<QuickProductGroup[]>): void {
    const groups = [...this.quickProductService.groups()];
    moveItemInArray(groups, event.previousIndex, event.currentIndex);
    const orderedIds = groups.map((g) => g.id);
    this.quickProductService.reorderGroups(orderedIds).subscribe();
  }

  onDropItem(event: CdkDragDrop<Product[]>): void {
    const group = this.selectedGroup;
    if (!group) return;

    const products = [...group.products];
    moveItemInArray(products, event.previousIndex, event.currentIndex);
    const orderedIds = products.map((p) => p.id);
    this.quickProductService.reorderGroupItems(group.id, orderedIds).subscribe();
  }

  handleAddProductToGroup(product: Product): void {
    const group = this.selectedGroup;
    if (!group) return;

    if (group.products.some((p) => p.id === product.id)) {
      return; // Ya está en el grupo
    }

    const updatedProductIds = [...group.products.map((p) => p.id), product.id];
    this.quickProductService.updateGroup(group.id, {
      name: group.name,
      productIds: updatedProductIds
    }).subscribe();
  }

  handleRemoveProductFromGroup(productId: string): void {
    const group = this.selectedGroup;
    if (!group) return;

    const updatedProductIds = group.products
      .filter((p) => p.id !== productId)
      .map((p) => p.id);

    this.quickProductService.updateGroup(group.id, {
      name: group.name,
      productIds: updatedProductIds
    }).subscribe();
  }

  isProductInCurrentGroup(productId: string): boolean {
    const group = this.selectedGroup;
    if (!group) return false;
    return group.products.some((p) => p.id === productId);
  }

  get filteredCatalogProducts(): Product[] {
    const term = this.productSearchTerm().toLowerCase().trim();
    const all = this.catalogProducts();
    if (!term) return all.slice(0, 10);
    return all
      .filter(
        (p) =>
          p.name.toLowerCase().includes(term) ||
          p.code.toLowerCase().includes(term)
      )
      .slice(0, 15);
  }

  handleClose(): void {
    this.close.emit();
  }
}
