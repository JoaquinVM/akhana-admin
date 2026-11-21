import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Subject, of } from 'rxjs';
import { ConsumptionsComponent } from './consumptions.component';
import { ConsumptionService } from '../../core/consumption/consumption.service';
import { ProductService } from '../../core/product/product.service';
import { Consumption } from '../../core/consumption/models/consumption.models';

describe('ConsumptionsComponent', () => {
  let component: ConsumptionsComponent;
  let fixture: ComponentFixture<ConsumptionsComponent>;
  let consumptionService: any;

  const consumption: Consumption = {
    id: 'con-1', consumptionNumber: 'CON-000001', consumptionDate: '2026-10-12', consumedFor: 'Ali', comment: null,
    totalAmount: 15.46, status: 'REGISTRADO', createdBy: 'operator', createdAt: '2026-10-12T12:00:00Z',
    voidedBy: null, voidedAt: null, voidReason: null, items: []
  };
  const voided: Consumption = { ...consumption, id: 'con-2', consumptionNumber: 'CON-000002', totalAmount: 4, status: 'ANULADO' };

  beforeEach(async () => {
    consumptionService = {
      getConsumptions: vi.fn().mockReturnValue(of([consumption, voided])),
      getConsumptionById: vi.fn().mockReturnValue(of(consumption)),
      voidConsumption: vi.fn().mockReturnValue(of({ ...consumption, status: 'ANULADO' }))
    };
    await TestBed.configureTestingModule({
      imports: [ConsumptionsComponent],
      providers: [
        { provide: ConsumptionService, useValue: consumptionService },
        { provide: ProductService, useValue: { getProducts: vi.fn().mockReturnValue(of([])) } }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(ConsumptionsComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('CON-01: lista consumos con número, para quién, total y estado', () => {
    const text = fixture.nativeElement.textContent;
    expect(text).toContain('CON-000001');
    expect(text).toContain('Ali');
    expect(text).toContain('15.46');
    expect(text).toContain('Anulado');
  });

  it('CON-01: el total visible no cuenta los anulados', () => {
    expect(component.visibleTotal()).toBe(15.46);
  });

  afterEach(() => vi.useRealTimers());

  it('CON-01: filtra por estado, fechas, producto y texto en el servidor', () => {
    vi.useFakeTimers();
    component.setStatusFilter('ANULADO');
    component.onFromDate('2026-10-01');
    component.onToDate('2026-10-25');
    component.onProductFilter('p-1');
    component.onSearch('ali');
    vi.advanceTimersByTime(250);
    expect(consumptionService.getConsumptions).toHaveBeenLastCalledWith(
      { from: '2026-10-01', to: '2026-10-25', productId: 'p-1', status: 'ANULADO', search: 'ali' });
  });

  it('CON-01: la búsqueda espera 250 ms y una respuesta vieja no pisa la más nueva', () => {
    vi.useFakeTimers();
    const slow = new Subject<Consumption[]>();
    const fast = new Subject<Consumption[]>();
    consumptionService.getConsumptions.mockClear();
    consumptionService.getConsumptions.mockReturnValueOnce(slow).mockReturnValueOnce(fast);

    component.onSearch('a');
    component.onSearch('al');
    vi.advanceTimersByTime(249);
    expect(consumptionService.getConsumptions).not.toHaveBeenCalled();
    vi.advanceTimersByTime(1);
    expect(consumptionService.getConsumptions).toHaveBeenCalledTimes(1);

    component.onSearch('ali');
    vi.advanceTimersByTime(250);
    fast.next([consumption]);
    slow.next([consumption, voided]);
    expect(component.consumptions().map(c => c.id)).toEqual(['con-1']);
  });

  it('CON-01: al anular desde el detalle actualiza la fila', () => {
    component.onUpdated({ ...consumption, status: 'ANULADO' });
    expect(component.consumptions()[0].status).toBe('ANULADO');
  });
});
