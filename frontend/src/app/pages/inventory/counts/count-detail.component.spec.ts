import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { vi } from 'vitest';
import { CountDetailComponent, draftQuantity, draftRequest } from './count-detail.component';
import { InventoryService } from '../../../core/inventory/inventory.service';
import { ProductService } from '../../../core/product/product.service';
import { CountProduct, StockCount } from '../../../core/inventory/models/inventory.models';

describe('CountDetailComponent', () => {
  const bar: CountProduct = {
    productId: 'bar', code: 'NSE-BAR-CHI', name: 'Barra Chia', saleUnit: 'UNIT', status: 'CONTROLLED',
    barcodes: ['7791000000017'], packages: [{ id: 'box', name: 'Caja ×12', baseQuantity: 12, barcode: '7792000000016' }],
    countedQuantity: null, breakdown: null, countedBy: null, countedAt: null
  };
  const count: StockCount = {
    id: 'c1', number: 3, categoryId: 'cat', categoryName: 'Snacks', status: 'ABIERTO', createdBy: 'ana',
    createdAt: '2026-11-22T12:00:00Z', closedBy: null, closedAt: null, countedProducts: 0, products: [bar]
  };

  let fixture: ComponentFixture<CountDetailComponent>;
  let component: CountDetailComponent;
  let inventory: any;

  beforeEach(async () => {
    inventory = {
      getCount: vi.fn(() => of(count)),
      saveCountLine: vi.fn(() => of({ ...count, products: [{ ...bar, countedQuantity: 40, breakdown: '3 Caja ×12 + 4' }] })),
      reviewCount: vi.fn(() => of({ id: 'c1', number: 3, categoryName: 'Snacks', notCounted: 0, differenceValue: 6,
        lines: [{ productId: 'bar', code: 'NSE-BAR-CHI', name: 'Barra Chia', saleUnit: 'UNIT', initialLoad: false,
          countedQuantity: 40, breakdown: '3 Caja ×12 + 4', expectedQuantity: 37, difference: 3, unitCost: 2,
          differenceValue: 6 }] })),
      confirmCount: vi.fn(() => of(count)),
      discardCount: vi.fn(() => of(count))
    };
    await TestBed.configureTestingModule({
      imports: [CountDetailComponent],
      providers: [
        provideRouter([]),
        { provide: InventoryService, useValue: inventory },
        { provide: ProductService, useValue: { getProducts: () => of([]) } },
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: new Map([['id', 'c1']]) } } }
      ]
    }).compileComponents();
    vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    fixture = TestBed.createComponent(CountDetailComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('INV-05: «3 Caja ×12 + 4» son 40 y se mandan como empaques y sueltas', () => {
    const draft = { packages: { box: 3 }, loose: 4 };
    expect(draftQuantity(bar, draft)).toBe(40);
    expect(draftRequest(bar, draft)).toEqual({ packages: [{ presentationId: 'box', quantity: 3 }], loose: 4 });
  });

  it('INV-05: a ciegas: mientras se cuenta no se ve el stock ni lo esperado', () => {
    const text: string = fixture.nativeElement.textContent;
    expect(text).toContain('Barra Chia');
    expect(text).not.toContain('Esperado');
    expect(text).not.toContain('37');
  });

  it('INV-05: el lector suma una caja con el código del empaque y una suelta con el del producto', () => {
    component.onCode('7792000000016');
    component.onCode('7792000000016');
    component.onCode('7791000000017');
    expect(component.draft(bar)).toEqual({ packages: { box: 2 }, loose: 1 });
    component.save(bar);
    expect(inventory.saveCountLine).toHaveBeenCalledWith('c1', 'bar', { packages: [{ presentationId: 'box', quantity: 2 }], loose: 1 });
  });

  it('INV-05: al revisar se ve la diferencia en unidades y Bs, y se confirma', () => {
    component.startReview();
    fixture.detectChanges();
    const text: string = fixture.nativeElement.textContent;
    expect(text).toContain('Esperado');
    expect(text).toContain('+3');
    expect(text).toContain('6.00');
    component.confirm();
    expect(inventory.confirmCount).toHaveBeenCalledWith('c1');
  });
});
