import { Component, OnInit, computed, inject, signal, DestroyRef } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Tag, TagRequest, TagStatus } from '../../core/tag/models/tag.models';
import { TagService } from '../../core/tag/tag.service';
import { ConfirmModalComponent } from '../../shared/components/confirm-modal/confirm-modal.component';
import { AuditModalComponent } from '../../shared/components/audit-modal/audit-modal.component';
import { ModalComponent } from '../../shared/components/modal/modal.component';
import { ColorPickerComponent } from '../../shared/components/color-picker/color-picker.component';

export type TagStatusFilter = 'ACTIVO' | 'ELIMINADO';

@Component({
  selector: 'app-tags',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    ConfirmModalComponent,
    AuditModalComponent,
    ModalComponent,
    ColorPickerComponent
  ],
  templateUrl: './tags.component.html',
  styleUrls: ['./tags.component.css']
})
export class TagsComponent implements OnInit {
  private readonly tagService = inject(TagService);
  private readonly fb = inject(FormBuilder);
  private readonly destroyRef = inject(DestroyRef);

  // Estados Reactivos con Signals
  tags = signal<Tag[]>([]);
  searchTerm = signal<string>('');
  selectedStatusFilter = signal<TagStatusFilter>('ACTIVO');
  isLoading = signal<boolean>(false);
  errorMessage = signal<string | null>(null);

  // Modal Formulario (Creación / Edición)
  isFormModalOpen = signal<boolean>(false);
  isEditing = signal<boolean>(false);
  selectedTagId = signal<string | null>(null);
  formError = signal<string | null>(null);
  isSubmitting = signal<boolean>(false);
  originalFormValues = signal<Record<string, any> | null>(null);
  formVersion = signal<number>(0);

  // Modal Confirmación al Descartar Cambios
  isDiscardConfirmOpen = signal<boolean>(false);

  // Modal Confirmación de Eliminación Lógica
  isConfirmModalOpen = signal<boolean>(false);
  tagToDelete = signal<Tag | null>(null);
  isDeleting = signal<boolean>(false);

  // Modal Auditoría
  isAuditModalOpen = signal<boolean>(false);
  selectedAuditData = signal<Record<string, any> | null>(null);
  auditModalSubtitle = signal<string>('');

  tagForm: FormGroup = this.fb.group({
    name: ['', [Validators.required, Validators.maxLength(150)]],
    color: ['', [Validators.required, Validators.maxLength(50)]]
  });

  // Lista de etiquetas filtrada y ordenada por Nombre A-Z según estado seleccionado
  filteredTags = computed(() => {
    const term = this.searchTerm().trim().toLowerCase();
    const statusFilter = this.selectedStatusFilter();
    let list = this.tags();

    // Filtro por estado
    list = list.filter(t => t.status === statusFilter);

    // Filtro de búsqueda en tiempo real por nombre
    const filtered = term.length === 0
      ? list
      : list.filter(t => t.name.toLowerCase().includes(term));

    // Orden inicial obligatorio: Nombre ascendente (A-Z)
    return filtered.sort((a, b) => a.name.localeCompare(b.name, 'es', { sensitivity: 'base' }));
  });

  // Señal computada para detectar cambios sin guardar (dirty check)
  hasFormChanges = computed<boolean>(() => {
    this.formVersion(); // Dependencia reactiva
    const formVal = this.tagForm.value;

    if (!this.isEditing()) {
      // En Creación: Se considera que hay cambios si el usuario ingresó algún valor
      const name = (formVal.name ?? '').toString().trim();
      const color = (formVal.color ?? '').toString().trim();
      return name.length > 0 || color.length > 0;
    }

    // En Edición: Comparar contra los valores originales cargados
    const orig = this.originalFormValues();
    if (!orig) return false;

    return this.isFieldModified('name') || this.isFieldModified('color');
  });

  ngOnInit(): void {
    // Escuchar cambios reactivos en el formulario para actualizar formVersion
    this.tagForm.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => {
        this.formVersion.update(v => v + 1);
      });

