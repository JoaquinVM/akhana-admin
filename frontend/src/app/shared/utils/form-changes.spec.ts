import { FormControl, FormGroup } from '@angular/forms';
import { hasFormChanges, isFieldModified } from './form-changes';

describe('form-changes', () => {
  const form = () => new FormGroup({ name: new FormControl('Ana'), amount: new FormControl<number | null>(0) });

  it('sin foto de valores no hay cambios', () => {
    expect(hasFormChanges(form(), null)).toBe(false);
    expect(isFieldModified(form(), null, 'name')).toBe(false);
  });

  it('ignora espacios y compara números como texto', () => {
    const f = form();
    const original = f.getRawValue();
    f.patchValue({ name: ' Ana ', amount: 0 });
    expect(hasFormChanges(f, original)).toBe(false);
  });

  it('marca el campo cambiado', () => {
    const f = form();
    const original = f.getRawValue();
    f.patchValue({ amount: 5 });
    expect(isFieldModified(f, original, 'amount')).toBe(true);
    expect(isFieldModified(f, original, 'name')).toBe(false);
    expect(hasFormChanges(f, original)).toBe(true);
  });
});
