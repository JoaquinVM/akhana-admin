import { Component, inject, computed } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { toSignal } from '@angular/core/rxjs-interop';

@Component({
  selector: 'app-section-page',
  standalone: true,
  templateUrl: './section-page.component.html',
  styleUrl: './section-page.component.css'
})
export class SectionPageComponent {
  private route = inject(ActivatedRoute);
  private routeData = toSignal(this.route.data);

  title = computed(() => this.routeData()?.['title'] || 'Sección');
  subtitle = computed(() => this.routeData()?.['subtitle'] || 'Estructura base de navegación');
}
