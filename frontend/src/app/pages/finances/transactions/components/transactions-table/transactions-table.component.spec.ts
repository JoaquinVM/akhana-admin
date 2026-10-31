import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TransactionsTableComponent } from './transactions-table.component';
import { FinancialTransaction } from '../../../../../core/finance/models/finance.models';

describe('TransactionsTableComponent', () => {
  let fixture: ComponentFixture<TransactionsTableComponent>;

  const baseTx: FinancialTransaction = {
    id: 'tx-1',
    type: 'GASTO_MANUAL',
    amount: 80,
    transactionDate: '2026-10-07T14:30:00',
    description: 'Compra de bolsas',
    status: 'ACTIVO',
    sourceAccount: { id: 'acc-bisa', name: 'BISA', color: '#0284c7' }
  };

  function render(transactions: FinancialTransaction[]): HTMLElement {
    fixture.componentRef.setInput('transactions', transactions);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [TransactionsTableComponent]
    }).compileComponents();

    fixture = TestBed.createComponent(TransactionsTableComponent);
  });

  it('CAJ-11: marca el movimiento de una caja cargado pasada la medianoche', () => {
    const root = render([
      { ...baseTx, id: 'tx-same', transactionDate: '2026-10-07T14:30:00Z', operationDate: '2026-10-07' },
      { ...baseTx, id: 'tx-late', transactionDate: '2026-11-01T04:30:00Z', operationDate: '2026-10-31' }
    ]);
    const rows = root.querySelectorAll('tbody tr');
    expect(rows[0].querySelector('.day-mark')).toBeNull();
    expect(rows[1].querySelector('.day-mark')?.textContent).toContain('Día 31/10 · cargada el 01/11 00:30');
  });

  it('debe mostrar editar y anular en un movimiento manual activo del sistema', () => {
    const el = render([baseTx]);

    expect(el.querySelector('.btn-edit')).not.toBeNull();
    expect(el.querySelector('.btn-void')).not.toBeNull();
    expect(el.querySelector('.historical-tag')).toBeNull();
  });

  it('debe marcar «Histórico» y ocultar editar y anular en un movimiento histórico (FIN-11)', () => {
    const el = render([{ ...baseTx, historical: true }]);

    expect(el.querySelector('.historical-tag')?.textContent?.trim()).toBe('Histórico');
    expect(el.querySelector('.btn-edit')).toBeNull();
    expect(el.querySelector('.btn-void')).toBeNull();
    expect(el.querySelector('.btn-audit')).not.toBeNull();
  });

  it('debe ocultar editar y anular en un movimiento anulado', () => {
    const el = render([{ ...baseTx, status: 'ANULADO' }]);

    expect(el.querySelector('.btn-edit')).toBeNull();
    expect(el.querySelector('.btn-void')).toBeNull();
  });

  it('debe avisar al padre las acciones de la fila', () => {
    const el = render([baseTx]);
    const edits: FinancialTransaction[] = [];
    const voids: FinancialTransaction[] = [];
    fixture.componentInstance.editRequested.subscribe(tx => edits.push(tx));
    fixture.componentInstance.voidRequested.subscribe(tx => voids.push(tx));

    (el.querySelector('.btn-edit') as HTMLButtonElement).click();
    (el.querySelector('.btn-void') as HTMLButtonElement).click();

    expect(edits[0].id).toBe('tx-1');
    expect(voids[0].id).toBe('tx-1');
  });

  it('debe mostrar el mensaje vacío sin movimientos', () => {
    const el = render([]);

    expect(el.textContent).toContain('No se encontraron transacciones financieras');
  });
});