    this.loadTags();
  }

  loadTags(): void {
    this.isLoading.set(true);
    this.errorMessage.set(null);

    const filter = this.selectedStatusFilter();

    this.tagService.getTags(undefined, filter).subscribe({
      next: (data) => {
        this.tags.set(data);
        this.isLoading.set(false);
      },
      error: () => {
        this.errorMessage.set('Error al cargar la lista de etiquetas. Por favor intente nuevamente.');
        this.isLoading.set(false);
      }
    });
  }

  setStatusFilter(filter: TagStatusFilter): void {
    if (this.selectedStatusFilter() !== filter) {
      this.selectedStatusFilter.set(filter);
      this.loadTags();
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

    const currentVal = (this.tagForm.get(fieldName)?.value ?? '').toString().trim().toLowerCase();
    const origVal = (orig[fieldName] ?? '').toString().trim().toLowerCase();

    return currentVal !== origVal;
  }

  // --- Apertura y Cierre de Modales ---

  openCreateModal(): void {
    this.isEditing.set(false);
    this.selectedTagId.set(null);
    this.formError.set(null);
    this.originalFormValues.set(null);
    this.tagForm.reset({
      name: '',
      color: ''
    });
    this.formVersion.update(v => v + 1);
    this.isFormModalOpen.set(true);
  }

  openEditModal(tag: Tag): void {
    if (tag.status === 'ELIMINADO') {
      return;
    }
    this.isEditing.set(true);
    this.selectedTagId.set(tag.id);
    this.formError.set(null);

    const initialData = {
      name: tag.name || '',
      color: tag.color || ''
    };

    this.originalFormValues.set(initialData);
    this.tagForm.reset(initialData);
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
    this.tagForm.reset({
      name: '',
      color: ''
    });
    this.formVersion.update(v => v + 1);
  }

  saveTag(): void {
    // En edición, si no hay cambios, no realizar submit
    if (this.isEditing() && !this.hasFormChanges()) {
      return;
    }

    if (this.tagForm.invalid) {
      this.tagForm.markAllAsTouched();
      return;
    }

    this.isSubmitting.set(true);
    this.formError.set(null);

    const formValue = this.tagForm.value;
    const request: TagRequest = {
      name: formValue.name.trim(),
      color: formValue.color.trim()
    };

    if (this.isEditing() && this.selectedTagId()) {
      this.tagService.updateTag(this.selectedTagId()!, request).subscribe({
        next: (updated) => {
          this.tags.update(prev => prev.map(t => t.id === updated.id ? updated : t));
          this.isSubmitting.set(false);
          this.isFormModalOpen.set(false);
          this.originalFormValues.set(null);
        },
        error: (err) => {
          this.isSubmitting.set(false);
          // Mostrar mensaje de negocio devuelto por el backend
          const msg = err.error?.message || 'Error al actualizar la etiqueta. Verifique los datos ingresados.';
          this.formError.set(msg);
        }
      });
    } else {
      this.tagService.createTag(request).subscribe({
        next: (created) => {
          this.tags.update(prev => [...prev, created]);
          this.isSubmitting.set(false);
          this.isFormModalOpen.set(false);
          this.originalFormValues.set(null);
        },
        error: (err) => {
          this.isSubmitting.set(false);
          // Mostrar mensaje de negocio devuelto por el backend
          const msg = err.error?.message || 'Error al crear la etiqueta. Verifique los datos ingresados.';
          this.formError.set(msg);
        }
      });
    }
  }

  // --- Eliminación Lógica ---

  openDeleteModal(tag: Tag): void {
    if (tag.status === 'ELIMINADO') {
      return;
    }
    this.tagToDelete.set(tag);
    this.isConfirmModalOpen.set(true);
  }

  cancelDelete(): void {
    this.isConfirmModalOpen.set(false);
    this.tagToDelete.set(null);
  }

  confirmDelete(): void {
    const tag = this.tagToDelete();
    if (!tag) return;

    this.isDeleting.set(true);
    this.tagService.deleteTag(tag.id).subscribe({
      next: () => {
        // En backend cambia a ELIMINADO
        if (this.selectedStatusFilter() === 'ACTIVO') {
          this.tags.update(prev => prev.filter(t => t.id !== tag.id));
        } else {
          // Si estamos en filtro ELIMINADO
          this.tags.update(prev => prev.map(t => t.id === tag.id ? { ...t, status: 'ELIMINADO' as const } : t));
        }
        this.isDeleting.set(false);
        this.isConfirmModalOpen.set(false);
        this.tagToDelete.set(null);
      },
      error: (err) => {
        this.isDeleting.set(false);
        this.errorMessage.set(err.error?.message || 'Error al eliminar la etiqueta.');
        this.isConfirmModalOpen.set(false);
      }
    });
  }

  // --- Auditoría ---

  openAuditModal(tag: Tag): void {
    this.auditModalSubtitle.set(`${tag.name}`);
    this.selectedAuditData.set({
      createdBy: tag.createdBy,
      createdAt: tag.createdAt,
      updatedBy: tag.updatedBy,
      updatedAt: tag.updatedAt,
      deletedBy: tag.deletedBy,
      deletedAt: tag.deletedAt
    });
    this.isAuditModalOpen.set(true);
  }

  closeAuditModal(): void {
    this.isAuditModalOpen.set(false);
    this.selectedAuditData.set(null);
  }
}
