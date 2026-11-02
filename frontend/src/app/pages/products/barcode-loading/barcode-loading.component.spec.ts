import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { BarcodePendingProduct } from '../../../core/product/models/product.models';
import { ProductService } from '../../../core/product/product.service';
import { ToastService } from '../../../shared/components/toast/toast.service';
import { BarcodeLoadingComponent } from './barcode-loading.component';

const EAN = '7791234567898';

describe('BarcodeLoadingComponent (CAT-10)', () => {
  let fixture: ComponentFixture<BarcodeLoadingComponent>;
  let component: BarcodeLoadingComponent;
  let service: {
    getBarcodePending: ReturnType<typeof vi.fn>;
    addBarcode: ReturnType<typeof vi.fn>;
    markNoBarcode: ReturnType<typeof vi.fn>;
  };
  let now: number;

  const row = (id: string, name: string): BarcodePendingProduct =>
    ({ id, code: `C-${id}`, name, supplierName: 'Proveedor', sellPrice: 20 });

  const button = (text: string): HTMLButtonElement =>
    [...fixture.nativeElement.querySelectorAll('.selected-actions button')]
      .find((b: HTMLButtonElement) => b.textContent?.trim() === text) as HTMLButtonElement;

  const scan = (code: string): KeyboardEvent => {
    let last!: KeyboardEvent;
    for (const key of [...code, 'Enter']) {
      last = new KeyboardEvent('keydown', { key, bubbles: true, cancelable: true });
      now += 10;
      Object.defineProperty(last, 'timeStamp', { value: now });
      document.body.dispatchEvent(last);
    }
    fixture.detectChanges();
    return last;
  };

  beforeEach(async () => {
    now = 1000;
    service = {
      getBarcodePending: vi.fn(() => of([row('a', 'Primero'), row('b', 'Segundo'), row('c', 'Tercero')])),
      addBarcode: vi.fn(() => of({})),
      markNoBarcode: vi.fn(() => of({}))
    };
    await TestBed.configureTestingModule({
      imports: [BarcodeLoadingComponent],
      providers: [
        provideRouter([]),
        { provide: ProductService, useValue: service },
        { provide: ToastService, useValue: { success: vi.fn(), error: vi.fn() } }
      ]
    }).compileComponents();
    fixture = TestBed.createComponent(BarcodeLoadingComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  afterEach(() => fixture.destroy());

  it('muestra cuántos faltan y empieza por el primero, con su nombre, código, proveedor y precio', () => {
    const text = fixture.nativeElement.textContent;

    expect(text).toContain('Faltan 3');
    expect(fixture.nativeElement.querySelector('.selected-name').textContent).toBe('Primero');
    expect(text).toContain('C-a');
    expect(text).toContain('Proveedor');
    expect(text).toContain('Bs 20.00');
  });

  it('un escaneo llena el código sin guardar: el Enter del lector no guarda', () => {
    const enter = scan(EAN);

    expect(component.barcode()).toBe(EAN);
    expect(enter.defaultPrevented).toBe(true);
    expect(service.addBarcode).not.toHaveBeenCalled();
  });

  it('Guardar y seguir guarda y pasa al siguiente', () => {
    scan(EAN);

    button('Guardar y seguir').click();
    fixture.detectChanges();

    expect(service.addBarcode).toHaveBeenCalledWith('a', EAN);
    expect(fixture.nativeElement.textContent).toContain('Faltan 2');
    expect(fixture.nativeElement.querySelector('.selected-name').textContent).toBe('Segundo');
    expect(component.barcode()).toBe('');
  });

  it('sin código, Guardar y seguir está deshabilitado', () => {
    expect(button('Guardar y seguir').disabled).toBe(true);
  });

  it('un error del servidor se muestra y el producto sigue elegido', () => {
    service.addBarcode.mockReturnValue(throwError(() => ({ error: { message: 'El código ya es de Crema (NEF-CRE).' } })));
    scan(EAN);

    button('Guardar y seguir').click();
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('El código ya es de Crema (NEF-CRE).');
    expect(fixture.nativeElement.querySelector('.selected-name').textContent).toBe('Primero');
    expect(fixture.nativeElement.textContent).toContain('Faltan 3');
  });

  it('No trae lo marca, lo saca de la lista y pasa al siguiente', () => {
    button('No trae').click();
    fixture.detectChanges();

    expect(service.markNoBarcode).toHaveBeenCalledWith('a');
    expect(fixture.nativeElement.textContent).toContain('Faltan 2');
    expect(fixture.nativeElement.querySelector('.selected-name').textContent).toBe('Segundo');
  });

  it('Saltar pasa al siguiente y el producto sigue en la lista', () => {
    button('Saltar').click();
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.selected-name').textContent).toBe('Segundo');
    expect(fixture.nativeElement.textContent).toContain('Faltan 3');
  });

  it('al terminar la lista dice que no quedan productos', () => {
    service.getBarcodePending.mockReturnValue(of([]));
    const empty = TestBed.createComponent(BarcodeLoadingComponent);
    empty.detectChanges();

    expect(empty.nativeElement.textContent).toContain('No quedan productos por revisar.');
    empty.destroy();
  });

  it('el buscador filtra la lista', () => {
    component.searchTerm.set('Tercero');
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelectorAll('.pending-row').length).toBe(1);
  });
});
