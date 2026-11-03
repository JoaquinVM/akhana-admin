import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { of, Subject, throwError } from 'rxjs';
import { vi } from 'vitest';
import { Product } from '../../../../core/product/models/product.models';
import { ProductService } from '../../../../core/product/product.service';
import { ToastService } from '../../../../shared/components/toast/toast.service';
import {
  BarcodeScannerComponent,
  CLOSE_WINDOW_TO_SCAN,
  SCAN_LOOKUP_FAILED,
  ScannedProduct,
  inactiveProductMessage,
  unknownBarcodeMessage
} from './barcode-scanner.component';

const EAN = '7791234567898';
const UPC = '036000291452';

describe('BarcodeScannerComponent (POS-15)', () => {
  let fixture: ComponentFixture<BarcodeScannerComponent>;
  let products: { getProductByBarcode: ReturnType<typeof vi.fn> };
  let toastError: ReturnType<typeof vi.fn>;
  let scanned: ScannedProduct[];
  let now: number;

  const product = (overrides: Partial<Product> = {}): Product =>
    ({ id: 'p1', code: 'NEF-SHA', name: 'Shampoo', status: 'ACTIVO', saleUnit: 'UNIT', barcodes: [EAN], ...overrides }) as Product;

  const scan = (code: string): void => {
    for (const key of [...code, 'Enter']) {
      const event = new KeyboardEvent('keydown', { key, bubbles: true, cancelable: true });
      now += 10;
      Object.defineProperty(event, 'timeStamp', { value: now });
      document.body.dispatchEvent(event);
    }
  };

  beforeEach(async () => {
    now = 1000;
    scanned = [];
    products = { getProductByBarcode: vi.fn() };
    toastError = vi.fn();
    await TestBed.configureTestingModule({
      imports: [BarcodeScannerComponent],
      providers: [
        { provide: ProductService, useValue: products },
        { provide: ToastService, useValue: { error: toastError } }
      ]
    }).compileComponents();
    fixture = TestBed.createComponent(BarcodeScannerComponent);
    fixture.componentRef.setInput('products', [product()]);
    fixture.componentRef.setInput('sessionOpen', true);
    fixture.componentInstance.scanned.subscribe((value) => scanned.push(value));
    fixture.detectChanges();
  });

  afterEach(() => {
    fixture.destroy();
    document.querySelectorAll('[aria-modal]').forEach((element) => element.remove());
  });

  const openDialog = (): void => {
    const dialog = document.createElement('div');
    dialog.setAttribute('aria-modal', 'true');
    document.body.appendChild(dialog);
  };

  it('un código del catálogo agrega ese producto sin consultar al servidor', () => {
    scan(EAN);

    expect(scanned).toEqual([{ product: expect.objectContaining({ id: 'p1' }), fromServer: false }]);
    expect(products.getProductByBarcode).not.toHaveBeenCalled();
  });

  it('cualquiera de sus códigos agrega el mismo producto', () => {
    fixture.componentRef.setInput('products', [product({ barcodes: [EAN, '4006381333931'] })]);
    fixture.detectChanges();

    scan('4006381333931');

    expect(scanned[0].product.id).toBe('p1');
  });

  it('un UPC-A de 12 dígitos encuentra el guardado con 13', () => {
    fixture.componentRef.setInput('products', [product({ barcodes: [`0${UPC}`] })]);
    fixture.detectChanges();

    scan(UPC);

    expect(scanned[0].product.id).toBe('p1');
  });

  it('un código cargado después de abrir la pantalla se encuentra en el servidor', () => {
    products.getProductByBarcode.mockReturnValue(of(product({ id: 'p2', barcodes: ['4006381333931'] })));

    scan('4006381333931');

    expect(scanned).toEqual([{ product: expect.objectContaining({ id: 'p2' }), fromServer: true }]);
  });

  it('un código desconocido solo avisa', () => {
    products.getProductByBarcode.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 404 })));

    scan('4006381333931');

    expect(scanned).toEqual([]);
    expect(toastError).toHaveBeenCalledWith(unknownBarcodeMessage('4006381333931'));
    expect(unknownBarcodeMessage(EAN)).toBe(`El código ${EAN} no está registrado. Búscalo por nombre o cárgalo en Productos.`);
  });

  it.each(['AB%12', 'A;B12', 'A/B12', 'A1B2C3'])('una lectura que no es un código (%s) avisa sin consultar al servidor', (code) => {
    scan(code);

    expect(products.getProductByBarcode).not.toHaveBeenCalled();
    expect(scanned).toEqual([]);
    expect(toastError).toHaveBeenCalledWith(unknownBarcodeMessage(code));
  });

  it('un error de red no se informa como código no registrado', () => {
    products.getProductByBarcode.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 0 })));

    scan('4006381333931');

    expect(toastError).toHaveBeenCalledWith(SCAN_LOOKUP_FAILED);
  });

  it('un producto inactivo avisa y no se agrega', () => {
    const inactive = product({ id: 'p2', status: 'INACTIVO' });
    products.getProductByBarcode.mockReturnValue(of(inactive));

    scan('4006381333931');

    expect(scanned).toEqual([]);
    expect(toastError).toHaveBeenCalledWith(inactiveProductMessage(inactive));
    expect(inactiveProductMessage(inactive)).toBe('Shampoo está inactivo y no se vende.');
  });

  it('con una ventana abierta no agrega nada y avisa', () => {
    openDialog();

    scan(EAN);

    expect(scanned).toEqual([]);
    expect(toastError).toHaveBeenCalledWith(CLOSE_WINDOW_TO_SCAN);
    expect(products.getProductByBarcode).not.toHaveBeenCalled();
  });

  it('si se abre una ventana mientras consulta al servidor, tampoco agrega', () => {
    const response = new Subject<Product>();
    products.getProductByBarcode.mockReturnValue(response);

    scan('4006381333931');
    openDialog();
    response.next(product({ id: 'p2' }));

    expect(scanned).toEqual([]);
    expect(toastError).toHaveBeenCalledWith(CLOSE_WINDOW_TO_SCAN);
  });

  it('con la caja cerrada no hace nada', () => {
    fixture.componentRef.setInput('sessionOpen', false);
    fixture.detectChanges();

    scan(EAN);

    expect(scanned).toEqual([]);
    expect(toastError).not.toHaveBeenCalled();
    expect(products.getProductByBarcode).not.toHaveBeenCalled();
  });

  it('una respuesta que llega después de salir de la pantalla no agrega ni avisa', () => {
    const response = new Subject<Product>();
    products.getProductByBarcode.mockReturnValue(response);

    scan('4006381333931');
    fixture.destroy();
    response.next(product({ id: 'p2' }));

    expect(scanned).toEqual([]);
    expect(toastError).not.toHaveBeenCalled();
  });

  it('al salir de la pantalla deja de escuchar', () => {
    fixture.destroy();

    scan(EAN);

    expect(scanned).toEqual([]);
  });
});
