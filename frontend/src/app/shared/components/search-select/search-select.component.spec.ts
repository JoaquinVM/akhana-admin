import { Component, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormControl, FormsModule, ReactiveFormsModule } from '@angular/forms';
import { SearchSelectComponent, SearchSelectOption } from './search-select.component';

@Component({
  standalone: true,
  imports: [SearchSelectComponent, ReactiveFormsModule, FormsModule],
  template: `
    <app-search-select [options]="options" placeholder="Seleccionar proveedor…" [formControl]="control" />
    <app-search-select [options]="options" [ngModel]="model()" (ngModelChange)="model.set($event)" />
  `
})
class HostComponent {
  options: SearchSelectOption[] = [
    { value: 's1', label: 'Bohemia', hint: 'BOH' },
    { value: 's2', label: 'Frutos Secos', hint: 'FRUTOS-SECOS' },
    { value: 's3', label: 'Ñandú Café' }
  ];
  control = new FormControl<string | null>('s2');
  model = signal<string | null>(null);
}

describe('SearchSelectComponent', () => {
  let fixture: ComponentFixture<HostComponent>;
  let host: HostComponent;
  const selects = () => fixture.nativeElement.querySelectorAll('app-search-select') as NodeListOf<HTMLElement>;
  const key = (el: Element, k: string) => el.dispatchEvent(new KeyboardEvent('keydown', { key: k, bubbles: true, cancelable: true }));

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [HostComponent] }).compileComponents();
    fixture = TestBed.createComponent(HostComponent);
    host = fixture.componentInstance;
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  });

  it('muestra el valor del formulario y filtra al escribir, sin importar tildes', () => {
    const first = selects()[0];
    expect(first.querySelector('.search-select-value')?.textContent).toContain('Frutos Secos');

    (first.querySelector('.search-select-trigger') as HTMLButtonElement).click();
    fixture.detectChanges();
    const input = first.querySelector('.search-select-input') as HTMLInputElement;
    input.value = 'nandu';
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    const options = first.querySelectorAll('.search-select-option');
    expect(options.length).toBe(1);
    expect(options[0].textContent).toContain('Ñandú Café');
  });

  it('elige con flechas y Enter, sin enviar formularios, y actualiza el control', () => {
    const first = selects()[0];
    (first.querySelector('.search-select-trigger') as HTMLButtonElement).click();
    fixture.detectChanges();
    const input = first.querySelector('.search-select-input') as HTMLInputElement;
    input.value = 'bo';
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    const enter = new KeyboardEvent('keydown', { key: 'Enter', bubbles: true, cancelable: true });
    input.dispatchEvent(enter);
    fixture.detectChanges();
    expect(enter.defaultPrevented).toBe(true);
    expect(host.control.value).toBe('s1');
    expect(first.querySelector('.search-select-panel')).toBeNull();
  });

  it('funciona con ngModel y se cierra con Escape', () => {
    const second = selects()[1];
    (second.querySelector('.search-select-trigger') as HTMLButtonElement).click();
    fixture.detectChanges();
    const input = second.querySelector('.search-select-input') as HTMLInputElement;
    key(input, 'ArrowDown');
    key(input, 'Enter');
    fixture.detectChanges();
    expect(host.model()).toBe('s2');

    (second.querySelector('.search-select-trigger') as HTMLButtonElement).click();
    fixture.detectChanges();
    key(second.querySelector('.search-select-input')!, 'Escape');
    fixture.detectChanges();
    expect(second.querySelector('.search-select-panel')).toBeNull();
  });
});
