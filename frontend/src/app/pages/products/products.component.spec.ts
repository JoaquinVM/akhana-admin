import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ProductsComponent } from './products.component';
import { ProductService } from '../../core/product/product.service';
import { CategoryService } from '../../core/category/category.service';
import { SupplierService } from '../../core/supplier/supplier.service';
import { TagService } from '../../core/tag/tag.service';
import { Product } from '../../core/product/models/product.models';
import { Category } from '../../core/category/models/category.models';
import { Supplier } from '../../core/supplier/models/supplier.models';
import { Tag } from '../../core/tag/models/tag.models';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';

describe('ProductsComponent', () => {
  let component: ProductsComponent;
  let fixture: ComponentFixture<ProductsComponent>;
  let mockProductService: any;
  let mockCategoryService: any;
  let mockSupplierService: any;
  let mockTagService: any;

  const mockCategories: Category[] = [
    {
      id: 'cat-1',
      name: 'Infusiones y Té',
      description: 'Hierbas y té orgánico',
      color: '#1B3B18',
      status: 'ACTIVO',
      createdBy: 'admin',
      createdAt: '2026-09-28T10:00:00Z',
      updatedBy: null,
      updatedAt: null,
      deletedBy: null,
      deletedAt: null
    }
  ];

  const mockSuppliers: Supplier[] = [
    {
      id: 'sup-1',
      name: 'Distribuidora Botánica',
      code: 'PROV-001',
      description: 'Suministros',
      phone: '+56 9 8765 4321',
      status: 'ACTIVO',
      createdBy: 'admin',
      createdAt: '2026-09-28T10:00:00Z',
      updatedBy: null,
      updatedAt: null,
      deletedBy: null,
      deletedAt: null
    }
  ];

  const mockTags: Tag[] = [
    {
      id: 'tag-1',
      name: 'Orgánico',
      color: '#7BB142',
      status: 'ACTIVO',
      createdBy: 'admin',
      createdAt: '2026-09-28T10:00:00Z',
      updatedBy: null,
      updatedAt: null,
      deletedBy: null,
      deletedAt: null
    },
    {
      id: 'tag-2',
      name: 'Vegano',
      color: '#164312',
      status: 'ACTIVO',
      createdBy: 'admin',
      createdAt: '2026-09-28T10:00:00Z',
      updatedBy: null,
      updatedAt: null,
      deletedBy: null,
      deletedAt: null
    }
  ];

  const mockProducts: Product[] = [
    {
      id: 'prod-1',
      code: 'PROD-001',
      name: 'Té Matcha Ceremonial 100g',
      categoryId: 'cat-1',
      categoryName: 'Infusiones y Té',
      categoryColor: '#1B3B18',
      supplierId: 'sup-1',
      supplierName: 'Distribuidora Botánica',
      description: 'Té verde ceremonial',
      tags: [{ id: 'tag-1', name: 'Orgánico', color: '#7BB142' }],
      buyPrice: 40.00,
      sellPrice: 50.00,
      fixedProfit: 10.00,
      percentageProfit: 25.00,
      status: 'ACTIVO',
      createdBy: 'admin',
      createdAt: '2026-09-28T10:00:00Z',
      updatedBy: null,
      updatedAt: null,
      deletedBy: null,
      deletedAt: null
    },
    {
      id: 'prod-2',
      code: 'PROD-002',
      name: 'Aceite Esencial Lavanda 15ml',
      categoryId: 'cat-1',
      categoryName: 'Infusiones y Té',
      categoryColor: '#1B3B18',
      supplierId: 'sup-1',
      supplierName: 'Distribuidora Botánica',
      description: 'Aceite puro',
      tags: [],
      buyPrice: 20.00,
      sellPrice: 35.00,
      fixedProfit: 15.00,
      percentageProfit: 75.00,
      status: 'INACTIVO',
      createdBy: 'admin',
      createdAt: '2026-09-28T11:00:00Z',
      updatedBy: null,
      updatedAt: null,
      deletedBy: null,
      deletedAt: null
    },
    {
      id: 'prod-3',
      code: 'PROD-003',
      name: 'Crema Descontinuada',
      categoryId: 'cat-1',
      categoryName: 'Infusiones y Té',
      categoryColor: '#1B3B18',
      supplierId: 'sup-1',
      supplierName: 'Distribuidora Botánica',
      description: null,
      tags: [],
      buyPrice: 10.00,
      sellPrice: 15.00,
      fixedProfit: 5.00,
      percentageProfit: 50.00,
      status: 'ELIMINADO',
      createdBy: 'admin',
      createdAt: '2026-09-20T10:00:00Z',
      updatedBy: null,
      updatedAt: null,
      deletedBy: 'admin',
      deletedAt: '2026-09-28T12:00:00Z'
    }
  ];

  beforeEach(async () => {
    mockProductService = {
      getProducts: vi.fn().mockReturnValue(of(mockProducts)),
      getProductById: vi.fn(),
      createProduct: vi.fn(),
      updateProduct: vi.fn(),
      changeProductStatus: vi.fn(),
      deleteProduct: vi.fn().mockReturnValue(of(null))
    };

    mockCategoryService = {
      getCategories: vi.fn().mockReturnValue(of(mockCategories))
    };

    mockSupplierService = {
      getSuppliers: vi.fn().mockReturnValue(of(mockSuppliers))
    };

    mockTagService = {
      getTags: vi.fn().mockReturnValue(of(mockTags))
    };

    await TestBed.configureTestingModule({
      imports: [ProductsComponent],
      providers: [
        { provide: ProductService, useValue: mockProductService },
        { provide: CategoryService, useValue: mockCategoryService },
        { provide: SupplierService, useValue: mockSupplierService },
        { provide: TagService, useValue: mockTagService }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(ProductsComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('debe inicializar y cargar productos ordenados por nombre A-Z', () => {
    expect(component.products().length).toBe(3);
    const filtered = component.filteredProducts();
    // En ACTIVE_INACTIVE no debe mostrar prod-3 (ELIMINADO)
    expect(filtered.length).toBe(2);
    // Orden A-Z: "Aceite Esencial Lavanda 15ml" antes de "Té Matcha Ceremonial 100g"
    expect(filtered[0].name).toBe('Aceite Esencial Lavanda 15ml');
    expect(filtered[1].name).toBe('Té Matcha Ceremonial 100g');
  });

  it('debe filtrar correctamente por estado Activos, Inactivos y Eliminados', () => {
    component.setStatusFilter('ACTIVO');
    expect(component.filteredProducts().length).toBe(1);
    expect(component.filteredProducts()[0].code).toBe('PROD-001');

    component.setStatusFilter('INACTIVO');
    expect(component.filteredProducts().length).toBe(1);
    expect(component.filteredProducts()[0].code).toBe('PROD-002');

    component.setStatusFilter('ELIMINADO');
    expect(component.filteredProducts().length).toBe(1);
    expect(component.filteredProducts()[0].code).toBe('PROD-003');
  });

  it('debe buscar reactivamente por código o nombre', () => {
    component.searchTerm.set('matcha');
    expect(component.filteredProducts().length).toBe(1);
    expect(component.filteredProducts()[0].name).toContain('Matcha');

    component.searchTerm.set('PROD-002');
    expect(component.filteredProducts().length).toBe(1);
    expect(component.filteredProducts()[0].code).toBe('PROD-002');
  });

  it('debe calcular automáticamente la utilidad fija y porcentual en tiempo real', () => {
    component.openCreateModal();

    component.productForm.patchValue({
      buyPrice: 40,
      sellPrice: 50
    });

    expect(component.computedFixedProfit()).toBe(10);
    expect(component.computedPercentageProfit()).toBe(25);

    component.productForm.patchValue({
      buyPrice: 50,
      sellPrice: 75
    });

    expect(component.computedFixedProfit()).toBe(25);
    expect(component.computedPercentageProfit()).toBe(50);
  });

  it('en creación, el botón guardar debe estar habilitado y marcar errores al enviar formulario inválido', () => {
    component.openCreateModal();
    expect(component.isEditing()).toBe(false);
    expect(component.productForm.invalid).toBe(true);

    component.saveProduct();

    expect(component.productForm.get('code')?.touched).toBe(true);
    expect(component.productForm.get('name')?.touched).toBe(true);
    expect(component.productForm.get('categoryId')?.touched).toBe(true);
    expect(component.productForm.get('supplierId')?.touched).toBe(true);
    expect(component.productForm.get('buyPrice')?.touched).toBe(true);
    expect(component.productForm.get('sellPrice')?.touched).toBe(true);
    expect(mockProductService.createProduct).not.toHaveBeenCalled();
  });

  it('debe enviar datos y crear producto exitosamente cuando el formulario es válido', () => {
    component.openCreateModal();

    component.productForm.patchValue({
      code: 'PROD-NEW',
      name: 'Nuevo Producto',
      categoryId: 'cat-1',
      supplierId: 'sup-1',
      buyPrice: 30,
      sellPrice: 45
    });

    const newProd: Product = {
      ...mockProducts[0],
      id: 'prod-new',
      code: 'PROD-NEW',
      name: 'Nuevo Producto'
    };

    mockProductService.createProduct.mockReturnValue(of(newProd));

    component.saveProduct();

    expect(mockProductService.createProduct).toHaveBeenCalledWith(expect.objectContaining({
      code: 'PROD-NEW',
      name: 'Nuevo Producto',
      categoryId: 'cat-1',
      supplierId: 'sup-1',
      buyPrice: 30,
      sellPrice: 45
    }));
    expect(component.isFormModalOpen()).toBe(false);
    expect(component.products()).toContainEqual(newProd);
  });

  it('debe mostrar error devuelto por backend en caso de 409 Conflict por código o nombre duplicado', () => {
    component.openCreateModal();
    component.productForm.patchValue({
      code: 'PROD-001',
      name: 'Té Matcha',
      categoryId: 'cat-1',
      supplierId: 'sup-1',
      buyPrice: 40,
      sellPrice: 50
    });

    mockProductService.createProduct.mockReturnValue(throwError(() => ({
      error: { message: 'Ya existe un producto activo o inactivo con el código especificado.' }
    })));

    component.saveProduct();

    expect(component.formError()).toBe('Ya existe un producto activo o inactivo con el código especificado.');
    expect(component.isFormModalOpen()).toBe(true);
  });

  it('en edición, el botón guardar debe iniciar deshabilitado y habilitarse al modificar valores', () => {
    component.openEditModal(mockProducts[0]);

    expect(component.isEditing()).toBe(true);
    expect(component.hasFormChanges()).toBe(false);

    // Modificar precio de venta
    component.productForm.patchValue({ sellPrice: 55 });
    expect(component.hasFormChanges()).toBe(true);
    expect(component.isFieldModified('sellPrice')).toBe(true);

    // Revertir a precio original
    component.productForm.patchValue({ sellPrice: 50 });
    expect(component.hasFormChanges()).toBe(false);
    expect(component.isFieldModified('sellPrice')).toBe(false);
  });

  it('debe solicitar confirmación al intentar cerrar modal con cambios sin guardar', () => {
    component.openCreateModal();
    component.productForm.patchValue({ code: 'PROD-DIRTY' });

    component.attemptCloseForm();

    expect(component.isDiscardConfirmOpen()).toBe(true);
    expect(component.isFormModalOpen()).toBe(true);

    component.discardChanges();

    expect(component.isDiscardConfirmOpen()).toBe(false);
    expect(component.isFormModalOpen()).toBe(false);
  });

  it('debe permitir seleccionar categoría y añadir o remover etiquetas', () => {
    component.openCreateModal();

    component.selectCategory(mockCategories[0]);
    expect(component.productForm.get('categoryId')?.value).toBe('cat-1');
    expect(component.selectedCategoryObject()?.name).toBe('Infusiones y Té');

    component.addTag(mockTags[0]);
    expect(component.productForm.get('tagIds')?.value).toEqual(['tag-1']);

    component.addTag(mockTags[1]);
    expect(component.productForm.get('tagIds')?.value).toEqual(['tag-1', 'tag-2']);

    component.removeTag('tag-1');
    expect(component.productForm.get('tagIds')?.value).toEqual(['tag-2']);
  });

  it('debe permitir seleccionar proveedor y estado mediante los dropdowns personalizados', () => {
    component.openEditModal(mockProducts[0]);

    // Verificar valor inicial de proveedor y estado
    expect(component.selectedSupplierObject()?.name).toBe('Distribuidora Botánica');
    expect(component.selectedStatusValue()).toBe('ACTIVO');

    // Conmutar apertura de dropdown de proveedor
    component.toggleSupplierDropdown();
    expect(component.isSupplierDropdownOpen()).toBe(true);
    expect(component.isCategoryDropdownOpen()).toBe(false);
    expect(component.isStatusDropdownOpen()).toBe(false);

    // Seleccionar proveedor
    component.selectSupplier(mockSuppliers[0]);
    expect(component.productForm.get('supplierId')?.value).toBe('sup-1');
    expect(component.isSupplierDropdownOpen()).toBe(false);

    // Conmutar apertura de dropdown de estado
    component.toggleStatusDropdown();
    expect(component.isStatusDropdownOpen()).toBe(true);
    expect(component.isSupplierDropdownOpen()).toBe(false);

    // Seleccionar nuevo estado
    component.selectStatus('INACTIVO');
    expect(component.productForm.get('status')?.value).toBe('INACTIVO');
    expect(component.selectedStatusValue()).toBe('INACTIVO');
    expect(component.isStatusDropdownOpen()).toBe(false);
    expect(component.isFieldModified('status')).toBe(true);
  });

  it('debe eliminar lógicamente un producto mediante confirmación', () => {
    component.openDeleteModal(mockProducts[0]);

    expect(component.isConfirmDeleteOpen()).toBe(true);
    expect(component.productToDelete()?.id).toBe('prod-1');

    mockProductService.deleteProduct.mockReturnValue(of(null));

    component.confirmDelete();

    expect(mockProductService.deleteProduct).toHaveBeenCalledWith('prod-1');
    expect(component.isConfirmDeleteOpen()).toBe(false);
    expect(component.products().find(p => p.id === 'prod-1')).toBeUndefined();
  });

  it('debe abrir el modal de auditoría con los datos del producto', () => {
    component.openAuditModal(mockProducts[0]);

    expect(component.isAuditModalOpen()).toBe(true);
    expect(component.auditModalSubtitle()).toContain('PROD-001 - Té Matcha Ceremonial 100g');
    expect(component.selectedAuditData()?.['createdBy']).toBe('admin');

    component.closeAuditModal();
    expect(component.isAuditModalOpen()).toBe(false);
  });
});
