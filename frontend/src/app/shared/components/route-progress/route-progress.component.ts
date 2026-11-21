import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { Event, NavigationCancel, NavigationEnd, NavigationError, NavigationStart, Router } from '@angular/router';
import { filter, map } from 'rxjs';

// A 3 px Herval line at the very top while the router opens a screen. It only shows: it changes nothing of the
// navigation, and the menu and header stay where they are.
@Component({
  selector: 'app-route-progress',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `@if (navigating()) {<div class="route-progress" role="progressbar" aria-label="Cargando pantalla"><span></span></div>}`,
  styleUrls: ['./route-progress.component.css']
})
export class RouteProgressComponent {
  private readonly router = inject(Router);

  readonly navigating = toSignal(
    this.router.events.pipe(
      filter((e: Event) => e instanceof NavigationStart || e instanceof NavigationEnd
        || e instanceof NavigationCancel || e instanceof NavigationError),
      map(e => e instanceof NavigationStart)
    ),
    { initialValue: false }
  );
}
