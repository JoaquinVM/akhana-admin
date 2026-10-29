import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { CreditorService } from '../../../../core/debt/creditor.service';
import { CustomerService } from '../../../../core/debt/customer.service';
import { Customer, CustomerRequest, CustomerStatus, DebtAccount } from '../../../../core/debt/models/debt.models';
import { ModalComponent } from '../../../../shared/components/modal/modal.component';
import { ConfirmModalComponent } from '../../../../shared/components/confirm-modal/confirm-modal.component';
import { AuditModalComponent } from '../../../../shared/components/audit-modal/audit-modal.component';
import { CustomerDetailModalComponent } from '../customer-detail-modal/customer-detail-modal.component';
import { SaleDetailModalComponent } from '../../../../shared/components/sale-detail-modal/sale-detail-modal.component';

// '' = active and inactive (deleted left out)
export type CustomerStatusFilter = '' | CustomerStatus;

interface CustomerDraft {
  name: string;
  phone: string;
  description: string;
  status: CustomerStatus;
}

// DEU-05 and DEU-07: customers with their receivable; create, edit, delete and detail with history
@Component({
  selector: 'app-customers-view',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    ModalComponent,
    ConfirmModalComponent,
    AuditModalComponent,
    CustomerDetailModalComponent,
    SaleDetailModalComponent
  ],
  templateUrl: './customers-view.component.html',
  styleUrls: ['./customers-view.component.css']
})
export class CustomersViewComponent implements OnInit {
  // The collection accounts come from the Deudas endpoint (/api/debts/accounts)
  private readonly creditorService = inject(CreditorService);
  private readonly customerService = inject(CustomerService);

  readonly statusFilters: { value: CustomerStatusFilter; label: string }[] = [
    { value: '', label: 'Activos/Inactivos' },
    { value: 'ACTIVO', label: 'Activos' },
    { value: 'INACTIVO', label: 'Inactivos' },
    { value: 'ELIMINADO', label: 'Eliminados' }
  ];

  customers = signal<Customer[]>([]);
  accounts = signal<DebtAccount[]>([]);
  statusFilter = signal<CustomerStatusFilter>('');
  searchTerm = signal<string>('');
  isLoading = signal<boolean>(false);
  errorMessage = signal<string | null>(null);

  // Create or edit form
  isFormOpen = signal<boolean>(false);
  editing = signal<Customer | null>(null);
  draft = signal<CustomerDraft>(emptyDraft());
  formError = signal<string | null>(null);
  isSubmitting = signal<boolean>(false);

  toDelete = signal<Customer | null>(null);
  isDeleting = signal<boolean>(false);

  auditCustomer = signal<Customer | null>(null);

  // Detail with history; opening a sale hides it and closing brings it back
  detailCustomerId = signal<string | null>(null);
  saleId = signal<string | null>(null);
  // Changes so the detail reloads the history
  detailRefresh = signal<number>(0);

  totalBalance = computed(() => round2(this.customers().reduce((sum, c) => sum + c.balance, 0)));

  auditData = computed(() => {
    const c = this.auditCustomer();
    return c
      ? { createdBy: c.createdBy, createdAt: c.createdAt, updatedBy: c.updatedBy, updatedAt: c.updatedAt,
          deletedBy: c.deletedBy, deletedAt: c.deletedAt }
      : null;
  });

  ngOnInit(): void {
    this.creditorService.getAccounts().subscribe({
      next: accounts => this.accounts.set(accounts),
      error: () => this.errorMessage.set('No se pudieron cargar las cuentas para cobrar.')
    });
    this.loadCustomers();
  }

  loadCustomers(): void {
    this.isLoading.set(true);
    this.errorMessage.set(null);
    this.customerService.getCustomers(this.statusFilter() || null, this.searchTerm()).subscribe({
      next: customers => {
        this.customers.set(customers);
        this.isLoading.set(false);
      },
      error: () => {
        this.errorMessage.set('Error al cargar los clientes. Intente nuevamente.');
        this.isLoading.set(false);
      }
    });
  }

  setStatusFilter(filter: CustomerStatusFilter): void {
    if (this.statusFilter() !== filter) {
      this.statusFilter.set(filter);
      this.loadCustomers();
    }
  }

  onSearch(term: string): void {
    this.searchTerm.set(term);
    this.loadCustomers();
  }

  statusLabel(status: CustomerStatus): string {
    return status === 'ACTIVO' ? 'Activo' : status === 'INACTIVO' ? 'Inactivo' : 'Eliminado';
  }

  // --- Create and edit ---

  openCreate(): void {
    this.editing.set(null);
    this.draft.set(emptyDraft());
    this.formError.set(null);
    this.isFormOpen.set(true);
  }

  openEdit(customer: Customer): void {
    if (customer.status === 'ELIMINADO') {
      return;
    }
    this.editing.set(customer);
    this.draft.set({
      name: customer.name,
      phone: customer.phone ?? '',
      description: customer.description ?? '',
      status: customer.status
    });
    this.formError.set(null);
    this.isFormOpen.set(true);
  }

  updateDraft(field: keyof CustomerDraft, value: string): void {
    this.draft.update(d => ({ ...d, [field]: value }));
  }

  closeForm(): void {
    if (!this.isSubmitting()) {
      this.isFormOpen.set(false);
    }
  }

  buildRequest(): CustomerRequest {
    const d = this.draft();
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
    const request = this.buildRequest();
    if (request.name.length === 0) {
      this.formError.set('El nombre es obligatorio.');
      return;
    }
    this.isSubmitting.set(true);
    this.formError.set(null);
    const editing = this.editing();
    const call = editing
      ? this.customerService.updateCustomer(editing.id, request)
      : this.customerService.createCustomer(request);
    call.subscribe({
      next: () => {
        this.isSubmitting.set(false);
        this.isFormOpen.set(false);
        this.loadCustomers();
      },
      error: err => {
        this.isSubmitting.set(false);
        this.formError.set(err.error?.message || 'No se pudo guardar el cliente.');
      }
    });
  }

  // --- Logical delete ---

  openDelete(customer: Customer): void {
    this.errorMessage.set(null);
    this.toDelete.set(customer);
  }

  confirmDelete(): void {
    const customer = this.toDelete();
    if (!customer) {
      return;
    }
    this.isDeleting.set(true);
    this.customerService.deleteCustomer(customer.id).subscribe({
      next: () => {
        this.isDeleting.set(false);
        this.toDelete.set(null);
        this.loadCustomers();
      },
      error: err => {
        this.isDeleting.set(false);
        this.toDelete.set(null);
        this.errorMessage.set(err.error?.message || 'No se pudo eliminar el cliente.');
      }
    });
  }

  // --- Detalle ---

  openDetail(customer: Customer): void {
    this.detailCustomerId.set(customer.id);
  }

  closeDetail(): void {
    this.detailCustomerId.set(null);
  }

  openSale(saleId: string): void {
    this.saleId.set(saleId);
  }

  // A sale voided from the POS changes the balance: on return reload the history and the list
  closeSale(): void {
    this.saleId.set(null);
    this.detailRefresh.update(n => n + 1);
    this.loadCustomers();
  }
}

function emptyDraft(): CustomerDraft {
  return { name: '', phone: '', description: '', status: 'ACTIVO' };
}

function round2(value: number): number {
  return Math.round(value * 100) / 100;
}
