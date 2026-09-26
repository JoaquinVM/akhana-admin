import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { SupplierService } from './supplier.service';
import { Supplier, SupplierRequest } from './models/supplier.models';

describe('SupplierService', () => {
  let service: SupplierService;
  let httpTesting: HttpTestingController;

  const mockSupplier: Supplier = {
    id: 'sup-1',
    name: 'Distribuidora del Norte S.A.',
    code: 'PROV-001',
    description: 'Insumos',
    phone: '+56 9 8765 4321',
    status: 'ACTIVO',
    createdBy: 'admin',
    createdAt: '2026-09-24T10:00:00Z',
    updatedBy: null,
    updatedAt: null,
    deletedBy: null,
    deletedAt: null
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        SupplierService,
        provideHttpClient(),
        provideHttpClientTesting()
      ]
    });

    service = TestBed.inject(SupplierService);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTesting.verify();
  });

  it('TEST-SUP-SVC-01: debe obtener la lista de proveedores sin filtro', () => {
    service.getSuppliers().subscribe(suppliers => {
      expect(suppliers.length).toBe(1);
      expect(suppliers[0].name).toBe('Distribuidora del Norte S.A.');
    });

    const req = httpTesting.expectOne('/api/suppliers');
    expect(req.request.method).toBe('GET');
    req.flush([mockSupplier]);
  });

  it('TEST-SUP-SVC-01-B: debe obtener la lista de proveedores con término de búsqueda', () => {
    service.getSuppliers('norte').subscribe(suppliers => {
      expect(suppliers.length).toBe(1);
    });

    const req = httpTesting.expectOne('/api/suppliers?search=norte');
    expect(req.request.method).toBe('GET');
    req.flush([mockSupplier]);
  });

  it('TEST-SUP-SVC-01-C: debe obtener la lista de proveedores con filtro de estado', () => {
    service.getSuppliers(undefined, 'ACTIVO').subscribe(suppliers => {
      expect(suppliers.length).toBe(1);
    });

    const req = httpTesting.expectOne('/api/suppliers?status=ACTIVO');
    expect(req.request.method).toBe('GET');
    req.flush([mockSupplier]);
  });

  it('TEST-SUP-SVC-01-D: debe obtener la lista de proveedores con búsqueda y filtro de estado ELIMINADO', () => {
    service.getSuppliers('dist', 'ELIMINADO').subscribe(suppliers => {
      expect(suppliers.length).toBe(1);
    });

    const req = httpTesting.expectOne('/api/suppliers?search=dist&status=ELIMINADO');
    expect(req.request.method).toBe('GET');
    req.flush([mockSupplier]);
  });

  it('TEST-SUP-SVC-01-E: no debe enviar query param status si es ALL o ACTIVE_INACTIVE', () => {
    service.getSuppliers(undefined, 'ALL').subscribe(suppliers => {
      expect(suppliers.length).toBe(1);
    });

    const req = httpTesting.expectOne('/api/suppliers');
    expect(req.request.method).toBe('GET');
    req.flush([mockSupplier]);
  });

  it('TEST-SUP-SVC-02: debe enviar POST al crear un proveedor', () => {
    const request: SupplierRequest = {
      name: 'Nuevo Proveedor',
      code: 'PROV-999'
    };

    service.createSupplier(request).subscribe(created => {
      expect(created.id).toBe('sup-1');
    });

    const req = httpTesting.expectOne('/api/suppliers');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(request);
    req.flush(mockSupplier);
  });

  it('TEST-SUP-SVC-03: debe enviar PUT al actualizar un proveedor', () => {
    const request: SupplierRequest = {
      name: 'Proveedor Editado',
      code: 'PROV-001',
      status: 'INACTIVO'
    };

    service.updateSupplier('sup-1', request).subscribe(updated => {
      expect(updated.name).toBe('Distribuidora del Norte S.A.');
    });

    const req = httpTesting.expectOne('/api/suppliers/sup-1');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual(request);
    req.flush(mockSupplier);
  });

  it('TEST-SUP-SVC-04: debe enviar DELETE en la eliminación lógica', () => {
    service.deleteSupplier('sup-1').subscribe();

    const req = httpTesting.expectOne('/api/suppliers/sup-1');
    expect(req.request.method).toBe('DELETE');
    req.flush(null);
  });
});
