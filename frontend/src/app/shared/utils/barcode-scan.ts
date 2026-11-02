// Starting values, to confirm with the real reader: the most time between two keys of a scan, Enter included
export const SCAN_MAX_KEY_GAP_MS = 60;
// With 6 and 7 digits a short code still counts as a scan and warns, instead of falling into a field
export const SCAN_MIN_DIGITS = 6;

const NOT_REPLAYABLE_TYPES = new Set(['date', 'time', 'month', 'week', 'datetime-local']);

/**
 * POS-15: tells a USB reader, which types like a keyboard, from a person. Digits are held back at the capture phase of
 * the window, before NF-07 and any field see them: a fast burst that ends in Enter is a scan and never reaches a field,
 * a form or a button; anything else is written back at the cursor, at most one gap late.
 */
export class ScanCapture {
  private held = '';
  private lastKeyAt = 0;
  private timer: ReturnType<typeof setTimeout> | null = null;
  // A date field cannot be written back: its digits go through, but the final Enter of a scan is still stopped
  private passThrough = false;
  private swallowEnterUntil = -1;

  constructor(
    private readonly document: Document,
    private readonly onScan: (code: string) => void
  ) {}

  private get window(): Window {
    return this.document.defaultView!;
  }

  install(): void {
    this.window.addEventListener('keydown', this.onKeydown, true);
    this.window.addEventListener('mousedown', this.release, true);
  }

  uninstall(): void {
    this.release();
    this.window.removeEventListener('keydown', this.onKeydown, true);
    this.window.removeEventListener('mousedown', this.release, true);
  }

  private readonly onKeydown = (event: KeyboardEvent): void => {
    const plain = !event.ctrlKey && !event.metaKey && !event.altKey && !event.isComposing;
    if (plain && !event.repeat && /^[0-9]$/.test(event.key)) {
      this.onDigit(event);
      return;
    }
    if (event.key === 'Enter' && this.isScanEnd(event)) {
      const code = this.passThrough ? '' : this.held;
      this.block(event);
      this.clear();
      this.swallowEnterUntil = event.timeStamp + SCAN_MAX_KEY_GAP_MS;
      if (code) this.onScan(code);
      return;
    }
    // A reader that ends with CR and LF sends two Enter
    if (event.key === 'Enter' && event.timeStamp <= this.swallowEnterUntil) {
      this.block(event);
      return;
    }
    // Any other key, written back first to keep the order of what was typed
    this.release();
  };

  private onDigit(event: KeyboardEvent): void {
    if (this.held && event.timeStamp - this.lastKeyAt > SCAN_MAX_KEY_GAP_MS) {
      this.release();
    }
    if (!this.held) {
      this.passThrough = this.focusedCannotBeWrittenBack();
    }
    if (!this.passThrough) {
      this.block(event);
    }
    this.held += event.key;
    this.lastKeyAt = event.timeStamp;
    if (this.timer !== null) clearTimeout(this.timer);
    this.timer = setTimeout(this.release, SCAN_MAX_KEY_GAP_MS);
  }

  private isScanEnd(event: KeyboardEvent): boolean {
    return this.held.length >= SCAN_MIN_DIGITS && event.timeStamp - this.lastKeyAt <= SCAN_MAX_KEY_GAP_MS;
  }

  private block(event: KeyboardEvent): void {
    event.preventDefault();
    event.stopImmediatePropagation();
  }

  private readonly release = (): void => {
    const digits = this.passThrough ? '' : this.held;
    this.clear();
    if (digits) this.writeBack(digits);
  };

  private clear(): void {
    this.held = '';
    this.passThrough = false;
    if (this.timer !== null) {
      clearTimeout(this.timer);
      this.timer = null;
    }
  }

  private focusedCannotBeWrittenBack(): boolean {
    const active = this.document.activeElement;
    return active instanceof HTMLInputElement && NOT_REPLAYABLE_TYPES.has(active.type);
  }

  private writeBack(digits: string): void {
    const active = this.document.activeElement;
    const editable = (active instanceof HTMLInputElement || active instanceof HTMLTextAreaElement)
      && !active.readOnly && !active.disabled;
    if (!editable) return;
    // Number fields have no selection API: this is the only way to write at the cursor, and it fires the input event
    if (!this.document.execCommand('insertText', false, digits)) {
      active.value += digits;
      active.dispatchEvent(new Event('input', { bubbles: true }));
    }
  }
}
