import { Component, OnInit, computed, inject, signal, DestroyRef, HostListener } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import {
  Product,
  ProductPresentationRequest,
  ProductRequest,
  ProductSpecialType,
  ProductStatus,
  SPECIAL_TYPE_LABELS,
  SUGGESTED_PRESENTATIONS
} from '../../core/product/models/product.models';
import { CreditorService } from '../../core/debt/creditor.service';
import { Creditor } from '../../core/debt/models/debt.models';
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
import { ProductImportModalComponent } from './product-import-modal/product-import-modal.component';

export type ProductStatusFilter = 'ACTIVE_INACTIVE' | 'ACTIVO' | 'INACTIVO' | 'ELIMINADO';

// CAT-11: editable row of the Presentaciones section; the server validates the rules
export interface PresentationDraft {
  id: string | null;
  name: string;
  weightKg: number | null;
  fixedPrice: number | null;
  active: boolean;
}

export const MAX_ACTIVE_PRESENTATIONS = 6;

function suggestedDrafts(): PresentationDraft[] {
  return SUGGESTED_PRESENTATIONS.map((p) => ({ id: null, name: p.name, weightKg: p.weightKg, fixedPrice: null, active: true }));
}

@Component({
  selector: 'app-products',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    ConfirmModalComponent,
    AuditModalComponent,
    ModalComponent,
    ProductImportModalComponent
  ],
  templateUrl: './products.component.html',
  styleUrls: ['./products.component.css']
})
export class ProductsComponent implements OnInit {
  private readonly productService = inject(ProductService);
  private readonly categoryService = inject(CategoryService);
  private readonly supplierService = inject(SupplierService);
  private readonly tagService = inject(TagService);
  private readonly creditorService = inject(CreditorService);
  private readonly fb = inject(FormBuilder);
  private readonly destroyRef = inject(DestroyRef);

  // State as signals
  products = signal<Product[]>([]);
  categories = signal<Category[]>([]);
  suppliers = signal<Supplier[]>([]);
  tags = signal<Tag[]>([]);
  // POS-13: active people who can own a third-party product
  creditors = signal<Creditor[]>([]);
  readonly specialTypeLabels = SPECIAL_TYPE_LABELS;

  searchTerm = signal<string>('');
  selectedStatusFilter = signal<ProductStatusFilter>('ACTIVE_INACTIVE');
  isLoading = signal<boolean>(false);
  errorMessage = signal<string | null>(null);

  // Modales
  isFormModalOpen = signal<boolean>(false);
  // CAT-06
  isImportModalOpen = signal<boolean>(false);
  isEditing = signal<boolean>(false);
  selectedProductId = signal<string | null>(null);
  formError = signal<string | null>(null);
  isSubmitting = signal<boolean>(false);
  originalFormValues = signal<Record<string, any> | null>(null);
  formVersion = signal<number>(0);

  // CAT-11: presentations of the product by kilo and warnings of the last save
  presentations = signal<PresentationDraft[]>([]);
  private originalPresentations = signal<string>('[]');
  saveWarnings = signal<string[]>([]);
  readonly maxActivePresentations = MAX_ACTIVE_PRESENTATIONS;

  // Custom select dropdowns in the form
  isCategoryDropdownOpen = signal<boolean>(false);
  isSupplierDropdownOpen = signal<boolean>(false);
  isStatusDropdownOpen = signal<boolean>(false);
  isTagDropdownOpen = signal<boolean>(false);

  // Discard changes confirmation modal
  isDiscardConfirmOpen = signal<boolean>(false);

  // Delete confirmation modal
  isConfirmDeleteOpen = signal<boolean>(false);
  productToDelete = signal<Product | null>(null);
  isDeleting = signal<boolean>(false);

