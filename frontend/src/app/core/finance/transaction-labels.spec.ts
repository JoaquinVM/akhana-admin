import { categoryLabel, categoryTypeLabel, transactionSign, transactionStatusLabel, transactionTypeLabel } from './transaction-labels';

describe('transaction-labels (H6)', () => {
  it('nombra cada tipo en español y separa los gastos que el código llama igual', () => {
    expect(transactionTypeLabel({ type: 'VENTA_INGRESO' })).toBe('Venta');
    expect(transactionTypeLabel({ type: 'SALDO_INICIAL' })).toBe('Saldo inicial');
    expect(transactionTypeLabel({ type: 'TRANSFERENCIA' })).toBe('Transferencia');
    expect(transactionTypeLabel({ type: 'INGRESO_MANUAL' })).toBe('Ingreso');
    expect(transactionTypeLabel({ type: 'INGRESO_MANUAL', customerCollectionId: 'c' })).toBe('Cobro a cliente');
    expect(transactionTypeLabel({ type: 'GASTO_MANUAL' })).toBe('Gasto');
    expect(transactionTypeLabel({ type: 'GASTO_MANUAL', cashSessionId: 's' })).toBe('Gasto de caja');
    expect(transactionTypeLabel({ type: 'GASTO_MANUAL', cashSessionId: 's', purchaseId: 'p' })).toBe('Pago de pedido');
    expect(transactionTypeLabel({ type: 'GASTO_MANUAL', creditorRepaymentId: 'r' })).toBe('Devolución a persona');
  });

  it('muestra el estado en español', () => {
    expect(transactionStatusLabel('ACTIVO')).toBe('Activo');
    expect(transactionStatusLabel('ANULADO')).toBe('Anulado');
  });

  it('pone el signo: una transferencia sale de su origen y entra a su destino', () => {
    expect(transactionSign({ type: 'VENTA_INGRESO' })).toBe('+');
    expect(transactionSign({ type: 'GASTO_MANUAL' })).toBe('-');
    const transfer = { type: 'TRANSFERENCIA' as const, sourceAccount: { id: 'caja' } };
    expect(transactionSign(transfer, 'caja')).toBe('-');
    expect(transactionSign(transfer, 'bisa')).toBe('+');
    expect(transactionSign(transfer)).toBe('');
  });

  it('distingue las dos categorías «Ajuste» y nombra el tipo en español', () => {
    expect(categoryLabel({ name: 'Ajuste', type: 'INGRESO' })).toBe('Ajuste (ingreso)');
    expect(categoryLabel({ name: 'Ajuste', type: 'GASTO' })).toBe('Ajuste (gasto)');
    expect(categoryLabel({ name: 'Pedido', type: 'GASTO' })).toBe('Pedido');
    expect(categoryTypeLabel('INGRESO')).toBe('Ingreso');
    expect(categoryTypeLabel('GASTO')).toBe('Gasto');
  });
});
