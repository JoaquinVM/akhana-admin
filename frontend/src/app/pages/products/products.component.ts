import { Component, OnInit, computed, inject, signal, DestroyRef, HostListener } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Product, ProductRequest, ProductStatus } from '../../core/product/models/product.models';
import { ProductService } from '../../core/product/product.service';
import { CategoryService } from '../../core/category/category.service';
import { Category } from '../../core/category/models/category.models';
import { SupplierService } from '../../core/supplier/supplier.service';
import { Supplier } from '../../core/supplier/models/supplier.models';
import { TagService } from '../../core/tag/tag.service';
import { Tag } from '../../core/tag/models/tag.models';
import { ConfirmModalComponent } from '../../shared/components/confirm-modal/confirm-modal.component';
import { AuditModalComponent } from '../../shared/components/audit-modal/audit-modal.component';
import { ModalComponent } from '../../shared/components/modal/modal.component';

export type ProductStatusFilter = 'ACTIVE_INACTIVE' | 'ACTIVO' | 'INACTIVO' | 'ELIMINADO';

@Component({
  selector: 'app-products',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    ConfirmModalComponent,
    AuditModalComponent,
    ModalComponent
  ],
  templateUrl: './products.component.html',
  styleUrls: ['./products.component.css']
})
export class ProductsComponent implements OnInit {
  private readonly productService = inject(ProductService);
  private readonly categoryService = inject(CategoryService);
  private readonly supplierService = inject(SupplierService);
  private readonly tagService = inject(TagService);
  private readonly fb = inject(FormBuilder);
  private readonly destroyRef = inject(DestroyRef);

  // Estados Reactivos con Signals
  products = signal<Product[]>([]);
  categories = signal<Category[]>([]);
  suppliers = signal<Supplier[]>([]);
  tags = signal<Tag[]>([]);

  searchTerm = signal<string>('');
  selectedStatusFilter = signal<ProductStatusFilter>('ACTIVE_INACTIVE');
  isLoading = signal<boolean>(false);
  errorMessage = signal<string | null>(null);

  // Modales
  isFormModalOpen = signal<boolean>(false);
  isEditing = signal<boolean>(false);
  selectedProductId = signal<string | null>(null);
  formError = signal<string | null>(null);
  isSubmitting = signal<boolean>(false);
  originalFormValues = signal<Record<string, any> | null>(null);
  formVersion = signal<number>(0);

  // Desplegables de selectores personalizados en formulario
  isCategoryDropdownOpen = signal<boolean>(false);
  isSupplierDropdownOpen = signal<boolean>(false);
  isStatusDropdownOpen = signal<boolean>(false);
  isTagDropdownOpen = signal<boolean>(false);

  // Modal Confirmación al Descartar Cambios
  isDiscardConfirmOpen = signal<boolean>(false);

  // Modal Confirmación de Eliminación
  isConfirmDeleteOpen = signal<boolean>(false);
  productToDelete = signal<Product | null>(null);
  isDeleting = signal<boolean>(false);

  // Modal Auditoría
  isAuditModalOpen = signal<boolean>(false);
  selectedAuditData = signal<Record<string, any> | null>(null);
  auditModalSubtitle = signal<string>('');

  productForm: FormGroup = this.fb.group({
    code: ['', [Validators.required, Validators.maxLength(50)]],
    name: ['', [Validators.required, Validators.maxLength(150)]],
    categoryId: ['', [Validators.required]],
    supplierId: ['', [Validators.required]],
    description: ['', [Validators.maxLength(500)]],
    tagIds: [[] as string[]],
    buyPrice: [null, [Validators.required, Validators.min(0.01)]],
    sellPrice: [null, [Validators.required, Validators.min(0.01)]],
    status: ['ACTIVO']
  });

