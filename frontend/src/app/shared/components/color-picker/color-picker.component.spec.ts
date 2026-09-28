import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ColorPickerComponent, PRESET_COLORS } from './color-picker.component';
import { vi } from 'vitest';

describe('ColorPickerComponent', () => {
  let component: ColorPickerComponent;
  let fixture: ComponentFixture<ColorPickerComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ColorPickerComponent]
    }).compileComponents();

    fixture = TestBed.createComponent(ColorPickerComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('debe crearse correctamente', () => {
    expect(component).toBeTruthy();
  });

  it('debe cargar los 18 colores de la paleta predefinida', () => {
    expect(component.colors.length).toBe(18);
  });

  it('debe seleccionar un color y emitir el valor a través del ControlValueAccessor', () => {
    const spy = vi.fn();
    component.registerOnChange(spy);

    const targetColor = PRESET_COLORS[0];
    component.selectColor(targetColor);

    expect(component.selectedColor()).toBe(targetColor.hex.toLowerCase());
    expect(spy).toHaveBeenCalledWith(targetColor.hex);
  });

  it('debe actualizar el color seleccionado al invocar writeValue', () => {
    component.writeValue('#3c6a00');
    expect(component.selectedColor()).toBe('#3c6a00');
  });

  it('debe deshabilitar la selección si disabled es true', () => {
    component.setDisabledState(true);
    const spy = vi.fn();
    component.registerOnChange(spy);

    component.selectColor(PRESET_COLORS[1]);
    expect(spy).not.toHaveBeenCalled();
  });
});
