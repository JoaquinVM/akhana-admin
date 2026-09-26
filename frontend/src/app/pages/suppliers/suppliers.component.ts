import { Component, OnInit, computed, inject, signal, DestroyRef } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Supplier, SupplierRequest } from '../../core/supplier/models/supplier.models';
import { SupplierService } from '../../core/supplier/supplier.service';
import { ConfirmModalComponent } from '../../shared/components/confirm-modal/confirm-modal.component';
import { AuditModalComponent } from '../../shared/components/audit-modal/audit-modal.component';
import { ModalComponent } from '../../shared/components/modal/modal.component';

export type SupplierStatusFilter = 'ACTIVE_INACTIVE' | 'ACTIVO' | 'INACTIVO' | 'ELIMINADO';

@Component({
  selector: 'app-suppliers',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    ConfirmModalComponent,
    AuditModalComponent,
    ModalComponent
  ],
  templateUrl: './suppliers.component.html',
  styleUrls: ['./suppliers.component.css']
})
export class SuppliersComponent implements OnInit {
  private readonly supplierService = inject(SupplierService);
  private readonly fb = inject(FormBuilder);
  private readonly destroyRef = inject(DestroyRef);

  // Estados Reactivos con Signals
  suppliers = signal<Supplier[]>([]);
  searchTerm = signal<string>('');
  selectedStatusFilter = signal<SupplierStatusFilter>('ACTIVE_INACTIVE');
  isLoading = signal<boolean>(false);
  errorMessage = signal<string | null>(null);

  // Modal Formulario (Creación / Edición)
  isFormModalOpen = signal<boolean>(false);
  isEditing = signal<boolean>(false);
  selectedSupplierId = signal<string | null>(null);
  formError = signal<string | null>(null);
  isSubmitting = signal<boolean>(false);
  originalFormValues = signal<Record<string, any> | null>(null);
  formVersion = signal<number>(0);

  // Modal Confirmación al Descartar Cambios
  isDiscardConfirmOpen = signal<boolean>(false);

  // Modal Confirmación de Eliminación
  isConfirmModalOpen = signal<boolean>(false);
  supplierToDelete = signal<Supplier | null>(null);
  isDeleting = signal<boolean>(false);

  // Modal Auditoría
  isAuditModalOpen = signal<boolean>(false);
  selectedAuditData = signal<Record<string, any> | null>(null);
  auditModalSubtitle = signal<string>('');

  supplierForm: FormGroup = this.fb.group({
    name: ['', [Validators.required, Validators.maxLength(150)]],
    code: ['', [Validators.required, Validators.maxLength(50)]],
    phone: ['', [Validators.maxLength(30)]],
    description: ['', [Validators.maxLength(500)]],
    status: ['ACTIVO']
  });

  // Lista de proveedores filtrada y ordenada por Nombre A-Z según estado seleccionado
  filteredSuppliers = computed(() => {
    const term = this.searchTerm().trim().toLowerCase();
    const statusFilter = this.selectedStatusFilter();
    let list = this.suppliers();

    if (statusFilter === 'ACTIVE_INACTIVE') {
      list = list.filter(s => s.status === 'ACTIVO' || s.status === 'INACTIVO');
    } else if (statusFilter === 'ACTIVO') {
      list = list.filter(s => s.status === 'ACTIVO');
    } else if (statusFilter === 'INACTIVO') {
      list = list.filter(s => s.status === 'INACTIVO');
    } else if (statusFilter === 'ELIMINADO') {
      list = list.filter(s => s.status === 'ELIMINADO');
    }

    const filtered = term.length === 0
      ? list
      : list.filter(s =>
          s.name.toLowerCase().includes(term) ||
          s.code.toLowerCase().includes(term)
        );

    // Orden inicial obligatorio: Nombre ascendente (A-Z)
    return filtered.sort((a, b) => a.name.localeCompare(b.name, 'es', { sensitivity: 'base' }));
  });

  // Señal computada para detectar cambios sin guardar
  hasFormChanges = computed<boolean>(() => {
    this.formVersion(); // Dependencia reactiva de cambios en el formulario
    const formVal = this.supplierForm.value;

    if (!this.isEditing()) {
      // En Creación: Se considera que hay cambios si el usuario ingresó algún valor en cualquier campo
      const name = (formVal.name ?? '').toString().trim();
      const code = (formVal.code ?? '').toString().trim();
      const phone = (formVal.phone ?? '').toString().trim();
      const description = (formVal.description ?? '').toString().trim();
      return name.length > 0 || code.length > 0 || phone.length > 0 || description.length > 0;
    }

    // En Edición: Comparar contra los valores originales cargados
    const orig = this.originalFormValues();
    if (!orig) return false;

    return this.isFieldModified('name') ||
           this.isFieldModified('code') ||
           this.isFieldModified('phone') ||
           this.isFieldModified('description') ||
           this.isFieldModified('status');
  });

  ngOnInit(): void {
    // Escuchar cambios reactivos en el formulario para actualizar formVersion
    this.supplierForm.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => {
        this.formVersion.update(v => v + 1);
      });

