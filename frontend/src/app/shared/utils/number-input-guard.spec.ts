import { DOCUMENT, provideEnvironmentInitializer } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { appConfig } from '../../app.config';
import { installNumberInputGuard } from './number-input-guard';

describe('installNumberInputGuard (NF-09)', () => {
  let input: HTMLInputElement;
  let unfinished: boolean;

  const field = (type = 'number'): HTMLInputElement => {
    const element = document.createElement('input');
    element.type = type;
    document.body.appendChild(element);
    return element;
  };

  // jsdom has no bad input: a lone point is emptied by the sanitizer, so the unfinished text is simulated
  const typeUnfinished = (): void => {
    input.value = '';
    unfinished = true;
  };
  const typeNumber = (text: string): void => {
    unfinished = false;
    input.value = text;
  };

  beforeAll(() => {
    TestBed.configureTestingModule({ providers: [provideEnvironmentInitializer(installNumberInputGuard)] });
    TestBed.inject(DOCUMENT);
  });

  beforeEach(() => {
    unfinished = false;
    input = field();
    Object.defineProperty(input, 'validity', { get: () => ({ badInput: unfinished }) });
    input.value = '30';
    input.focus();
  });

  afterEach(() => {
    input.blur();
    input.remove();
  });

  it('no escribe en el campo mientras el texto no es un número', () => {
    typeUnfinished();

    input.value = '0';

    expect(input.value).toBe('');
  });

  it('aplica lo retenido al salir si el texto nunca fue un número', () => {
    typeUnfinished();
    input.value = '0';

    input.blur();

    expect(input.value).toBe('0');
  });

  it('limpia el campo al salir si no había nada retenido', () => {
    typeUnfinished();

    input.blur();

    expect(input.value).toBe('');
  });

  it('descarta lo retenido si el texto llegó a ser un número', () => {
    typeUnfinished();
    input.value = '0';
    typeNumber('.5');

    input.blur();

    expect(input.value).toBe('.5');
  });

  it('no reescribe el mismo número con otra forma', () => {
    typeNumber('.0');

    input.value = '0';

    expect(input.value).toBe('.0');
  });

  it('escribe un número distinto cuando el texto es un número', () => {
    typeNumber('12');

    input.value = '30';

    expect(input.value).toBe('30');
  });

  it('un botón escribe en el campo aunque no le quite el foco', () => {
    typeUnfinished();
    input.value = '0';
    const button = document.createElement('button');
    document.body.appendChild(button);

    button.dispatchEvent(new Event('pointerdown', { bubbles: true }));
    unfinished = false;
    input.value = '30';

    expect(input.value).toBe('30');
    expect(document.activeElement).toBe(input);
    button.remove();
  });

  it('no toca el campo si la ventana pierde el foco', () => {
    typeUnfinished();
    input.value = '0';

    input.dispatchEvent(new FocusEvent('focusout', { bubbles: true }));

    expect(Object.prototype.hasOwnProperty.call(input, 'value')).toBe(true);
    expect(input.value).toBe('');
  });

  it('deja de vigilar el campo al salir', () => {
    input.blur();

    expect(Object.prototype.hasOwnProperty.call(input, 'value')).toBe(false);
  });

  it('no vigila un campo de texto ni uno de solo lectura', () => {
    const text = field('text');
    const readOnly = field();
    readOnly.readOnly = true;

    text.focus();
    expect(Object.prototype.hasOwnProperty.call(text, 'value')).toBe(false);
    readOnly.focus();
    expect(Object.prototype.hasOwnProperty.call(readOnly, 'value')).toBe(false);

    text.remove();
    readOnly.remove();
  });

  // Last: its listeners stay on the document
  it('appConfig la instala en la app', () => {
    const guardsOn = (other: HTMLInputElement): number => {
      const defineProperty = vi.spyOn(Object, 'defineProperty');
      other.focus();
      const guards = defineProperty.mock.calls.filter(([target, key]) => target === other && key === 'value').length;
      defineProperty.mockRestore();
      other.blur();
      other.remove();
      return guards;
    };
    input.blur();
    const before = guardsOn(field());

    TestBed.resetTestingModule();
    TestBed.configureTestingModule({ providers: appConfig.providers });
    TestBed.inject(DOCUMENT);

    expect(guardsOn(field())).toBe(before + 1);
  });
});
