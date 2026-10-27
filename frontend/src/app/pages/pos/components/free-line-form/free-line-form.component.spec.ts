import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FreeLineFormComponent } from './free-line-form.component';
import { Product } from '../../../../core/product/models/product.models';

const unidentified = {
  id: 'prod-sin', code: 'SIN-IDENT', name: 'Producto sin identificar', specialType: 'UNIDENTIFIED'
} as Product;

describe('FreeLineFormComponent', () => {
  let component: FreeLineFormComponent;
  let fixture: ComponentFixture<FreeLineFormComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [FreeLineFormComponent] }).compileComponents();
    fixture = TestBed.createComponent(FreeLineFormComponent);
    fixture.componentRef.setInput('product', unidentified);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('POS-11: exige nombre y precio mayor a 0 con hasta 2 decimales', () => {
    const emitted: unknown[] = [];
    component.confirmed.subscribe((line) => emitted.push(line));

    component.confirm();
    expect(component.nameError()).toBe('Escriba el nombre.');
    component.name.set('Granola');
    component.price.set(1.005);
    component.confirm();
    expect(component.priceError()).toBe('El precio admite hasta 2 decimales.');
    expect(emitted).toEqual([]);

    component.name.set('  Granola de coco ');
    component.price.set(25);
    component.confirm();
    expect(emitted).toEqual([{ name: 'Granola de coco', price: 25 }]);
  });
});
