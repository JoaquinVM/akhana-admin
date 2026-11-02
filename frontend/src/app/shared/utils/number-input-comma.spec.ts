import { provideEnvironmentInitializer } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { appConfig } from '../../app.config';
import { ToastService } from '../components/toast/toast.service';
import { installNumberInputComma, USE_POINT_MESSAGE } from './number-input-comma';

describe('installNumberInputComma (NF-07)', () => {
  let input: HTMLInputElement;
  let execCommand: ReturnType<typeof vi.fn<(command: string, ui?: boolean, value?: string) => boolean>>;
  let toastError: ReturnType<typeof vi.fn<(text: string) => void>>;

  const pressComma = (target: HTMLElement, init: KeyboardEventInit = {}): KeyboardEvent => {
    const event = new KeyboardEvent('keydown', { key: ',', bubbles: true, cancelable: true, ...init });
    target.dispatchEvent(event);
    return event;
  };
  const paste = (target: HTMLElement, text: string): Event => {
    const event = new Event('paste', { bubbles: true, cancelable: true });
    Object.defineProperty(event, 'clipboardData', { value: { getData: () => text } });
    target.dispatchEvent(event);
    return event;
  };
  const field = (type: string): HTMLInputElement => {
    const element = document.createElement('input');
    element.type = type;
    document.body.appendChild(element);
    return element;
  };

  // One install for all the tests: the listeners stay on the document
  beforeAll(() => {
    TestBed.configureTestingModule({
      providers: [
        provideEnvironmentInitializer(installNumberInputComma),
        { provide: ToastService, useValue: { error: (text: string) => toastError(text) } }
      ]
    });
    TestBed.inject(ToastService);
  });

  beforeEach(() => {
    execCommand = vi.fn<(command: string, ui?: boolean, value?: string) => boolean>(() => true);
    (document as unknown as { execCommand: unknown }).execCommand = execCommand;
    toastError = vi.fn<(text: string) => void>();
    input = field('number');
  });

  afterEach(() => input.remove());

  it('la coma escribe un punto en un campo numérico', () => {
    const event = pressComma(input);
    expect(event.defaultPrevented).toBe(true);
    expect(execCommand).toHaveBeenCalledWith('insertText', false, '.');
    expect(toastError).not.toHaveBeenCalled();
  });

  it('cubre un campo numérico nuevo sin configurarlo', () => {
    const other = field('number');
    pressComma(other);
    expect(execCommand).toHaveBeenCalledWith('insertText', false, '.');
    other.remove();
  });

  // The text of the field, which a number field does not expose while it is invalid
  const typedField = (initial: string): { text: () => string } => {
    let text = initial;
    const invalid = (): boolean => !/^\d*\.?\d*$/.test(text) || text === '.';
    Object.defineProperty(input, 'validity', { get: () => ({ badInput: invalid() }) });
    execCommand.mockImplementation((command: string, _ui?: boolean, value?: string) => {
      text = command === 'insertText' ? text + value : text.slice(0, -1);
      return true;
    });
    return { text: () => text };
  };

  it('un segundo separador no entra', () => {
    const typed = typedField('2.');
    pressComma(input);
    expect(typed.text()).toBe('2.');
  });

  it('un separador al comienzo sí entra', () => {
    const typed = typedField('');
    pressComma(input);
    expect(typed.text()).toBe('.');
  });

  it('sobre un punto solo, que Firefox ya da por inválido, no entra otro', () => {
    const typed = typedField('.');
    pressComma(input);
    expect(typed.text()).toBe('.');
  });

  it('appConfig instala el escucha en la app', () => {
    const before = execCommand.mock.calls.length;
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({ providers: appConfig.providers });
    TestBed.inject(ToastService);
    pressComma(input);
    // The one from this suite and the one from appConfig: both act on the same key
    expect(execCommand.mock.calls.length).toBeGreaterThan(before + 1);
  });

  it('si el navegador no puede escribir el punto, avisa', () => {
    execCommand.mockReturnValue(false);
    pressComma(input);
    expect(toastError).toHaveBeenCalledWith(USE_POINT_MESSAGE);
  });

  it('en un campo de texto la coma sigue siendo una coma', () => {
    const text = field('text');
    const event = pressComma(text);
    expect(event.defaultPrevented).toBe(false);
    expect(execCommand).not.toHaveBeenCalled();
    text.remove();
  });

  it('no toca un atajo con coma ni un campo de solo lectura', () => {
    expect(pressComma(input, { metaKey: true }).defaultPrevented).toBe(false);
    expect(pressComma(input, { ctrlKey: true }).defaultPrevented).toBe(false);
    input.readOnly = true;
    expect(pressComma(input).defaultPrevented).toBe(false);
    expect(execCommand).not.toHaveBeenCalled();
  });

  it('un número pegado con coma no entra y avisa', () => {
    const event = paste(input, '2,125');
    expect(event.defaultPrevented).toBe(true);
    expect(toastError).toHaveBeenCalledWith(USE_POINT_MESSAGE);
  });

  it('un número pegado con punto entra normal', () => {
    const event = paste(input, '2.125');
    expect(event.defaultPrevented).toBe(false);
    expect(toastError).not.toHaveBeenCalled();
  });

  it('pegar con coma en un campo de texto entra normal', () => {
    const text = field('text');
    expect(paste(text, 'Nueces, 1 kg').defaultPrevented).toBe(false);
    text.remove();
  });
});
