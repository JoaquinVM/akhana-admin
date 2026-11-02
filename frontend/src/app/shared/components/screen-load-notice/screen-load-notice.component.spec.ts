import { Component, DOCUMENT } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter, withNavigationErrorHandler } from '@angular/router';
import { ScreenLoadNoticeComponent } from './screen-load-notice.component';
import { isScreenLoadFailure, reportScreenLoadFailure } from './screen-load-notice.service';

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

  it('CA-07: no recarga hasta que se toca Actualizar', async () => {
    const { router, text } = await setup();
    await router.navigateByUrl('/products').catch(() => undefined);
    text();
    expect(assign).not.toHaveBeenCalled();
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