  // Lista de productos filtrada y ordenada por Nombre A-Z
  filteredProducts = computed(() => {
    const term = this.searchTerm().trim().toLowerCase();
    const statusFilter = this.selectedStatusFilter();
    let list = this.products();

    if (statusFilter === 'ACTIVE_INACTIVE') {
      list = list.filter(p => p.status === 'ACTIVO' || p.status === 'INACTIVO');
    } else if (statusFilter === 'ACTIVO') {
      list = list.filter(p => p.status === 'ACTIVO');
    } else if (statusFilter === 'INACTIVO') {
      list = list.filter(p => p.status === 'INACTIVO');
    } else if (statusFilter === 'ELIMINADO') {
      list = list.filter(p => p.status === 'ELIMINADO');
    }

    const filtered = term.length === 0
      ? list
      : list.filter(p =>
          p.code.toLowerCase().includes(term) ||
          p.name.toLowerCase().includes(term)
        );

    // Orden inicial obligatorio: Nombre ascendente (A-Z)
    return filtered.sort((a, b) => a.name.localeCompare(b.name, 'es', { sensitivity: 'base' }));
  });

  // Cálculo reactivo de utilidad fija
  computedFixedProfit = computed<number>(() => {
    this.formVersion();
    const buy = Number(this.productForm.get('buyPrice')?.value) || 0;
    const sell = Number(this.productForm.get('sellPrice')?.value) || 0;
    if (buy <= 0 && sell <= 0) return 0;
    return Number((sell - buy).toFixed(2));
  });

  // Cálculo reactivo de utilidad porcentual
  computedPercentageProfit = computed<number>(() => {
    this.formVersion();
    const buy = Number(this.productForm.get('buyPrice')?.value) || 0;
    const sell = Number(this.productForm.get('sellPrice')?.value) || 0;
    if (buy <= 0) return 0;
    const profit = sell - buy;
    return Number(((profit / buy) * 100).toFixed(2));
  });

  // Categoría actualmente seleccionada en el formulario
  selectedCategoryObject = computed<Category | null>(() => {
    this.formVersion();
    const catId = this.productForm.get('categoryId')?.value;
    if (!catId) return null;
    return this.categories().find(c => c.id === catId) || null;
  });

  // Proveedor actualmente seleccionado en el formulario
  selectedSupplierObject = computed<Supplier | null>(() => {
    this.formVersion();
    const supId = this.productForm.get('supplierId')?.value;
    if (!supId) return null;
    return this.suppliers().find(s => s.id === supId) || null;
  });

  // Estado actualmente seleccionado en el formulario
  selectedStatusValue = computed<ProductStatus>(() => {
    this.formVersion();
    return (this.productForm.get('status')?.value as ProductStatus) || 'ACTIVO';
  });

  // Etiquetas actualmente seleccionadas en el formulario
  selectedTagsObjects = computed<Tag[]>(() => {
    this.formVersion();
    const tagIds: string[] = this.productForm.get('tagIds')?.value || [];
    return this.tags().filter(t => tagIds.includes(t.id));
  });

  // Etiquetas disponibles para añadir (no seleccionadas aún y activas)
  availableTagsObjects = computed<Tag[]>(() => {
    this.formVersion();
    const selectedIds: string[] = this.productForm.get('tagIds')?.value || [];
    return this.tags().filter(t => t.status !== 'ELIMINADO' && !selectedIds.includes(t.id));
  });

  // Detección reactiva de cambios sin guardar
  hasFormChanges = computed<boolean>(() => {
    this.formVersion();
    const val = this.productForm.value;

    if (!this.isEditing()) {
      const code = (val.code ?? '').toString().trim();
      const name = (val.name ?? '').toString().trim();
      const categoryId = (val.categoryId ?? '').toString().trim();
      const supplierId = (val.supplierId ?? '').toString().trim();
      const description = (val.description ?? '').toString().trim();
      const buyPrice = val.buyPrice !== null && val.buyPrice !== undefined && val.buyPrice !== '';
      const sellPrice = val.sellPrice !== null && val.sellPrice !== undefined && val.sellPrice !== '';
      const hasTags = Array.isArray(val.tagIds) && val.tagIds.length > 0;

      return code.length > 0 || name.length > 0 || categoryId.length > 0 ||
             supplierId.length > 0 || description.length > 0 || buyPrice || sellPrice || hasTags;
    }

    const orig = this.originalFormValues();
    if (!orig) return false;

    return this.isFieldModified('code') ||
           this.isFieldModified('name') ||
           this.isFieldModified('categoryId') ||
           this.isFieldModified('supplierId') ||
           this.isFieldModified('description') ||
           this.isFieldModified('tagIds') ||
           this.isFieldModified('buyPrice') ||
           this.isFieldModified('sellPrice') ||
           this.isFieldModified('status');
  });

