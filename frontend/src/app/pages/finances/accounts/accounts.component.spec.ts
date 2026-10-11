import { ComponentFixture, TestBed } from '@angular/core/testing';
import { AccountsComponent } from './accounts.component';
import { FinancialAccountService } from '../../../core/finance/financial-account.service';
import { FinancialTransactionService } from '../../../core/finance/financial-transaction.service';
import { of, throwError } from 'rxjs';
import { FinancialAccount, FinancialTransaction } from '../../../core/finance/models/finance.models';

describe('AccountsComponent', () => {
  let component: AccountsComponent;
  let fixture: ComponentFixture<AccountsComponent>;
  let mockAccountService: any;
  let mockTransactionService: any;

  const mockAccounts: FinancialAccount[] = [
    {
      id: 'acc-caja',
      name: 'Caja',
      color: '#164312',
      initialBalance: 100,
      currentBalance: 500,
      isCashAccount: true,
      isQrAccount: false
    },
    {
      id: 'acc-bisa',
      name: 'Cuenta bancaria BISA',
      color: '#0284c7',
      initialBalance: 1000,
      currentBalance: 1200,
      isCashAccount: false,
      isQrAccount: true
    },
    {
      id: 'acc-zero',
      name: 'Cuenta Cerrada',
      color: '#475569',
      initialBalance: 0,
      currentBalance: 0,
      isCashAccount: false,
      isQrAccount: false
    }
  ];

  beforeEach(async () => {
    mockAccountService = {
      getAccounts: vi.fn().mockReturnValue(of(mockAccounts)),
      createAccount: vi.fn().mockReturnValue(of(mockAccounts[0])),
      updateAccount: vi.fn().mockReturnValue(of(mockAccounts[0])),
      deleteAccount: vi.fn().mockReturnValue(of(void 0))
    };

    mockTransactionService = {
      getTransactions: vi.fn().mockReturnValue(of([]))
    };

    await TestBed.configureTestingModule({
      imports: [AccountsComponent],
      providers: [
        { provide: FinancialAccountService, useValue: mockAccountService },
        { provide: FinancialTransactionService, useValue: mockTransactionService }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(AccountsComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('debe crearse e inicializar la lista de cuentas', () => {
    expect(component).toBeTruthy();
    expect(component.accounts().length).toBe(3);
    expect(component.totalBalance()).toBe(1700);
  });

  it('debe validar las reglas de eliminación de cuenta canDelete()', () => {
    const caja = mockAccounts[0]; // isCashAccount = true
    const bisa = mockAccounts[1]; // isQrAccount = true
    const zeroAcc = mockAccounts[2]; // currentBalance = 0, no caja, no qr

    expect(component.canDelete(caja)).toBe(false);
    expect(component.canDelete(bisa)).toBe(false);
    expect(component.canDelete(zeroAcc)).toBe(true);

    const nonZeroAcc: FinancialAccount = {
      id: 'acc-other',
      name: 'Billetera',
      color: '#ea580c',
      initialBalance: 50,
      currentBalance: 50,
      isCashAccount: false,
      isQrAccount: false
    };
    expect(component.canDelete(nonZeroAcc)).toBe(false);
  });

  it('debe abrir modal de creación y enviar datos válidos a createAccount', () => {
    component.openCreateModal();
    expect(component.isFormModalOpen()).toBe(true);
    expect(component.isEditing()).toBe(false);

    component.accountForm.setValue({
      name: 'Billetera Sol',
      color: '#d97706',
      initialBalance: 250,
      isQrAccount: false
    });

    component.saveAccount();

    expect(mockAccountService.createAccount).toHaveBeenCalledWith({
      name: 'Billetera Sol',
      color: '#d97706',
      initialBalance: 250,
      isQrAccount: false
    });
    expect(component.isFormModalOpen()).toBe(false);
  });

  it('debe abrir modal de edición y deshabilitar saldo inicial', () => {
    const acc = mockAccounts[1];
    component.openEditModal(acc);

    expect(component.isFormModalOpen()).toBe(true);
    expect(component.isEditing()).toBe(true);
    expect(component.selectedAccountId()).toBe('acc-bisa');
    expect(component.accountForm.get('initialBalance')?.disabled).toBe(true);

    component.accountForm.patchValue({
      name: 'BISA Actualizado',
      color: '#0f766e',
      isQrAccount: true
    });

    component.saveAccount();

    expect(mockAccountService.updateAccount).toHaveBeenCalledWith('acc-bisa', {
      name: 'BISA Actualizado',
      color: '#0f766e',
      initialBalance: 1000,
      isQrAccount: true
    });
  });

  it('debe cargar movimientos de la cuenta al abrir el modal de transacciones', () => {
    const dummyTxs: FinancialTransaction[] = [
      {
        id: 'tx-1',
        type: 'INGRESO_MANUAL',
        amount: 200,
        transactionDate: '2026-10-07T12:00:00',
        description: 'Aporte de capital',
        status: 'ACTIVO',
        sourceAccount: { id: 'acc-bisa', name: 'BISA', color: '#0284c7' }
      }
    ];
    mockTransactionService.getTransactions.mockReturnValue(of(dummyTxs));

    component.openMovementsModal(mockAccounts[1]);

    expect(component.isMovementsModalOpen()).toBe(true);
    expect(component.selectedAccountForMovements()?.id).toBe('acc-bisa');
    expect(mockTransactionService.getTransactions).toHaveBeenCalledWith({ accountId: 'acc-bisa' });
    expect(component.accountTransactions().length).toBe(1);
  });

  it('debe ejecutar confirmDelete() y recargar la lista de cuentas', () => {
    component.openDeleteModal(mockAccounts[2]);
    expect(component.isDeleteModalOpen()).toBe(true);

    component.confirmDelete();

    expect(mockAccountService.deleteAccount).toHaveBeenCalledWith('acc-zero');
    expect(component.isDeleteModalOpen()).toBe(false);
    expect(mockAccountService.getAccounts).toHaveBeenCalledTimes(2);
  });
});
