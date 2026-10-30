import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TransactionsComponent } from './transactions.component';
import { FinancialTransactionService } from '../../../core/finance/financial-transaction.service';
import { FinancialAccountService } from '../../../core/finance/financial-account.service';
import { FinancialCategoryService } from '../../../core/finance/financial-category.service';
import { SaleService } from '../../../core/sale/sale.service';
import { of } from 'rxjs';
import { storeMonthStart, storeToday } from '../../../shared/utils/store-date';
import {
  FinancialAccount,
  FinancialCategory,
  FinancialTransaction
} from '../../../core/finance/models/finance.models';

describe('TransactionsComponent', () => {
  let component: TransactionsComponent;
  let fixture: ComponentFixture<TransactionsComponent>;
  let mockTransactionService: any;
  let mockAccountService: any;
  let mockCategoryService: any;
  let mockSaleService: any;

  const mockAccounts: FinancialAccount[] = [
    {
      id: 'acc-caja',
      name: 'Caja',
      color: '#164312',
      initialBalance: 0,
      currentBalance: 300,
      isCashAccount: true,
      isQrAccount: false
    },
    {
      id: 'acc-bisa',
      name: 'BISA',
      color: '#0284c7',
      initialBalance: 1000,
      currentBalance: 1500,
      isCashAccount: false,
      isQrAccount: true
    },
    {
      id: 'acc-joaco',
      name: 'Joaco',
      color: '#d97706',
      initialBalance: 500,
      currentBalance: 700,
      isCashAccount: false,
      isQrAccount: false
    }
  ];

  const mockCategories: FinancialCategory[] = [
    { id: 'cat-sales', name: 'Ventas', type: 'INGRESO', color: '#2E5B27', isSalesDefault: true },
    { id: 'cat-supplies', name: 'Insumos', type: 'GASTO', color: '#EF4444', isSalesDefault: false }
  ];

  const mockTransactions: FinancialTransaction[] = [
    {
      id: 'tx-1',
      type: 'INGRESO_MANUAL',
      amount: 500,
      transactionDate: '2026-10-07T14:30:00',
      description: 'Aporte socios',
      status: 'ACTIVO',
      sourceAccount: { id: 'acc-bisa', name: 'BISA', color: '#0284c7' },
      category: { id: 'cat-sales', name: 'Ventas', type: 'INGRESO', color: '#2E5B27' },
      createdBy: 'admin',
      createdAt: '2026-10-07T14:30:00'
    },
    {
      id: 'tx-2',
      type: 'VENTA_INGRESO',
      amount: 150,
      transactionDate: '2026-10-07T15:00:00',
      description: 'Ingreso por venta #101',
      status: 'ACTIVO',
      saleId: 'sale-101',
      sourceAccount: { id: 'acc-caja', name: 'Caja', color: '#164312' },
      category: { id: 'cat-sales', name: 'Ventas', type: 'INGRESO', color: '#2E5B27' }
    }
  ];

  beforeEach(async () => {
    mockTransactionService = {
      getTransactions: vi.fn().mockReturnValue(of(mockTransactions)),
      createTransaction: vi.fn().mockReturnValue(of(mockTransactions[0])),
      updateTransaction: vi.fn().mockReturnValue(of(mockTransactions[0])),
      voidTransaction: vi.fn().mockReturnValue(of(mockTransactions[0]))
    };

    mockAccountService = {
      getAccounts: vi.fn().mockReturnValue(of(mockAccounts))
    };

    mockCategoryService = {
      getCategories: vi.fn().mockReturnValue(of(mockCategories))
    };

    mockSaleService = {
      getSaleById: vi.fn().mockReturnValue(of(null))
    };

    await TestBed.configureTestingModule({
      imports: [TransactionsComponent],
      providers: [
        { provide: FinancialTransactionService, useValue: mockTransactionService },
        { provide: FinancialAccountService, useValue: mockAccountService },
        { provide: FinancialCategoryService, useValue: mockCategoryService },
        { provide: SaleService, useValue: mockSaleService }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(TransactionsComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('debe crearse y cargar transacciones y metadatos', () => {
    expect(component).toBeTruthy();
    expect(component.transactions().length).toBe(2);
    expect(component.accounts().length).toBe(3);
    expect(component.categories().length).toBe(2);
  });

  it('debe inicializar el rango de fechas por defecto con primer día del mes y día actual de la tienda en formato YYYY-MM-DD', () => {
    expect(component.startDate()).toBe(storeMonthStart());
    expect(component.endDate()).toBe(storeToday());
  });

  it('debe enviar a getTransactions el inicio del día (00:00:00) y fin del día (23:59:59) en formato Instant ISO', () => {
    component.startDate.set('2026-10-01');
    component.endDate.set('2026-10-08');
    component.loadTransactions();

    // Days of the store (UTC-4), whatever the time zone of the machine running the tests
    const expectedStart = '2026-10-01T04:00:00.000Z';
    const expectedEnd = '2026-10-09T03:59:59.999Z';

    expect(mockTransactionService.getTransactions).toHaveBeenCalledWith(
      expect.objectContaining({
        startDate: expectedStart,
        endDate: expectedEnd
      })
    );
  });

  it('debe soportar rango de un solo día abarcando las 24 horas completas', () => {
    component.startDate.set('2026-10-08');
    component.endDate.set('2026-10-08');
    component.loadTransactions();

    const expectedStart = '2026-10-08T04:00:00.000Z';
    const expectedEnd = '2026-10-09T03:59:59.999Z';

    expect(mockTransactionService.getTransactions).toHaveBeenCalledWith(
      expect.objectContaining({
        startDate: expectedStart,
        endDate: expectedEnd
      })
    );
  });

  it('debe filtrar las cuentas elegibles para ingreso manual excluyendo Caja', () => {
    const eligible = component.incomeEligibleAccounts();
    expect(eligible.length).toBe(2);
    expect(eligible.some(a => a.name === 'Caja')).toBe(false);
  });

  it('debe registrar un ingreso manual válido', () => {
    component.openIncomeModal();
    expect(component.activeModalType()).toBe('INGRESO');

    component.incomeForm.patchValue({
      accountId: 'acc-bisa',
      categoryId: 'cat-sales',
      amount: 400,
      description: 'Capital operativo'
    });

    component.submitIncome();

    expect(mockTransactionService.createTransaction).toHaveBeenCalled();
    expect(component.activeModalType()).toBeNull();
  });

  it('debe marcar campos como touched y no llamar al servicio si incomeForm es inválido', () => {
    component.openIncomeModal();
    component.incomeForm.patchValue({
      accountId: '',
      categoryId: '',
      amount: null,
      description: ''
    });

    component.submitIncome();

    expect(component.incomeForm.invalid).toBe(true);
    expect(component.incomeForm.get('accountId')?.touched).toBe(true);
    expect(component.incomeForm.get('categoryId')?.touched).toBe(true);
    expect(component.incomeForm.get('amount')?.touched).toBe(true);
    expect(component.incomeForm.get('description')?.touched).toBe(true);
    expect(mockTransactionService.createTransaction).not.toHaveBeenCalled();
  });

  it('debe registrar un gasto manual válido', () => {
    component.openExpenseModal();
    expect(component.activeModalType()).toBe('GASTO');

    component.expenseForm.patchValue({
      accountId: 'acc-caja',
      categoryId: 'cat-supplies',
      amount: 80,
      description: 'Compra de bolsas'
    });

    component.submitExpense();

    expect(mockTransactionService.createTransaction).toHaveBeenCalled();
    expect(component.activeModalType()).toBeNull();
  });

  it('debe marcar campos como touched y no llamar al servicio si expenseForm es inválido', () => {
    component.openExpenseModal();
    component.expenseForm.patchValue({
      accountId: '',
      categoryId: '',
      amount: null,
      description: ''
    });

    component.submitExpense();

    expect(component.expenseForm.invalid).toBe(true);
    expect(component.expenseForm.get('accountId')?.touched).toBe(true);
    expect(component.expenseForm.get('categoryId')?.touched).toBe(true);
    expect(component.expenseForm.get('amount')?.touched).toBe(true);
    expect(component.expenseForm.get('description')?.touched).toBe(true);
    expect(mockTransactionService.createTransaction).not.toHaveBeenCalled();
  });

  it('debe marcar campos como touched y no llamar al servicio si transferForm tiene campos vacíos', () => {
    component.openTransferModal();
    component.transferForm.patchValue({
      sourceAccountId: '',
      destinationAccountId: '',
      amount: null,
      description: ''
    });

    component.submitTransfer();

    expect(component.transferForm.invalid).toBe(true);
    expect(component.transferForm.get('sourceAccountId')?.touched).toBe(true);
    expect(component.transferForm.get('destinationAccountId')?.touched).toBe(true);
    expect(component.transferForm.get('amount')?.touched).toBe(true);
    expect(component.transferForm.get('description')?.touched).toBe(true);
    expect(mockTransactionService.createTransaction).not.toHaveBeenCalled();
  });

  it('debe impedir transferencia si origen y destino son la misma cuenta', () => {
    component.openTransferModal();
    expect(component.activeModalType()).toBe('TRANSFERENCIA');

    component.transferForm.patchValue({
      sourceAccountId: 'acc-bisa',
      destinationAccountId: 'acc-bisa',
      amount: 100,
      description: 'Transferencia duplicada'
    });

    component.submitTransfer();

    expect(mockTransactionService.createTransaction).not.toHaveBeenCalled();
    expect(component.modalFormError()).toBe('La cuenta origen y destino deben ser diferentes.');
  });

  it('debe registrar transferencia válida entre cuentas distintas', () => {
    component.openTransferModal();

    component.transferForm.patchValue({
      sourceAccountId: 'acc-bisa',
      destinationAccountId: 'acc-joaco',
      amount: 250,
      description: 'Traspaso a cuenta Joaco'
    });

    component.submitTransfer();

    expect(mockTransactionService.createTransaction).toHaveBeenCalled();
    expect(component.activeModalType()).toBeNull();
  });

  it('debe abrir modal de anulación y confirmar anulación auditada', () => {
    const tx = mockTransactions[0];
    component.openVoidModal(tx);

    expect(component.isVoidModalOpen()).toBe(true);
    expect(component.transactionToVoid()?.id).toBe('tx-1');

    component.voidReason.set('Error en digitación');
    component.confirmVoid();

    expect(mockTransactionService.voidTransaction).toHaveBeenCalledWith('tx-1', {
      voidReason: 'Error en digitación'
    });
    expect(component.isVoidModalOpen()).toBe(false);
  });

  it('no debe permitir anular transacción con saleId (venta POS protegida)', () => {
    const saleTx = mockTransactions[1]; // has saleId: 'sale-101'
    component.openVoidModal(saleTx);

    expect(component.isVoidModalOpen()).toBe(false);
    expect(component.transactionToVoid()).toBeNull();
  });

  it('no debe permitir anular ni editar un movimiento histórico (FIN-11)', () => {
    const historicalTx: FinancialTransaction = { ...mockTransactions[0], id: 'tx-his', historical: true };

    component.openVoidModal(historicalTx);
    component.openEditModal(historicalTx);

    expect(component.isVoidModalOpen()).toBe(false);
    expect(component.activeModalType()).toBeNull();
  });

  it('debe mostrar el modal de ingreso con su texto al abrirlo', () => {
    component.openIncomeModal();
    fixture.detectChanges();

    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('Registrar ingreso Manual');
    expect(text).toContain('Nota: La cuenta Caja no admite ingresos manuales directos.');
    expect(fixture.nativeElement.querySelector('#inc-acc')).not.toBeNull();
  });

  it('debe mostrar el modal de transferencia con origen y destino', () => {
    component.openTransferModal();
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('#trf-src')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('#trf-dst')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('#trf-cat')).toBeNull();
  });

  it('debe actualizar el filtro y recargar al cambiar el estado', () => {
    mockTransactionService.getTransactions.mockClear();
    const select = fixture.nativeElement.querySelector('#filter-status') as HTMLSelectElement;
    select.value = 'ANULADO';
    select.dispatchEvent(new Event('change'));

    expect(component.selectedStatus()).toBe('ANULADO');
    expect(mockTransactionService.getTransactions).toHaveBeenCalledWith(
      expect.objectContaining({ status: 'ANULADO' })
    );
  });

  it('debe abrir modal de detalle de venta al consultar transacción con saleId', () => {
    component.openSaleDetail('sale-101');

    expect(component.isSaleDetailOpen()).toBe(true);
    expect(component.selectedSaleId()).toBe('sale-101');

    component.closeSaleDetail();
    expect(component.isSaleDetailOpen()).toBe(false);
    expect(component.selectedSaleId()).toBeNull();
  });
});
