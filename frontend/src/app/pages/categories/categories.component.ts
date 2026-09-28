import { Component, OnInit, computed, inject, signal, DestroyRef } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Category, CategoryRequest, CategoryStatus } from '../../core/category/models/category.models';
import { CategoryService } from '../../core/category/category.service';
import { ConfirmModalComponent } from '../../shared/components/confirm-modal/confirm-modal.component';
import { AuditModalComponent } from '../../shared/components/audit-modal/audit-modal.component';
import { ModalComponent } from '../../shared/components/modal/modal.component';
import { ColorPickerComponent } from '../../shared/components/color-picker/color-picker.component';

export type CategoryStatusFilter = 'ACTIVO' | 'ELIMINADO';

@Component({
  selector: 'app-categories',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    ConfirmModalComponent,
    AuditModalComponent,
    ModalComponent,
    ColorPickerComponent
  ],
  templateUrl: './categories.component.html',
  styleUrls: ['./categories.component.css']
})
export class CategoriesComponent implements OnInit {
  private readonly categoryService = inject(CategoryService);
  private readonly fb = inject(FormBuilder);
  private readonly destroyRef = inject(DestroyRef);

  // Estados Reactivos con Signals
  categories = signal<Category[]>([]);
  searchTerm = signal<string>('');
  selectedStatusFilter = signal<CategoryStatusFilter>('ACTIVO');
  isLoading = signal<boolean>(false);
  errorMessage = signal<string | null>(null);

  // Modal Formulario (Creación / Edición)
  isFormModalOpen = signal<boolean>(false);
  isEditing = signal<boolean>(false);
  selectedCategoryId = signal<string | null>(null);
  formError = signal<string | null>(null);
  isSubmitting = signal<boolean>(false);
  originalFormValues = signal<Record<string, any> | null>(null);
  formVersion = signal<number>(0);

  // Modal Confirmación al Descartar Cambios
  isDiscardConfirmOpen = signal<boolean>(false);

  // Modal Confirmación de Eliminación Lógica
  isConfirmModalOpen = signal<boolean>(false);
  categoryToDelete = signal<Category | null>(null);
  isDeleting = signal<boolean>(false);

  // Modal Auditoría
  isAuditModalOpen = signal<boolean>(false);
  selectedAuditData = signal<Record<string, any> | null>(null);
  auditModalSubtitle = signal<string>('');

  categoryForm: FormGroup = this.fb.group({
    name: ['', [Validators.required, Validators.maxLength(150)]],
    description: ['', [Validators.maxLength(500)]],
    color: ['', [Validators.required, Validators.maxLength(50)]]
  });

  // Lista de categorías filtrada y ordenada por Nombre A-Z según estado seleccionado
  filteredCategories = computed(() => {
    const term = this.searchTerm().trim().toLowerCase();
    const statusFilter = this.selectedStatusFilter();
    let list = this.categories();

    // Filtro por estado
    list = list.filter(c => c.status === statusFilter);

    // Filtro de búsqueda en tiempo real por nombre
    const filtered = term.length === 0
      ? list
      : list.filter(c => c.name.toLowerCase().includes(term));

    // Orden inicial obligatorio: Nombre ascendente (A-Z)
    return filtered.sort((a, b) => a.name.localeCompare(b.name, 'es', { sensitivity: 'base' }));
  });

  // Señal computada para detectar cambios sin guardar (dirty check)
  hasFormChanges = computed<boolean>(() => {
    this.formVersion(); // Dependencia reactiva
    const formVal = this.categoryForm.value;

    if (!this.isEditing()) {
      // En Creación: Se considera que hay cambios si el usuario ingresó algún valor
      const name = (formVal.name ?? '').toString().trim();
      const description = (formVal.description ?? '').toString().trim();
      const color = (formVal.color ?? '').toString().trim();
      return name.length > 0 || description.length > 0 || color.length > 0;
    }

    // En Edición: Comparar contra los valores originales cargados
    const orig = this.originalFormValues();
    if (!orig) return false;

    return this.isFieldModified('name') ||
           this.isFieldModified('description') ||
           this.isFieldModified('color');
  });

  ngOnInit(): void {
    // Escuchar cambios reactivos en el formulario para actualizar formVersion
    this.categoryForm.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => {
        this.formVersion.update(v => v + 1);
      });

