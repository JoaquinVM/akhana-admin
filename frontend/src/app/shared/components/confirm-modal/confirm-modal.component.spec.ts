import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ConfirmModalComponent } from './confirm-modal.component';
import { By } from '@angular/platform-browser';

describe('ConfirmModalComponent', () => {
  let component: ConfirmModalComponent;
  let fixture: ComponentFixture<ConfirmModalComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ConfirmModalComponent]
    }).compileComponents();

    fixture = TestBed.createComponent(ConfirmModalComponent);
    component = fixture.componentInstance;
  });

  it('no debe renderizar nada cuando isOpen es false', () => {
    fixture.componentRef.setInput('isOpen', false);
    fixture.detectChanges();

    const backdrop = fixture.debugElement.query(By.css('.modal-backdrop'));
    expect(backdrop).toBeNull();
  });

  it('TEST-MOD-01: debe renderizar título, mensaje y textos parametrizados cuando isOpen es true', () => {
    fixture.componentRef.setInput('isOpen', true);
    fixture.componentRef.setInput('title', '¿Eliminar Proveedor?');
    fixture.componentRef.setInput('message', 'Esta acción cambiará el estado a ELIMINADO.');
    fixture.componentRef.setInput('confirmText', 'Eliminar Definitivamente');
    fixture.componentRef.setInput('cancelText', 'Volver');
    fixture.detectChanges();

    const titleEl = fixture.debugElement.query(By.css('.modal-title'));
    const messageEl = fixture.debugElement.query(By.css('.modal-message'));
    const cancelBtn = fixture.debugElement.query(By.css('.btn-outline'));
    const confirmBtn = fixture.debugElement.query(By.css('.btn-danger'));

    expect(titleEl.nativeElement.textContent.trim()).toBe('¿Eliminar Proveedor?');
    expect(messageEl.nativeElement.textContent.trim()).toBe('Esta acción cambiará el estado a ELIMINADO.');
    expect(cancelBtn.nativeElement.textContent.trim()).toBe('Volver');
    expect(confirmBtn.nativeElement.textContent.trim()).toBe('Eliminar Definitivamente');
  });

  it('TEST-MOD-02: debe emitir evento confirm al presionar el botón de confirmación', () => {
    fixture.componentRef.setInput('isOpen', true);
    fixture.detectChanges();

    let confirmedEmitted = false;
    component.confirm.subscribe(() => {
      confirmedEmitted = true;
    });

    const confirmBtn = fixture.debugElement.query(By.css('.btn-danger'));
    confirmBtn.nativeElement.click();

    expect(confirmedEmitted).toBe(true);
  });

  it('TEST-MOD-03: debe emitir evento cancel al presionar cancelar o hacer clic en backdrop', () => {
    fixture.componentRef.setInput('isOpen', true);
    fixture.detectChanges();

    let cancelCount = 0;
    component.cancel.subscribe(() => {
      cancelCount++;
    });

    const cancelBtn = fixture.debugElement.query(By.css('.btn-outline'));
    cancelBtn.nativeElement.click();
    expect(cancelCount).toBe(1);

    const backdrop = fixture.debugElement.query(By.css('.modal-backdrop'));
    backdrop.nativeElement.dispatchEvent(new MouseEvent('click', { bubbles: true }));
    expect(cancelCount).toBe(2);
  });
});
