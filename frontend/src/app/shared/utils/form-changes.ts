import { AbstractControl } from '@angular/forms';

// Values the form had when its modal opened; compare against them to know what the user changed
export type FormSnapshot = Record<string, unknown>;

// A field counts as modified when its trimmed text differs from the one it had when the modal opened
export function isFieldModified(form: AbstractControl, original: FormSnapshot | null, field: string): boolean {
  if (!original) {
    return false;
  }
  return asText(form.get(field)?.value) !== asText(original[field]);
}

// The form has unsaved changes when any of its snapshot fields was modified
export function hasFormChanges(form: AbstractControl, original: FormSnapshot | null): boolean {
  return !!original && Object.keys(original).some(field => isFieldModified(form, original, field));
}

function asText(value: unknown): string {
  return (value ?? '').toString().trim();
}
