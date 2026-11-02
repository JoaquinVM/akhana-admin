// Starting values, to confirm with the real reader: the most time between two keys of a scan, Enter included
export const SCAN_MAX_KEY_GAP_MS = 60;
// Short enough that a label with a short code still warns instead of falling into a field; a person does not chain this
// many keys and Enter under the gap
export const SCAN_MIN_CHARS = 4;

const NOT_REPLAYABLE_TYPES = new Set(['date', 'time', 'month', 'week', 'datetime-local']);
const NOT_TYPED_IN_TYPES = new Set(['checkbox', 'radio', 'button', 'submit', 'reset', 'file', 'range', 'color', 'image']);
const MODIFIER_KEYS = new Set(['Shift', 'CapsLock', 'Control', 'Alt', 'AltGraph', 'Meta']);

type PassThroughField = HTMLInputElement | HTMLSelectElement;

/**
 * POS-15: tells a USB reader, which types like a keyboard, from a person. Printable keys are held back at the capture
 * phase of the window, before NF-07 and any field see them: a fast burst that ends in Enter is a scan, with letters or
 * not, and never reaches a field, a form or a button; anything else is written back at the cursor, at most one gap late.
 */
export class ScanCapture {
  private held = '';
  private lastKeyAt = 0;
  private timer: ReturnType<typeof setTimeout> | null = null;
  // Date fields and selects cannot be written back: their keys go through, and a scan puts back the value they had
  private passThrough: { field: PassThroughField; value: string } | null = null;
  private swallowEnterUntil = -1;
  // Fields can act on the release of Enter too, so each stopped Enter also stops its keyup
  private enterUpsToStop = 0;
  private writingBack = false;

  constructor(
    private readonly document: Document,
    private readonly onScan: (code: string) => void
  ) {}

  private get window(): Window {
    return this.document.defaultView!;
  }

  install(): void {
    this.window.addEventListener('keydown', this.onKeydown, true);
    this.window.addEventListener('keyup', this.onKeyup, true);
    this.window.addEventListener('mousedown', this.release, true);
  }

  uninstall(): void {
    this.release();
    this.window.removeEventListener('keydown', this.onKeydown, true);
    this.window.removeEventListener('keyup', this.onKeyup, true);
    this.window.removeEventListener('mousedown', this.release, true);
  }

  private readonly onKeydown = (event: KeyboardEvent): void => {
    if (this.writingBack || MODIFIER_KEYS.has(event.key)) return;
    const plain = !event.ctrlKey && !event.metaKey && !event.altKey && !event.isComposing;
    if (plain && !event.repeat && event.key.length === 1 && !this.activatesFocused(event)) {
      this.onPrintable(event);
      return;
    }
    if (event.key === 'Enter' && this.isScanEnd(event)) {
      const code = this.passThrough ? '' : this.held;
      this.restorePassThrough();
      this.blockEnter(event);
      this.clear();
      this.swallowEnterUntil = event.timeStamp + SCAN_MAX_KEY_GAP_MS;
      if (code) this.onScan(code);
      return;
    }
    // A reader that ends with CR and LF sends two Enter
    if (event.key === 'Enter' && event.timeStamp <= this.swallowEnterUntil) {
      this.blockEnter(event);
      return;
    }
    if (event.key === 'Enter') {
      this.enterUpsToStop = 0;
    }
    // Any other key, written back first to keep the order of what was typed
    this.release();
  };

  private readonly onKeyup = (event: KeyboardEvent): void => {
    if (event.key === 'Enter' && this.enterUpsToStop > 0) {
      this.enterUpsToStop--;
      this.block(event);
    }
  };

  private onPrintable(event: KeyboardEvent): void {
    if (this.held && event.timeStamp - this.lastKeyAt > SCAN_MAX_KEY_GAP_MS) {
      this.release();
    }
    if (!this.held) {
      this.passThrough = this.focusedPassThroughField();
    }
    if (!this.passThrough) {
      this.block(event);
    }
    this.held += event.key;
    this.lastKeyAt = event.timeStamp;
    if (this.timer !== null) clearTimeout(this.timer);
    this.timer = setTimeout(this.release, SCAN_MAX_KEY_GAP_MS);
  }

  // A lone space on a button or a checkbox presses it: it cannot be held and written back
  private activatesFocused(event: KeyboardEvent): boolean {
    return event.key === ' ' && !this.held && !this.focusedTextField();
  }

  private isScanEnd(event: KeyboardEvent): boolean {
    return this.held.length >= SCAN_MIN_CHARS && event.timeStamp - this.lastKeyAt <= SCAN_MAX_KEY_GAP_MS;
  }

  private blockEnter(event: KeyboardEvent): void {
    this.enterUpsToStop++;
    this.block(event);
  }

  private block(event: KeyboardEvent): void {
    event.preventDefault();
    event.stopImmediatePropagation();
  }

  private readonly release = (): void => {
    const text = this.passThrough ? '' : this.held;
    this.clear();
    if (text) this.writeBack(text);
  };

  private clear(): void {
    this.held = '';
    this.passThrough = null;
    if (this.timer !== null) {
      clearTimeout(this.timer);
      this.timer = null;
    }
  }

  private focusedPassThroughField(): { field: PassThroughField; value: string } | null {
    const active = this.document.activeElement;
    const passes = active instanceof HTMLSelectElement
      || (active instanceof HTMLInputElement && NOT_REPLAYABLE_TYPES.has(active.type));
    return passes ? { field: active as PassThroughField, value: (active as PassThroughField).value } : null;
  }

  private restorePassThrough(): void {
    if (!this.passThrough) return;
    const { field, value } = this.passThrough;
    field.value = value;
    field.dispatchEvent(new Event('input', { bubbles: true }));
    field.dispatchEvent(new Event('change', { bubbles: true }));
  }

  private focusedTextField(): HTMLInputElement | HTMLTextAreaElement | null {
    const active = this.document.activeElement;
    const typedIn = active instanceof HTMLTextAreaElement
      || (active instanceof HTMLInputElement && !NOT_TYPED_IN_TYPES.has(active.type));
    if (!typedIn) return null;
    const field = active as HTMLInputElement | HTMLTextAreaElement;
    return field.readOnly || field.disabled ? null : field;
  }

  private writeBack(text: string): void {
    const field = this.focusedTextField();
    if (!field) return;
    for (const run of text.match(/[0-9]+|[^0-9]/g) ?? []) {
      if (/^[0-9]/.test(run)) {
        this.insert(field, run);
      } else {
        this.writeKey(field, run);
      }
    }
  }

  // Others can act on the key, as NF-07 does with the comma, so it goes through them first
  private writeKey(field: HTMLInputElement | HTMLTextAreaElement, key: string): void {
    const event = new KeyboardEvent('keydown', { key, bubbles: true, cancelable: true });
    this.writingBack = true;
    field.dispatchEvent(event);
    this.writingBack = false;
    if (!event.defaultPrevented) {
      this.insert(field, key);
    }
  }

  private insert(field: HTMLInputElement | HTMLTextAreaElement, text: string): void {
    // Number fields have no selection API: this is the only way to write at the cursor, and it fires the input event
    if (this.document.execCommand('insertText', false, text)) return;
    // A number field refuses what would not be a number, like a second point: it stays refused
    if ((field as HTMLInputElement).type === 'number' && !/^[0-9]+$/.test(text)) return;
    field.value += text;
    field.dispatchEvent(new Event('input', { bubbles: true }));
  }
}
