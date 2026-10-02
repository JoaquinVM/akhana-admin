import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { ProductService } from './product.service';
import { Product, ProductRequest } from './models/product.models';

describe('ProductService', () => {
  let service: ProductService;
  let httpTesting: HttpTestingController;

  const mockProduct: Product = {
    id: 'prod-1',
    code: 'PROD-001',
    name: 'Té Matcha Ceremonial 100g',
    categoryId: 'cat-1',
    categoryName: 'Infusiones y Té',
    categoryColor: '#1B3B18',
    supplierId: 'sup-1',
    supplierName: 'Distribuidora Botánica',
    description: 'Té orgánico',
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
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        ProductService,
        provideHttpClient(),
        provideHttpClientTesting()
      ]
    });

    service = TestBed.inject(ProductService);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTesting.verify();
  });

  it('debe obtener la lista de productos sin filtro', () => {
    service.getProducts().subscribe(products => {
      expect(products.length).toBe(1);
      expect(products[0].code).toBe('PROD-001');
    });

    const req = httpTesting.expectOne('/api/products');
    expect(req.request.method).toBe('GET');
    req.flush([mockProduct]);
  });

  it('debe obtener la lista de productos con búsqueda y filtro de estado', () => {
    service.getProducts('matcha', 'ACTIVO').subscribe(products => {
      expect(products.length).toBe(1);
    });

    const req = httpTesting.expectOne('/api/products?search=matcha&status=ACTIVO');
    expect(req.request.method).toBe('GET');
    req.flush([mockProduct]);
  });

  it('no debe enviar status si es ALL o ACTIVE_INACTIVE', () => {
    service.getProducts(undefined, 'ACTIVE_INACTIVE').subscribe(products => {
      expect(products.length).toBe(1);
    });

    const req = httpTesting.expectOne('/api/products');
    expect(req.request.method).toBe('GET');
    req.flush([mockProduct]);
  });

  it('debe enviar POST al crear producto', () => {
    const request: ProductRequest = {
      code: 'PROD-002',
      name: 'Té Verde',
      categoryId: 'cat-1',
      supplierId: 'sup-1',
      tagIds: ['tag-1'],
      buyPrice: 30.00,
      sellPrice: 45.00
    };

    service.createProduct(request).subscribe(created => {
      expect(created.id).toBe('prod-1');
    });

    const req = httpTesting.expectOne('/api/products');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(request);
    req.flush(mockProduct);
  });

  it('debe enviar PUT al actualizar producto', () => {
    const request: ProductRequest = {
      code: 'PROD-001',
      name: 'Té Matcha Editado',
      categoryId: 'cat-1',
      supplierId: 'sup-1',
      tagIds: [],
      buyPrice: 45.00,
      sellPrice: 60.00
    };

    service.updateProduct('prod-1', request).subscribe(updated => {
      expect(updated.code).toBe('PROD-001');
    });

    const req = httpTesting.expectOne('/api/products/prod-1');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual(request);
    req.flush(mockProduct);
  });

  it('debe enviar PATCH al cambiar estado', () => {
    service.changeProductStatus('prod-1', 'INACTIVO').subscribe(res => {
      expect(res.id).toBe('prod-1');
    });

    const req = httpTesting.expectOne('/api/products/prod-1/status?status=INACTIVO');
    expect(req.request.method).toBe('PATCH');
    req.flush({ ...mockProduct, status: 'INACTIVO' });
  });

  it('debe enviar DELETE en eliminación lógica', () => {
    service.deleteProduct('prod-1').subscribe();

    const req = httpTesting.expectOne('/api/products/prod-1');
    expect(req.request.method).toBe('DELETE');
    req.flush(null);
  });
});
