import { Component, DOCUMENT, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter, withNavigationErrorHandler } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { of } from 'rxjs';
import { ScreenLoadNoticeComponent } from './screen-load-notice.component';
import { isScreenLoadFailure, reportScreenLoadFailure } from './screen-load-notice.service';
import { CashService } from '../../../core/cash/cash.service';
import { CustomerService } from '../../../core/debt/customer.service';
import { Product } from '../../../core/product/models/product.models';
import { ProductService } from '../../../core/product/product.service';
import { QuickProductService } from '../../../core/quick-product/quick-product.service';
import { SaleService } from '../../../core/sale/sale.service';
import { RegisterSaleComponent } from '../../../pages/pos/register-sale/register-sale.component';

@Component({ template: '' })
class ScreenStub {}

describe('ScreenLoadNoticeComponent (NF-10)', () => {
  const failures = [
    ['Chrome', 'Failed to fetch dynamically imported module: https://akhana/chunk-ABC.js'],
    ['Firefox', 'error loading dynamically imported module: https://akhana/chunk-ABC.js'],
    ['Safari', 'Importing a module script failed.']
  ];
  const assign = vi.fn();

  const setup = async () => {
    assign.mockClear();
    TestBed.configureTestingModule({
      imports: [ScreenLoadNoticeComponent],
      providers: [
        provideRouter([
          { path: 'pos', component: ScreenStub },
          { path: 'other', component: ScreenStub },
          { path: 'products', loadComponent: () => Promise.reject(new TypeError(failures[0][1])) },
          { path: 'broken', loadComponent: () => Promise.reject(new Error('a bug in the screen')) },
          { path: 'guarded', component: ScreenStub, canActivate: [() => { throw new Error('guard failed'); }] },
          { path: 'denied', component: ScreenStub, canActivate: [() => TestBed.inject(Router).parseUrl('/pos')] }
        ], withNavigationErrorHandler(reportScreenLoadFailure)),
        { provide: DOCUMENT, useFactory: () => {
          const real = document;
          return new Proxy(real, {
            get: (target, key) => key === 'location' ? { assign } : Reflect.get(target, key, target)
          });
        } }
      ]
    });
    const fixture = TestBed.createComponent(ScreenLoadNoticeComponent);
    const router = TestBed.inject(Router);
    await router.navigateByUrl('/pos');
    return { fixture, router, text: () => (fixture.detectChanges(), fixture.nativeElement.textContent as string) };
  };
  const button = (host: HTMLElement, label: string) =>
    Array.from(host.querySelectorAll('button')).find(b => b.textContent?.includes(label) || b.getAttribute('aria-label') === label)!;

  it.each(failures)('reconoce el error de %s', (_, message) => {
    expect(isScreenLoadFailure(new TypeError(message))).toBe(true);
  });

  it('no confunde otros errores', () => {
    expect(isScreenLoadFailure(new Error('a bug in the screen'))).toBe(false);
    expect(isScreenLoadFailure(null)).toBe(false);
  });

  it('CA-05: si la pantalla no carga avisa y sigue en la que estaba', async () => {
    const { fixture, router, text } = await setup();
    await router.navigateByUrl('/products').catch(() => undefined);
    expect(text()).toContain('No se pudo abrir esta pantalla. Puede haber una versión nueva o un problema de conexión.');
    expect(button(fixture.nativeElement, 'Actualizar')).toBeTruthy();
    expect(router.url).toBe('/pos');
  });

  it('CA-06: Actualizar abre la pantalla pedida', async () => {
    const { fixture, router, text } = await setup();
    await router.navigateByUrl('/products').catch(() => undefined);
    text();
    button(fixture.nativeElement, 'Actualizar').click();
    expect(assign).toHaveBeenCalledWith('/products');
  });

  it('el aviso se va al cerrarlo o al entrar a otra pantalla', async () => {
    const { fixture, router, text } = await setup();
    await router.navigateByUrl('/products').catch(() => undefined);
    text();
    button(fixture.nativeElement, 'Cerrar aviso').click();
    expect(text()).not.toContain('No se pudo abrir');

    await router.navigateByUrl('/products').catch(() => undefined);
    expect(text()).toContain('No se pudo abrir');
    await router.navigateByUrl('/other');
    expect(text()).not.toContain('No se pudo abrir');
  });

  it.each(['/broken', '/guarded', '/denied'])('CA-09: %s no muestra este aviso', async url => {
    const { router, text } = await setup();
    await router.navigateByUrl(url).catch(() => undefined);
    expect(text()).not.toContain('No se pudo abrir');
  });
});

describe('ScreenLoadNotice con la venta abierta (CA-07)', () => {
  const product = { id: 'prod-1', code: 'P001', name: 'Té Verde Matcha', sellPrice: 20, saleUnit: 'UNIT', status: 'ACTIVO', tags: [] } as unknown as Product;
  const location = { assign: vi.fn(), reload: vi.fn(), replace: vi.fn(), href: 'http://localhost/pos/sale' };

  beforeEach(() => {
    vi.clearAllMocks();
    vi.stubGlobal('location', location);
    vi.stubGlobal('document', new Proxy(document, {
      get: (target, key) => key === 'location' ? location : Reflect.get(target, key, target)
    }));
    TestBed.configureTestingModule({
      imports: [ScreenLoadNoticeComponent],
      providers: [
        provideRouter([
          { path: 'pos/sale', component: RegisterSaleComponent },
          { path: 'products', loadComponent: () => Promise.reject(new TypeError('Failed to fetch dynamically imported module')) }
        ], withNavigationErrorHandler(reportScreenLoadFailure)),
        { provide: DOCUMENT, useFactory: () => document },
        { provide: CashService, useValue: { currentSession: signal(null), getCurrentSession: () => of({ status: 'ABIERTA' }) } },
        { provide: ProductService, useValue: { getProducts: () => of([product]) } },
        { provide: QuickProductService, useValue: { groups: signal([]), isLoading: signal(false), loadFailed: signal(false), loadGroups: () => of([]) } },
        { provide: SaleService, useValue: {} },
        { provide: CustomerService, useValue: { getCustomers: () => of([]) } }
      ]
    });
  });

  afterEach(() => vi.unstubAllGlobals());

  it('el carrito sigue igual y nada recarga hasta tocar Actualizar', async () => {
    const harness = await RouterTestingHarness.create('/pos/sale');
    const sale = harness.routeDebugElement!.componentInstance as RegisterSaleComponent;
    sale.addToCart(product);
    sale.addToCart(product);
    const cart = sale.cartItems();
    const notice = TestBed.createComponent(ScreenLoadNoticeComponent);

    await TestBed.inject(Router).navigateByUrl('/products').catch(() => undefined);
    harness.detectChanges();
    notice.detectChanges();

    expect(notice.nativeElement.textContent).toContain('No se pudo abrir esta pantalla');
    expect(TestBed.inject(Router).url).toBe('/pos/sale');
    expect(harness.routeDebugElement!.componentInstance).toBe(sale);
    expect(sale.cartItems()).toBe(cart);
    expect(sale.cartItems()[0].quantity).toBe(2);
    expect(harness.routeNativeElement!.textContent).toContain(product.name);
    expect(location.reload).not.toHaveBeenCalled();
    expect(location.assign).not.toHaveBeenCalled();
    expect(location.replace).not.toHaveBeenCalled();
    expect(location.href).toBe('http://localhost/pos/sale');
  });
});
