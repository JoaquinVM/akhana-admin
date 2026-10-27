import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { MigrationService } from './migration.service';
import { MigrationFiles } from './models/migration.models';

describe('MigrationService', () => {
  let service: MigrationService;
  let httpTesting: HttpTestingController;

  const files: MigrationFiles = {
    recaudaciones: new File(['r'], 'Akhana Recaudaciones.xlsx'),
    pedidos: new File(['p'], 'Akhana Pedidos.xlsx'),
    cutoffDate: '2026-10-26'
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [MigrationService, provideHttpClient(), provideHttpClientTesting()]
    });
    service = TestBed.inject(MigrationService);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpTesting.verify());

  it('FIN-11: Revisar envía los dos archivos y la fecha de corte a /preview', () => {
    service.preview(files).subscribe();

    const req = httpTesting.expectOne('/api/migration/preview');
    expect(req.request.method).toBe('POST');
    const body = req.request.body as FormData;
    expect((body.get('recaudaciones') as File).name).toBe('Akhana Recaudaciones.xlsx');
    expect((body.get('pedidos') as File).name).toBe('Akhana Pedidos.xlsx');
    expect(body.get('cutoffDate')).toBe('2026-10-26');
    req.flush({});
  });

  it('FIN-11: Importar usa el mismo formulario en /api/migration', () => {
    service.importHistory(files).subscribe();

    const req = httpTesting.expectOne('/api/migration');
    expect(req.request.method).toBe('POST');
    expect((req.request.body as FormData).get('cutoffDate')).toBe('2026-10-26');
    req.flush({});
  });
});
