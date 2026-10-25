import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { CreditorsViewComponent } from './creditors-view.component';
import { CreditorService } from '../../../../core/debt/creditor.service';
import { PurchaseService } from '../../../../core/purchase/purchase.service';
import { Creditor } from '../../../../core/debt/models/debt.models';

const mami: Creditor = {
  id: 'cre-1', name: 'Mami', phone: null, description: null, status: 'ACTIVO', debt: 150,
  createdBy: 'operator', createdAt: '2026-10-25T12:00:00Z', updatedBy: null, updatedAt: null, deletedBy: null, deletedAt: null
};
const rosa: Creditor = { ...mami, id: 'cre-2', name: 'Rosa', debt: 50.25 };

describe('CreditorsViewComponent', () => {
  let component: CreditorsViewComponent;
  let fixture: ComponentFixture<CreditorsViewComponent>;
  let creditorService: any;

  beforeEach(async () => {
    creditorService = {
      getCreditors: vi.fn().mockReturnValue(of([mami, rosa])),
      getAccounts: vi.fn().mockReturnValue(of([])),
      createCreditor: vi.fn().mockReturnValue(of(mami)),
      updateCreditor: vi.fn().mockReturnValue(of(mami)),
      deleteCreditor: vi.fn().mockReturnValue(of(undefined)),
      getHistory: vi.fn().mockReturnValue(of({ creditor: mami, entries: [] }))
    };
    await TestBed.configureTestingModule({
      imports: [CreditorsViewComponent],
      providers: [
        { provide: CreditorService, useValue: creditorService },
        { provide: PurchaseService, useValue: { getPurchaseById: vi.fn() } }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(CreditorsViewComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('DEU-04: lista personas con su deuda y el total adeudado', () => {
    expect(creditorService.getCreditors).toHaveBeenCalledWith(null, '');
    expect(component.creditors().length).toBe(2);
    expect(component.totalDebt()).toBe(200.25);
  });

  it('DEU-04: filtra por estado y texto', () => {
    component.setStatusFilter('INACTIVO');
    expect(creditorService.getCreditors).toHaveBeenLastCalledWith('INACTIVO', '');
    component.onSearch('ma');
    expect(creditorService.getCreditors).toHaveBeenLastCalledWith('INACTIVO', 'ma');
  });

  it('DEU-01: crea una persona con nombre recortado y campos vacíos en null', () => {
    component.openCreate();
    component.updateDraft('name', '  Tía Rosa ');
    component.save();
    expect(creditorService.createCreditor).toHaveBeenCalledWith({ name: 'Tía Rosa', phone: null, description: null, status: 'ACTIVO' });
    expect(component.isFormOpen()).toBe(false);
  });

  it('DEU-01: exige nombre y muestra el error del servidor', () => {
    component.openCreate();
    component.save();
    expect(component.formError()).toBe('El nombre es obligatorio.');
    expect(creditorService.createCreditor).not.toHaveBeenCalled();

    creditorService.createCreditor.mockReturnValue(throwError(() => ({ error: { message: "Ya existe una persona con el nombre 'Mami'." } })));
    component.updateDraft('name', 'mami');
    component.save();
    expect(component.formError()).toBe("Ya existe una persona con el nombre 'Mami'.");
  });

  it('DEU-01: al eliminar con deuda muestra el rechazo del servidor', () => {
    creditorService.deleteCreditor.mockReturnValue(throwError(() => ({
      error: { message: 'Mami tiene una deuda de Bs 150.00; devuélvala antes de darla de baja.' }
    })));
    component.openDelete(mami);
    component.confirmDelete();
    expect(component.errorMessage()).toBe('Mami tiene una deuda de Bs 150.00; devuélvala antes de darla de baja.');
  });

  it('DEU-04: abrir un pedido oculta el detalle y al cerrarlo lo recarga', () => {
    component.openDetail(mami);
    component.openPurchase('pur-1');
    expect(component.purchaseId()).toBe('pur-1');
    component.closePurchase();
    expect(component.purchaseId()).toBeNull();
    expect(component.detailRefresh()).toBe(1);
    expect(component.detailCreditorId()).toBe('cre-1');
  });
});
