import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CashDetailModalComponent } from './cash-detail-modal.component';
import { CashSessionDetail } from '../../../../core/cash/models/cash.models';

describe('CashDetailModalComponent', () => {
  let fixture: ComponentFixture<CashDetailModalComponent>;

  // CAJ-11: #146 opened on 29-10 and moved to 28-10; one of its sales was recorded after midnight
  const session: CashSessionDetail = {
    id: 'cs-146', sessionNumber: 146, status: 'CERRADA', openingAmount: 100, openedBy: 'admin',
    openedAt: '2026-10-29T13:10:00Z', closingAmount: 300, closedBy: 'admin', closedAt: '2026-10-30T12:00:00Z',
    totalSalesCash: 200, totalSalesQr: 0, totalSales: 200, expectedCash: 300, difference: 0, salesCount: 2,
    operationDate: '2026-10-28', cuts: [],
    sales: [
      { id: 's-1', saleNumber: 'VTA-000001', cashSessionId: 'cs-146', totalAmount: 100, paymentMethod: 'EFECTIVO',
        createdBy: 'admin', createdAt: '2026-10-29T15:00:00Z', operationDate: '2026-10-28' },
      { id: 's-2', saleNumber: 'VTA-000002', cashSessionId: 'cs-146', totalAmount: 100, paymentMethod: 'EFECTIVO',
        createdBy: 'admin', createdAt: '2026-10-28T19:00:00Z', operationDate: '2026-10-28' }
    ]
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [CashDetailModalComponent] }).compileComponents();
    fixture = TestBed.createComponent(CashDetailModalComponent);
    fixture.componentRef.setInput('isOpen', true);
  });

  it('CAJ-11: muestra el día de operación de la caja y marca la que no es del día de su apertura', () => {
    fixture.componentRef.setInput('session', session);
    fixture.detectChanges();
    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('Día de operación:');
    expect(text).toContain('28/10/2026');
    expect(text).toContain('Día 28/10 · abierta el 29/10');
  });

  it('CAJ-11: en la pestaña Ventas marca solo la venta cargada otro día', () => {
    fixture.componentRef.setInput('session', session);
    fixture.detectChanges();
    const rows = (fixture.nativeElement as HTMLElement).querySelectorAll('tbody tr');
    expect(rows[0].querySelector('.day-mark')?.textContent).toContain('Día 28/10 · cargada el 29/10 11:00');
    expect(rows[1].querySelector('.day-mark')).toBeNull();
  });

  it('CAJ-11: una caja del día en que se abrió no lleva marca', () => {
    fixture.componentRef.setInput('session', { ...session, openedAt: '2026-10-28T13:10:00Z', sales: [] });
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).textContent).not.toContain('abierta el');
  });
});
