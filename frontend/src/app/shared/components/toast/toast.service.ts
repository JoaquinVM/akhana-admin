import { Injectable, signal } from '@angular/core';

export type ToastKind = 'success' | 'error';

export interface Toast {
  id: number;
  kind: ToastKind;
  text: string;
}

// How long a notice stays on screen
export const TOAST_DURATION_MS = 4000;

/**
 * Short notice after saving or voiding something, the same on every screen (the POS keeps its own banner). Errors
 * stay in each form, next to what failed; this is for confirmations.
 */
@Injectable({ providedIn: 'root' })
export class ToastService {
  private nextId = 1;
  readonly toasts = signal<Toast[]>([]);

  success(text: string): void {
    this.show('success', text);
  }

  error(text: string): void {
    this.show('error', text);
  }

  dismiss(id: number): void {
    this.toasts.update((list) => list.filter((t) => t.id !== id));
  }

  private show(kind: ToastKind, text: string): void {
    const toast: Toast = { id: this.nextId++, kind, text };
    // At most three at a time: the oldest goes first
    this.toasts.update((list) => [...list.slice(-2), toast]);
    setTimeout(() => this.dismiss(toast.id), TOAST_DURATION_MS);
  }
}
