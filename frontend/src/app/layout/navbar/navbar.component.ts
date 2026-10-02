import { Component, OnInit, inject, signal, computed } from '@angular/core';
import { Router, RouterLink, RouterLinkActive } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { CashService } from '../../core/cash/cash.service';
import { NAVIGATION_CONFIG } from '../../core/navigation/navigation.config';
import { NavGroup, NavItem } from '../../core/navigation/models/navigation.models';

@Component({
  selector: 'app-navbar',
  standalone: true,
  imports: [RouterLink, RouterLinkActive],
  templateUrl: './navbar.component.html',
  styleUrl: './navbar.component.css'
})
export class NavbarComponent implements OnInit {
  private router = inject(Router);
  authService = inject(AuthService);
  readonly cashService = inject(CashService);

  readonly navGroups: NavGroup[] = NAVIGATION_CONFIG;

  // Control estricto de menú desplegable único
  openGroupId = signal<string | null>(null);

  isCashOpen = computed(() => {
    const session = this.cashService.currentSession();
    return session !== null && session.status === 'ABIERTA';
  });

  ngOnInit(): void {
    // Cargar estado de sesión de caja compartida si no se ha cargado aún
    this.cashService.getCurrentSession().subscribe({
      error: () => {
        // En caso de error o sin caja activa, currentSession permanece null (Cerrada)
      }
    });
  }

  getItemBadge(item: NavItem): string | null {
    if (item.route === '/pos') {
      return this.isCashOpen() ? 'Abierta' : 'Cerrada';
    }
    return item.badge || null;
  }

  isGroupActive(group: NavGroup): boolean {
    const currentUrl = this.router.url.split('?')[0];
    return group.children.some(child =>
      currentUrl === child.route || currentUrl.startsWith(child.route + '/')
    );
  }

  openGroup(groupId: string): void {
    this.openGroupId.set(groupId);
  }

  closeGroup(groupId: string): void {
    if (this.openGroupId() === groupId) {
      this.openGroupId.set(null);
    }
  }

  closeAllMenus(): void {
    this.openGroupId.set(null);
    if (typeof document !== 'undefined' && document.activeElement instanceof HTMLElement) {
      document.activeElement.blur();
    }
  }

  logout(): void {
    this.closeAllMenus();
    this.authService.logout();
  }
}
