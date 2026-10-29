import { Component, ElementRef, HostListener, computed, forwardRef, inject, input, signal } from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';

export interface SearchSelectOption {
  value: string;
  label: string;
  // Second line, e.g. the supplier code
  hint?: string;
}

let nextId = 0;

/**
 * A select with a search box for long lists (97 suppliers): type to filter, arrows to move, Enter to choose,
 * Escape to close. Works with formControlName and with ngModel.
 */
@Component({
  selector: 'app-search-select',
  standalone: true,
  templateUrl: './search-select.component.html',
  styleUrls: ['./search-select.component.css'],
  providers: [{ provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => SearchSelectComponent), multi: true }]
})
export class SearchSelectComponent implements ControlValueAccessor {
  private readonly host: ElementRef<HTMLElement> = inject(ElementRef);

  readonly options = input<SearchSelectOption[]>([]);
  readonly placeholder = input<string>('Seleccionar…');
  readonly inputId = input<string>(`search-select-${nextId++}`);
  readonly invalid = input<boolean>(false);

  readonly value = signal<string | null>(null);
  readonly isOpen = signal<boolean>(false);
  readonly term = signal<string>('');
  readonly activeIndex = signal<number>(0);
  readonly disabled = signal<boolean>(false);

  readonly selected = computed(() => this.options().find((o) => o.value === this.value()) ?? null);

  readonly filtered = computed(() => {
    const term = normalize(this.term());
    if (!term) return this.options();
    return this.options().filter((o) => normalize(`${o.label} ${o.hint ?? ''}`).includes(term));
  });

  private onChange: (value: string | null) => void = () => undefined;
  private onTouched: () => void = () => undefined;

  writeValue(value: string | null): void {
    this.value.set(value || null);
  }

  registerOnChange(fn: (value: string | null) => void): void {
    this.onChange = fn;
  }

  registerOnTouched(fn: () => void): void {
    this.onTouched = fn;
  }

  setDisabledState(disabled: boolean): void {
    this.disabled.set(disabled);
  }

  open(): void {
    if (this.disabled()) return;
    this.term.set('');
    const index = this.filtered().findIndex((o) => o.value === this.value());
    this.activeIndex.set(Math.max(0, index));
    this.isOpen.set(true);
    // The search box takes the focus once it is drawn
    setTimeout(() => this.host.nativeElement.querySelector<HTMLInputElement>('.search-select-input')?.focus());
  }

  close(): void {
    if (this.isOpen()) {
      this.isOpen.set(false);
      this.onTouched();
    }
  }

  toggle(): void {
    if (this.isOpen()) {
      this.close();
    } else {
      this.open();
    }
  }

  choose(option: SearchSelectOption): void {
    this.value.set(option.value);
    this.onChange(option.value);
    this.close();
    this.host.nativeElement.querySelector<HTMLElement>('.search-select-trigger')?.focus();
  }

  onTermChange(term: string): void {
    this.term.set(term);
    this.activeIndex.set(0);
  }

  onKeydown(event: KeyboardEvent): void {
    const count = this.filtered().length;
    if (event.key === 'ArrowDown') {
      event.preventDefault();
      this.activeIndex.set(count ? (this.activeIndex() + 1) % count : 0);
      this.scrollActive();
    } else if (event.key === 'ArrowUp') {
      event.preventDefault();
      this.activeIndex.set(count ? (this.activeIndex() - 1 + count) % count : 0);
      this.scrollActive();
    } else if (event.key === 'Enter') {
      // Never submits the surrounding form
      event.preventDefault();
      const option = this.filtered()[this.activeIndex()];
      if (option) this.choose(option);
    } else if (event.key === 'Escape') {
      event.preventDefault();
      event.stopPropagation();
      this.close();
    }
  }

  onTriggerKeydown(event: KeyboardEvent): void {
    if (event.key === 'Enter' || event.key === ' ' || event.key === 'ArrowDown') {
      event.preventDefault();
      this.open();
    }
  }

  @HostListener('document:mousedown', ['$event'])
  onDocumentMousedown(event: MouseEvent): void {
    if (this.isOpen() && !this.host.nativeElement.contains(event.target as Node)) {
      this.close();
    }
  }

  private scrollActive(): void {
    setTimeout(() => this.host.nativeElement.querySelector('.search-select-option.active')?.scrollIntoView({ block: 'nearest' }));
  }
}

function normalize(value: string): string {
  return value.normalize('NFD').replace(/\p{M}/gu, '').toLowerCase().trim();
}