  // Audit modal
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
    buyPrice: [null, [Validators.required, Validators.min(0)]],
    sellPrice: [null, [Validators.required, Validators.min(0.01)]],
    status: ['ACTIVO'],
    saleUnit: ['UNIT'],
    // POS-11: '' is a normal product; type and owner are only chosen on create
    specialType: [''],
    ownerCreditorId: ['']
  });

  // POS-11: un producto especial no lleva costo, precio, unidad ni presentaciones
  isSpecialForm = computed<boolean>(() => {
    this.formVersion();
    return !!this.productForm.get('specialType')?.value;
  });

  // When editing, the owner may be inactive and missing from the list
  hasOwnerOption(): boolean {
    const id = this.productForm.get('ownerCreditorId')?.value;
    return this.creditors().some((c) => c.id === id);
  }

  editingOwnerName(): string {
    const id = this.selectedProductId();
    return this.products().find((p) => p.id === id)?.ownerCreditorName ?? '';
  }

  isThirdPartyForm = computed<boolean>(() => {
    this.formVersion();
    return this.productForm.get('specialType')?.value === 'THIRD_PARTY';
  });

  // Products filtered and sorted by name A-Z
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

  // Fixed profit, computed
  computedFixedProfit = computed<number>(() => {
    this.formVersion();
    const buy = Number(this.productForm.get('buyPrice')?.value) || 0;
    const sell = Number(this.productForm.get('sellPrice')?.value) || 0;
    if (buy <= 0 && sell <= 0) return 0;
    return Number((sell - buy).toFixed(2));
  });

  // Percentage profit, computed
  computedPercentageProfit = computed<number>(() => {
    this.formVersion();
    const buy = Number(this.productForm.get('buyPrice')?.value) || 0;
    const sell = Number(this.productForm.get('sellPrice')?.value) || 0;
    if (buy <= 0) return 0;
    const profit = sell - buy;
    return Number(((profit / buy) * 100).toFixed(2));
  });

  // Category selected in the form
  selectedCategoryObject = computed<Category | null>(() => {
    this.formVersion();
    const catId = this.productForm.get('categoryId')?.value;
    if (!catId) return null;
    return this.categories().find(c => c.id === catId) || null;
  });

  // Supplier selected in the form
  selectedSupplierObject = computed<Supplier | null>(() => {
    this.formVersion();
    const supId = this.productForm.get('supplierId')?.value;
    if (!supId) return null;
    return this.suppliers().find(s => s.id === supId) || null;
  });

  // Status selected in the form
  selectedStatusValue = computed<ProductStatus>(() => {
    this.formVersion();
    return (this.productForm.get('status')?.value as ProductStatus) || 'ACTIVO';
  });

  // Tags selected in the form
  selectedTagsObjects = computed<Tag[]>(() => {
    this.formVersion();
    const tagIds: string[] = this.productForm.get('tagIds')?.value || [];
    return this.tags().filter(t => tagIds.includes(t.id));
  });

  // Tags that can be added (active and not selected yet)
  availableTagsObjects = computed<Tag[]>(() => {
    this.formVersion();
    const selectedIds: string[] = this.productForm.get('tagIds')?.value || [];
    return this.tags().filter(t => t.status !== 'ELIMINADO' && !selectedIds.includes(t.id));
  });

  // Unsaved changes detection
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
           this.isFieldModified('status') ||
           this.isFieldModified('saleUnit') ||
           this.presentationsModified();
  });

  isKilogramForm = computed<boolean>(() => {
    this.formVersion();
    return this.productForm.get('saleUnit')?.value === 'KILOGRAM';
  });

  activePresentationCount = computed<number>(() => this.presentations().filter((p) => p.active).length);

  presentationsModified = computed<boolean>(() =>
    this.isEditing() && JSON.stringify(this.presentations()) !== this.originalPresentations()
  );

  ngOnInit(): void {
    this.productForm.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => {
        this.formVersion.update(v => v + 1);
      });

    // CAT-11: switching to Kilo without presentations proposes 100 g and 1/2 libra
    this.productForm.get('saleUnit')?.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((unit) => {
        if (unit === 'KILOGRAM' && this.presentations().length === 0) {
          this.presentations.set(suggestedDrafts());
        }
      });

    // POS-11: choosing a special type sets the prices to 0 and sale by unit
    this.productForm.get('specialType')?.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((type) => this.applySpecialType(type));

    this.loadCatalogDependencies();
    this.loadProducts();
  }

  private applySpecialType(type: ProductSpecialType | ''): void {
    const sell = this.productForm.get('sellPrice');
    const owner = this.productForm.get('ownerCreditorId');
    if (type) {
      this.productForm.patchValue({ buyPrice: 0, sellPrice: 0, saleUnit: 'UNIT' }, { emitEvent: false });
      sell?.setValidators([Validators.required, Validators.min(0)]);
      this.presentations.set([]);
    } else {
      sell?.setValidators([Validators.required, Validators.min(0.01)]);
    }
    owner?.setValidators(type === 'THIRD_PARTY' ? [Validators.required] : []);
    sell?.updateValueAndValidity({ emitEvent: false });
    owner?.updateValueAndValidity({ emitEvent: false });
    this.formVersion.update(v => v + 1);
  }

  loadCatalogDependencies(): void {
    this.creditorService.getCreditors('ACTIVO').subscribe({
      next: (data) => this.creditors.set(data),
      error: () => this.creditors.set([])
    });
    // Load the non-deleted categories
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

  // --- Modified fields ---

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

  // --- Category, supplier, status and tag selection ---

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

  // --- Opening and closing modals ---

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
      status: 'ACTIVO',
      saleUnit: 'UNIT',
      specialType: '',
      ownerCreditorId: ''
    });
    this.presentations.set([]);
    this.originalPresentations.set('[]');
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
      status: product.status,
      saleUnit: product.saleUnit ?? 'UNIT',
      specialType: product.specialType ?? '',
      ownerCreditorId: product.ownerCreditorId ?? ''
    };

    const drafts: PresentationDraft[] = (product.presentations ?? []).map((p) => ({
      id: p.id,
      name: p.name,
      weightKg: p.weightKg,
      fixedPrice: p.fixedPrice,
      active: p.active
    }));
    this.presentations.set(drafts);
    this.originalPresentations.set(JSON.stringify(drafts));
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
      status: 'ACTIVO',
      saleUnit: 'UNIT',
      specialType: '',
      ownerCreditorId: ''
    });
    this.presentations.set([]);
    this.originalPresentations.set('[]');
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
      status: this.isEditing() ? val.status : 'ACTIVO',
      saleUnit: val.saleUnit,
      presentations: val.saleUnit === 'KILOGRAM' && !val.specialType ? this.presentationRequests() : undefined,
      specialType: val.specialType || null,
      ownerCreditorId: val.specialType === 'THIRD_PARTY' ? val.ownerCreditorId : null
    };
    this.saveWarnings.set([]);

    if (this.isEditing() && this.selectedProductId()) {
      this.productService.updateProduct(this.selectedProductId()!, request).subscribe({
        next: (updated) => {
          this.products.update(prev => prev.map(p => p.id === updated.id ? updated : p));
          this.saveWarnings.set(updated.warnings ?? []);
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
          this.saveWarnings.set(created.warnings ?? []);
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

  // --- Presentaciones (CAT-11) ---

  addPresentation(): void {
    this.presentations.update((list) => [...list, { id: null, name: '', weightKg: null, fixedPrice: null, active: true }]);
  }

  updatePresentation(index: number, field: 'name' | 'weightKg' | 'fixedPrice', value: string): void {
    const parsed = field === 'name' ? value : value === '' || value === null ? null : Number(value);
    this.presentations.update((list) => list.map((p, i) => (i === index ? { ...p, [field]: parsed } : p)));
  }

  // A new one is removed from the list; a saved one is deactivated, never deleted
  removePresentation(index: number): void {
    const draft = this.presentations()[index];
    if (!draft.id) {
      this.presentations.update((list) => list.filter((_, i) => i !== index));
      return;
    }
    this.setPresentationActive(index, false);
  }

  setPresentationActive(index: number, active: boolean): void {
    this.presentations.update((list) => list.map((p, i) => (i === index ? { ...p, active } : p)));
  }

  // Preview; the server recomputes. Without a fixed price: weight x price per kilo
  presentationPricePreview(draft: PresentationDraft): number | null {
    if (draft.fixedPrice) return draft.fixedPrice;
    const sell = Number(this.productForm.get('sellPrice')?.value) || 0;
    return draft.weightKg ? Math.round(draft.weightKg * sell * 100) / 100 : null;
  }

  presentationCostPreview(draft: PresentationDraft): number | null {
    const buy = Number(this.productForm.get('buyPrice')?.value) || 0;
    return draft.weightKg ? Math.round(draft.weightKg * buy * 100) / 100 : null;
  }

  private presentationRequests(): ProductPresentationRequest[] {
    return this.presentations().map((p) => ({
      id: p.id,
      name: p.name.trim(),
      weightKg: Number(p.weightKg),
      fixedPrice: p.fixedPrice ? Number(p.fixedPrice) : null,
      active: p.active
    }));
  }

  // --- Logical delete ---

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

  // --- Audit ---

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
