import { DOCUMENT, inject } from '@angular/core';
import { ToastService } from '../components/toast/toast.service';

export const USE_POINT_MESSAGE = 'Usa punto para los decimales';

function numberInput(target: EventTarget | null): HTMLInputElement | null {
  const editable = target instanceof HTMLInputElement && target.type === 'number' && !target.readOnly && !target.disabled;
  return editable ? target : null;
}

/**
 * NF-07: the point is the only decimal separator. Depending on the browser and its language, a number field reads a
 * comma as thousands (2,125 -> 2125) or as invalid, and the app never gets to see it. So in every number field of the
 * app the comma key writes a point, and a pasted number with a comma does not go in. Listens on the document so that
 * new fields need no setup.
 */
export function installNumberInputComma(): void {
  const document = inject(DOCUMENT);
  const toast = inject(ToastService);

  document.addEventListener('keydown', (event: KeyboardEvent) => {
    const input = numberInput(event.target);
    if (!input || event.key !== ',' || event.ctrlKey || event.metaKey || event.altKey || event.isComposing) return;

    event.preventDefault();
    const wasValid = !input.validity.badInput;
    // Number fields have no selection API: this is the only way to write at the cursor
    if (!document.execCommand('insertText', false, '.')) {
      toast.error(USE_POINT_MESSAGE);
      return;
    }
    if (wasValid && input.validity.badInput) {
      // A lone point is invalid too, but a digit completes it; with a separator already there nothing does
      document.execCommand('insertText', false, '0');
      const secondSeparator = input.validity.badInput;
      document.execCommand('delete');
      if (secondSeparator) document.execCommand('delete');
    }
  }, true);

  document.addEventListener('paste', (event: ClipboardEvent) => {
    if (!numberInput(event.target) || !event.clipboardData?.getData('text').includes(',')) return;
    event.preventDefault();
    toast.error(USE_POINT_MESSAGE);
  }, true);
}
