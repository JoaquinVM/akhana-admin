import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { PurchaseReadingModalComponent } from './purchase-reading-modal.component';
import { PurchaseReadingService } from '../../../../core/purchase/purchase-reading.service';
import { Purchase, PurchaseDraft } from '../../../../core/purchase/models/purchase.models';

describe('PurchaseReadingModalComponent', () => {
  let component: PurchaseReadingModalComponent;
  let fixture: ComponentFixture<PurchaseReadingModalComponent>;
  let readingService: any;
  const file = new File(['x'], 'factura.jpg', { type: 'image/jpeg' });
  const pending = { id: 'pur-7', purchaseNumber: 'PED-000007', purchaseDate: '2026-11-01', itemsTotal: 80, costToConfirm: true };
  const draft = (pendingPurchases: PurchaseDraft['pendingPurchases'] = []) =>
    ({ readingId: 'read-1', source: 'PHOTO', lines: [], pendingPurchases }) as unknown as PurchaseDraft;

  beforeEach(async () => {
    readingService = { readText: vi.fn(), readPhoto: vi.fn() };
    await TestBed.configureTestingModule({
      imports: [PurchaseReadingModalComponent],
      providers: [{ provide: PurchaseReadingService, useValue: readingService }]
    }).compileComponents();
    fixture = TestBed.createComponent(PurchaseReadingModalComponent);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('mode', 'TEXT');
    fixture.detectChanges();
  });

  it('un texto vacío no llama a la lectura', () => {
    component.text.set('   ');
    component.readText();
    expect(readingService.readText).not.toHaveBeenCalled();
    expect(component.errorMessage()).toContain('Pega el texto');
  });

  it('CA-01: entrega el borrador leído', () => {
    const read = vi.fn();
    component.draftRead.subscribe(read);
    readingService.readText.mockReturnValue(of(draft()));
    component.text.set(' 2 jamaica ');
    component.readText();
    expect(readingService.readText).toHaveBeenCalledWith('2 jamaica');
    expect(read).toHaveBeenCalledWith({ draft: draft(), file: null, completePurchaseId: null });
  });

  it('CA-24: si falla conserva el texto y muestra el mensaje', () => {
    const read = vi.fn();
    component.draftRead.subscribe(read);
    readingService.readText.mockReturnValue(throwError(() => ({ error: { message: 'No se pudo leer.' } })));
    component.text.set('2 jamaica');
    component.readText();
    expect(component.text()).toBe('2 jamaica');
    expect(component.errorMessage()).toBe('No se pudo leer.');
    expect(component.isReading()).toBe(false);
    expect(read).not.toHaveBeenCalled();
  });

  it('CA-06: muestra el aviso de un proveedor por vez', () => {
    readingService.readText.mockReturnValue(throwError(() => ({ error: { message: 'Pega un proveedor por vez.' } })));
    component.text.set('Flora: 1. Granel: 2');
    component.readText();
    expect(component.errorMessage()).toBe('Pega un proveedor por vez.');
  });

  it('CA-13: una foto sin pedidos pendientes sigue como pedido nuevo', () => {
    const read = vi.fn();
    component.draftRead.subscribe(read);
    readingService.readPhoto.mockReturnValue(of(draft()));
    component.readPhoto(file);
    expect(read).toHaveBeenCalledWith({ draft: draft(), file, completePurchaseId: null });
  });

  it('CA-12: con un pedido pendiente primero ofrece completarlo; si no, sigue como nuevo', () => {
    const read = vi.fn();
    component.draftRead.subscribe(read);
    readingService.readPhoto.mockReturnValue(of(draft([pending])));
    component.readPhoto(file);
    expect(read).not.toHaveBeenCalled();
    expect(component.pendingChoice()).not.toBeNull();

    component.choose('pur-7');
    expect(read).toHaveBeenLastCalledWith({ draft: draft([pending]), file, completePurchaseId: 'pur-7' });
    component.choose(null);
    expect(read).toHaveBeenLastCalledWith({ draft: draft([pending]), file, completePurchaseId: null });
  });

  it('CA-09: leyendo la llegada de un pedido no ofrece otros', () => {
    const read = vi.fn();
    component.draftRead.subscribe(read);
    fixture.componentRef.setInput('forPurchase', { id: 'pur-1', purchaseNumber: 'PED-000001' } as Purchase);
    readingService.readPhoto.mockReturnValue(of(draft([pending])));
    component.readPhoto(file);
    expect(component.pendingChoice()).toBeNull();
    expect(read).toHaveBeenCalled();
  });
});
