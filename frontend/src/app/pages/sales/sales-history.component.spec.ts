import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { SalesHistoryComponent } from './sales-history.component';
import { SaleService } from '../../core/sale/sale.service';
import { Sale } from '../../core/sale/models/sale.models';

describe('SalesHistoryComponent', () => {
  let component: SalesHistoryComponent;
  let fixture: ComponentFixture<SalesHistoryComponent>;
  let saleService: any;

  const sale: Sale = {
    id: 's-1', saleNumber: 'VTA-000010', cashSessionId: 'c-1', sessionNumber: 3, status: 'COMPLETADA',
    totalAmount: 25.5, paymentMethod: 'CREDITO', saleType: 'AL_COSTO', creditAmount: 25.5,
    createdBy: 'operador', createdAt: '2026-10-20T12:00:00Z'
  };
  const page = (number: number, totalPages: number) =>
    of({ content: [sale], totalElements: totalPages * 20, totalPages, size: 20, number });

  beforeEach(async () => {
    saleService = {
      searchSales: vi.fn().mockReturnValue(page(0, 3)),
      getSellers: vi.fn().mockReturnValue(of(['admin', 'operador'])),
      getSaleById: vi.fn().mockReturnValue(of(sale))
    };
    await TestBed.configureTestingModule({
      imports: [SalesHistoryComponent],
      providers: [{ provide: SaleService, useValue: saleService }]
    }).compileComponents();

    fixture = TestBed.createComponent(SalesHistoryComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('POS-09: carga la primera página, los usuarios y muestra la paginación', () => {
    expect(saleService.searchSales).toHaveBeenCalledWith(
      { from: null, to: null, status: null, saleType: null, paymentMethod: null, createdBy: null }, 0, 20);
    const text = fixture.nativeElement.textContent;
    expect(text).toContain('VTA-000010');
    expect(text).toContain('Sesión #3');
    expect(text).toContain('Crédito');
    expect(text).toContain('60 ventas · Página 1 de 3');
    expect(component.sellers()).toEqual(['admin', 'operador']);
  });

  it('POS-09: filtra por fecha, estado, tipo, método y usuario en el servidor y vuelve a la página 1', () => {
    saleService.searchSales.mockReturnValue(page(1, 3));
    component.goToPage(1);
    expect(component.currentPage()).toBe(1);

    saleService.searchSales.mockReturnValue(page(0, 1));
    component.setStatusFilter('ANULADA');
    component.onFromDate('2026-10-01');
    component.onToDate('2026-10-25');
    component.onSaleType('AL_COSTO');
    component.onPaymentMethod('CREDITO');
    component.onCreatedBy('operador');
    expect(saleService.searchSales).toHaveBeenLastCalledWith({
      from: '2026-10-01', to: '2026-10-25', status: 'ANULADA', saleType: 'AL_COSTO',
      paymentMethod: 'CREDITO', createdBy: 'operador'
    }, 0, 20);
    expect(component.currentPage()).toBe(0);

    component.clearFilters();
    expect(component.hasFilters()).toBe(false);
  });

  it('POS-09: no pide páginas fuera de rango', () => {
    saleService.searchSales.mockClear();
    component.goToPage(-1);
    component.goToPage(3);
    component.goToPage(0);
    expect(saleService.searchSales).not.toHaveBeenCalled();
  });

  it('POS-08: abre el detalle de la venta', () => {
    component.openDetail(sale);
    expect(component.detailSale()).toEqual(sale);
  });

  it('POS-09: muestra el error del servidor', () => {
    saleService.searchSales.mockReturnValue(throwError(() => ({ error: { message: 'La fecha desde no puede ser posterior a la fecha hasta.' } })));
    component.onFromDate('2026-10-30');
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('La fecha desde no puede ser posterior');
  });
});
