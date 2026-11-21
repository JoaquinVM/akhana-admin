import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Product, ProductPresentation } from '../../../core/product/models/product.models';
import { PresentationPickerComponent } from './presentation-picker.component';

// CAT-11, CAT-15: the picker of a product by kilo, in the POS and in Consumos
describe('PresentationPickerComponent', () => {
  let fixture: ComponentFixture<PresentationPickerComponent>;

  const pkg = (overrides: Partial<ProductPresentation>): ProductPresentation => ({
    id: 'p', name: 'x', baseQuantity: 0.1, fixedPrice: null, price: 21, cost: 13, active: true, sold: true, barcode: null,
    ...overrides
  });
  // Nuez pelada: 130 cost and 210 price per kilo
  const nut = {
    id: 'prod-nut', name: 'Nuez pelada', saleUnit: 'KILOGRAM', buyPrice: 130, sellPrice: 210,
    presentations: [
      pkg({ id: 'half', name: '1/2 libra', baseQuantity: 0.227, fixedPrice: 45, price: 45, cost: 29.51 }),
      pkg({ id: 'sack', name: 'Saco 25 kg', baseQuantity: 25, price: null, cost: 3250, sold: false }),
      pkg({ id: 'hundred', name: '100 g' }),
      pkg({ id: 'old', name: '1 libra', baseQuantity: 0.454, active: false })
    ]
  } as unknown as Product;

  const options = (): string[] => Array.from(fixture.nativeElement.querySelectorAll('.option-name'))
    .map((el) => (el as HTMLElement).textContent!.trim());
  const amounts = (): string[] => Array.from(fixture.nativeElement.querySelectorAll('.option-amount'))
    .map((el) => (el as HTMLElement).textContent!.trim());

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [PresentationPickerComponent] }).compileComponents();
    fixture = TestBed.createComponent(PresentationPickerComponent);
    fixture.componentRef.setInput('product', nut);
  });

  it('ofrece solo los empaques activos que se venden, de menor a mayor, y «Otro peso»', () => {
    fixture.detectChanges();
    expect(options()).toEqual(['100 g', '1/2 libra', 'Otro peso…']);
    expect(amounts().slice(0, 2)).toEqual(['Bs 21.00', 'Bs 45.00']);
  });

  it('al costo muestra equivalencia × costo, sin el precio propio', () => {
    fixture.componentRef.setInput('priceMode', 'atCost');
    fixture.detectChanges();
    expect(amounts().slice(0, 2)).toEqual(['Bs 13.00', 'Bs 29.51']);
  });
});
