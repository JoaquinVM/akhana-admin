import { DOCUMENT, Injectable, inject, signal } from '@angular/core';
import { NavigationEnd, NavigationError, Router } from '@angular/router';

// What each browser says when a lazy screen cannot be fetched: Chrome, Firefox and Safari
const LOAD_FAILURES = [/dynamically imported module/i, /importing a module script failed/i];

export function isScreenLoadFailure(error: unknown): boolean {
  const message = (error as { message?: unknown } | null)?.message;
  return typeof message === 'string' && LOAD_FAILURES.some(failure => failure.test(message));
}

/**
 * NF-10: after a release an open app looks for files that no longer exist. Nothing reloads on its own, so whatever
 * is being written stays until the person chooses to update.
 */
@Injectable({ providedIn: 'root' })
export class ScreenLoadNoticeService {
  private readonly document = inject(DOCUMENT);
  readonly requestedUrl = signal<string | null>(null);

  constructor() {
    inject(Router).events.subscribe(event => {
      if (event instanceof NavigationEnd) {
        this.dismiss();
      }
    });
  }

  dismiss(): void {
    this.requestedUrl.set(null);
  }

  update(): void {
    const url = this.requestedUrl();
    if (url) {
      this.document.location.assign(url);
    }
  }
}

/** Router hook: runs in an injection context. */
export function reportScreenLoadFailure(navigationError: NavigationError): void {
  if (isScreenLoadFailure(navigationError.error)) {
    inject(ScreenLoadNoticeService).requestedUrl.set(navigationError.url);
  }
}
