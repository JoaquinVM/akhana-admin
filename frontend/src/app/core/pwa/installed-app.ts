import { InjectionToken } from '@angular/core';

/** MOV-02: true when Admin runs from the home screen. iPhone only reports it through navigator.standalone. */
export function isInstalledApp(win: Window = window): boolean {
  return win.matchMedia?.('(display-mode: standalone)').matches === true
    || (win.navigator as Navigator & { standalone?: boolean }).standalone === true;
}

export const INSTALLED_APP = new InjectionToken<boolean>('INSTALLED_APP', {
  providedIn: 'root',
  factory: () => isInstalledApp()
});

/** MOV-03: only the installed app registers the worker, so the browser (the store's PC) stays as it was. */
export function registerServiceWorker(win: Window = window): void {
  if (isInstalledApp(win) && 'serviceWorker' in win.navigator) {
    win.navigator.serviceWorker.register('/sw.js').catch(err => console.error(err));
  }
}
