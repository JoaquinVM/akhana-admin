import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ModalComponent } from './modal.component';
import { By } from '@angular/platform-browser';
import { Component, signal } from '@angular/core';
import { InteractivityChecker } from '@angular/cdk/a11y';
import { ConfirmModalComponent } from '../confirm-modal/confirm-modal.component';

// jsdom has no layout, so the focus trap would see every button as hidden
const visibleChecker = {
  isFocusable: (el: HTMLElement) => el.tabIndex >= 0, isTabbable: (el: HTMLElement) => el.tabIndex >= 0,
  isVisible: () => true, isDisabled: () => false
};

@Component({
  standalone: true,
  imports: [ModalComponent, ConfirmModalComponent],
  template: `
    <button id="opener">Abrir</button>
    <app-modal [isOpen]="formOpen()" title="Formulario" (close)="formCloses = formCloses + 1">
      <input id="field" />
    </app-modal>
    <app-confirm-modal [isOpen]="confirmOpen()" (cancel)="confirmOpen.set(false)" />
  `
})
class StackHostComponent {
  formOpen = signal(false);
  confirmOpen = signal(false);
  formCloses = 0;
}

describe('ModalComponent', () => {
  let component: ModalComponent;
  let fixture: ComponentFixture<ModalComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ModalComponent],
      providers: [{ provide: InteractivityChecker, useValue: visibleChecker }]
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

  it('al abrir lleva el foco adentro y al cerrar lo devuelve a donde estaba', async () => {
    const host = TestBed.createComponent(StackHostComponent);
    host.detectChanges();
    const opener = host.nativeElement.querySelector('#opener') as HTMLButtonElement;
    document.body.appendChild(host.nativeElement);
    opener.focus();

    host.componentInstance.formOpen.set(true);
    host.detectChanges();
    await host.whenStable();
    await new Promise(resolve => setTimeout(resolve));
    const card = host.nativeElement.querySelector('app-modal .modal-card') as HTMLElement;
    expect(card.contains(document.activeElement)).toBe(true);

    host.componentInstance.formOpen.set(false);
    host.detectChanges();
    expect(document.activeElement).toBe(opener);
    host.nativeElement.remove();
  });

  it('con una confirmación encima, Escape cierra solo la confirmación', () => {
    const host = TestBed.createComponent(StackHostComponent);
    host.componentInstance.formOpen.set(true);
    host.detectChanges();
    host.componentInstance.confirmOpen.set(true);
    host.detectChanges();

    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
    host.detectChanges();
    expect(host.componentInstance.confirmOpen()).toBe(false);
    expect(host.componentInstance.formCloses).toBe(0);

    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
    expect(host.componentInstance.formCloses).toBe(1);
  });

  it('debe aplicar la clase de tamaño correspondiente (size-lg)', () => {
    fixture.componentRef.setInput('isOpen', true);
    fixture.componentRef.setInput('size', 'lg');
    fixture.detectChanges();

    const card = fixture.debugElement.query(By.css('.modal-card'));
    expect(card.nativeElement.classList.contains('size-lg')).toBe(true);
  });
});
