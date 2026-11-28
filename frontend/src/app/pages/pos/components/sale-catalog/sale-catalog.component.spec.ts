import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { signal } from '@angular/core';
import { vi } from 'vitest';
import { SaleCatalogComponent } from './sale-catalog.component';
import { QuickProductService } from '../../../../core/quick-product/quick-product.service';

describe('SaleCatalogComponent', () => {
  let fixture: ComponentFixture<SaleCatalogComponent>;
  let component: SaleCatalogComponent;
  let quickService: any;

  const tab100: any = { id: 'p1', code: 'PAT-TAB-PIS-100G', name: 'Tab s/Pistacho 100g', sellPrice: 32 };
  const tab50: any = { id: 'p2', code: 'PAT-TAB-PIS-50G', name: 'Tab s/Pistacho 50g', sellPrice: 19 };
  const pistacho: any = { id: 'p3', code: 'FSE-PIS', name: 'Pistacho', sellPrice: 280, buyPrice: 210 };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [SaleCatalogComponent],
      providers: [{
        provide: QuickProductService,
        useValue: quickService = {
          groups: signal<any[]>([
            { id: 'g1', name: 'Bebidas', products: [tab100] },
            { id: 'g2', name: 'Frutos secos', products: [pistacho] }
          ]),
          isLoading: signal(false),
          loadFailed: signal(false)
        }
      }]
    }).compileComponents();
    fixture = TestBed.createComponent(SaleCatalogComponent);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('products', [tab100, tab50, pistacho]);
    fixture.detectChanges();
  });

  const text = (): string => fixture.nativeElement.textContent;
  const NO_GROUPS = 'No existen grupos';

  it('POS-17: mientras carga el catálogo lo dice, y lo buscado aparece solo al terminar', () => {
    fixture.componentRef.setInput('products', []);
    fixture.componentRef.setInput('catalogLoading', true);
    component.productSearchTerm.set('pistacho');
    fixture.detectChanges();
    expect(text()).toContain('Cargando productos');
    expect(fixture.debugElement.queryAll(By.css('.search-result-item')).length).toBe(0);

    fixture.componentRef.setInput('products', [tab100, tab50, pistacho]);
    fixture.componentRef.setInput('catalogLoading', false);
    fixture.detectChanges();
    expect(text()).not.toContain('Cargando productos');
    expect(fixture.debugElement.queryAll(By.css('.search-result-item')).length).toBe(3);
  });

  it('POS-17: si el catálogo falla lo dice y ofrece reintentar', () => {
    const retry = vi.fn();
    component.retryCatalog.subscribe(retry);
    fixture.componentRef.setInput('catalogFailed', true);
    fixture.detectChanges();
    expect(text()).toContain('No se pudieron cargar los productos');
    fixture.nativeElement.querySelector('.search-box-card app-connection-notice button').click();
    expect(retry).toHaveBeenCalledTimes(1);
  });

  it('POS-17: mientras cargan los grupos no dice que no existen', () => {
    quickService.groups.set([]);
    quickService.isLoading.set(true);
    fixture.detectChanges();
    expect(text()).toContain('Cargando productos rápidos');
    expect(text()).not.toContain(NO_GROUPS);
  });

  it('POS-17: si los grupos fallan ofrece reintentar y no dice que no existen', () => {
    const retry = vi.fn();
    component.retryQuickGroups.subscribe(retry);
    quickService.groups.set([]);
    quickService.loadFailed.set(true);
    fixture.detectChanges();
    expect(text()).toContain('No se pudieron cargar los productos rápidos');
    expect(text()).not.toContain(NO_GROUPS);
    fixture.nativeElement.querySelector('.quick-products-card app-connection-notice button').click();
    expect(retry).toHaveBeenCalledTimes(1);
  });

  it('POS-17: «No existen grupos» solo cuando el servidor respondió que no hay', () => {
    quickService.groups.set([]);
    fixture.detectChanges();
    expect(text()).toContain(NO_GROUPS);
  });

  it('096: en una venta al costo el buscador y los rápidos muestran el costo, no el precio', () => {
    component.productSearchTerm.set('FSE-PIS');
    fixture.detectChanges();
    const result = (): string => fixture.debugElement.query(By.css('.search-result-item')).nativeElement.textContent;
    expect(result()).toContain('280.00');

    fixture.componentRef.setInput('atCost', true);
    fixture.detectChanges();
    expect(result()).toContain('210.00');
    expect(result()).not.toContain('280.00');
  });

  it('POS-01: busca por nombre o código y al elegir avisa y limpia el buscador', () => {
    const add = vi.fn();
    component.add.subscribe(add);
    component.productSearchTerm.set('pistacho');
    fixture.detectChanges();
    const results = fixture.debugElement.queryAll(By.css('.search-result-item'));
    expect(results.length).toBe(3);

    // The exact name comes first
    results[0].nativeElement.click();
    expect(add).toHaveBeenCalledWith({ product: pistacho, presentation: null });
    expect(component.productSearchTerm()).toBe('');
  });

  it('POS-01: muestra todos los resultados, sin el tope de 8, y cuántos son', () => {
    const kefirs = Array.from({ length: 12 }, (_, i) =>
      ({ id: `k${i}`, code: `KEF-${i}`, name: `Kéfir ${i}`, supplierName: 'Lácteos Sur', sellPrice: 10 }));
    fixture.componentRef.setInput('products', [tab100, ...kefirs]);
    component.productSearchTerm.set('kefir');
    fixture.detectChanges();
    expect(fixture.debugElement.queryAll(By.css('.search-result-item')).length).toBe(12);
    expect(fixture.nativeElement.querySelector('.search-results-count').textContent).toContain('12 resultados');

    component.productSearchTerm.set('lacteos');
    expect(component.filteredSearchResults().length).toBe(12);
  });

  it('POS-01: muestra el proveedor en la línea del código, y nada si no tiene', () => {
    const kefir = { id: 'k1', code: 'BEI-AKE-1L', name: 'Agua de Kefir', supplierName: 'Beautiful Imilla', sellPrice: 26 };
    fixture.componentRef.setInput('products', [kefir, tab100]);
    component.productSearchTerm.set('kefir');
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.res-meta').textContent.trim()).toBe('BEI-AKE-1L · Beautiful Imilla');

    component.productSearchTerm.set('pistacho 100');
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.res-meta').textContent.trim()).toBe('PAT-TAB-PIS-100G');
    expect(fixture.nativeElement.querySelector('.res-supplier')).toBeNull();
  });

  it('Enter agrega el producto cuyo código es el escrito, aunque haya más resultados', () => {
    const add = vi.fn();
    component.add.subscribe(add);
    component.productSearchTerm.set('pat-tab-pis-100g');
    fixture.detectChanges();
    expect(component.filteredSearchResults().length).toBe(1);

    component.productSearchTerm.set('fse-pis');
    const input = fixture.debugElement.query(By.css('.form-control-search')).nativeElement as HTMLInputElement;
    input.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter' }));
    expect(add).toHaveBeenCalledWith({ product: pistacho, presentation: null });
  });

  it('Enter con un solo resultado lo agrega; con varios y sin código exacto no hace nada', () => {
    const add = vi.fn();
    component.add.subscribe(add);
    component.productSearchTerm.set('tab s');
    component.addFromSearch();
    expect(add).not.toHaveBeenCalled();
    expect(component.productSearchTerm()).toBe('tab s');

    component.productSearchTerm.set('50g');
    component.addFromSearch();
    expect(add).toHaveBeenCalledWith({ product: tab50, presentation: null });
  });

  it('POS-02: muestra el primer grupo, cambia de grupo y agrega con un toque', () => {
    const add = vi.fn();
    component.add.subscribe(add);
    expect(component.activeGroup()?.name).toBe('Bebidas');

    component.selectGroup('g2');
    fixture.detectChanges();
    fixture.debugElement.query(By.css('.quick-item-card')).nativeElement.click();
    expect(add).toHaveBeenCalledWith({ product: pistacho, presentation: null });
  });

  it('POS-02: cada producto rápido es un botón, que se usa con el teclado', () => {
    const card = fixture.nativeElement.querySelector('.quick-item-card') as HTMLElement;
    expect(card.tagName).toBe('BUTTON');
    expect(card.getAttribute('type')).toBe('button');
  });

  it('pide abrir la configuración de grupos', () => {
    const configure = vi.fn();
    component.configure.subscribe(configure);
    fixture.debugElement.query(By.css('.quick-header button')).nativeElement.click();
    expect(configure).toHaveBeenCalled();
  });

  describe('CAT-15: cajas que se venden', () => {
    const box: any = { id: 'pres-box', name: 'Caja ×12', baseQuantity: 12, fixedPrice: 44, price: 44, cost: 25.56,
      active: true, sold: true, barcode: '7790000000017' };
    const display: any = { id: 'pres-dis', name: 'Display ×24', baseQuantity: 24, fixedPrice: null, price: null, cost: 51.12,
      active: true, sold: false, barcode: null };
    const bar: any = { id: 'p4', code: 'NSE-BAR-CHI', name: 'Barra Chia', saleUnit: 'UNIT', sellPrice: 4, buyPrice: 2.13,
      presentations: [box, display] };
    const halfPound: any = { id: 'pres-half', name: '1/2 libra', baseQuantity: 0.227, fixedPrice: 45, price: 45, cost: 29.51,
      active: true, sold: true, barcode: null };
    const nut: any = { id: 'p5', code: 'FSE-NUE', name: 'Nuez pelada', saleUnit: 'KILOGRAM', sellPrice: 210,
      presentations: [halfPound] };

    beforeEach(() => {
      fixture.componentRef.setInput('products', [bar, nut]);
      fixture.detectChanges();
    });

    const names = (): string[] => fixture.debugElement.queryAll(By.css('.search-result-item .res-name'))
      .map(el => el.nativeElement.textContent.trim());

    it('la caja que se vende de un producto por unidad es una fila con su precio; la que no se vende, no', () => {
      component.productSearchTerm.set('barra chia');
      fixture.detectChanges();
      expect(names()).toEqual(['Barra Chia', 'Barra Chia · Caja ×12']);
      const prices = fixture.debugElement.queryAll(By.css('.search-result-item .res-price span'))
        .map(el => el.nativeElement.textContent.trim());
      expect(prices).toEqual(['Bs 4.00', 'Bs 44.00']);
    });

    it('elegir la caja la emite con su empaque; elegir el producto, sin empaque', () => {
      const add = vi.fn();
      component.add.subscribe(add);
      component.productSearchTerm.set('barra chia');
      fixture.detectChanges();
      const rows = fixture.debugElement.queryAll(By.css('.search-result-item'));
      rows[1].nativeElement.click();
      expect(add).toHaveBeenLastCalledWith({ product: bar, presentation: box });
      component.productSearchTerm.set('barra chia');
      fixture.detectChanges();
      fixture.debugElement.queryAll(By.css('.search-result-item'))[0].nativeElement.click();
      expect(add).toHaveBeenLastCalledWith({ product: bar, presentation: null });
    });

    it('096: en una venta al costo la fila de la caja muestra el costo de la caja (12 × 2,13)', () => {
      fixture.componentRef.setInput('atCost', true);
      component.productSearchTerm.set('barra chia');
      fixture.detectChanges();
      const prices = fixture.debugElement.queryAll(By.css('.search-result-item .res-price span'))
        .map(el => el.nativeElement.textContent.trim());
      expect(prices).toEqual(['Bs 2.13', 'Bs 25.56']);
    });

    it('los empaques de un producto por kilo no son filas: siguen en su selector', () => {
      component.productSearchTerm.set('nuez');
      fixture.detectChanges();
      expect(names()).toEqual(['Nuez pelada']);
    });

    it('el código de la caja escrito entero la agrega con Enter; el código del producto agrega el producto', () => {
      const add = vi.fn();
      component.add.subscribe(add);
      component.productSearchTerm.set('7790000000017');
      component.addFromSearch();
      expect(add).toHaveBeenLastCalledWith({ product: bar, presentation: box });
      component.productSearchTerm.set('NSE-BAR-CHI');
      component.addFromSearch();
      expect(add).toHaveBeenLastCalledWith({ product: bar, presentation: null });
    });
  });

  it('INV-11: el buscador muestra «Quedan N» de lo que controla stock; al costo, el costo promedio', () => {
    const tea: any = { id: 'p9', code: 'TEA-VER', name: 'Té verde', sellPrice: 15, buyPrice: 12, cost: 11,
      stockStatus: 'CONTROLLED', stock: 2 };
    fixture.componentRef.setInput('products', [tea, tab100]);
    component.productSearchTerm.set('té verde');
    fixture.detectChanges();
    const row = fixture.debugElement.queryAll(By.css('.search-result-item'))[0].nativeElement.textContent;
    expect(row).toContain('Quedan 2');
    fixture.componentRef.setInput('atCost', true);
    fixture.detectChanges();
    expect(component.entryPrice(component.filteredSearchResults()[0])).toBe(11);
  });
});
