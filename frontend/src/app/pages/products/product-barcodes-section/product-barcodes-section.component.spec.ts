import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ProductBarcodesSectionComponent } from './product-barcodes-section.component';

const EAN = '7791234567898';

describe('ProductBarcodesSectionComponent (CAT-10)', () => {
  let fixture: ComponentFixture<ProductBarcodesSectionComponent>;
  let emitted: string[][];

  beforeEach(async () => {
    emitted = [];
    await TestBed.configureTestingModule({ imports: [ProductBarcodesSectionComponent] }).compileComponents();
    fixture = TestBed.createComponent(ProductBarcodesSectionComponent);
    fixture.componentInstance.barcodesChange.subscribe((value) => emitted.push(value));
    fixture.detectChanges();
  });

  afterEach(() => fixture.destroy());

  it('un código escrito se agrega con Enter, sin enviar el formulario', () => {
    const input: HTMLInputElement = fixture.nativeElement.querySelector('#productBarcode');
    input.value = '036000291452';
    input.dispatchEvent(new Event('input'));
    const enter = new KeyboardEvent('keydown', { key: 'Enter', bubbles: true, cancelable: true });

    input.dispatchEvent(enter);

    expect(emitted).toEqual([['0036000291452']]);
    expect(enter.defaultPrevented).toBe(true);
  });

  it('un código escrito sin pulsar Agregar entra a la lista al salir del campo, antes de guardar', () => {
    const input: HTMLInputElement = fixture.nativeElement.querySelector('#productBarcode');
    input.value = EAN;
    input.dispatchEvent(new Event('input'));

    input.dispatchEvent(new Event('blur'));

    expect(emitted).toEqual([[EAN]]);
  });

  it('el mismo código no se repite en la lista', () => {
    fixture.componentRef.setInput('barcodes', [EAN]);
    fixture.detectChanges();

    fixture.componentInstance.add(EAN);

    expect(emitted).toEqual([]);
  });

  it('quitar saca el código de la lista', () => {
    fixture.componentRef.setInput('barcodes', [EAN, '4006381333931']);
    fixture.detectChanges();

    fixture.nativeElement.querySelector('.barcode-row button').click();

    expect(emitted).toEqual([['4006381333931']]);
  });

  it('muestra la marca «No trae» y permite quitarla', () => {
    let cleared = false;
    fixture.componentInstance.clearMark.subscribe(() => (cleared = true));
    fixture.componentRef.setInput('noBarcode', true);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Marcado «No trae»');
    fixture.nativeElement.querySelector('.no-barcode-mark button').click();

    expect(cleared).toBe(true);
  });
});
