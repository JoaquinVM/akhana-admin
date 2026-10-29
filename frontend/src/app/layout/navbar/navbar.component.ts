import { Component, OnInit, inject, signal, computed } from '@angular/core';
import { Router, RouterLink, RouterLinkActive } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { CashService } from '../../core/cash/cash.service';
import { NAVIGATION_CONFIG, roleLabel } from '../../core/navigation/navigation.config';
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
  readonly roleLabel = roleLabel;

  // Menu filtered by the permissions of the signed-in user
  readonly visibleNavGroups = computed<NavGroup[]>(() => {
    if (typeof this.authService.permissions === 'function') {
      this.authService.permissions();
    }
    return NAVIGATION_CONFIG.map(group => {
      const authorizedChildren = group.children.filter(item => this.hasItemPermission(item));
      return {
        ...group,
        children: authorizedChildren
      };
    }).filter(group => group.children.length > 0);
  });

  get navGroups(): NavGroup[] {
    return this.visibleNavGroups();
  }

  // Only one dropdown open at a time
  openGroupId = signal<string | null>(null);

  // Narrow screens (the operator's phone): the menu folds behind a button
  mobileMenuOpen = signal<boolean>(false);

  isCashOpen = computed(() => {
    const session = this.cashService.currentSession();
    return session !== null && session.status === 'ABIERTA';
  });

  ngOnInit(): void {
    // Load the shared cash session state for the POS badge
    this.cashService.getCurrentSession().subscribe({
      error: () => {
        // On error or without an open session, currentSession stays null (closed)
      }
    });
  }

  getItemBadge(item: NavItem): string | null {
    if (item.route === '/pos') {
      return this.isCashOpen() ? 'Abierta' : 'Cerrada';
    }
    return item.badge || null;
  }

  hasItemPermission(item: NavItem): boolean {
    if (!item.permissionKey) {
      return true;
    }
    if (typeof this.authService.hasPermission === 'function') {
      return this.authService.hasPermission(item.permissionKey);
    }
    return true;
  }

  isGroupActive(group: NavGroup): boolean {
    const currentUrl = this.router.url.split('?')[0];
    return group.children.some(child =>
      currentUrl === child.route || currentUrl.startsWith(child.route + '/')
    );
  }

  // Hover opens a group on a wide screen; in the folded menu only taps count (touch also fires mouse events)
  openGroup(groupId: string): void {
    if (!this.mobileMenuOpen()) {
      this.openGroupId.set(groupId);
    }
  }

  // Touch screens have no hover: in the folded menu a tap opens or closes the group; on a wide screen the
  // click keeps open the group the hover already opened
  toggleGroup(groupId: string): void {
    if (this.mobileMenuOpen() && this.openGroupId() === groupId) {
      this.openGroupId.set(null);
      return;
    }
    this.openGroupId.set(groupId);
  }

  toggleMobileMenu(): void {
    this.mobileMenuOpen.update((open) => !open);
    this.openGroupId.set(null);
  }

  closeGroup(groupId: string): void {
    if (!this.mobileMenuOpen() && this.openGroupId() === groupId) {
      this.openGroupId.set(null);
    }
  }

  // Leaving the bar with the mouse closes the dropdown, not the folded menu
  onNavLeave(): void {
    if (!this.mobileMenuOpen()) {
      this.openGroupId.set(null);
    }
  }

  closeAllMenus(): void {
    this.openGroupId.set(null);
    this.mobileMenuOpen.set(false);
    if (typeof document !== 'undefined' && document.activeElement instanceof HTMLElement) {
      document.activeElement.blur();
    }
  }

  logout(): void {
    this.closeAllMenus();
    this.authService.logout();
  }
}
