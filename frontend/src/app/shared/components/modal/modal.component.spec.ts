import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ModalComponent } from './modal.component';
import { By } from '@angular/platform-browser';

describe('ModalComponent', () => {
  let component: ModalComponent;
  let fixture: ComponentFixture<ModalComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ModalComponent]
    }).compileComponents();

    fixture = TestBed.createComponent(ModalComponent);
    component = fixture.componentInstance;
  });

  it('no debe renderizar nada cuando isOpen es false', () => {
    fixture.componentRef.setInput('isOpen', false);
    fixture.detectChanges();

    const backdrop = fixture.debugElement.query(By.css('.modal-backdrop'));
    expect(backdrop).toBeNull();
  });

  it('debe renderizar título y subtítulo cuando isOpen es true', () => {
    fixture.componentRef.setInput('isOpen', true);
    fixture.componentRef.setInput('title', 'Título Modal Reutilizable');
    fixture.componentRef.setInput('subtitle', 'Subtítulo explicativo');
    fixture.detectChanges();

    const titleEl = fixture.debugElement.query(By.css('.modal-title'));
    const subtitleEl = fixture.debugElement.query(By.css('.modal-subtitle'));

    expect(titleEl.nativeElement.textContent.trim()).toBe('Título Modal Reutilizable');
    expect(subtitleEl.nativeElement.textContent.trim()).toBe('Subtítulo explicativo');
  });

  it('debe emitir evento close al presionar el botón de cerrar', () => {
    fixture.componentRef.setInput('isOpen', true);
    fixture.detectChanges();

    let closeEmitted = false;
    component.close.subscribe(() => {
      closeEmitted = true;
    });

    const closeBtn = fixture.debugElement.query(By.css('.btn-close'));
    closeBtn.nativeElement.click();

    expect(closeEmitted).toBe(true);
  });

  it('debe emitir close al hacer clic en el backdrop si closeOnBackdrop es true', () => {
    fixture.componentRef.setInput('isOpen', true);
    fixture.componentRef.setInput('closeOnBackdrop', true);
    fixture.detectChanges();

    let closeEmitted = false;
    component.close.subscribe(() => {
      closeEmitted = true;
    });

    const backdrop = fixture.debugElement.query(By.css('.modal-backdrop'));
    backdrop.nativeElement.dispatchEvent(new MouseEvent('click', { bubbles: true }));

    expect(closeEmitted).toBe(true);
  });

  it('no debe emitir close al hacer clic en el backdrop si closeOnBackdrop es false', () => {
    fixture.componentRef.setInput('isOpen', true);
    fixture.componentRef.setInput('closeOnBackdrop', false);
    fixture.detectChanges();

    let closeEmitted = false;
    component.close.subscribe(() => {
      closeEmitted = true;
    });

    const backdrop = fixture.debugElement.query(By.css('.modal-backdrop'));
    backdrop.nativeElement.dispatchEvent(new MouseEvent('click', { bubbles: true }));

    expect(closeEmitted).toBe(false);
  });

  it('debe emitir close al presionar la tecla Escape si closeOnEscape es true', () => {
    fixture.componentRef.setInput('isOpen', true);
    fixture.componentRef.setInput('closeOnEscape', true);
    fixture.detectChanges();

    let closeEmitted = false;
    component.close.subscribe(() => {
      closeEmitted = true;
    });

    const event = new KeyboardEvent('keydown', { key: 'Escape', bubbles: true });
    document.dispatchEvent(event);

    expect(closeEmitted).toBe(true);
  });

  it('debe aplicar la clase de tamaño correspondiente (size-lg)', () => {
    fixture.componentRef.setInput('isOpen', true);
    fixture.componentRef.setInput('size', 'lg');
    fixture.detectChanges();

    const card = fixture.debugElement.query(By.css('.modal-card'));
    expect(card.nativeElement.classList.contains('size-lg')).toBe(true);
  });
});
