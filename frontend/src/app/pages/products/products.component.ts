import { Component, DOCUMENT, OnInit, computed, inject, signal, DestroyRef, effect, untracked } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { FormBuilder, FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
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
import { ProductPhotoSectionComponent } from './product-photo-section/product-photo-section.component';
import { ProductPhotoThumbComponent } from './product-photo-thumb/product-photo-thumb.component';
import { ProductBarcodesSectionComponent } from './product-barcodes-section/product-barcodes-section.component';
import { ToastService } from '../../shared/components/toast/toast.service';
import { SearchSelectComponent, SearchSelectOption } from '../../shared/components/search-select/search-select.component';
import { canonicalBarcode, looksLikeBarcode } from '../../shared/utils/barcode';
import { formatBs } from '../../shared/utils/money';
import { ScanCapture } from '../../shared/utils/barcode-scan';
import { searchProducts } from '../../shared/utils/product-search';
import { lineAmount } from '../pos/register-sale/cart-item';
import { IconComponent } from '../../shared/components/icon/icon.component';
import { ConnectionNoticeComponent } from '../../shared/components/connection-notice/connection-notice.component';

export type ProductStatusFilter = 'ACTIVE_INACTIVE' | 'ACTIVO' | 'INACTIVO' | 'ELIMINADO';

// CAT-15: editable row of the Empaques section (the presentations of CAT-11); the server validates the rules
export interface PresentationDraft {
  id: string | null;
  name: string;
  baseQuantity: number | null;
  fixedPrice: number | null;
  active: boolean;
  sold: boolean;
  barcode: string;
}

export const MAX_ACTIVE_PRESENTATIONS = 6;

function suggestedDrafts(): PresentationDraft[] {
  return SUGGESTED_PRESENTATIONS.map((p) => ({
    id: null, name: p.name, baseQuantity: p.baseQuantity, fixedPrice: null, active: true, sold: true, barcode: ''
  }));
}

export const PRODUCTS_PAGE_SIZE = 100;

@Component({
  selector: 'app-products',
  standalone: true,
  imports: [IconComponent, SearchSelectComponent, ConnectionNoticeComponent,
    CommonModule,
    ReactiveFormsModule,
    ConfirmModalComponent,
    AuditModalComponent,
    ModalComponent,
    ProductImportModalComponent,
    ProductPhotoSectionComponent,
    ProductPhotoThumbComponent,
    ProductBarcodesSectionComponent,
    RouterLink
  ],
  templateUrl: './products.component.html',
  styleUrls: ['./products.component.css']
})
export class ProductsComponent implements OnInit {
  private readonly toast = inject(ToastService);
  private readonly document = inject(DOCUMENT);
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
  // CAT-12: combined with the status filter and the search
  onlyWithoutPhoto = signal<boolean>(false);
  isLoading = signal<boolean>(false);
  errorMessage = signal<string | null>(null);
  // Lists the form and the filters choose from; a failed one would otherwise look like an empty list
  catalogLoadFailures = signal<string[]>([]);
  catalogLoadMessage = computed(() => {
    const failed = this.catalogLoadFailures();
    return failed.length > 0
      ? `No se pudieron cargar ${failed.length > 1 ? failed.slice(0, -1).join(', ') + ' y ' + failed[failed.length - 1] : failed[0]}. Revisa la conexión antes de elegirlos.`
      : null;
  });

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

  // CAT-15: packages of the product and warnings of the last save
  presentations = signal<PresentationDraft[]>([]);
  // The sale unit the packages were written for: changing it turns them off (CAT-15)
  private formSaleUnit: string = 'UNIT';
  private originalPresentations = signal<string>('[]');
  saveWarnings = signal<string[]>([]);
  readonly maxActivePresentations = MAX_ACTIVE_PRESENTATIONS;

  // CAT-10: barcodes of the product in the form
  barcodes = signal<string[]>([]);
  private originalBarcodes = signal<string[]>([]);
  formNoBarcode = signal<boolean>(false);

  // Custom select dropdowns in the form
  // Picker of the tag to add: it only adds, the chosen tags live in tagIds
  readonly tagPicker = new FormControl<string | null>(null);

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

  // Products filtered by status and search (POS-01 search rules)
  // Rows drawn at a time (the catalog has ~850 products); a new search or filter starts again from the first page
  visibleCount = signal<number>(PRODUCTS_PAGE_SIZE);
  visibleProducts = computed(() => this.filteredProducts().slice(0, this.visibleCount()));
  hiddenCount = computed(() => Math.max(0, this.filteredProducts().length - this.visibleCount()));
  private readonly resetVisible = effect(() => {
    this.filteredProducts();
    untracked(() => this.visibleCount.set(PRODUCTS_PAGE_SIZE));
  });

  showMore(): void {
    this.visibleCount.update((count) => count + PRODUCTS_PAGE_SIZE);
  }

  filteredProducts = computed(() => {
    const term = this.searchTerm();
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

    if (this.onlyWithoutPhoto()) {
      list = list.filter(p => !p.photoId);
    }

    // A search orders by relevance; without one, by name A-Z
    if (term.trim().length > 0) {
      return searchProducts(term, list);
    }
    return [...list].sort((a, b) => a.name.localeCompare(b.name, 'es', { sensitivity: 'base' }));
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
  categoryOptions = computed<SearchSelectOption[]>(() =>
    this.categories().map((c) => ({ value: c.id, label: c.name })));

  supplierOptions = computed<SearchSelectOption[]>(() =>
    this.suppliers().map((s) => ({ value: s.id, label: s.name, hint: s.code })));

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

  tagOptions = computed<SearchSelectOption[]>(() =>
    this.availableTagsObjects().map((t) => ({ value: t.id, label: t.name })));

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
           this.presentationsModified() ||
           this.barcodesModified();
  });

  isKilogramForm = computed<boolean>(() => {
    this.formVersion();
    return this.productForm.get('saleUnit')?.value === 'KILOGRAM';
  });

  activePresentationCount = computed<number>(() => this.presentations().filter((p) => p.active).length);

  presentationsModified = computed<boolean>(() =>
    this.isEditing() && JSON.stringify(this.presentations()) !== this.originalPresentations()
  );

  barcodesModified = computed<boolean>(() =>
    JSON.stringify(this.barcodes()) !== JSON.stringify(this.originalBarcodes())
  );

  // CAT-10: the section is the last of the form, so the fixed header says which product the code is being saved on
  formSubtitle = computed<string>(() => {
    this.formVersion();
    const name = (this.productForm.get('name')?.value ?? '').toString().trim();
    if (this.barcodesModified() && name) {
      const price = this.productForm.get('sellPrice')?.value;
      return price === null || price === undefined || price === '' ? name : `${name} · ${formatBs(Number(price))}`;
    }
    return this.isEditing()
      ? 'Modifica los datos del producto seleccionado'
      : 'Ingresa la información comercial y características del nuevo producto';
  });

  // CAT-10: a product by kilo with codes still shows them, so they can be removed before saving it as kilo
  showBarcodes = computed<boolean>(() => {
    this.formVersion();
    return !this.isSpecialForm() && (!this.isKilogramForm() || this.barcodes().length > 0);
  });

  // CAT-10: on the whole open form, so a scan never writes in another field or saves it, whatever the product is
  private readonly barcodeCapture = effect((onCleanup) => {
    if (!this.isFormModalOpen()) return;
    const capture = new ScanCapture(this.document, (code) => this.onBarcodeScanned(code));
    capture.install();
    onCleanup(() => capture.uninstall());
  });

  // The barcode section leaves the form scrolled to its end, and the error is at its start
  private readonly showFormError = effect(() => {
    if (this.formError()) {
      setTimeout(() => this.document.querySelector('.form-error')?.scrollIntoView({ block: 'nearest' }));
    }
  });

  private onBarcodeScanned(code: string): void {
    // A confirmation over the form does not take the focus, so the capture has to stay on while it is open
    if (this.document.querySelectorAll('[aria-modal="true"]').length > 1) {
      this.toast.error('Cierra esta ventana para escanear.');
      return;
    }
    // CAT-15: with the cursor in the code of a package, the scan is that package's code
    const packageIndex = this.document.activeElement?.getAttribute('data-package-barcode');
    if (packageIndex !== null && packageIndex !== undefined) {
      if (!looksLikeBarcode(code)) {
        this.toast.error('El código de barras no es válido.');
        return;
      }
      this.updatePresentation(Number(packageIndex), 'barcode', canonicalBarcode(code));
      return;
    }
    if (!this.showBarcodes()) {
      this.toast.error('Este producto no admite código de barras.');
      return;
    }
    if (!looksLikeBarcode(code)) {
      this.toast.error('El código de barras no es válido.');
      return;
    }
    const barcode = canonicalBarcode(code);
    if (!this.barcodes().includes(barcode)) {
      this.barcodes.update((list) => [...list, barcode]);
    }
    setTimeout(() => this.document.querySelector('.barcodes-section')?.scrollIntoView({ block: 'nearest' }));
  }

  clearNoBarcodeMark(): void {
    const id = this.selectedProductId();
    if (!id) return;
    this.productService.clearNoBarcode(id).subscribe({
      next: (updated) => {
        this.formNoBarcode.set(false);
        this.products.update(prev => prev.map(p => p.id === updated.id ? updated : p));
      },
      error: (err) => this.formError.set(err.error?.message || 'No se pudo quitar la marca.')
    });
  }

  ngOnInit(): void {
    this.productForm.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => {
        this.formVersion.update(v => v + 1);
      });

    this.tagPicker.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((tagId) => {
        const tag = this.tags().find(t => t.id === tagId);
        if (tag) {
          this.addTag(tag);
          this.tagPicker.setValue(null, { emitEvent: false });
        }
      });

    // CAT-15: changing between unit and kilo turns off the packages, whose base quantity changes meaning; switching to
    // Kilo proposes 100 g and 1/2 libra (CAT-11)
    this.productForm.get('saleUnit')?.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((unit) => {
        if (!unit || unit === this.formSaleUnit) return;
        this.formSaleUnit = unit;
        this.presentations.update((list) => list.filter((p) => p.id).map((p) => ({ ...p, active: false })));
        if (unit === 'KILOGRAM' && !this.presentations().some((p) => p.active)) {
          this.presentations.update((list) => [...list, ...suggestedDrafts()]);
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
      this.barcodes.set([]);
    } else {
      sell?.setValidators([Validators.required, Validators.min(0.01)]);
    }
    owner?.setValidators(type === 'THIRD_PARTY' ? [Validators.required] : []);
    sell?.updateValueAndValidity({ emitEvent: false });
    owner?.updateValueAndValidity({ emitEvent: false });
    this.formVersion.update(v => v + 1);
  }

  loadCatalogDependencies(): void {
    this.catalogLoadFailures.set([]);
    const failed = (what: string) => this.catalogLoadFailures.update(list => [...list, what]);
    this.creditorService.getCreditors('ACTIVO').subscribe({
      next: (data) => this.creditors.set(data),
      error: () => {
        this.creditors.set([]);
        failed('los dueños');
      }
    });
    // Load the non-deleted categories
    this.categoryService.getCategories(undefined, 'ACTIVO').subscribe({
      next: (data) => this.categories.set(data),
      error: () => failed('las categorías')
    });

    // Cargar proveedores activos
    this.supplierService.getSuppliers(undefined, 'ACTIVO').subscribe({
      next: (data) => this.suppliers.set(data),
      error: () => failed('los proveedores')
    });

    // Cargar etiquetas activas
    this.tagService.getTags(undefined, 'ACTIVO').subscribe({
      next: (data) => this.tags.set(data),
      error: () => failed('las etiquetas')
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
        this.errorMessage.set('Error al cargar el catálogo de productos. Por favor intenta nuevamente.');
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

  selectCategory(category: Category): void {
    this.productForm.patchValue({ categoryId: category.id });
    this.productForm.get('categoryId')?.markAsTouched();
    this.formVersion.update(v => v + 1);
  }

  addTag(tag: Tag): void {
    const currentTagIds: string[] = this.productForm.get('tagIds')?.value || [];
    if (!currentTagIds.includes(tag.id)) {
      this.productForm.patchValue({ tagIds: [...currentTagIds, tag.id] });
      this.formVersion.update(v => v + 1);
    }
  }

  removeTag(tagId: string): void {
    const currentTagIds: string[] = this.productForm.get('tagIds')?.value || [];
    this.productForm.patchValue({ tagIds: currentTagIds.filter(id => id !== tagId) });
    this.formVersion.update(v => v + 1);
  }


  // --- Opening and closing modals ---

  openCreateModal(): void {
    this.isEditing.set(false);
    this.selectedProductId.set(null);
    this.formError.set(null);
    this.originalFormValues.set(null);

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
    this.formSaleUnit = 'UNIT';
    this.presentations.set([]);
    this.originalPresentations.set('[]');
    this.barcodes.set([]);
    this.originalBarcodes.set([]);
    this.formNoBarcode.set(false);
    this.formVersion.update(v => v + 1);
    this.isFormModalOpen.set(true);
  }

  openEditModal(product: Product): void {
    if (product.status === 'ELIMINADO') return;

    this.isEditing.set(true);
    this.selectedProductId.set(product.id);
    this.formError.set(null);

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
      baseQuantity: p.baseQuantity,
      fixedPrice: p.fixedPrice,
      active: p.active,
      sold: p.sold,
      barcode: p.barcode ?? ''
    }));
    this.formSaleUnit = product.saleUnit ?? 'UNIT';
    this.presentations.set(drafts);
    this.originalPresentations.set(JSON.stringify(drafts));
    this.barcodes.set(product.barcodes ?? []);
    this.originalBarcodes.set(product.barcodes ?? []);
    this.formNoBarcode.set(!!product.noBarcode);
    this.originalFormValues.set(initialData);
    this.productForm.reset(initialData);
    this.formVersion.update(v => v + 1);
    this.isFormModalOpen.set(true);
  }

  // CAT-12: the photo of the product open in the form
  selectedProductPhotoId = computed<string | null>(() => {
    const id = this.selectedProductId();
    return this.products().find(p => p.id === id)?.photoId ?? null;
  });

  onPhotoChange(change: { productId: string; photoId: string | null }): void {
    this.products.update(list => list.map(p => (p.id === change.productId ? { ...p, photoId: change.photoId } : p)));
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
    this.formSaleUnit = 'UNIT';
    this.presentations.set([]);
    this.originalPresentations.set('[]');
    this.barcodes.set([]);
    this.originalBarcodes.set([]);
    this.formNoBarcode.set(false);
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
      presentations: !val.specialType ? this.presentationRequests() : undefined,
      specialType: val.specialType || null,
      ownerCreditorId: val.specialType === 'THIRD_PARTY' ? val.ownerCreditorId : null,
      barcodes: this.barcodesModified() ? this.barcodes() : undefined
    };
    this.saveWarnings.set([]);

    if (this.isEditing() && this.selectedProductId()) {
      this.productService.updateProduct(this.selectedProductId()!, request).subscribe({
        next: (updated) => {
          this.toast.success(`Producto ${updated.name} actualizado.`);
          this.products.update(prev => prev.map(p => p.id === updated.id ? updated : p));
          this.saveWarnings.set(updated.warnings ?? []);
          this.isSubmitting.set(false);
          this.isFormModalOpen.set(false);
          this.originalFormValues.set(null);
        },
        error: (err) => {
          this.isSubmitting.set(false);
          this.formError.set(err.error?.message || 'Error al actualizar el producto. Revisa los datos.');
        }
      });
    } else {
      this.productService.createProduct(request).subscribe({
        next: (created) => {
          this.toast.success(`Producto ${created.name} creado.`);
          this.products.update(prev => [...prev, created]);
          this.saveWarnings.set(created.warnings ?? []);
          this.isSubmitting.set(false);
          this.isFormModalOpen.set(false);
          this.originalFormValues.set(null);
        },
        error: (err) => {
          this.isSubmitting.set(false);
          this.formError.set(err.error?.message || 'Error al crear el producto. Revisa los datos.');
        }
      });
    }
  }

  // --- Empaques (CAT-15) ---

  addPresentation(): void {
    this.presentations.update((list) => [...list,
      { id: null, name: '', baseQuantity: null, fixedPrice: null, active: true, sold: this.isKilogramForm(), barcode: '' }]);
  }

  updatePresentation(index: number, field: 'name' | 'baseQuantity' | 'fixedPrice' | 'barcode', value: string): void {
    const parsed = field === 'name' || field === 'barcode' ? value : value === '' || value === null ? null : Number(value);
    this.presentations.update((list) => list.map((p, i) => (i === index ? { ...p, [field]: parsed } : p)));
  }

  // A package that is not sold has no price of its own
  setPresentationSold(index: number, sold: boolean): void {
    this.presentations.update((list) => list.map((p, i) => (i === index ? { ...p, sold, fixedPrice: sold ? p.fixedPrice : null } : p)));
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

  // Preview; the server recomputes. Without its own price: base quantity x price
  presentationPricePreview(draft: PresentationDraft): number | null {
    if (!draft.sold) return null;
    if (draft.fixedPrice) return draft.fixedPrice;
    const sell = Number(this.productForm.get('sellPrice')?.value) || 0;
    return draft.baseQuantity ? lineAmount(Number(draft.baseQuantity), sell) : null;
  }

  // Rounded like the server (HALF_UP on the exact product): 100 g at Bs 40,15/kg is Bs 4,02
  presentationCostPreview(draft: PresentationDraft): number | null {
    const buy = Number(this.productForm.get('buyPrice')?.value) || 0;
    return draft.baseQuantity ? lineAmount(Number(draft.baseQuantity), buy) : null;
  }

  private presentationRequests(): ProductPresentationRequest[] {
    return this.presentations().map((p) => ({
      id: p.id,
      name: p.name.trim(),
      baseQuantity: Number(p.baseQuantity),
      fixedPrice: p.sold && p.fixedPrice ? Number(p.fixedPrice) : null,
      active: p.active,
      sold: p.sold,
      barcode: p.barcode.trim()
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
        this.toast.success('Producto eliminado.');
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
