import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ProductPhotoService } from '../../../core/product/product-photo.service';
import { ProductPhotoSectionComponent } from './product-photo-section.component';

describe('ProductPhotoSectionComponent (CAT-12)', () => {
  let fixture: ComponentFixture<ProductPhotoSectionComponent>;
  let component: ProductPhotoSectionComponent;
  let photoService: { getImage: ReturnType<typeof vi.fn>; upload: ReturnType<typeof vi.fn>; remove: ReturnType<typeof vi.fn> };
  let changes: (string | null)[];

  const text = () => fixture.nativeElement.textContent as string;
  const select = () => {
    const field = fixture.nativeElement.querySelector('input[type=file]') as HTMLInputElement;
    component.onFileSelected({ target: Object.defineProperty(field, 'files', { value: [new File(['x'], 'a.jpg')], configurable: true }) } as unknown as Event);
    fixture.detectChanges();
  };

  beforeEach(() => {
    vi.stubGlobal('URL', Object.assign(URL, { createObjectURL: vi.fn(() => 'blob:image'), revokeObjectURL: vi.fn() }));
    photoService = {
      getImage: vi.fn().mockReturnValue(of(new Blob(['x']))),
      upload: vi.fn().mockReturnValue(of({ photoId: 'photo-2' })),
      remove: vi.fn().mockReturnValue(of(undefined))
    };
    TestBed.configureTestingModule({
      imports: [ProductPhotoSectionComponent],
      providers: [{ provide: ProductPhotoService, useValue: photoService }]
    });
    fixture = TestBed.createComponent(ProductPhotoSectionComponent);
    component = fixture.componentInstance;
    changes = [];
    component.photoChange.subscribe((id) => changes.push(id));
  });

  afterEach(() => vi.unstubAllGlobals());

  it('al crear un producto pide guardarlo primero', () => {
    fixture.detectChanges();

    expect(text()).toContain('Guarda el producto para agregar la foto.');
    expect(fixture.nativeElement.querySelector('input[type=file]')).toBeNull();
  });

  it('sin foto ofrece «Agregar foto» y sube sin foto esperada', () => {
    fixture.componentRef.setInput('productId', 'prod-1');
    fixture.detectChanges();
    expect(text()).toContain('Agregar foto');
    expect(text()).not.toContain('Quitar foto');

    select();

    expect(photoService.upload).toHaveBeenCalledWith('prod-1', expect.any(File), null);
    expect(changes).toEqual(['photo-2']);
  });

  it('con foto la muestra, ofrece cambiarla y manda la foto que veía', () => {
    fixture.componentRef.setInput('productId', 'prod-1');
    fixture.componentRef.setInput('photoId', 'photo-1');
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('img').getAttribute('src')).toBe('blob:image');
    expect(text()).toContain('Cambiar foto');

    select();

    expect(photoService.upload).toHaveBeenCalledWith('prod-1', expect.any(File), 'photo-1');
  });

  it('el input acepta los tres formatos, sin forzar la cámara, para que el celular ofrezca ambas', () => {
    fixture.componentRef.setInput('productId', 'prod-1');
    fixture.detectChanges();
    const field = fixture.nativeElement.querySelector('input[type=file]') as HTMLInputElement;

    expect(field.accept).toBe('image/jpeg,image/png,image/webp');
    expect(field.hasAttribute('capture')).toBe(false);
  });

  it('muestra la espera mientras sube', () => {
    fixture.componentRef.setInput('productId', 'prod-1');
    photoService.upload.mockReturnValue(of());
    fixture.detectChanges();

    select();

    expect(text()).toContain('Guardando la foto...');
    expect(fixture.nativeElement.querySelector('input[type=file]').disabled).toBe(true);
  });

  it('muestra el mensaje del servidor y no cambia la foto', () => {
    fixture.componentRef.setInput('productId', 'prod-1');
    photoService.upload.mockReturnValue(throwError(() => ({ status: 400, error: { message: 'La foto debe ser JPEG, PNG o WebP de hasta 10 MB.' } })));
    fixture.detectChanges();

    select();

    expect(text()).toContain('La foto debe ser JPEG, PNG o WebP de hasta 10 MB.');
    expect(changes).toEqual([]);
  });

  it('el rechazo de nginx por tamaño usa el mismo mensaje, y otro error sin texto uno genérico', () => {
    fixture.componentRef.setInput('productId', 'prod-1');
    photoService.upload.mockReturnValue(throwError(() => ({ status: 413, error: '<html>' })));
    fixture.detectChanges();
    select();
    expect(text()).toContain('La foto debe ser JPEG, PNG o WebP de hasta 10 MB.');

    photoService.upload.mockReturnValue(throwError(() => ({ status: 502 })));
    select();
    expect(text()).toContain('No se pudo guardar la foto. Intenta de nuevo.');
  });

  it('quitar pide confirmar y avisa que quedó sin foto', () => {
    fixture.componentRef.setInput('productId', 'prod-1');
    fixture.componentRef.setInput('photoId', 'photo-1');
    fixture.detectChanges();

    const remove = Array.from<HTMLButtonElement>(fixture.nativeElement.querySelectorAll('button'))
      .find((button) => button.textContent?.includes('Quitar foto'))!;
    remove.click();
    fixture.detectChanges();
    expect(component.isConfirmRemoveOpen()).toBe(true);
    expect(photoService.remove).not.toHaveBeenCalled();

    component.confirmRemove();

    expect(photoService.remove).toHaveBeenCalledWith('prod-1', 'photo-1');
    expect(changes).toEqual([null]);
    expect(component.isConfirmRemoveOpen()).toBe(false);
  });

  it('si falta el archivo muestra «Sin foto» y deja cambiarla', () => {
    photoService.getImage.mockReturnValue(throwError(() => ({ status: 404 })));
    fixture.componentRef.setInput('productId', 'prod-1');
    fixture.componentRef.setInput('photoId', 'photo-1');
    fixture.detectChanges();

    expect(text()).toContain('Sin foto');
    expect(text()).toContain('Cambiar foto');
  });
});
