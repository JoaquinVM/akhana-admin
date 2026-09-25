import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NavbarComponent } from './navbar.component';
import { provideRouter, Router } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { Component, signal } from '@angular/core';
import { UserSession } from '../../core/auth/models/auth.models';
import { By } from '@angular/platform-browser';

@Component({ template: '' })
class DummyComponent {}

describe('NavbarComponent', () => {
  let component: NavbarComponent;
  let fixture: ComponentFixture<NavbarComponent>;
  let router: Router;
  let mockAuthService: any;
  let currentUserSignal: any;

  const mockUser: UserSession = {
    id: 'user-1',
    username: 'admin',
    role: 'ADMIN'
  };

  beforeEach(async () => {
    currentUserSignal = signal<UserSession | null>(mockUser);

    mockAuthService = {
      currentUser: currentUserSignal,
      logout: vi.fn()
    };

    await TestBed.configureTestingModule({
      imports: [NavbarComponent],
      providers: [
        provideRouter([
          { path: 'pos', component: DummyComponent },
          { path: 'sales', component: DummyComponent },
          { path: 'products', component: DummyComponent },
          { path: 'categories', component: DummyComponent },
          { path: 'tags', component: DummyComponent },
          { path: 'users', component: DummyComponent }
        ]),
        { provide: AuthService, useValue: mockAuthService }
      ]
    }).compileComponents();

    router = TestBed.inject(Router);
    fixture = TestBed.createComponent(NavbarComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('debe crearse exitosamente', () => {
    expect(component).toBeTruthy();
  });

  it('debe renderizar los 3 grupos principales (Ventas, Catálogo, Seguridad)', () => {
    const groupLabels = fixture.debugElement
      .queryAll(By.css('.group-label'))
      .map(el => el.nativeElement.textContent.trim());

    expect(groupLabels).toContain('Ventas');
    expect(groupLabels).toContain('Catálogo');
    expect(groupLabels).toContain('Seguridad');
  });

  it('debe desplegar únicamente el menú del grupo activo en hover y cerrar el anterior', () => {
    // Inicialmente ningún menú está abierto
    expect(component.openGroupId()).toBeNull();
    expect(fixture.debugElement.query(By.css('.dropdown-menu'))).toBeNull();

    // Abrir grupo Ventas
    component.openGroup('sales');
    fixture.detectChanges();

    expect(component.openGroupId()).toBe('sales');
    const salesMenu = fixture.debugElement.query(By.css('.dropdown-menu'));
    expect(salesMenu).toBeTruthy();
    const salesItems = salesMenu.queryAll(By.css('.dropdown-item'));
    expect(salesItems.length).toBe(2);

    // Abrir grupo Catálogo: debe cerrar Ventas y abrir únicamente Catálogo
    component.openGroup('catalog');
    fixture.detectChanges();

    expect(component.openGroupId()).toBe('catalog');
    const allMenus = fixture.debugElement.queryAll(By.css('.dropdown-menu'));
    expect(allMenus.length).toBe(1); // Solo un menú abierto a la vez
    const catalogItems = allMenus[0].queryAll(By.css('.dropdown-item'));
    expect(catalogItems.length).toBe(3);
  });

  it('debe cerrar el menú automáticamente al hacer clic en una opción (closeAllMenus)', () => {
    component.openGroup('sales');
    fixture.detectChanges();

    expect(component.openGroupId()).toBe('sales');

    const itemLink = fixture.debugElement.query(By.css('.dropdown-item'));
    itemLink.triggerEventHandler('click', { button: 0, preventDefault: vi.fn() });
    fixture.detectChanges();

    expect(component.openGroupId()).toBeNull();
    expect(fixture.debugElement.query(By.css('.dropdown-menu'))).toBeNull();
  });

  it('no debe mostrar ningún encabezado de categoría como Módulo Comercial', () => {
    component.openGroup('sales');
    fixture.detectChanges();

    expect(fixture.debugElement.query(By.css('.dropdown-header'))).toBeNull();
    const textContent = fixture.debugElement.query(By.css('.dropdown-menu')).nativeElement.textContent;
    expect(textContent).not.toContain('MÓDULO COMERCIAL');
    expect(textContent).not.toContain('Módulo Comercial');
  });

  it('debe identificar reactivamente el grupo activo cuando la URL coincide con un hijo', () => {
    vi.spyOn(router, 'url', 'get').mockReturnValue('/products');

    const salesGroup = component.navGroups.find(g => g.id === 'sales')!;
    const catalogGroup = component.navGroups.find(g => g.id === 'catalog')!;

    expect(component.isGroupActive(salesGroup)).toBe(false);
    expect(component.isGroupActive(catalogGroup)).toBe(true);
  });

  it('debe mostrar la información del usuario autenticado y su rol', () => {
    const usernameEl = fixture.debugElement.query(By.css('.username'));
    const roleBadgeEl = fixture.debugElement.query(By.css('.role-badge'));

    expect(usernameEl.nativeElement.textContent.trim()).toBe('admin');
    expect(roleBadgeEl.nativeElement.textContent.trim()).toBe('ADMIN');
  });

  it('debe invocar authService.logout() al hacer clic en Cerrar Sesión', () => {
    const logoutBtn = fixture.debugElement.query(By.css('.logout-btn'));
    logoutBtn.nativeElement.click();

    expect(mockAuthService.logout).toHaveBeenCalledTimes(1);
    expect(component.openGroupId()).toBeNull();
  });
});
