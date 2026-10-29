import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { signal } from '@angular/core';
import { vi } from 'vitest';
import { SaleCatalogComponent } from './sale-catalog.component';
import { QuickProductService } from '../../../../core/quick-product/quick-product.service';

describe('SaleCatalogComponent', () => {
  let fixture: ComponentFixture<SaleCatalogComponent>;
  let component: SaleCatalogComponent;

  const tab100: any = { id: 'p1', code: 'PAT-TAB-PIS-100G', name: 'Tab s/Pistacho 100g', sellPrice: 32 };
  const tab50: any = { id: 'p2', code: 'PAT-TAB-PIS-50G', name: 'Tab s/Pistacho 50g', sellPrice: 19 };
  const pistacho: any = { id: 'p3', code: 'FSE-PIS', name: 'Pistacho', sellPrice: 280 };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [SaleCatalogComponent],
      providers: [{
        provide: QuickProductService,
        useValue: { groups: signal([
          { id: 'g1', name: 'Bebidas', products: [tab100] },
          { id: 'g2', name: 'Frutos secos', products: [pistacho] }
        ]) }
      }]
    }).compileComponents();
    fixture = TestBed.createComponent(SaleCatalogComponent);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('products', [tab100, tab50, pistacho]);
    fixture.detectChanges();
  });

  it('POS-01: busca por nombre o código y al elegir avisa y limpia el buscador', () => {
    const add = vi.fn();
    component.add.subscribe(add);
    component.productSearchTerm.set('pistacho');
    fixture.detectChanges();
    const results = fixture.debugElement.queryAll(By.css('.search-result-item'));
    expect(results.length).toBe(3);

    results[2].nativeElement.click();
    expect(add).toHaveBeenCalledWith(pistacho);
    expect(component.productSearchTerm()).toBe('');
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
    expect(add).toHaveBeenCalledWith(pistacho);
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
    expect(add).toHaveBeenCalledWith(tab50);
  });

  it('POS-02: muestra el primer grupo, cambia de grupo y agrega con un toque', () => {
    const add = vi.fn();
    component.add.subscribe(add);
    expect(component.activeGroup()?.name).toBe('Bebidas');

    component.selectGroup('g2');
    fixture.detectChanges();
    fixture.debugElement.query(By.css('.quick-item-card')).nativeElement.click();
    expect(add).toHaveBeenCalledWith(pistacho);
  });

  it('pide abrir la configuración de grupos', () => {
    const configure = vi.fn();
    component.configure.subscribe(configure);
    fixture.debugElement.query(By.css('.quick-header button')).nativeElement.click();
    expect(configure).toHaveBeenCalled();
  });
});
