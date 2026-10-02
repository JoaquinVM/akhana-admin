import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { QuickProductService } from './quick-product.service';
import { QuickProductGroup, QuickProductGroupRequest } from './models/quick-product.models';

describe('QuickProductService', () => {
  let service: QuickProductService;
  let httpTesting: HttpTestingController;

  const mockGroup: QuickProductGroup = {
    id: 'grp-1',
    name: 'Bebidas',
    displayOrder: 0,
    createdAt: '2026-10-02T10:00:00Z',
    updatedAt: '2026-10-02T10:00:00Z',
    products: []
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        QuickProductService,
        provideHttpClient(),
        provideHttpClientTesting()
      ]
    });

    service = TestBed.inject(QuickProductService);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTesting.verify();
  });

  it('debe cargar grupos y seleccionar el primero por defecto', () => {
    service.loadGroups().subscribe((groups) => {
      expect(groups.length).toBe(1);
      expect(service.groups().length).toBe(1);
      expect(service.activeGroupId()).toBe('grp-1');
      expect(service.activeGroup()?.name).toBe('Bebidas');
    });

    const req = httpTesting.expectOne('/api/quick-products/groups');
    expect(req.request.method).toBe('GET');
    req.flush([mockGroup]);
  });

  it('debe crear un grupo y añadirlo al signal de grupos', () => {
    const newGroupReq: QuickProductGroupRequest = { name: 'Snacks', productIds: [] };
    const createdGroup: QuickProductGroup = { ...mockGroup, id: 'grp-2', name: 'Snacks', displayOrder: 1 };

    service.createGroup(newGroupReq).subscribe((res) => {
      expect(res.name).toBe('Snacks');
      expect(service.groups().length).toBe(1);
      expect(service.activeGroupId()).toBe('grp-2');
    });

    const req = httpTesting.expectOne('/api/quick-products/groups');
    expect(req.request.method).toBe('POST');
    req.flush(createdGroup);
  });

  it('debe reordenar grupos y actualizar el signal', () => {
    const reordered: QuickProductGroup[] = [
      { ...mockGroup, id: 'grp-2', displayOrder: 0 },
      { ...mockGroup, id: 'grp-1', displayOrder: 1 }
    ];

    service.reorderGroups(['grp-2', 'grp-1']).subscribe((groups) => {
      expect(groups[0].id).toBe('grp-2');
      expect(service.groups()[0].id).toBe('grp-2');
    });

    const req = httpTesting.expectOne('/api/quick-products/groups/reorder');
    expect(req.request.method).toBe('PUT');
    req.flush(reordered);
  });
});
