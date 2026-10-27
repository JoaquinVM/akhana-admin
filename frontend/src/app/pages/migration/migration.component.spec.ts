import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { MigrationComponent } from './migration.component';
import { MigrationService } from '../../core/migration/migration.service';
import { MigrationResult } from '../../core/migration/models/migration.models';

describe('MigrationComponent', () => {
  let fixture: ComponentFixture<MigrationComponent>;
  let component: MigrationComponent;
  let migrationService: any;

  const clean: MigrationResult = {
    cutoffDate: '2026-10-26', imported: false,
    sheets: [{ sheet: 'Ventas', read: 6053, toImport: 6050, rejected: 0, afterCutoff: 3 }],
    rejected: [],
    notices: [{ sheet: 'Pedidos', rowNumber: 353, messages: ['La suma de las líneas (1371.00) no coincide con el Monto (1496.00): el pedido queda con el Monto.'] }],
    unassignedLines: 12, unassignedTotal: 340.5,
    thirdPartyDebts: [{ creditorName: 'Mami', amount: 1250 }],
    months: [{ month: '2026-08', sales: 66951.5, profit: 14716.99, unassigned: 3952, atCost: 0, thirdParty: 0, consumptions: 40 }],
    movements: [
      { month: '2026-08', concept: 'Pedido', amount: -67097.56 },
      { month: '2026-08', concept: 'Venta', amount: 67031.5 },
      { month: '2026-09', concept: 'Venta', amount: 65000 }
    ]
  };
  const withRejected: MigrationResult = {
    ...clean,
    sheets: [{ sheet: 'Ventas', read: 6053, toImport: 6052, rejected: 1, afterCutoff: 0 }],
    rejected: [{ sheet: 'Ventas', rowNumber: 15, messages: ['El código X no existe en el catálogo.'] }]
  };

  beforeEach(async () => {
    migrationService = {
      preview: vi.fn().mockReturnValue(of(clean)),
      importHistory: vi.fn().mockReturnValue(of({ ...clean, imported: true }))
    };
    await TestBed.configureTestingModule({
      imports: [MigrationComponent],
      providers: [{ provide: MigrationService, useValue: migrationService }]
    }).compileComponents();

    fixture = TestBed.createComponent(MigrationComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  const fill = () => {
    component.recaudaciones.set(new File(['r'], 'Akhana Recaudaciones.xlsx'));
    component.pedidos.set(new File(['p'], 'Akhana Pedidos.xlsx'));
    component.cutoffDate.set('2026-10-26');
    fixture.detectChanges();
  };
  const text = (): string => fixture.nativeElement.textContent;

  it('FIN-11: Revisar pide los dos archivos y la fecha de corte, y muestra el resultado sin guardar', () => {
    expect(component.canReview()).toBe(false);
    fill();
    component.review();
    fixture.detectChanges();

    expect(migrationService.preview).toHaveBeenCalledWith(expect.objectContaining({ cutoffDate: '2026-10-26' }));
    expect(text()).toContain('todavía no se guardó nada');
    expect(text()).toContain('Deuda inicial con Mami');
    expect(text()).toContain('1,250.00');
    expect(text()).toContain('no coincide con el Monto');
    expect(component.movementsByMonth().map(g => g.month)).toEqual(['2026-08', '2026-09']);
    expect(component.canImport()).toBe(true);
  });

  it('FIN-11: con una fila rechazada no se puede importar', () => {
    migrationService.preview.mockReturnValue(of(withRejected));
    fill();
    component.review();
    fixture.detectChanges();

    expect(text()).toContain('1 filas rechazadas');
    expect(text()).toContain('El código X no existe en el catálogo.');
    expect(component.canImport()).toBe(false);
    component.askImport();
    expect(component.confirmOpen()).toBe(false);
  });

  it('FIN-11: Importar pide confirmación, guarda y bloquea el formulario', () => {
    fill();
    component.review();
    component.askImport();
    expect(component.confirmOpen()).toBe(true);
    component.confirmImport();
    fixture.detectChanges();

    expect(migrationService.importHistory).toHaveBeenCalled();
    expect(component.confirmOpen()).toBe(false);
    expect(text()).toContain('Migración terminada');
    expect(component.canImport()).toBe(false);
  });

  it('FIN-11: muestra el mensaje del servidor si la migración ya se hizo', () => {
    migrationService.preview.mockReturnValue(throwError(() => ({ error: { message: 'La migración histórica ya se hizo.' } })));
    fill();
    component.review();
    fixture.detectChanges();

    expect(text()).toContain('La migración histórica ya se hizo.');
    expect(component.result()).toBeNull();
  });
});
