import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Subject, of, throwError } from 'rxjs';
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

  it('DEU-04: la búsqueda espera 250 ms y una respuesta vieja no pisa la más nueva', () => {
    vi.useFakeTimers();
    const slow = new Subject<Creditor[]>();
    const fast = new Subject<Creditor[]>();
    creditorService.getCreditors.mockClear();
    creditorService.getCreditors.mockReturnValueOnce(slow).mockReturnValueOnce(fast);
    component.onSearch('x');
    component.onSearch('xy');
    vi.advanceTimersByTime(249);
    expect(creditorService.getCreditors).not.toHaveBeenCalled();
    vi.advanceTimersByTime(1);
    expect(creditorService.getCreditors).toHaveBeenCalledTimes(1);
    component.onSearch('xyz');
    vi.advanceTimersByTime(250);
    vi.useRealTimers();
    fast.next([mami]);
    slow.next([mami, rosa]);
    expect(component.creditors().length).toBe(1);
  });

  it('DEU-04: filtra por estado y texto', () => {
    component.setStatusFilter('INACTIVO');
    expect(creditorService.getCreditors).toHaveBeenLastCalledWith('INACTIVO', '');
    vi.useFakeTimers();
    component.onSearch('ma');
    vi.advanceTimersByTime(250);
    vi.useRealTimers();
    expect(creditorService.getCreditors).toHaveBeenLastCalledWith('INACTIVO', 'ma');
  });

  it('DEU-01: crea una persona con nombre recortado y campos vacíos en null', () => {
    component.openCreate();
    component.form.controls.name.setValue('  Tía Rosa ');
    component.save();
    expect(creditorService.createCreditor).toHaveBeenCalledWith({ name: 'Tía Rosa', phone: null, description: null, status: 'ACTIVO' });
    expect(component.isFormOpen()).toBe(false);
  });

  it('DEU-01: exige nombre con el error junto al campo y muestra el error del servidor', () => {
    component.openCreate();
    component.form.controls.name.setValue('   ');
    component.save();
    fixture.detectChanges();
    expect(component.formError()).toBeNull();
    expect(fixture.nativeElement.querySelector('#creditor-name').classList).toContain('is-invalid');
    expect(fixture.nativeElement.querySelector('.invalid-feedback').textContent).toContain('El nombre es obligatorio.');
    expect(creditorService.createCreditor).not.toHaveBeenCalled();

    creditorService.createCreditor.mockReturnValue(throwError(() => ({ error: { message: "Ya existe una persona con el nombre 'Mami'." } })));
    component.form.controls.name.setValue('mami');
    component.save();
    expect(component.formError()).toBe("Ya existe una persona con el nombre 'Mami'.");
  });

  it('DEU-01: al cerrar con cambios sin guardar pide confirmar y sin cambios cierra directo', () => {
    component.openEdit(mami);
    component.attemptCloseForm();
    expect(component.isFormOpen()).toBe(false);

    component.openEdit(mami);
    component.form.controls.phone.setValue('70000000');
    component.attemptCloseForm();
    expect(component.isDiscardConfirmOpen()).toBe(true);
    expect(component.isFormOpen()).toBe(true);
    component.discardChanges();
    expect(component.isFormOpen()).toBe(false);
  });

  it('DEU-04: el error de carga muestra el mensaje del servidor', () => {
    creditorService.getCreditors.mockReturnValue(throwError(() => ({ error: { message: 'Sin conexión con la base.' } })));
    component.loadCreditors();
    expect(component.errorMessage()).toBe('Sin conexión con la base.');
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
