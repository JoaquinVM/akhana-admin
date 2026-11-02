import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ProductBarcodesSectionComponent } from './product-barcodes-section.component';

const EAN = '7791234567898';

describe('ProductBarcodesSectionComponent (CAT-10)', () => {
  let fixture: ComponentFixture<ProductBarcodesSectionComponent>;
  let emitted: string[][];
  let now: number;

  const scan = (code: string): KeyboardEvent => {
    let last!: KeyboardEvent;
    for (const key of [...code, 'Enter']) {
      last = new KeyboardEvent('keydown', { key, bubbles: true, cancelable: true });
      now += 10;
      Object.defineProperty(last, 'timeStamp', { value: now });
      document.body.dispatchEvent(last);
    }
    return last;
  };

  beforeEach(async () => {
    now = 1000;
    emitted = [];
    await TestBed.configureTestingModule({ imports: [ProductBarcodesSectionComponent] }).compileComponents();
    fixture = TestBed.createComponent(ProductBarcodesSectionComponent);
    fixture.componentInstance.barcodesChange.subscribe((value) => emitted.push(value));
    fixture.componentRef.setInput('active', true);
    fixture.detectChanges();
    TestBed.tick();
  });

  afterEach(() => fixture.destroy());

  it('un escaneo va a la lista y su Enter no envía el formulario', () => {
    const enter = scan(EAN);

    expect(emitted).toEqual([[EAN]]);
    expect(enter.defaultPrevented).toBe(true);
  });

  it('con el formulario cerrado no escucha al lector', () => {
    fixture.componentRef.setInput('active', false);
    fixture.detectChanges();
    TestBed.tick();

    const enter = scan(EAN);

    expect(emitted).toEqual([]);
    expect(enter.defaultPrevented).toBe(false);
  });

  it('un código escrito se agrega con Enter, sin enviar el formulario', () => {
    const input: HTMLInputElement = fixture.nativeElement.querySelector('#productBarcode');
    input.value = '036000291452';
    input.dispatchEvent(new Event('input'));
    const enter = new KeyboardEvent('keydown', { key: 'Enter', bubbles: true, cancelable: true });

    input.dispatchEvent(enter);

    expect(emitted).toEqual([['0036000291452']]);
    expect(enter.defaultPrevented).toBe(true);
  });

  it('el mismo código no se repite en la lista', () => {
    fixture.componentRef.setInput('barcodes', [EAN]);
    fixture.detectChanges();

    scan(EAN);

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
