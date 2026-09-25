import { ComponentFixture, TestBed } from '@angular/core/testing';
import { SectionPageComponent } from './section-page.component';
import { ActivatedRoute } from '@angular/router';
import { of } from 'rxjs';
import { By } from '@angular/platform-browser';

describe('SectionPageComponent', () => {
  let component: SectionPageComponent;
  let fixture: ComponentFixture<SectionPageComponent>;

  const mockActivatedRoute = {
    data: of({
      title: 'Productos',
      subtitle: 'Catálogo Maestro de Artículos'
    })
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [SectionPageComponent],
      providers: [
        { provide: ActivatedRoute, useValue: mockActivatedRoute }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(SectionPageComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('debe crearse exitosamente', () => {
    expect(component).toBeTruthy();
  });

  it('debe mostrar el título de la sección obtenido desde route.data', () => {
    const titleEl = fixture.debugElement.query(By.css('.page-title'));
    expect(titleEl.nativeElement.textContent.trim()).toBe('Productos');
  });

  it('debe mostrar el subtítulo correspondiente en el encabezado', () => {
    const subtitleEl = fixture.debugElement.query(By.css('.page-subtitle'));
    expect(subtitleEl.nativeElement.textContent).toContain('Catálogo Maestro de Artículos');
  });

  it('no debe renderizar ninguna etiqueta o badge "En Línea"', () => {
    const statusBadge = fixture.debugElement.query(By.css('.status-badge'));
    expect(statusBadge).toBeNull();
    expect(fixture.nativeElement.textContent).not.toContain('En Línea');
  });
});
