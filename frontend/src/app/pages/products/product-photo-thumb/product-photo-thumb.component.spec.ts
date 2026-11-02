import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ProductPhotoService } from '../../../core/product/product-photo.service';
import { ProductPhotoThumbComponent } from './product-photo-thumb.component';

describe('ProductPhotoThumbComponent (CAT-12)', () => {
  let fixture: ComponentFixture<ProductPhotoThumbComponent>;
  let photoService: { getThumbnail: ReturnType<typeof vi.fn> };
  let reveal: (visible: boolean) => void;
  let disconnect: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    disconnect = vi.fn();
    vi.stubGlobal('IntersectionObserver', class {
      constructor(callback: (entries: { isIntersecting: boolean }[]) => void) {
        reveal = (visible) => callback([{ isIntersecting: visible }]);
      }
      observe = vi.fn();
      disconnect = disconnect;
    });
    vi.stubGlobal('URL', Object.assign(URL, { createObjectURL: vi.fn(() => 'blob:thumb'), revokeObjectURL: vi.fn() }));
    photoService = { getThumbnail: vi.fn().mockReturnValue(of(new Blob(['x']))) };
    TestBed.configureTestingModule({
      imports: [ProductPhotoThumbComponent],
      providers: [{ provide: ProductPhotoService, useValue: photoService }]
    });
    fixture = TestBed.createComponent(ProductPhotoThumbComponent);
    fixture.componentRef.setInput('productId', 'prod-1');
  });

  afterEach(() => vi.unstubAllGlobals());

  it('sin foto muestra «Sin foto» y no pide nada', () => {
    fixture.detectChanges();
    reveal(true);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Sin foto');
    expect(photoService.getThumbnail).not.toHaveBeenCalled();
  });

  it('pide la miniatura solo cuando la fila se hace visible', () => {
    fixture.componentRef.setInput('photoId', 'photo-1');
    fixture.detectChanges();
    expect(photoService.getThumbnail).not.toHaveBeenCalled();

    reveal(false);
    fixture.detectChanges();
    expect(photoService.getThumbnail).not.toHaveBeenCalled();

    reveal(true);
    fixture.detectChanges();
    expect(photoService.getThumbnail).toHaveBeenCalledExactlyOnceWith('prod-1', 'photo-1');
    expect(fixture.nativeElement.querySelector('img').getAttribute('src')).toBe('blob:thumb');
    expect(disconnect).toHaveBeenCalled();
  });

  it('si falta el archivo muestra «Sin foto»', () => {
    photoService.getThumbnail.mockReturnValue(throwError(() => ({ status: 404 })));
    fixture.componentRef.setInput('photoId', 'photo-1');
    fixture.detectChanges();
    reveal(true);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Sin foto');
    expect(fixture.nativeElement.querySelector('img')).toBeNull();
  });

  it('libera la imagen al destruirse', () => {
    fixture.componentRef.setInput('photoId', 'photo-1');
    fixture.detectChanges();
    reveal(true);
    fixture.detectChanges();

    fixture.destroy();

    expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:thumb');
  });
});
