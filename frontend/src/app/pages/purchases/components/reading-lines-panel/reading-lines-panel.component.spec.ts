import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ReadingLinesPanelComponent } from './reading-lines-panel.component';
import { Product } from '../../../../core/product/models/product.models';
import { PurchaseDraftLine } from '../../../../core/purchase/models/purchase.models';

describe('ReadingLinesPanelComponent', () => {
  let component: ReadingLinesPanelComponent;
  let fixture: ComponentFixture<ReadingLinesPanelComponent>;
  const azul = { id: 'p-1', code: 'CEA', name: 'Cepillo Azul', status: 'ACTIVO' } as Product;
  const rojo = { id: 'p-2', code: 'CER', name: 'Cepillo Rojo', status: 'ACTIVO' } as Product;
  const line = {
    text: 'cepillo', quantity: 2, quantityDoubtful: false, quantityText: '2', productId: null, unitCost: null, costNote: null,
    suggestions: [{ id: 'p-1', code: 'CEA', name: 'Cepillo Azul', supplierName: null }, { id: 'gone', code: 'X', name: 'Ya no', supplierName: null }]
  } as PurchaseDraftLine;

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [ReadingLinesPanelComponent] }).compileComponents();
    fixture = TestBed.createComponent(ReadingLinesPanelComponent);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('lines', [line]);
    fixture.componentRef.setInput('products', [azul, rojo]);
    fixture.detectChanges();
  });

  it('CA-03: muestra las sugerencias que siguen en el catálogo', () => {
    expect(component.suggestions(line)).toEqual([azul]);
    expect(fixture.nativeElement.textContent).toContain('2 × cepillo');
  });

  it('CA-03: elegir una sugerencia la entrega', () => {
    const chosen = vi.fn();
    component.choose.subscribe(chosen);
    (fixture.nativeElement.querySelector('.suggestions button') as HTMLButtonElement).click();
    expect(chosen).toHaveBeenCalledWith({ index: 0, product: azul });
  });

  it('CA-03: el buscador encuentra otro producto', () => {
    component.onSearch(0, 'rojo');
    expect(component.results()).toEqual([rojo]);
  });
});
