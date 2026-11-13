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

  it('CAJ-14: muestra cada reapertura con quién, motivo y lo que tenía el cierre, también si son varias', () => {
    fixture.componentRef.setInput('session', {
      ...session,
      reopenings: [
        { id: 'r-1', reason: 'Faltaron ventas', reopenedBy: 'luis', reopenedAt: '2026-10-30T13:00:00Z', closingAmount: 105,
          closingComment: 'Faltan 5', closedBy: 'ana', closedAt: '2026-10-30T12:00:00Z', expectedCash: 110, difference: -5,
          cuts: [{ denomination: 100, cashQuantity: 1, reserveQuantity: 0, subtotal: 100 }] },
        { id: 'r-2', reason: 'Otra vez', reopenedBy: 'eva', reopenedAt: '2026-10-30T15:00:00Z', closingAmount: 120,
          closingComment: null, closedBy: 'ana', closedAt: '2026-10-30T14:00:00Z', expectedCash: 120, difference: 0, cuts: [] }
      ]
    });
    fixture.detectChanges();

    const blocks = Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('.reopening')).map(el => el.textContent ?? '');
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Reaperturas (2)');
    expect(blocks.length).toBe(2);
    expect(blocks[0]).toContain('luis');
    expect(blocks[0]).toContain('Faltaron ventas');
    expect(blocks[0]).toContain('ana');
    expect(blocks[0]).toContain('105.00');
    expect(blocks[0]).toContain('110.00');
    expect(blocks[0]).toContain('-5.00');
    expect(blocks[0]).toContain('Faltan 5');
    expect(blocks[0]).toContain('Bs 100.00 × 1');
    expect(blocks[1]).toContain('Otra vez');
    expect(blocks[1]).toContain('eva');
  });

  it('CAJ-14: sin reaperturas no hay bloque', () => {
    fixture.componentRef.setInput('session', session);
    fixture.detectChanges();

    expect((fixture.nativeElement as HTMLElement).textContent).not.toContain('Reaperturas');
  });

  it('CAJ-04: el arqueo muestra las reservas del cierre, también sin cortes', () => {
    fixture.componentRef.setInput('session', { ...session, cuts: [], closingReserveBills: 150, closingReserveCoins: 200 });
    fixture.componentInstance.setTab('cortes');
    fixture.detectChanges();
    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('Reserva billetes');
    expect(text).toContain('Bs 150.00');
    expect(text).toContain('Reserva monedas');
    expect(text).toContain('Bs 200.00');
    expect(text).not.toContain('por monto directo');
  });

  it('CAJ-04: una caja sin reservas se ve como antes', () => {
    fixture.componentRef.setInput('session', { ...session, cuts: [] });
    fixture.componentInstance.setTab('cortes');
    fixture.detectChanges();
    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('por monto directo');
    expect(text).not.toContain('Reserva billetes');
  });

  it('CAJ-04, CAJ-14: la reapertura muestra las reservas del cierre deshecho', () => {
    fixture.componentRef.setInput('session', {
      ...session,
      reopenings: [
        { id: 'r-1', reason: 'Faltó contar', reopenedBy: 'luis', reopenedAt: '2026-10-30T13:00:00Z', closingAmount: 450,
          closingComment: null, closedBy: 'ana', closedAt: '2026-10-30T12:00:00Z', expectedCash: 450, difference: 0,
          cuts: [{ denomination: 100, cashQuantity: 1, reserveQuantity: 0, subtotal: 100 }],
          closingReserveBills: 150, closingReserveCoins: 200 }
      ]
    });
    fixture.detectChanges();
    const block = (fixture.nativeElement as HTMLElement).querySelector('.reopening')?.textContent ?? '';
    expect(block).toContain('Billetes Bs 150.00');
    expect(block).toContain('Monedas Bs 200.00');
  });
});
