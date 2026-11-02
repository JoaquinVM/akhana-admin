import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ReopenCashModalComponent } from './reopen-cash-modal.component';
import { CashSessionSummary } from '../../../../core/cash/models/cash.models';

describe('ReopenCashModalComponent', () => {
  let fixture: ComponentFixture<ReopenCashModalComponent>;
  let component: ReopenCashModalComponent;
  let emitted: string[];

  const session = (difference: number | null): CashSessionSummary => ({
    id: 'cs-1', sessionNumber: 150, status: 'CERRADA', openingAmount: 100, openedBy: 'ana', openedAt: '2026-10-30T13:00:00Z',
    closingAmount: 93, closedAt: '2026-10-30T22:00:00Z', totalSalesCash: 0, totalSalesQr: 0, totalSales: 0, expectedCash: 100,
    difference, salesCount: 0, reopenable: true
  });
  const text = (): string => (fixture.nativeElement as HTMLElement).textContent ?? '';

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [ReopenCashModalComponent] }).compileComponents();
    fixture = TestBed.createComponent(ReopenCashModalComponent);
    component = fixture.componentInstance;
    emitted = [];
    component.confirmed.subscribe((reason) => emitted.push(reason));
    fixture.componentRef.setInput('isOpen', true);
  });

  it('CAJ-14: sin motivo, o solo con espacios, no se puede reabrir', () => {
    fixture.componentRef.setInput('session', session(0));
    fixture.detectChanges();
    expect(component.canSubmit()).toBe(false);

    component.reason.set('   ');
    component.onSubmit();

    expect(component.canSubmit()).toBe(false);
    expect(emitted).toEqual([]);
  });

  it('CAJ-14: con motivo envía el texto sin espacios de más', () => {
    fixture.componentRef.setInput('session', session(0));
    fixture.detectChanges();

    component.reason.set(' Cerrada por error ');
    component.onSubmit();

    expect(emitted).toEqual(['Cerrada por error']);
  });

  it('CAJ-14: avisa el efecto de un faltante, con su monto', () => {
    fixture.componentRef.setInput('session', session(-7));
    fixture.detectChanges();
    expect(text()).toContain('Se anula el ajuste por faltante de Bs 7.00');
    expect(text()).toContain('el saldo de Caja vuelve a lo que era antes del cierre');
  });

  it('CAJ-14: avisa el efecto de un sobrante, con su monto', () => {
    fixture.componentRef.setInput('session', session(12.5));
    fixture.detectChanges();
    expect(text()).toContain('Se anula el ajuste por sobrante de Bs 12.50');
  });

  it('CAJ-14: sin diferencia avisa que ningún saldo cambia', () => {
    fixture.componentRef.setInput('session', session(0));
    fixture.detectChanges();
    expect(text()).toContain('ningún saldo cambia');
  });

  it('CAJ-14: muestra el rechazo del servidor y mientras envía no deja enviar otra vez', () => {
    fixture.componentRef.setInput('session', session(0));
    fixture.componentRef.setInput('serverError', 'Solo se puede reabrir la última caja.');
    fixture.componentRef.setInput('isLoading', true);
    component.reason.set('Motivo');
    fixture.detectChanges();

    expect(text()).toContain('Solo se puede reabrir la última caja.');
    expect(component.canSubmit()).toBe(false);
  });
});