  ngOnInit(): void {
    this.productForm.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => {
        this.formVersion.update(v => v + 1);
      });

    this.loadCatalogDependencies();
    this.loadProducts();
  }

  loadCatalogDependencies(): void {
    // Cargar categorías no eliminadas
    this.categoryService.getCategories(undefined, 'ACTIVO').subscribe({
      next: (data) => this.categories.set(data),
      error: () => console.warn('Error al cargar categorías para productos.')
    });

    // Cargar proveedores activos
    this.supplierService.getSuppliers(undefined, 'ACTIVO').subscribe({
      next: (data) => this.suppliers.set(data),
      error: () => console.warn('Error al cargar proveedores para productos.')
    });

    // Cargar etiquetas activas
    this.tagService.getTags(undefined, 'ACTIVO').subscribe({
      next: (data) => this.tags.set(data),
      error: () => console.warn('Error al cargar etiquetas para productos.')
    });
  }

  loadProducts(): void {
    this.isLoading.set(true);
    this.errorMessage.set(null);

    const filter = this.selectedStatusFilter();

    this.productService.getProducts(undefined, filter).subscribe({
      next: (data) => {
        this.products.set(data);
        this.isLoading.set(false);
      },
      error: () => {
        this.errorMessage.set('Error al cargar el catálogo de productos. Por favor intente nuevamente.');
        this.isLoading.set(false);
      }
    });
  }

  setStatusFilter(filter: ProductStatusFilter): void {
    if (this.selectedStatusFilter() !== filter) {
      this.selectedStatusFilter.set(filter);
      this.loadProducts();
    }
  }

  onSearch(event: Event): void {
    const value = (event.target as HTMLInputElement).value;
    this.searchTerm.set(value);
  }

  // --- Identificación de Campos Modificados ---

  isFieldModified(fieldName: string): boolean {
    this.formVersion();
    if (!this.isEditing()) return false;
    const orig = this.originalFormValues();
    if (!orig) return false;

    if (fieldName === 'tagIds') {
      const currentTags: string[] = (this.productForm.get('tagIds')?.value || []).slice().sort();
      const origTags: string[] = (orig['tagIds'] || []).slice().sort();
      if (currentTags.length !== origTags.length) return true;
      return currentTags.some((val, idx) => val !== origTags[idx]);
    }

    if (fieldName === 'buyPrice' || fieldName === 'sellPrice') {
      const currentNum = Number(this.productForm.get(fieldName)?.value);
      const origNum = Number(orig[fieldName]);
      return currentNum !== origNum;
    }

    const currentVal = (this.productForm.get(fieldName)?.value ?? '').toString().trim();
    const origVal = (orig[fieldName] ?? '').toString().trim();
    return currentVal !== origVal;
  }

  // --- Selección de Categoría, Proveedor, Estado y Etiquetas ---

  closeAllDropdowns(): void {
    this.isCategoryDropdownOpen.set(false);
    this.isSupplierDropdownOpen.set(false);
    this.isStatusDropdownOpen.set(false);
    this.isTagDropdownOpen.set(false);
  }

  toggleCategoryDropdown(): void {
    const open = !this.isCategoryDropdownOpen();
    this.closeAllDropdowns();
    this.isCategoryDropdownOpen.set(open);
  }

  selectCategory(category: Category): void {
    this.productForm.patchValue({ categoryId: category.id });
    this.productForm.get('categoryId')?.markAsTouched();
    this.isCategoryDropdownOpen.set(false);
    this.formVersion.update(v => v + 1);
  }

  toggleSupplierDropdown(): void {
    const open = !this.isSupplierDropdownOpen();
    this.closeAllDropdowns();
    this.isSupplierDropdownOpen.set(open);
  }

  selectSupplier(supplier: Supplier): void {
    this.productForm.patchValue({ supplierId: supplier.id });
    this.productForm.get('supplierId')?.markAsTouched();
    this.isSupplierDropdownOpen.set(false);
    this.formVersion.update(v => v + 1);
  }

  toggleStatusDropdown(): void {
    const open = !this.isStatusDropdownOpen();
    this.closeAllDropdowns();
    this.isStatusDropdownOpen.set(open);
  }

  selectStatus(status: ProductStatus): void {
    this.productForm.patchValue({ status });
    this.productForm.get('status')?.markAsTouched();
    this.isStatusDropdownOpen.set(false);
    this.formVersion.update(v => v + 1);
  }

  toggleTagDropdown(): void {
    const open = !this.isTagDropdownOpen();
    this.closeAllDropdowns();
    this.isTagDropdownOpen.set(open);
  }

  addTag(tag: Tag): void {
    const currentTagIds: string[] = this.productForm.get('tagIds')?.value || [];
    if (!currentTagIds.includes(tag.id)) {
      this.productForm.patchValue({ tagIds: [...currentTagIds, tag.id] });
      this.formVersion.update(v => v + 1);
    }
    this.isTagDropdownOpen.set(false);
  }

  removeTag(tagId: string): void {
    const currentTagIds: string[] = this.productForm.get('tagIds')?.value || [];
    this.productForm.patchValue({ tagIds: currentTagIds.filter(id => id !== tagId) });
    this.formVersion.update(v => v + 1);
  }

  @HostListener('document:click', ['$event'])
  onDocumentClick(event: MouseEvent): void {
    if (!this.isFormModalOpen()) return;
    const target = event.target as HTMLElement;
    if (!target.closest('.position-relative')) {
      this.closeAllDropdowns();
    }
  }

  // --- Apertura y Cierre de Modales ---

  openCreateModal(): void {
    this.isEditing.set(false);
    this.selectedProductId.set(null);
    this.formError.set(null);
    this.originalFormValues.set(null);
    this.closeAllDropdowns();

    this.productForm.reset({
      code: '',
      name: '',
      categoryId: '',
      supplierId: '',
      description: '',
      tagIds: [],
      buyPrice: null,
      sellPrice: null,
      status: 'ACTIVO'
    });
    this.formVersion.update(v => v + 1);
    this.isFormModalOpen.set(true);
  }

  openEditModal(product: Product): void {
    if (product.status === 'ELIMINADO') return;

    this.isEditing.set(true);
    this.selectedProductId.set(product.id);
    this.formError.set(null);
    this.closeAllDropdowns();

    const initialData = {
      code: product.code || '',
      name: product.name || '',
      categoryId: product.categoryId || '',
      supplierId: product.supplierId || '',
      description: product.description || '',
      tagIds: product.tags ? product.tags.map(t => t.id) : [],
      buyPrice: product.buyPrice,
      sellPrice: product.sellPrice,
      status: product.status
    };

    this.originalFormValues.set(initialData);
    this.productForm.reset(initialData);
    this.formVersion.update(v => v + 1);
    this.isFormModalOpen.set(true);
  }

  attemptCloseForm(): void {
    if (this.isSubmitting()) return;

    if (this.hasFormChanges()) {
      this.isDiscardConfirmOpen.set(true);
    } else {
      this.closeFormModal();
    }
  }

  discardChanges(): void {
    this.isDiscardConfirmOpen.set(false);
    this.closeFormModal();
  }

  cancelDiscard(): void {
    this.isDiscardConfirmOpen.set(false);
  }

  closeFormModal(): void {
    this.isFormModalOpen.set(false);
    this.formError.set(null);
    this.originalFormValues.set(null);
    this.closeAllDropdowns();
    this.productForm.reset({
      code: '',
      name: '',
      categoryId: '',
      supplierId: '',
      description: '',
      tagIds: [],
      buyPrice: null,
      sellPrice: null,
      status: 'ACTIVO'
    });
    this.formVersion.update(v => v + 1);
  }

  saveProduct(): void {
    if (this.isEditing() && !this.hasFormChanges()) {
      return;
    }

    if (this.productForm.invalid) {
      this.productForm.markAllAsTouched();
      return;
    }

    this.isSubmitting.set(true);
    this.formError.set(null);

    const val = this.productForm.value;
    const request: ProductRequest = {
      code: val.code.trim(),
      name: val.name.trim(),
      categoryId: val.categoryId,
      supplierId: val.supplierId,
      description: val.description ? val.description.trim() : null,
      tagIds: val.tagIds || [],
      buyPrice: Number(val.buyPrice),
      sellPrice: Number(val.sellPrice),
      status: this.isEditing() ? val.status : 'ACTIVO'
    };

    if (this.isEditing() && this.selectedProductId()) {
      this.productService.updateProduct(this.selectedProductId()!, request).subscribe({
        next: (updated) => {
          this.products.update(prev => prev.map(p => p.id === updated.id ? updated : p));
          this.isSubmitting.set(false);
          this.isFormModalOpen.set(false);
          this.originalFormValues.set(null);
        },
        error: (err) => {
          this.isSubmitting.set(false);
          this.formError.set(err.error?.message || 'Error al actualizar el producto. Verifique los datos.');
        }
      });
    } else {
      this.productService.createProduct(request).subscribe({
        next: (created) => {
          this.products.update(prev => [...prev, created]);
          this.isSubmitting.set(false);
          this.isFormModalOpen.set(false);
          this.originalFormValues.set(null);
        },
        error: (err) => {
          this.isSubmitting.set(false);
          this.formError.set(err.error?.message || 'Error al crear el producto. Verifique los datos.');
        }
      });
    }
  }

  // --- Eliminación Lógica ---

  openDeleteModal(product: Product): void {
    if (product.status === 'ELIMINADO') return;
    this.productToDelete.set(product);
    this.isConfirmDeleteOpen.set(true);
  }

  cancelDelete(): void {
    this.isConfirmDeleteOpen.set(false);
    this.productToDelete.set(null);
  }

  confirmDelete(): void {
    const product = this.productToDelete();
    if (!product) return;

    this.isDeleting.set(true);
    this.productService.deleteProduct(product.id).subscribe({
      next: () => {
        if (this.selectedStatusFilter() === 'ELIMINADO') {
          this.products.update(prev => prev.map(p => p.id === product.id ? { ...p, status: 'ELIMINADO' as const } : p));
        } else {
          this.products.update(prev => prev.filter(p => p.id !== product.id));
        }
        this.isDeleting.set(false);
        this.isConfirmDeleteOpen.set(false);
        this.productToDelete.set(null);
      },
      error: (err) => {
        this.isDeleting.set(false);
        this.errorMessage.set(err.error?.message || 'Error al eliminar el producto.');
        this.isConfirmDeleteOpen.set(false);
      }
    });
  }

  // --- Auditoría ---

  openAuditModal(product: Product): void {
    this.auditModalSubtitle.set(`${product.code} - ${product.name}`);
    this.selectedAuditData.set({
      createdBy: product.createdBy,
      createdAt: product.createdAt,
      updatedBy: product.updatedBy,
      updatedAt: product.updatedAt,
      deletedBy: product.deletedBy,
      deletedAt: product.deletedAt
    });
    this.isAuditModalOpen.set(true);
  }

  closeAuditModal(): void {
    this.isAuditModalOpen.set(false);
    this.selectedAuditData.set(null);
  }
}
