import { DOCUMENT, inject } from '@angular/core';

const nativeValue = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value')!;

function isEditableNumberInput(target: EventTarget | null): target is HTMLInputElement {
  return target instanceof HTMLInputElement && target.type === 'number' && !target.readOnly && !target.disabled;
}

/**
 * NF-09: while the text of a number field is not a number yet (a lone point), the field reads as empty, the parent
 * turns that into 0 and writes it back, and the next digit lands after that 0: .50 became 050. So the field with the
 * focus takes no writes while its text is unfinished, nor one that says the number it already shows (.0 would become
 * 0). What was held back applies on leaving the field if the text never became a number. Listens on the document so
 * that new fields need no setup.
 */
export function installNumberInputGuard(): void {
  const document = inject(DOCUMENT);

  let guarded: HTMLInputElement | null = null;
  let heldBack: unknown = null;

  const read = (input: HTMLInputElement): string => nativeValue.get!.call(input);
  const write = (input: HTMLInputElement, value: unknown): void => nativeValue.set!.call(input, value);

  const settle = (input: HTMLInputElement): void => {
    if (read(input) === '') write(input, heldBack ?? '');
    heldBack = null;
  };

  const release = (): void => {
    if (!guarded) return;
    const input = guarded;
    guarded = null;
    delete (input as { value?: string }).value;
    settle(input);
  };

  const guard = (input: HTMLInputElement): void => {
    guarded = input;
    heldBack = null;
    Object.defineProperty(input, 'value', {
      configurable: true,
      get: () => read(input),
      set: (next: unknown) => {
        if (input.validity.badInput) {
          heldBack = next;
          return;
        }
        const current = read(input);
        const sameNumber = next !== null && next !== '' && current !== '' && Number(next) === Number(current);
        if (!sameNumber) {
          heldBack = null;
          write(input, next);
        }
      }
    });
  };

  document.addEventListener('focusin', (event: FocusEvent) => {
    if (event.target === guarded) return;
    release();
    if (isEditableNumberInput(event.target)) guard(event.target);
  }, true);

  document.addEventListener('focusout', (event: FocusEvent) => {
    // Going to another window keeps the field active, and the text with it
    if (event.target === guarded && document.activeElement !== guarded) release();
  }, true);

  // On a touch screen a button does not take the focus, and what it writes has to show
  document.addEventListener('pointerdown', (event: PointerEvent) => {
    if (guarded && event.target !== guarded) settle(guarded);
  }, true);
}
