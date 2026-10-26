import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { TransactionsByCategoryReportComponent } from './transactions-by-category-report.component';
import { ReportService } from '../../../../core/report/report.service';
import {
  TransactionsByCategoryMonth, TransactionsByCategoryReport
} from '../../../../core/report/models/report.models';

describe('TransactionsByCategoryReportComponent', () => {
  let component: TransactionsByCategoryReportComponent;
  let fixture: ComponentFixture<TransactionsByCategoryReportComponent>;
  let reportService: any;

  const month = (value: string | null, overrides: Partial<TransactionsByCategoryMonth> = {}): TransactionsByCategoryMonth => ({
    month: value, incomes: [], incomeTotal: 0, expenses: [], expenseTotal: 0, net: 0, transfers: [],
    consumptions: 0, consumptionTotal: 0, ...overrides
  });
  const august = month('2026-08', {
    incomes: [{ categoryId: 'v', categoryName: 'Venta', amount: 67031.5 }],
    incomeTotal: 67031.5,
    expenses: [
      { categoryId: 'c', categoryName: 'Comida', amount: -2530 },
      { categoryId: 'p', categoryName: 'Pedido', amount: -67097.56 }
    ],
    expenseTotal: -69627.56,
    net: -2596.06,
    transfers: [{
      sourceAccountId: 'caja', sourceAccountName: 'Caja', destinationAccountId: 'ef', destinationAccountName: 'Efectivo',
      amount: 19500
    }],
    consumptions: 2,
    consumptionTotal: 45.5
  });
  const july = month('2026-07', {
    incomes: [{ categoryId: 'v', categoryName: 'Venta', amount: 37688 }], incomeTotal: 37688, net: 37688
  });
  const report: TransactionsByCategoryReport = {
    from: '2026-07-01', to: '2026-08-31',
    months: [july, august],
    totals: month(null, {
      incomes: [{ categoryId: 'v', categoryName: 'Venta', amount: 104719.5 }], incomeTotal: 104719.5,
      expenses: august.expenses, expenseTotal: -69627.56, net: 35091.94, transfers: august.transfers,
      consumptions: 2, consumptionTotal: 45.5
    })
  };

  beforeEach(async () => {
    reportService = { getTransactionsByCategory: vi.fn().mockReturnValue(of(report)) };
    await TestBed.configureTestingModule({
      imports: [TransactionsByCategoryReportComponent],
      providers: [{ provide: ReportService, useValue: reportService }]
    }).compileComponents();

    fixture = TestBed.createComponent(TransactionsByCategoryReportComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('FIN-10: carga el mes en curso sin fechas y llena los filtros con el rango del servidor', () => {
    expect(reportService.getTransactionsByCategory).toHaveBeenCalledWith({ from: null, to: null });
    expect(component.fromDate()).toBe('2026-07-01');
    expect(component.toDate()).toBe('2026-08-31');
  });

  it('FIN-10: una columna por mes y el total del rango; ingresos +, gastos − y neto, como Resumen Transacc.', () => {
    const text = fixture.nativeElement.textContent;
    expect(text).toContain('julio 2026');
    expect(text).toContain('agosto 2026');
    expect(text).toContain('Total del rango');
    expect(text).toContain('67,031.50');
    expect(text).toContain('-67,097.56');
    expect(text).toContain('-2,530.00');
    expect(text).toContain('Neto');
    expect(text).toContain('35,091.94');
    // Julio no tiene gastos de Comida
    const comida = component.expenseLines().find(line => line.label === 'Comida')!;
    expect(comida.amounts).toEqual([null, -2530, -2530]);
  });

  it('FIN-10: las transferencias van aparte por origen → destino, fuera del neto', () => {
    const text = fixture.nativeElement.textContent;
    expect(text).toContain('Transferencias');
    expect(text).toContain('Caja → Efectivo');
    expect(text).toContain('19,500.00');
  });

  it('CON-03: la línea Consumo al costo está marcada «sin movimiento de dinero»', () => {
    const text = fixture.nativeElement.textContent;
    expect(text).toContain('Consumo al costo');
    expect(text).toContain('sin movimiento de dinero');
    expect(text).toContain('-45.50');
  });

  it('FIN-10: al cambiar una fecha vuelve a pedir el reporte y muestra el error del servidor', () => {
    reportService.getTransactionsByCategory.mockReturnValue(
      throwError(() => ({ error: { message: 'El rango del reporte no puede superar un año.' } })));
    component.onToDate('2027-12-31');
    fixture.detectChanges();
    expect(reportService.getTransactionsByCategory).toHaveBeenLastCalledWith({ from: '2026-07-01', to: '2027-12-31' });
    expect(fixture.nativeElement.textContent).toContain('El rango del reporte no puede superar un año.');
  });
});
