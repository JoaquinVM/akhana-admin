import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ProductService } from '../../../core/product/product.service';
import { ProductWebHistoryComponent } from './product-web-history.component';

describe('ProductWebHistoryComponent (CAT-13)', () => {
  let fixture: ComponentFixture<ProductWebHistoryComponent>;
  let service: { getWebPublicationHistory: ReturnType<typeof vi.fn> };

  const show = () => {
    fixture.componentRef.setInput('productId', 'p1');
    fixture.detectChanges();
    return fixture.nativeElement.textContent as string;
  };

  beforeEach(() => {
    service = { getWebPublicationHistory: vi.fn() };
    TestBed.configureTestingModule({
      imports: [ProductWebHistoryComponent],
      providers: [{ provide: ProductService, useValue: service }]
    });
    fixture = TestBed.createComponent(ProductWebHistoryComponent);
  });

  it('muestra quién y cuándo de cada cambio, en el orden que da el servidor', () => {
    service.getWebPublicationHistory.mockReturnValue(of([
      { published: false, changedBy: 'beto', changedAt: '2026-11-03T21:00:00Z' },
      { published: true, changedBy: 'ana', changedAt: '2026-11-02T21:00:00Z' }
    ]));

    const text = show();

    expect(service.getWebPublicationHistory).toHaveBeenCalledWith('p1');
    const items = fixture.nativeElement.querySelectorAll('li');
    expect(items[0].textContent).toContain('Desmarcado de la web');
    expect(items[0].textContent).toContain('beto');
    expect(items[1].textContent).toContain('Marcado para la web');
    expect(items[1].textContent).toContain('ana');
    expect(text).toContain('/11/2026');
  });

  it('dice si nunca se marcó', () => {
    service.getWebPublicationHistory.mockReturnValue(of([]));
    expect(show()).toContain('Nunca se marcó para la web.');
  });

  it('dice si no pudo cargar', () => {
    service.getWebPublicationHistory.mockReturnValue(throwError(() => new Error('red')));
    expect(show()).toContain('No se pudo cargar el historial del catálogo web.');
  });
});
