import { Component, inject, signal } from '@angular/core';
import { Router, RouterLink, RouterLinkActive } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { NAVIGATION_CONFIG } from '../../core/navigation/navigation.config';
import { NavGroup } from '../../core/navigation/models/navigation.models';

@Component({
  selector: 'app-navbar',
  standalone: true,
  imports: [RouterLink, RouterLinkActive],
  templateUrl: './navbar.component.html',
  styleUrl: './navbar.component.css'
})
export class NavbarComponent {
  private router = inject(Router);
  authService = inject(AuthService);

  readonly navGroups: NavGroup[] = NAVIGATION_CONFIG;

  // Control estricto de menú desplegable único
  openGroupId = signal<string | null>(null);

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
