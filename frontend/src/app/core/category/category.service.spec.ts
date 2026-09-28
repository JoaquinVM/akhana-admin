import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { CategoryService } from './category.service';
import { Category, CategoryRequest } from './models/category.models';

describe('CategoryService', () => {
  let service: CategoryService;
  let httpTesting: HttpTestingController;

  const mockCategory: Category = {
    id: 'cat-1',
    name: 'Aromaterapia',
    description: 'Aceites y difusores',
    color: '#164312',
    status: 'ACTIVO',
    createdBy: 'admin',
    createdAt: '2026-09-26T10:00:00Z',
    updatedBy: null,
    updatedAt: null,
    deletedBy: null,
    deletedAt: null
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        CategoryService,
        provideHttpClient(),
        provideHttpClientTesting()
      ]
    });

    service = TestBed.inject(CategoryService);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTesting.verify();
  });

  it('debe obtener la lista de categorías sin filtro', () => {
    service.getCategories().subscribe(categories => {
      expect(categories.length).toBe(1);
      expect(categories[0].name).toBe('Aromaterapia');
    });

    const req = httpTesting.expectOne('/api/categories');
    expect(req.request.method).toBe('GET');
    req.flush([mockCategory]);
  });

  it('debe obtener categorías filtradas por búsqueda y estado', () => {
    service.getCategories('aroma', 'ACTIVO').subscribe(categories => {
      expect(categories.length).toBe(1);
    });

    const req = httpTesting.expectOne('/api/categories?search=aroma&status=ACTIVO');
    expect(req.request.method).toBe('GET');
    req.flush([mockCategory]);
  });

  it('debe obtener una categoría por id', () => {
    service.getCategoryById('cat-1').subscribe(category => {
      expect(category.id).toBe('cat-1');
      expect(category.name).toBe('Aromaterapia');
    });

    const req = httpTesting.expectOne('/api/categories/cat-1');
    expect(req.request.method).toBe('GET');
    req.flush(mockCategory);
  });

  it('debe crear una categoría por POST', () => {
    const request: CategoryRequest = {
      name: 'Fitoterapia',
      description: 'Hierbas',
      color: '#3c6a00'
    };

    service.createCategory(request).subscribe(category => {
      expect(category.name).toBe('Fitoterapia');
    });

    const req = httpTesting.expectOne('/api/categories');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(request);
    req.flush({ ...mockCategory, id: 'cat-2', name: 'Fitoterapia', color: '#3c6a00' });
  });

  it('debe actualizar una categoría por PUT', () => {
    const request: CategoryRequest = {
      name: 'Aromaterapia Mod',
      description: 'Nueva descripción',
      color: '#2e5b27'
    };

    service.updateCategory('cat-1', request).subscribe(category => {
      expect(category.name).toBe('Aromaterapia Mod');
    });

    const req = httpTesting.expectOne('/api/categories/cat-1');
    expect(req.request.method).toBe('PUT');
    req.flush({ ...mockCategory, ...request });
  });

  it('debe eliminar una categoría por DELETE', () => {
    service.deleteCategory('cat-1').subscribe(res => {
      expect(res).toBeNull();
    });

    const req = httpTesting.expectOne('/api/categories/cat-1');
    expect(req.request.method).toBe('DELETE');
    req.flush(null);
  });
});
