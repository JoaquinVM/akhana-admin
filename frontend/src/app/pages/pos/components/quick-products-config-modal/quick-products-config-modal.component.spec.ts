import { ComponentFixture, TestBed } from '@angular/core/testing';
import { QuickProductsConfigModalComponent } from './quick-products-config-modal.component';
import { QuickProductService } from '../../../../core/quick-product/quick-product.service';
import { ProductService } from '../../../../core/product/product.service';
import { Product } from '../../../../core/product/models/product.models';
import { QuickProductGroup } from '../../../../core/quick-product/models/quick-product.models';
import { of } from 'rxjs';
import { vi } from 'vitest';
import { signal } from '@angular/core';

describe('QuickProductsConfigModalComponent', () => {
  let component: QuickProductsConfigModalComponent;
  let fixture: ComponentFixture<QuickProductsConfigModalComponent>;
  let mockQuickProductService: any;
  let mockProductService: any;

  const mockProduct1: Product = {
    id: 'prod-1',
    code: 'P001',
    name: 'Té Matcha',
    categoryId: 'c1',
    categoryName: 'Té',
    categoryColor: '#2e5b27',
    supplierId: 's1',
    supplierName: 'Sup1',
    tags: [],
    buyPrice: 10,
    sellPrice: 25,
    fixedProfit: 15,
    percentageProfit: 150,
    saleUnit: 'UNIT',
    status: 'ACTIVO',
    createdBy: 'admin',
    createdAt: '2026-01-01'
  };

  const mockProduct2: Product = {
    id: 'prod-2',
    code: 'P002',
    name: 'Café Latte',
    categoryId: 'c2',
    categoryName: 'Café',
    categoryColor: '#854d0e',
    supplierId: 's1',
    supplierName: 'Sup1',
    tags: [],
    buyPrice: 8,
    sellPrice: 18,
    fixedProfit: 10,
    percentageProfit: 125,
    saleUnit: 'UNIT',
    status: 'ACTIVO',
    createdBy: 'admin',
    createdAt: '2026-01-01'
  };

  const mockGroup: QuickProductGroup = {
    id: 'grp-1',
    name: 'Favoritos',
    displayOrder: 1,
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
    products: [mockProduct1]
  };

  beforeEach(async () => {
    mockProductService = {
      getProducts: vi.fn().mockReturnValue(of([mockProduct1, mockProduct2]))
    };

    mockQuickProductService = {
      groups: signal<QuickProductGroup[]>([mockGroup]),
      createGroup: vi.fn().mockReturnValue(of({ ...mockGroup, id: 'grp-2', name: 'Snacks', products: [] })),
      updateGroup: vi.fn().mockReturnValue(of(mockGroup)),
      deleteGroup: vi.fn().mockReturnValue(of(undefined)),
      reorderGroups: vi.fn().mockReturnValue(of([mockGroup])),
      reorderGroupItems: vi.fn().mockReturnValue(of(mockGroup))
    };

    await TestBed.configureTestingModule({
      imports: [QuickProductsConfigModalComponent],
      providers: [
        { provide: ProductService, useValue: mockProductService },
        { provide: QuickProductService, useValue: mockQuickProductService }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(QuickProductsConfigModalComponent);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('isOpen', true);
    fixture.detectChanges();
  });

  it('debe crearse y cargar los productos del catálogo y grupo seleccionado', () => {
    expect(component).toBeTruthy();
    expect(mockProductService.getProducts).toHaveBeenCalledWith(undefined, 'ACTIVO');
    expect(component.catalogProducts().length).toBe(2);
    expect(component.selectedGroupId()).toBe('grp-1');
    expect(component.selectedGroup?.name).toBe('Favoritos');
  });

  it('debe crear un nuevo grupo de productos rápidos', () => {
    component.newGroupName.set('Snacks');
    component.handleCreateGroup();

    expect(mockQuickProductService.createGroup).toHaveBeenCalledWith({
      name: 'Snacks',
      productIds: []
    });
    expect(component.newGroupName()).toBe('');
  });

  it('debe añadir un producto al grupo actual si no está ya presente', () => {
    component.handleAddProductToGroup(mockProduct2);

    expect(mockQuickProductService.updateGroup).toHaveBeenCalledWith('grp-1', {
      name: 'Favoritos',
      productIds: ['prod-1', 'prod-2']
    });
  });

  it('no debe duplicar un producto si ya pertenece al grupo actual', () => {
    mockQuickProductService.updateGroup.mockClear();
    component.handleAddProductToGroup(mockProduct1); // Ya está en el grupo

    expect(mockQuickProductService.updateGroup).not.toHaveBeenCalled();
  });

  it('debe remover un producto del grupo actual', () => {
    component.handleRemoveProductFromGroup('prod-1');

    expect(mockQuickProductService.updateGroup).toHaveBeenCalledWith('grp-1', {
      name: 'Favoritos',
      productIds: []
    });
  });

  it('al buscar muestra todas las coincidencias, no solo las primeras 15 (POS-02)', () => {
    const many = Array.from({ length: 40 }, (_, i) => ({
      ...mockProduct2,
      id: `kef-${i}`,
      code: `KEF-${String(i).padStart(2, '0')}`,
      name: `Agua Kefir ${String(i).padStart(2, '0')}`
    }));
    component.catalogProducts.set([mockProduct1, ...many]);
    component.productSearchTerm.set('kefir');

    const results = component.filteredCatalogProducts;
    expect(results.length).toBe(40);
    expect(results.some((p) => p.name === 'Agua Kefir 39')).toBe(true);
  });

  it('muestra el proveedor de cada producto del catálogo, y nada si no tiene (POS-01)', () => {
    component.catalogProducts.set([mockProduct1, { ...mockProduct2, supplierName: '' }]);
    fixture.detectChanges();

    const cards: HTMLElement[] = Array.from(fixture.nativeElement.querySelectorAll('.catalog-item-card'));
    expect(cards.length).toBe(2);
    expect(cards[0].querySelector('.cp-supplier')?.textContent?.trim()).toBe('· Sup1');
    expect(cards[1].querySelector('.cp-supplier')).toBeNull();
  });

  it('recarga el catálogo cada vez que se abre, para ver productos creados después (POS-02)', () => {
    fixture.componentRef.setInput('isOpen', false);
    fixture.detectChanges();
    mockProductService.getProducts.mockClear();
    mockProductService.getProducts.mockReturnValue(of([mockProduct1, mockProduct2, { ...mockProduct2, id: 'prod-3', code: 'P003', name: 'Nuevo' }]));

    fixture.componentRef.setInput('isOpen', true);
    fixture.detectChanges();

    expect(mockProductService.getProducts).toHaveBeenCalledWith(undefined, 'ACTIVO');
    expect(component.catalogProducts().length).toBe(3);
  });

  it('debe emitir close al invocar handleClose()', () => {
    let closed = false;
    component.close.subscribe(() => (closed = true));

    component.handleClose();
    expect(closed).toBe(true);
  });
});
