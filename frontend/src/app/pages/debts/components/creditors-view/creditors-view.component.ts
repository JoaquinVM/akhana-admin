import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { Subscription } from 'rxjs';
import { searchDebouncer } from '../../../../shared/utils/search-debounce';
import { CreditorService } from '../../../../core/debt/creditor.service';
import { Creditor, CreditorRequest, CreditorStatus, DebtAccount } from '../../../../core/debt/models/debt.models';
import { ModalComponent } from '../../../../shared/components/modal/modal.component';
import { ConfirmModalComponent } from '../../../../shared/components/confirm-modal/confirm-modal.component';
import { AuditModalComponent } from '../../../../shared/components/audit-modal/audit-modal.component';
import { SaleDetailModalComponent } from '../../../../shared/components/sale-detail-modal/sale-detail-modal.component';
import { CreditorDetailModalComponent } from '../creditor-detail-modal/creditor-detail-modal.component';
import { PurchaseDetailModalComponent } from '../../../purchases/components/purchase-detail-modal/purchase-detail-modal.component';
import { ToastService } from '../../../../shared/components/toast/toast.service';
import { IconComponent } from '../../../../shared/components/icon/icon.component';
import { FormSnapshot, hasFormChanges } from '../../../../shared/utils/form-changes';

// '' = active and inactive (deleted left out)
export type CreditorStatusFilter = '' | CreditorStatus;

// DEU-01 and DEU-04: people with their debt; create, edit, delete and detail with history
@Component({
  selector: 'app-creditors-view',
  standalone: true,
  imports: [IconComponent, 
    CommonModule,
    FormsModule,
    ReactiveFormsModule,
    ModalComponent,
    ConfirmModalComponent,
    AuditModalComponent,
    CreditorDetailModalComponent,
    PurchaseDetailModalComponent,
    SaleDetailModalComponent
  ],
  templateUrl: './creditors-view.component.html',
  styleUrls: ['./creditors-view.component.css']
})
export class CreditorsViewComponent implements OnInit {
  private readonly toast = inject(ToastService);
  private readonly creditorService = inject(CreditorService);
  private readonly fb = inject(FormBuilder);

  readonly statusFilters: { value: CreditorStatusFilter; label: string }[] = [
    { value: '', label: 'Activas/Inactivas' },
    { value: 'ACTIVO', label: 'Activas' },
    { value: 'INACTIVO', label: 'Inactivas' },
    { value: 'ELIMINADO', label: 'Eliminadas' }
  ];

  creditors = signal<Creditor[]>([]);
  accounts = signal<DebtAccount[]>([]);
  statusFilter = signal<CreditorStatusFilter>('');
  searchTerm = signal<string>('');
  private readonly debounceSearch = searchDebouncer();
  private creditorsLoad?: Subscription;
  isLoading = signal<boolean>(false);
  errorMessage = signal<string | null>(null);

  // Create or edit form
  isFormOpen = signal<boolean>(false);
  editing = signal<Creditor | null>(null);
  form = this.fb.nonNullable.group({
    // A name made only of spaces counts as empty
    name: ['', [Validators.required, Validators.pattern(/\S/), Validators.maxLength(150)]],
    phone: ['', [Validators.maxLength(30)]],
    description: ['', [Validators.maxLength(500)]],
    status: ['ACTIVO' as CreditorStatus]
  });
  // Values when the modal opened, to ask before discarding changes
  private originalValues: FormSnapshot | null = null;
  formError = signal<string | null>(null);
  isSubmitting = signal<boolean>(false);
  isDiscardConfirmOpen = signal<boolean>(false);

  toDelete = signal<Creditor | null>(null);
  isDeleting = signal<boolean>(false);

  auditCreditor = signal<Creditor | null>(null);

  // Detail with history; opening a purchase hides it and closing brings it back
  detailCreditorId = signal<string | null>(null);
  purchaseId = signal<string | null>(null);
  // POS-13: sale opened from the person's history
  saleId = signal<string | null>(null);
  // Changes so the detail reloads the history
  detailRefresh = signal<number>(0);

  totalDebt = computed(() => round2(this.creditors().reduce((sum, c) => sum + c.debt, 0)));

  auditData = computed(() => {
    const c = this.auditCreditor();
    return c
      ? { createdBy: c.createdBy, createdAt: c.createdAt, updatedBy: c.updatedBy, updatedAt: c.updatedAt,
          deletedBy: c.deletedBy, deletedAt: c.deletedAt }
      : null;
  });

  ngOnInit(): void {
    this.creditorService.getAccounts().subscribe({
      next: accounts => this.accounts.set(accounts),
      error: err => this.errorMessage.set(err.error?.message ?? 'No se pudieron cargar las cuentas para devolver.')
    });
    this.loadCreditors();
  }