    this.loadCategories();
  }

  loadCategories(): void {
    this.isLoading.set(true);
    this.errorMessage.set(null);

    const filter = this.selectedStatusFilter();

    this.categoryService.getCategories(undefined, filter).subscribe({
      next: (data) => {
        this.categories.set(data);
        this.isLoading.set(false);
      },
      error: () => {
        this.errorMessage.set('Error al cargar la lista de categorías. Por favor intente nuevamente.');
        this.isLoading.set(false);
      }
    });
  }

  setStatusFilter(filter: CategoryStatusFilter): void {
    if (this.selectedStatusFilter() !== filter) {
      this.selectedStatusFilter.set(filter);
      this.loadCategories();
    }
  }

  onSearch(event: Event): void {
    const value = (event.target as HTMLInputElement).value;
    this.searchTerm.set(value);
  }

  // --- Identificación de Campos Modificados ---

  isFieldModified(fieldName: string): boolean {
    this.formVersion();
    if (!this.isEditing()) {
      return false;
    }
    const orig = this.originalFormValues();
    if (!orig) {
      return false;
    }

    const currentVal = (this.categoryForm.get(fieldName)?.value ?? '').toString().trim().toLowerCase();
    const origVal = (orig[fieldName] ?? '').toString().trim().toLowerCase();

    return currentVal !== origVal;
  }

  // --- Apertura y Cierre de Modales ---

  openCreateModal(): void {
    this.isEditing.set(false);
    this.selectedCategoryId.set(null);
    this.formError.set(null);
    this.originalFormValues.set(null);
    this.categoryForm.reset({
      name: '',
      description: '',
      color: ''
    });
    this.formVersion.update(v => v + 1);
    this.isFormModalOpen.set(true);
  }

  openEditModal(category: Category): void {
    if (category.status === 'ELIMINADO') {
      return;
    }
    this.isEditing.set(true);
    this.selectedCategoryId.set(category.id);
    this.formError.set(null);

    const initialData = {
      name: category.name || '',
      description: category.description || '',
      color: category.color || ''
    };

    this.originalFormValues.set(initialData);
    this.categoryForm.reset(initialData);
    this.formVersion.update(v => v + 1);
    this.isFormModalOpen.set(true);
  }

  attemptCloseForm(): void {
    if (this.isSubmitting()) {
      return;
    }
    // Si existen cambios sin guardar, solicitar confirmación
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
    this.categoryForm.reset({
      name: '',
      description: '',
      color: ''
    });
    this.formVersion.update(v => v + 1);
  }

  saveCategory(): void {
    // En edición, si no hay cambios, no realizar submit
    if (this.isEditing() && !this.hasFormChanges()) {
      return;
    }

    if (this.categoryForm.invalid) {
      this.categoryForm.markAllAsTouched();
      return;
    }

    this.isSubmitting.set(true);
    this.formError.set(null);

    const formValue = this.categoryForm.value;
    const request: CategoryRequest = {
      name: formValue.name.trim(),
      description: formValue.description ? formValue.description.trim() : null,
      color: formValue.color.trim()
    };

    if (this.isEditing() && this.selectedCategoryId()) {
      this.categoryService.updateCategory(this.selectedCategoryId()!, request).subscribe({
        next: (updated) => {
          this.categories.update(prev => prev.map(c => c.id === updated.id ? updated : c));
          this.isSubmitting.set(false);
          this.isFormModalOpen.set(false);
          this.originalFormValues.set(null);
        },
        error: (err) => {
          this.isSubmitting.set(false);
          // Mostrar mensaje de negocio devuelto por el backend
          const msg = err.error?.message || 'Error al actualizar la categoría. Verifique los datos ingresados.';
          this.formError.set(msg);
        }
      });
    } else {
      this.categoryService.createCategory(request).subscribe({
        next: (created) => {
          this.categories.update(prev => [...prev, created]);
          this.isSubmitting.set(false);
          this.isFormModalOpen.set(false);
          this.originalFormValues.set(null);
        },
        error: (err) => {
          this.isSubmitting.set(false);
          // Mostrar mensaje de negocio devuelto por el backend
          const msg = err.error?.message || 'Error al crear la categoría. Verifique los datos ingresados.';
          this.formError.set(msg);
        }
      });
    }
  }

  // --- Eliminación Lógica ---

  openDeleteModal(category: Category): void {
    if (category.status === 'ELIMINADO') {
      return;
    }
    this.categoryToDelete.set(category);
    this.isConfirmModalOpen.set(true);
  }

  cancelDelete(): void {
    this.isConfirmModalOpen.set(false);
    this.categoryToDelete.set(null);
  }

  confirmDelete(): void {
    const category = this.categoryToDelete();
    if (!category) return;

    this.isDeleting.set(true);
    this.categoryService.deleteCategory(category.id).subscribe({
      next: () => {
        // En backend cambia a ELIMINADO
        if (this.selectedStatusFilter() === 'ACTIVO') {
          this.categories.update(prev => prev.filter(c => c.id !== category.id));
        } else {
          // Si estamos en filtro ELIMINADO
          this.categories.update(prev => prev.map(c => c.id === category.id ? { ...c, status: 'ELIMINADO' as const } : c));
        }
        this.isDeleting.set(false);
        this.isConfirmModalOpen.set(false);
        this.categoryToDelete.set(null);
      },
      error: (err) => {
        this.isDeleting.set(false);
        this.errorMessage.set(err.error?.message || 'Error al eliminar la categoría.');
        this.isConfirmModalOpen.set(false);
      }
    });
  }

  // --- Auditoría ---

  openAuditModal(category: Category): void {
    this.auditModalSubtitle.set(`${category.name}`);
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
