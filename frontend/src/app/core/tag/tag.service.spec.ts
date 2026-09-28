import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { TagService } from './tag.service';
import { Tag, TagRequest } from './models/tag.models';

describe('TagService', () => {
  let service: TagService;
  let httpTesting: HttpTestingController;

  const mockTag: Tag = {
    id: 'tag-1',
    name: 'Vegano',
    color: '#164312',
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
        TagService,
        provideHttpClient(),
        provideHttpClientTesting()
      ]
    });

    service = TestBed.inject(TagService);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTesting.verify();
  });

  it('debe obtener la lista de etiquetas sin filtro', () => {
    service.getTags().subscribe(tags => {
      expect(tags.length).toBe(1);
      expect(tags[0].name).toBe('Vegano');
    });

    const req = httpTesting.expectOne('/api/tags');
    expect(req.request.method).toBe('GET');
    req.flush([mockTag]);
  });

  it('debe obtener etiquetas filtradas por búsqueda y estado', () => {
    service.getTags('veg', 'ACTIVO').subscribe(tags => {
      expect(tags.length).toBe(1);
    });

    const req = httpTesting.expectOne('/api/tags?search=veg&status=ACTIVO');
    expect(req.request.method).toBe('GET');
    req.flush([mockTag]);
  });

  it('debe obtener una etiqueta por id', () => {
    service.getTagById('tag-1').subscribe(tag => {
      expect(tag.id).toBe('tag-1');
      expect(tag.name).toBe('Vegano');
    });

    const req = httpTesting.expectOne('/api/tags/tag-1');
    expect(req.request.method).toBe('GET');
    req.flush(mockTag);
  });

  it('debe crear una etiqueta por POST', () => {
    const request: TagRequest = {
      name: 'Sin Gluten',
      color: '#3c6a00'
    };

    service.createTag(request).subscribe(tag => {
      expect(tag.name).toBe('Sin Gluten');
    });

    const req = httpTesting.expectOne('/api/tags');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(request);
    req.flush({ ...mockTag, id: 'tag-2', name: 'Sin Gluten', color: '#3c6a00' });
  });

  it('debe actualizar una etiqueta por PUT', () => {
    const request: TagRequest = {
      name: 'Vegano Plus',
      color: '#2e5b27'
    };

    service.updateTag('tag-1', request).subscribe(tag => {
      expect(tag.name).toBe('Vegano Plus');
    });

    const req = httpTesting.expectOne('/api/tags/tag-1');
    expect(req.request.method).toBe('PUT');
    req.flush({ ...mockTag, ...request });
  });

  it('debe eliminar una etiqueta por DELETE', () => {
    service.deleteTag('tag-1').subscribe(res => {
      expect(res).toBeNull();
    });

    const req = httpTesting.expectOne('/api/tags/tag-1');
    expect(req.request.method).toBe('DELETE');
    req.flush(null);
  });
});
