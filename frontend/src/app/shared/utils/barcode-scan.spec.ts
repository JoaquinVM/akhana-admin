import { vi } from 'vitest';
import { SCAN_MAX_KEY_GAP_MS, ScanCapture } from './barcode-scan';

const EAN = '7791234567898';

describe('ScanCapture (POS-15)', () => {
  let capture: ScanCapture;
  let scans: string[];
  let input: HTMLInputElement;
  let received: KeyboardEvent[];
  let execCommand: ReturnType<typeof vi.fn<(command: string, ui?: boolean, value?: string) => boolean>>;
  let now: number;

  const listener = (event: Event) => received.push(event as KeyboardEvent);

  // Sends one key with its real time: the capture decides by the time of the key, not of the handler
  const press = (target: EventTarget, key: string, init: KeyboardEventInit = {}): KeyboardEvent => {
    const event = new KeyboardEvent('keydown', { key, bubbles: true, cancelable: true, ...init });
    Object.defineProperty(event, 'timeStamp', { value: now });
    target.dispatchEvent(event);
    return event;
  };
  const type = (target: EventTarget, text: string, gapMs: number): KeyboardEvent[] =>
    [...text].map((key) => {
      const event = press(target, key);
      now += gapMs;
      vi.advanceTimersByTime(gapMs);
      return event;
    });
  const scan = (target: EventTarget, code = EAN): KeyboardEvent => {
    type(target, code, 10);
    return press(target, 'Enter');
  };

  beforeEach(() => {
    vi.useFakeTimers();
    now = 1000;
    scans = [];
    received = [];
    execCommand = vi.fn<(command: string, ui?: boolean, value?: string) => boolean>(() => true);
    (document as unknown as { execCommand: unknown }).execCommand = execCommand;
    input = document.createElement('input');
    input.type = 'number';
    document.body.appendChild(input);
    input.focus();
    input.addEventListener('keydown', listener);
    document.addEventListener('keydown', listener, true);
    capture = new ScanCapture(document, (code) => scans.push(code));
    capture.install();
  });

  afterEach(() => {
    capture.uninstall();
    document.removeEventListener('keydown', listener, true);
    input.remove();
    vi.useRealTimers();
  });

  it('un escaneo avisa el código y ni el campo ni la página reciben sus teclas', () => {
    const enter = scan(input);

    expect(scans).toEqual([EAN]);
    expect(received).toEqual([]);
    expect(enter.defaultPrevented).toBe(true);
    expect(execCommand).not.toHaveBeenCalled();
  });

  it('el Enter de un escaneo no llega al formulario ni al botón con foco', () => {
    const button = document.createElement('button');
    const clicked = vi.fn();
    button.addEventListener('keydown', clicked);
    document.body.appendChild(button);
    button.focus();

    const enter = scan(button);

    expect(enter.defaultPrevented).toBe(true);
    expect(clicked).not.toHaveBeenCalled();
    button.remove();
  });

  it('el keyup del Enter de un escaneo tampoco llega a los campos', () => {
    const released = vi.fn();
    input.addEventListener('keyup', released);

    scan(input);
    input.dispatchEvent(new KeyboardEvent('keyup', { key: 'Enter', bubbles: true, cancelable: true }));
    expect(released).not.toHaveBeenCalled();

    // The next Enter of a person, whole
    now += 500;
    press(input, 'Enter');
    input.dispatchEvent(new KeyboardEvent('keyup', { key: 'Enter', bubbles: true, cancelable: true }));
    expect(released).toHaveBeenCalledTimes(1);
  });

  it('tres escaneos seguidos son tres lecturas', () => {
    scan(input);
    now += 300;
    vi.advanceTimersByTime(300);
    scan(input);
    now += 300;
    vi.advanceTimersByTime(300);
    scan(input);

    expect(scans).toEqual([EAN, EAN, EAN]);
  });

  it('dos Enter seguidos de un lector cuentan como uno', () => {
    scan(input);
    now += 5;
    const second = press(input, 'Enter');

    expect(second.defaultPrevented).toBe(true);
    expect(received).toEqual([]);
  });

  it('un Enter de una persona después de un escaneo pasa', () => {
    scan(input);
    now += 500;
    const later = press(input, 'Enter');

    expect(later.defaultPrevented).toBe(false);
  });

  it('dígitos a mano se escriben de vuelta en el campo, en orden, y no son un escaneo', () => {
    type(input, '123', SCAN_MAX_KEY_GAP_MS + 40);

    expect(scans).toEqual([]);
    expect(execCommand.mock.calls.map((call) => call[2])).toEqual(['1', '2', '3']);
  });

  it('un dígito suelto se escribe al vencer la espera', () => {
    press(input, '5');
    expect(execCommand).not.toHaveBeenCalled();

    vi.advanceTimersByTime(SCAN_MAX_KEY_GAP_MS);

    expect(execCommand).toHaveBeenCalledWith('insertText', false, '5');
  });

  it('dos dígitos pulsados casi a la vez se escriben juntos y en orden', () => {
    type(input, '42', 5);
    vi.advanceTimersByTime(SCAN_MAX_KEY_GAP_MS);

    expect(execCommand.mock.calls.map((call) => call[2])).toEqual(['42']);
  });

  it('pocos dígitos seguidos de Enter son tipeo: se escriben y el Enter pasa', () => {
    type(input, '12345', 10);
    const enter = press(input, 'Enter');

    expect(scans).toEqual([]);
    expect(execCommand).toHaveBeenCalledWith('insertText', false, '12345');
    expect(enter.defaultPrevented).toBe(false);
  });

  it('un Enter tardío no convierte en escaneo lo que se escribió rápido', () => {
    type(input, EAN, 10);
    now += SCAN_MAX_KEY_GAP_MS + 10;
    const enter = press(input, 'Enter');

    expect(scans).toEqual([]);
    expect(enter.defaultPrevented).toBe(false);
    expect(execCommand).toHaveBeenCalledWith('insertText', false, EAN);
  });

  it('otra tecla escribe primero lo retenido y después pasa, para conservar el orden', () => {
    press(input, '2');
    const comma = press(input, ',');

    expect(execCommand).toHaveBeenCalledWith('insertText', false, '2');
    expect(comma.defaultPrevented).toBe(false);
    expect(received.map((event) => event.key)).toContain(',');
  });

  it('un clic escribe lo retenido antes de que cambie el foco', () => {
    press(input, '7');

    window.dispatchEvent(new MouseEvent('mousedown'));

    expect(execCommand).toHaveBeenCalledWith('insertText', false, '7');
  });

  it('una tecla mantenida, o con Ctrl, no se retiene', () => {
    expect(press(input, '1', { repeat: true }).defaultPrevented).toBe(false);
    expect(press(input, '1', { ctrlKey: true }).defaultPrevented).toBe(false);
  });

  it('sin campo con foco, los dígitos tecleados no van a ningún lado', () => {
    input.blur();

    press(document.body, '5');
    vi.advanceTimersByTime(SCAN_MAX_KEY_GAP_MS);

    expect(execCommand).not.toHaveBeenCalled();
  });

  it('si no se puede escribir en el cursor, agrega al valor y avisa al campo', () => {
    execCommand.mockReturnValue(false);
    const text = document.createElement('input');
    document.body.appendChild(text);
    text.focus();
    text.value = '1';
    const onInput = vi.fn();
    text.addEventListener('input', onInput);

    press(text, '2');
    vi.advanceTimersByTime(SCAN_MAX_KEY_GAP_MS);

    expect(text.value).toBe('12');
    expect(onInput).toHaveBeenCalled();
    text.remove();
  });

  it('en un campo de fecha los dígitos pasan, y un escaneo devuelve la fecha y detiene su Enter', () => {
    const date = document.createElement('input');
    date.type = 'date';
    date.value = '2026-11-02';
    document.body.appendChild(date);
    date.focus();
    const onInput = vi.fn();
    date.addEventListener('input', onInput);

    const digit = press(date, '2');
    now += 10;
    expect(digit.defaultPrevented).toBe(false);
    // What the browser does with the digits of a date field
    date.value = '2024-07-07';
    type(date, '123456', 10);
    const enter = press(date, 'Enter');

    expect(enter.defaultPrevented).toBe(true);
    expect(date.value).toBe('2026-11-02');
    expect(onInput).toHaveBeenCalled();
    expect(scans).toEqual([]);
    expect(execCommand).not.toHaveBeenCalled();
    date.remove();
  });

  it('en un campo de fecha, escribir a mano no devuelve nada', () => {
    const date = document.createElement('input');
    date.type = 'date';
    date.value = '2026-11-02';
    document.body.appendChild(date);
    date.focus();

    press(date, '2');
    date.value = '2024-11-02';
    vi.advanceTimersByTime(SCAN_MAX_KEY_GAP_MS);

    expect(date.value).toBe('2024-11-02');
    date.remove();
  });

  it('al desinstalar, escribe lo retenido y deja de escuchar', () => {
    press(input, '3');
    capture.uninstall();

    expect(execCommand).toHaveBeenCalledWith('insertText', false, '3');
    expect(press(input, '4').defaultPrevented).toBe(false);
  });
});
