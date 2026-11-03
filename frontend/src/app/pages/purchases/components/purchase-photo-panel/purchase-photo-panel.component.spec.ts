import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { PurchasePhotoPanelComponent } from './purchase-photo-panel.component';
import { PurchaseReadingService } from '../../../../core/purchase/purchase-reading.service';

describe('PurchasePhotoPanelComponent', () => {
  let fixture: ComponentFixture<PurchasePhotoPanelComponent>;
  let readingService: any;
  const file = new File(['x'], 'factura.jpg', { type: 'image/jpeg' });
  const choose = () => ({ target: { files: [file], value: 'x' } }) as unknown as Event;

  beforeEach(async () => {
    URL.createObjectURL = vi.fn().mockReturnValue('blob:foto');
    URL.revokeObjectURL = vi.fn();
    readingService = {
      getPhotoImage: vi.fn().mockReturnValue(of(new Blob(['x']))),
      uploadPhoto: vi.fn().mockReturnValue(of({ photoId: 'photo-2' }))
    };
    await TestBed.configureTestingModule({
      imports: [PurchasePhotoPanelComponent],
      providers: [{ provide: PurchaseReadingService, useValue: readingService }]
    }).compileComponents();
    fixture = TestBed.createComponent(PurchasePhotoPanelComponent);
    fixture.componentRef.setInput('purchaseId', 'pur-1');
  });

  it('CA-21: muestra la foto del pedido', () => {
    fixture.componentRef.setInput('photoId', 'photo-1');
    fixture.detectChanges();
    expect(readingService.getPhotoImage).toHaveBeenCalledWith('pur-1', 'photo-1');
    expect(fixture.nativeElement.querySelector('img.invoice-photo')).not.toBeNull();
  });

  it('CA-21: sin foto y editable, permite agregarla y avisa al terminar', async () => {
    const changed = vi.fn();
    fixture.componentInstance.changed.subscribe(changed);
    fixture.componentRef.setInput('editable', true);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Agregar foto');

    await fixture.componentInstance.onFile(choose());
    expect(readingService.uploadPhoto).toHaveBeenCalledWith('pur-1', file);
    expect(changed).toHaveBeenCalled();
  });

  it('CA-23: muestra el error del servidor', async () => {
    readingService.uploadPhoto.mockReturnValue(throwError(() => ({ error: { message: 'La foto debe ser JPEG, PNG o WebP de hasta 10 MB.' } })));
    fixture.componentRef.setInput('editable', true);
    fixture.detectChanges();
    await fixture.componentInstance.onFile(choose());
    expect(fixture.componentInstance.errorMessage()).toContain('JPEG');
  });

  it('sin foto y sin poder editar, no muestra nada', () => {
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent.trim()).toBe('');
  });
});