    this.loadSuppliers();
  }

  loadSuppliers(): void {
    this.isLoading.set(true);
    this.errorMessage.set(null);

    const filter = this.selectedStatusFilter();

    this.supplierService.getSuppliers(undefined, filter).subscribe({
      next: (data) => {
        this.suppliers.set(data);
        this.isLoading.set(false);
      },
      error: () => {
        this.errorMessage.set('Error al cargar la lista de proveedores. Por favor intente nuevamente.');
        this.isLoading.set(false);
      }
    });
  }

  setStatusFilter(filter: SupplierStatusFilter): void {
    if (this.selectedStatusFilter() !== filter) {
      this.selectedStatusFilter.set(filter);
      this.loadSuppliers();
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

    const currentVal = (this.supplierForm.get(fieldName)?.value ?? '').toString().trim();
    const origVal = (orig[fieldName] ?? '').toString().trim();

    return currentVal !== origVal;
  }

  // --- Apertura y Cierre de Modales ---

  openCreateModal(): void {
    this.isEditing.set(false);
    this.selectedSupplierId.set(null);
    this.formError.set(null);
    this.originalFormValues.set(null);
    this.supplierForm.reset({
      name: '',
      code: '',
      phone: '',
      description: '',
      status: 'ACTIVO'
    });
    this.formVersion.update(v => v + 1);
    this.isFormModalOpen.set(true);
  }

  openEditModal(supplier: Supplier): void {
    if (supplier.status === 'ELIMINADO') {
      return;
    }
    this.isEditing.set(true);
    this.selectedSupplierId.set(supplier.id);
    this.formError.set(null);

    const initialData = {
      name: supplier.name || '',
      code: supplier.code || '',
      phone: supplier.phone || '',
      description: supplier.description || '',
      status: supplier.status
    };

    this.originalFormValues.set(initialData);
    this.supplierForm.reset(initialData);
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
    this.supplierForm.reset({
      name: '',
      code: '',
      phone: '',
      description: '',
      status: 'ACTIVO'
    });
    this.formVersion.update(v => v + 1);
  }

  saveSupplier(): void {
    // En edición, si no hay cambios, no realizar submit
    if (this.isEditing() && !this.hasFormChanges()) {
      return;
    }

    if (this.supplierForm.invalid) {
      this.supplierForm.markAllAsTouched();
      return;
    }

    this.isSubmitting.set(true);
    this.formError.set(null);

    const formValue = this.supplierForm.value;
    const request: SupplierRequest = {
      name: formValue.name.trim(),
      code: formValue.code.trim(),
      phone: formValue.phone ? formValue.phone.trim() : null,
      description: formValue.description ? formValue.description.trim() : null,
      status: this.isEditing() ? formValue.status : 'ACTIVO'
    };

    if (this.isEditing() && this.selectedSupplierId()) {
      this.supplierService.updateSupplier(this.selectedSupplierId()!, request).subscribe({
        next: (updated) => {
          this.suppliers.update(prev => prev.map(s => s.id === updated.id ? updated : s));
          this.isSubmitting.set(false);
          this.isFormModalOpen.set(false);
          this.originalFormValues.set(null);
        },
        error: (err) => {
          this.isSubmitting.set(false);
          // Mostrar mensaje de negocio devuelto por el backend
          const msg = err.error?.message || 'Error al actualizar el proveedor. Verifique los datos ingresados.';
          this.formError.set(msg);
        }
      });
    } else {
      this.supplierService.createSupplier(request).subscribe({
        next: (created) => {
          this.suppliers.update(prev => [...prev, created]);
          this.isSubmitting.set(false);
          this.isFormModalOpen.set(false);
          this.originalFormValues.set(null);
        },
        error: (err) => {
          this.isSubmitting.set(false);
          // Mostrar mensaje de negocio devuelto por el backend
          const msg = err.error?.message || 'Error al crear el proveedor. Verifique los datos ingresados.';
          this.formError.set(msg);
        }
      });
    }
  }

  // --- Eliminación Lógica ---

  openDeleteModal(supplier: Supplier): void {
    if (supplier.status === 'ELIMINADO') {
      return;
    }
    this.supplierToDelete.set(supplier);
    this.isConfirmModalOpen.set(true);
  }

  cancelDelete(): void {
    this.isConfirmModalOpen.set(false);
    this.supplierToDelete.set(null);
  }

  confirmDelete(): void {
    const supplier = this.supplierToDelete();
    if (!supplier) return;

    this.isDeleting.set(true);
    this.supplierService.deleteSupplier(supplier.id).subscribe({
      next: () => {
        // En backend cambia a ELIMINADO.
        if (this.selectedStatusFilter() === 'ACTIVE_INACTIVE' || this.selectedStatusFilter() === 'ACTIVO' || this.selectedStatusFilter() === 'INACTIVO') {
          this.suppliers.update(prev => prev.filter(s => s.id !== supplier.id));
        } else {
          // Si estamos en filtro ELIMINADO, actualizamos el registro
          this.suppliers.update(prev => prev.map(s => s.id === supplier.id ? { ...s, status: 'ELIMINADO' as const } : s));
        }
        this.isDeleting.set(false);
        this.isConfirmModalOpen.set(false);
        this.supplierToDelete.set(null);
      },
      error: (err) => {
        this.isDeleting.set(false);
        this.errorMessage.set(err.error?.message || 'Error al eliminar el proveedor.');
        this.isConfirmModalOpen.set(false);
      }
    });
  }

  // --- Auditoría ---

  openAuditModal(supplier: Supplier): void {
    this.auditModalSubtitle.set(`${supplier.code} - ${supplier.name}`);
    this.selectedAuditData.set({
      createdBy: supplier.createdBy,
      createdAt: supplier.createdAt,
      updatedBy: supplier.updatedBy,
      updatedAt: supplier.updatedAt,
      deletedBy: supplier.deletedBy,
      deletedAt: supplier.deletedAt
    });
    this.isAuditModalOpen.set(true);
  }

  closeAuditModal(): void {
    this.isAuditModalOpen.set(false);
    this.selectedAuditData.set(null);
  }
}