  loadCreditors(): void {
    this.isLoading.set(true);
    this.errorMessage.set(null);
    // A slower answer of an earlier search must not replace the latest one
    this.creditorsLoad?.unsubscribe();
    this.creditorsLoad = this.creditorService.getCreditors(this.statusFilter() || null, this.searchTerm()).subscribe({
      next: creditors => {
        this.creditors.set(creditors);
        this.isLoading.set(false);
      },
      error: err => {
        this.errorMessage.set(err.error?.message ?? 'Error al cargar las personas. Intenta nuevamente.');
        this.isLoading.set(false);
      }
    });
  }

  setStatusFilter(filter: CreditorStatusFilter): void {
    if (this.statusFilter() !== filter) {
      this.statusFilter.set(filter);
      this.loadCreditors();
    }
  }

  onSearch(term: string): void {
    this.searchTerm.set(term);
    this.debounceSearch(() => this.loadCreditors());
  }

  statusLabel(status: CreditorStatus): string {
    return status === 'ACTIVO' ? 'Activa' : status === 'INACTIVO' ? 'Inactiva' : 'Eliminada';
  }

  // --- Create and edit ---

  openCreate(): void {
    this.editing.set(null);
    this.resetForm({ name: '', phone: '', description: '', status: 'ACTIVO' });
  }

  openEdit(creditor: Creditor): void {
    if (creditor.status === 'ELIMINADO') {
      return;
    }
    this.editing.set(creditor);
    this.resetForm({
      name: creditor.name,
      phone: creditor.phone ?? '',
      description: creditor.description ?? '',
      status: creditor.status
    });
  }

  private resetForm(values: { name: string; phone: string; description: string; status: CreditorStatus }): void {
    this.form.reset(values);
    this.originalValues = this.form.getRawValue();
    this.formError.set(null);
    this.isFormOpen.set(true);
  }

  hasFormChanges(): boolean {
    return hasFormChanges(this.form, this.originalValues);
  }

  // With unsaved changes, ask before closing
  attemptCloseForm(): void {
    if (this.isSubmitting()) {
      return;
    }
    if (this.hasFormChanges()) {
      this.isDiscardConfirmOpen.set(true);
    } else {
      this.closeForm();
    }
  }

  discardChanges(): void {
    this.isDiscardConfirmOpen.set(false);
    this.closeForm();
  }

  closeForm(): void {
    this.isFormOpen.set(false);
    this.formError.set(null);
    this.originalValues = null;
  }

  buildRequest(): CreditorRequest {
    const d = this.form.getRawValue();
    const phone = d.phone.trim();
    const description = d.description.trim();
    return {
      name: d.name.trim(),
      phone: phone.length > 0 ? phone : null,
      description: description.length > 0 ? description : null,
      status: this.editing() ? d.status : 'ACTIVO'
    };
  }

  save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const request = this.buildRequest();
    this.isSubmitting.set(true);
    this.formError.set(null);
    const editing = this.editing();
    const call = editing
      ? this.creditorService.updateCreditor(editing.id, request)
      : this.creditorService.createCreditor(request);
    call.subscribe({
      next: () => {
        this.toast.success(editing ? 'Persona actualizada.' : 'Persona creada.');
        this.isSubmitting.set(false);
        this.closeForm();
        this.loadCreditors();
      },
      error: err => {
        this.isSubmitting.set(false);
        this.formError.set(err.error?.message || 'No se pudo guardar la persona.');
      }
    });
  }

  // --- Logical delete ---

  openDelete(creditor: Creditor): void {
    this.errorMessage.set(null);
    this.toDelete.set(creditor);
  }

  confirmDelete(): void {
    const creditor = this.toDelete();
    if (!creditor) {
      return;
    }
    this.isDeleting.set(true);
    this.creditorService.deleteCreditor(creditor.id).subscribe({
      next: () => {
        this.toast.success('Persona dada de baja.');
        this.isDeleting.set(false);
        this.toDelete.set(null);
        this.loadCreditors();
      },
      error: err => {
        this.isDeleting.set(false);
        this.toDelete.set(null);
        this.errorMessage.set(err.error?.message || 'No se pudo eliminar la persona.');
      }
    });
  }

  // --- Detalle ---

  openDetail(creditor: Creditor): void {
    this.detailCreditorId.set(creditor.id);
  }

  closeDetail(): void {
    this.detailCreditorId.set(null);
  }

  openPurchase(purchaseId: string): void {
    this.purchaseId.set(purchaseId);
  }

  // A payment voided from the purchase changes the debt: reload the history and the list
  closePurchase(): void {
    this.purchaseId.set(null);
    this.detailRefresh.update(n => n + 1);
    this.loadCreditors();
  }
}

function round2(value: number): number {
  return Math.round(value * 100) / 100;
}
