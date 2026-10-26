import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { CustomersViewComponent } from './customers-view.component';
import { CreditorService } from '../../../../core/debt/creditor.service';
import { CustomerService } from '../../../../core/debt/customer.service';
import { SaleService } from '../../../../core/sale/sale.service';
import { Customer } from '../../../../core/debt/models/debt.models';

const neida: Customer = {
  id: 'cus-1', name: 'Tía Neida', phone: null, description: null, status: 'ACTIVO', balance: 579,
  createdBy: 'operator', createdAt: '2026-10-25T12:00:00Z', updatedBy: null, updatedAt: null, deletedBy: null, deletedAt: null
};
const otra: Customer = { ...neida, id: 'cus-2', name: 'Otra tienda', balance: 20.5 };

describe('CustomersViewComponent', () => {
  let component: CustomersViewComponent;
  let fixture: ComponentFixture<CustomersViewComponent>;
  let customerService: any;

  beforeEach(async () => {
    customerService = {
      getCustomers: vi.fn().mockReturnValue(of([neida, otra])),
      createCustomer: vi.fn().mockReturnValue(of(neida)),
      updateCustomer: vi.fn().mockReturnValue(of(neida)),
      deleteCustomer: vi.fn().mockReturnValue(of(undefined)),
      getHistory: vi.fn().mockReturnValue(of({ customer: neida, entries: [] }))
    };
    await TestBed.configureTestingModule({
      imports: [CustomersViewComponent],
      providers: [
        { provide: CustomerService, useValue: customerService },
        { provide: CreditorService, useValue: { getAccounts: vi.fn().mockReturnValue(of([])) } },
        { provide: SaleService, useValue: { getSaleById: vi.fn() } }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(CustomersViewComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('DEU-07: lista clientes con su saldo y el total por cobrar', () => {
    expect(customerService.getCustomers).toHaveBeenCalledWith(null, '');
    expect(component.customers().length).toBe(2);
    expect(component.totalBalance()).toBe(599.5);
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Total por cobrar');
  });

  it('DEU-07: filtra por estado y texto', () => {
    component.setStatusFilter('INACTIVO');
    expect(customerService.getCustomers).toHaveBeenLastCalledWith('INACTIVO', '');
    component.onSearch('nei');
    expect(customerService.getCustomers).toHaveBeenLastCalledWith('INACTIVO', 'nei');
  });

  it('DEU-05: crea un cliente activo y exige nombre', () => {
    component.openCreate();
    component.save();
    expect(component.formError()).toBe('El nombre es obligatorio.');

    component.updateDraft('name', '  Tía Neida ');
    component.save();
    expect(customerService.createCustomer).toHaveBeenCalledWith({ name: 'Tía Neida', phone: null, description: null, status: 'ACTIVO' });
    expect(component.isFormOpen()).toBe(false);
  });

  it('DEU-05: muestra el rechazo del servidor al eliminar un cliente con saldo', () => {
    customerService.deleteCustomer.mockReturnValue(throwError(() => ({ error: { message: 'Tía Neida tiene un saldo por cobrar de Bs 579.00.' } })));
    component.openDelete(neida);
    component.confirmDelete();
    expect(component.errorMessage()).toBe('Tía Neida tiene un saldo por cobrar de Bs 579.00.');
  });

  it('DEU-07: al volver de una venta recarga el historial y el listado', () => {
    component.openDetail(neida);
    component.openSale('sale-1');
    expect(component.saleId()).toBe('sale-1');
    component.closeSale();
    expect(component.saleId()).toBeNull();
    expect(component.detailRefresh()).toBe(1);
    expect(customerService.getCustomers).toHaveBeenCalledTimes(2);
  });
});
