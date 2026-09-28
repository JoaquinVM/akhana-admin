import { Component, forwardRef, input, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';

export interface ColorOption {
  hex: string;
  name: string;
}

export const PRESET_COLORS: ColorOption[] = [
  { hex: '#164312', name: 'Bosque Profundo' },
  { hex: '#2e5b27', name: 'Verde Oliva' },
  { hex: '#3c6a00', name: 'Verde Hoja' },
  { hex: '#5b7f52', name: 'Salvia Botánico' },
  { hex: '#0f766e', name: 'Teal Esmeralda' },
  { hex: '#0284c7', name: 'Azul Celeste' },
  { hex: '#3b82f6', name: 'Azul Zafiro' },
  { hex: '#7c3aed', name: 'Lavanda Violeta' },
  { hex: '#86198f', name: 'Fucsia Pétalo' },
  { hex: '#b91c1c', name: 'Rojo Carmín' },
  { hex: '#ea580c', name: 'Terracota Naranja' },
  { hex: '#d97706', name: 'Ámbar Cálido' },
  { hex: '#fabd0d', name: 'Mostaza Silvestre' },
  { hex: '#b45309', name: 'Ocre Tierra' },
  { hex: '#854d0e', name: 'Corteza Marrón' },
  { hex: '#475569', name: 'Piedra Gris' },
  { hex: '#334155', name: 'Pizarra Oscura' },
  { hex: '#111f0f', name: 'Musgo Nocturno' }
];

@Component({
  selector: 'app-color-picker',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './color-picker.component.html',
  styleUrls: ['./color-picker.component.css'],
  providers: [
    {
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => ColorPickerComponent),
      multi: true
    }
  ]
})
export class ColorPickerComponent implements ControlValueAccessor {
  label = input<string>('Color Identificador');
  required = input<boolean>(true);
  isModified = input<boolean>(false);
  errorMessage = input<string | null>(null);

  readonly colors = PRESET_COLORS;
  selectedColor = signal<string>('');
  disabled = signal<boolean>(false);

  private onChange: (val: string) => void = () => {};
  private onTouched: () => void = () => {};

  writeValue(value: string | null): void {
    this.selectedColor.set(value ? value.toLowerCase() : '');
  }

  registerOnChange(fn: (val: string) => void): void {
    this.onChange = fn;
  }

  registerOnTouched(fn: () => void): void {
    this.onTouched = fn;
  }

  setDisabledState(isDisabled: boolean): void {
    this.disabled.set(isDisabled);
  }

  selectColor(color: ColorOption): void {
    if (this.disabled()) return;
    this.selectedColor.set(color.hex.toLowerCase());
    this.onChange(color.hex);
    this.onTouched();
  }

  getSelectedColorName(): string {
    const current = this.selectedColor().toLowerCase();
    const found = this.colors.find(c => c.hex.toLowerCase() === current);
    return found ? found.name : 'Personalizado';
  }
}
