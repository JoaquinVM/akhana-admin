import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { FinancialCategory, FinancialCategoryRequest, FinancialCategoryType } from '../../../core/finance/models/finance.models';
import { FinancialCategoryService } from '../../../core/finance/financial-category.service';
import { ModalComponent } from '../../../shared/components/modal/modal.component';
import { ConfirmModalComponent } from '../../../shared/components/confirm-modal/confirm-modal.component';
import { AuditModalComponent } from '../../../shared/components/audit-modal/audit-modal.component';
import { ColorPickerComponent } from '../../../shared/components/color-picker/color-picker.component';

export type CategoryTypeFilter = 'TODOS' | 'INGRESO' | 'GASTO';

@Component({
  selector: 'app-financial-categories',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    ModalComponent,
    ConfirmModalComponent,
    AuditModalComponent,
    ColorPickerComponent
  ],
  templateUrl: './categories.component.html',
  styleUrls: ['./categories.component.css']
})
export class FinancialCategoriesComponent implements OnInit {
  private readonly categoryService = inject(FinancialCategoryService);
  private readonly fb = inject(FormBuilder);

  categories = signal<FinancialCategory[]>([]);
  isLoading = signal<boolean>(false);
  errorMessage = signal<string | null>(null);

  searchTerm = signal<string>('');
  selectedTypeFilter = signal<CategoryTypeFilter>('TODOS');

  // Modal Formulario (Creación / Edición)
  isFormModalOpen = signal<boolean>(false);
  isEditing = signal<boolean>(false);
  selectedCategoryId = signal<string | null>(null);
  formError = signal<string | null>(null);
  isSubmitting = signal<boolean>(false);

  // Modal Eliminar
  isDeleteModalOpen = signal<boolean>(false);
  categoryToDelete = signal<FinancialCategory | null>(null);
  isDeleting = signal<boolean>(false);

  // Modal Auditoría
  isAuditModalOpen = signal<boolean>(false);
  selectedAuditData = signal<Record<string, any> | null>(null);
  auditModalSubtitle = signal<string>('');

  categoryForm: FormGroup = this.fb.group({
    name: ['', [Validators.required, Validators.maxLength(100)]],
    type: ['INGRESO' as FinancialCategoryType, [Validators.required]],
    color: ['#2E5B27', [Validators.required]],
    isSalesDefault: [false]
  });

  filteredCategories = computed(() => {
    const term = this.searchTerm().trim().toLowerCase();
    const filter = this.selectedTypeFilter();
    let list = this.categories();

    if (filter === 'INGRESO') {
      list = list.filter(c => c.type === 'INGRESO');
    } else if (filter === 'GASTO') {
      list = list.filter(c => c.type === 'GASTO');
    }

    if (term.length > 0) {
      list = list.filter(c => c.name.toLowerCase().includes(term));
    }

    return [...list].sort((a, b) => a.name.localeCompare(b.name, 'es', { sensitivity: 'base' }));
  });

  ngOnInit(): void {
    this.loadCategories();
  }

  loadCategories(): void {
    this.isLoading.set(true);
    this.errorMessage.set(null);
    this.categoryService.getCategories().subscribe({
      next: (data) => {
        this.categories.set(data);
        this.isLoading.set(false);
      },
      error: (err) => {
        this.errorMessage.set(err.error?.message || 'Error al cargar las categorías financieras.');
        this.isLoading.set(false);
      }
    });
  }

  setTypeFilter(filter: CategoryTypeFilter): void {
    this.selectedTypeFilter.set(filter);
  }

  onSearch(event: Event): void {
    const val = (event.target as HTMLInputElement).value;
    this.searchTerm.set(val);
  }

  openCreateModal(): void {
    this.isEditing.set(false);
    this.selectedCategoryId.set(null);
    this.formError.set(null);
    this.categoryForm.reset({
      name: '',
      type: 'INGRESO',
      color: '#2E5B27',
      isSalesDefault: false
    });
    this.categoryForm.get('type')?.enable();
    this.isFormModalOpen.set(true);
  }

  openEditModal(category: FinancialCategory): void {
    this.isEditing.set(true);
    this.selectedCategoryId.set(category.id);
    this.formError.set(null);
    this.categoryForm.reset({
      name: category.name,
      type: category.type,
      color: category.color || '#2E5B27',
      isSalesDefault: category.isSalesDefault
    });
    this.categoryForm.get('type')?.disable();
    this.isFormModalOpen.set(true);
  }

  closeFormModal(): void {
    this.isFormModalOpen.set(false);
    this.formError.set(null);
  }

  saveCategory(): void {
    if (this.categoryForm.invalid) {
      this.categoryForm.markAllAsTouched();
      return;
    }

    this.isSubmitting.set(true);
    this.formError.set(null);

    const val = this.categoryForm.getRawValue();
    const req: FinancialCategoryRequest = {
      name: val.name.trim(),
      type: val.type,
      color: val.color || '#2E5B27',
      isSalesDefault: val.type === 'INGRESO' ? !!val.isSalesDefault : false
    };

    if (this.isEditing() && this.selectedCategoryId()) {
      this.categoryService.updateCategory(this.selectedCategoryId()!, req).subscribe({
        next: () => {
          this.isSubmitting.set(false);
          this.isFormModalOpen.set(false);
          this.loadCategories();
        },
        error: (err) => {
          this.isSubmitting.set(false);
          this.formError.set(err.error?.message || 'Error al actualizar la categoría.');
        }
      });
    } else {
      this.categoryService.createCategory(req).subscribe({
        next: () => {
          this.isSubmitting.set(false);
          this.isFormModalOpen.set(false);
          this.loadCategories();
        },
        error: (err) => {
          this.isSubmitting.set(false);
          this.formError.set(err.error?.message || 'Error al crear la categoría.');
        }
      });
    }
  }

  canDelete(category: FinancialCategory): boolean {
    return !category.isSalesDefault;
  }

  getDeleteTooltip(category: FinancialCategory): string {
    if (category.isSalesDefault) {
      return 'La categoría de ventas predeterminada está protegida y no puede eliminarse.';
    }
    return 'Eliminar categoría';
  }

  openDeleteModal(category: FinancialCategory): void {
    if (!this.canDelete(category)) return;
    this.categoryToDelete.set(category);
    this.isDeleteModalOpen.set(true);
  }

  closeDeleteModal(): void {
    this.isDeleteModalOpen.set(false);
    this.categoryToDelete.set(null);
  }

  confirmDelete(): void {
    const cat = this.categoryToDelete();
    if (!cat) return;

    this.isDeleting.set(true);
    this.categoryService.deleteCategory(cat.id).subscribe({
      next: () => {
        this.isDeleting.set(false);
        this.closeDeleteModal();
        this.loadCategories();
      },
      error: (err) => {
        this.isDeleting.set(false);
        this.errorMessage.set(err.error?.message || 'Error al eliminar la categoría.');
        this.closeDeleteModal();
      }
    });
  }

  openAuditModal(category: FinancialCategory): void {
    this.auditModalSubtitle.set(category.name);
    this.selectedAuditData.set({
      createdBy: category.createdBy,
      createdAt: category.createdAt,
      updatedBy: category.updatedBy,
      updatedAt: category.updatedAt,
      deletedBy: category.deletedBy,
      deletedAt: category.deletedAt
    });
    this.isAuditModalOpen.set(true);
  }

  closeAuditModal(): void {
    this.isAuditModalOpen.set(false);
    this.selectedAuditData.set(null);
  }
}
