import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { Product } from '../../../core/product/models/product.models';
import { ProductService } from '../../../core/product/product.service';
import { ProductWebSectionComponent } from './product-web-section.component';

describe('ProductWebSectionComponent (CAT-13)', () => {
  let fixture: ComponentFixture<ProductWebSectionComponent>;
  let service: { markForWeb: ReturnType<typeof vi.fn>; unmarkForWeb: ReturnType<typeof vi.fn> };
  let changes: Product[];

  const text = () => fixture.nativeElement.textContent as string;
  const checkbox = () => fixture.nativeElement.querySelector('input[type=checkbox]') as HTMLInputElement;
  const setup = (inputs: { published?: boolean; hasPhoto?: boolean; active?: boolean }) => {
    fixture.componentRef.setInput('productId', 'p1');
    fixture.componentRef.setInput('published', inputs.published ?? false);
    fixture.componentRef.setInput('hasPhoto', inputs.hasPhoto ?? false);
    fixture.componentRef.setInput('active', inputs.active ?? true);
    fixture.detectChanges();
  };

  beforeEach(() => {
    service = {
      markForWeb: vi.fn().mockReturnValue(of({ id: 'p1', webPublished: true })),
      unmarkForWeb: vi.fn().mockReturnValue(of({ id: 'p1', webPublished: false }))
    };
    TestBed.configureTestingModule({
      imports: [ProductWebSectionComponent],
      providers: [{ provide: ProductService, useValue: service }]
    });
    fixture = TestBed.createComponent(ProductWebSectionComponent);
    changes = [];
    fixture.componentInstance.publishedChange.subscribe((product) => changes.push(product));
  });

  it('marcar guarda enseguida y avisa al listado', () => {
    setup({ hasPhoto: true });
    checkbox().click();

    expect(service.markForWeb).toHaveBeenCalledWith('p1');
    expect(changes[0].webPublished).toBe(true);
  });

  it('desmarcar guarda enseguida', () => {
    setup({ published: true, hasPhoto: true });
    checkbox().click();

    expect(service.unmarkForWeb).toHaveBeenCalledWith('p1');
    expect(changes[0].webPublished).toBe(false);
  });

  it('si falla muestra el error del servidor y deja la casilla como estaba', () => {
    service.markForWeb.mockReturnValue(throwError(() => ({ error: { message: 'Un producto especial no se publica en la web.' } })));
    setup({});
    checkbox().click();
    fixture.detectChanges();

    expect(text()).toContain('Un producto especial no se publica en la web.');
    expect(checkbox().checked).toBe(false);
    expect(changes).toEqual([]);
  });

  it('dice por qué un producto marcado todavía no sale', () => {
    setup({ published: true, hasPhoto: false });
    expect(text()).toContain('Falta la foto');

    setup({ published: true, hasPhoto: true, active: false });
    expect(text()).toContain('Está inactivo');

    setup({ published: true, hasPhoto: true, active: true });
    expect(text()).toContain('Se verá en la web en menos de 15 minutos.');
  });
});
