import { ComponentFixture, TestBed } from '@angular/core/testing';
import { SalesTableComponent } from './sales-table.component';
import { Sale } from '../../../core/sale/models/sale.models';

describe('SalesTableComponent', () => {
  let component: SalesTableComponent;
  let fixture: ComponentFixture<SalesTableComponent>;

  const base: Sale = {
    id: 's-1', saleNumber: 'VTA-000001', cashSessionId: 'c-1', sessionNumber: 7, status: 'COMPLETADA',
    totalAmount: 50, paymentMethod: 'EFECTIVO', createdBy: 'admin', createdAt: '2026-10-20T12:00:00Z',
    saleType: 'NORMAL', creditAmount: 0
  };
  const credit: Sale = {
    ...base, id: 's-2', saleNumber: 'VTA-000002', paymentMethod: 'CREDITO', saleType: 'AL_COSTO',
    customerName: 'Tienda amiga', creditAmount: 40, totalAmount: 40
  };
  const partial: Sale = {
    ...base, id: 's-3', saleNumber: 'VTA-000003', paymentMethod: 'QR', saleType: 'AL_COSTO', creditAmount: 15, totalAmount: 20
  };
  const voided: Sale = { ...base, id: 's-4', saleNumber: 'VTA-000004', status: 'ANULADA' };

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [SalesTableComponent] }).compileComponents();
    fixture = TestBed.createComponent(SalesTableComponent);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('sales', [base, credit, partial, voided]);
  });

  it('POS-09: muestra Crédito, la parte a crédito, el tipo al costo y el cliente', () => {
    fixture.detectChanges();
    const rows = fixture.nativeElement.querySelectorAll('tbody tr');
    expect(rows[1].textContent).toContain('Crédito');
    expect(rows[1].textContent).toContain('Al costo');
    expect(rows[1].textContent).toContain('Cliente: Tienda amiga');
    expect(rows[2].textContent).toContain('QR');
    expect(rows[2].textContent).toContain('+ Crédito Bs 15.00');
    expect(rows[0].textContent).not.toContain('Crédito');
  });

  it('POS-09: la columna Caja y el botón Anular son opcionales', () => {
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).not.toContain('Sesión #7');
    expect(fixture.nativeElement.querySelector('[aria-label="Anular venta"]')).toBeNull();

    fixture.componentRef.setInput('showSession', true);
    fixture.componentRef.setInput('showVoid', true);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Sesión #7');
    expect(fixture.nativeElement.querySelectorAll('[aria-label="Anular venta"]').length).toBe(4);
  });

  it('CAJ-11: marca la venta cargada pasada la medianoche con su día de operación', () => {
    const late: Sale = { ...base, id: 's-5', saleNumber: 'VTA-000005', createdAt: '2026-11-01T04:20:00Z', operationDate: '2026-10-31' };
    fixture.componentRef.setInput('sales', [{ ...base, operationDate: '2026-10-20' }, late]);
    fixture.detectChanges();
    const rows = fixture.nativeElement.querySelectorAll('tbody tr');
    expect(rows[0].querySelector('.day-mark')).toBeNull();
    expect(rows[1].querySelector('.day-mark').textContent).toContain('Día 31/10 · cargada el 01/11 00:20');
  });

  it('POS-07: no anula una venta anulada ni con motivo de bloqueo', () => {
    const emitted: Sale[] = [];
    component.voidSale.subscribe(s => emitted.push(s));
    component.onVoid(voided);
    component.onVoid(base);
    fixture.componentRef.setInput('voidBlockedReason', 'No se pueden anular ventas con caja cerrada');
    component.onVoid(credit);
    expect(emitted).toEqual([base]);
    expect(component.voidTitle(credit)).toBe('No se pueden anular ventas con caja cerrada');
  });

  it('POS-08: Ver detalle emite la venta', () => {
    const emitted: Sale[] = [];
    component.viewDetail.subscribe(s => emitted.push(s));
    fixture.detectChanges();
    fixture.nativeElement.querySelector('[aria-label="Ver detalle"]').click();
    expect(emitted).toEqual([base]);
  });
});
