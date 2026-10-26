import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { ConsumptionFormModalComponent } from './consumption-form-modal.component';
import { ConsumptionService } from '../../../../core/consumption/consumption.service';
import { Product } from '../../../../core/product/models/product.models';
import { Consumption } from '../../../../core/consumption/models/consumption.models';

function product(overrides: Partial<Product>): Product {
  return {
    id: 'p-1', code: 'ROQ-1', name: 'Roquin', categoryId: 'c-1', categoryName: 'Snacks', categoryColor: '#000',
    supplierId: 'sup-1', supplierName: 'Granel', tags: [], buyPrice: 2.73, sellPrice: 4, fixedProfit: 1.27,
    percentageProfit: 46.52, status: 'ACTIVO', saleUnit: 'UNIT', createdBy: 'admin', createdAt: '2026-10-01T00:00:00Z',
    ...overrides
  };
}

describe('ConsumptionFormModalComponent', () => {
  let component: ConsumptionFormModalComponent;
  let fixture: ComponentFixture<ConsumptionFormModalComponent>;
  let consumptionService: any;

  const roquin = product({});
  const nuts = product({ id: 'p-2', code: 'NUE-1', name: 'Nueces', saleUnit: 'KILOGRAM', buyPrice: 60, sellPrice: 90 });
  const gift = product({ id: 'p-3', code: 'REG-1', name: 'Regalo sin costo', buyPrice: 0, status: 'INACTIVO' });

  beforeEach(async () => {
    consumptionService = {
      registerConsumption: vi.fn().mockReturnValue(of({ id: 'con-1' } as Consumption))
    };
    await TestBed.configureTestingModule({
      imports: [ConsumptionFormModalComponent],
      providers: [{ provide: ConsumptionService, useValue: consumptionService }]
    }).compileComponents();

    fixture = TestBed.createComponent(ConsumptionFormModalComponent);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('products', [roquin, nuts, gift]);
    fixture.componentRef.setInput('isOpen', true);
    fixture.detectChanges();
  });

  it('CON-01: busca productos, incluidos los inactivos', () => {
    component.productSearch.set('regalo');
    expect(component.productMatches().map(p => p.id)).toEqual(['p-3']);
  });

  it('CON-01: el mismo producto suma cantidad y el total va al costo', () => {
    component.addProduct(roquin);
    component.addProduct(roquin);
    expect(component.lines().length).toBe(1);
    expect(component.lines()[0].quantity).toBe(2);
    expect(component.total()).toBe(5.46);
  });

  it('CON-01: un producto por peso empieza en 100 g y acepta presentaciones', () => {
    component.addProduct(nuts);
    expect(component.lines()[0].quantity).toBe(0.1);
    expect(component.total()).toBe(6);
    component.updateQuantity(0, 0.227);
    expect(component.lineSubtotal(component.lines()[0])).toBe(13.62);
  });

  it('CON-01: advierte cuando el producto no tiene costo', () => {
    component.addProduct(gift);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('no tiene costo registrado; el consumo vale Bs 0,00');
  });

  it('CON-01: sin líneas no envía', () => {
    component.submit();
    expect(component.formError()).toBe('El consumo debe tener al menos una línea.');
    expect(consumptionService.registerConsumption).not.toHaveBeenCalled();
  });

  it('CON-01: envía fecha, para quién, comentario y líneas sin costo', () => {
    const saved = vi.fn();
    component.saved.subscribe(saved);
    component.consumptionDate.set('2026-10-20');
    component.consumedFor.set('  Ali ');
    component.comment.set('Regalo Ali');
    component.addProduct(roquin);
    component.submit();
    expect(consumptionService.registerConsumption).toHaveBeenCalledWith({
      consumptionDate: '2026-10-20', consumedFor: 'Ali', comment: 'Regalo Ali',
      items: [{ productId: 'p-1', quantity: 1 }]
    });
    expect(saved).toHaveBeenCalled();
  });

  it('muestra el error del servidor', () => {
    consumptionService.registerConsumption.mockReturnValue(
      throwError(() => ({ error: { message: 'La fecha del consumo no puede ser futura.' } })));
    component.addProduct(roquin);
    component.submit();
    expect(component.formError()).toBe('La fecha del consumo no puede ser futura.');
  });
});
