import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NavbarComponent } from './navbar.component';
import { provideRouter, Router } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { CashService } from '../../core/cash/cash.service';
import { Component, signal } from '@angular/core';
import { UserSession } from '../../core/auth/models/auth.models';
import { By } from '@angular/platform-browser';
import { of } from 'rxjs';

@Component({ template: '' })
class DummyComponent {}

describe('NavbarComponent', () => {
  let component: NavbarComponent;
  let fixture: ComponentFixture<NavbarComponent>;
  let router: Router;
  let mockAuthService: any;
  let currentUserSignal: any;
  let permissionsSignal: any;

  const mockUser: UserSession = {
    id: 'user-1',
    username: 'admin',
    role: 'ADMINISTRATOR'
  };

  beforeEach(async () => {
    currentUserSignal = signal<UserSession | null>(mockUser);
    permissionsSignal = signal<string[]>([
      'POS', 'SALES_HISTORY', 'CASH_HISTORY', 'PRODUCTS', 'CATEGORIES', 'TAGS', 'SUPPLIERS',
      'FINANCES_SUMMARY', 'FINANCES_TRANSACTIONS', 'FINANCES_ACCOUNTS', 'FINANCES_CATEGORIES', 'USERS'
    ]);

    mockAuthService = {
      currentUser: currentUserSignal,
      permissions: permissionsSignal,
      hasPermission: vi.fn().mockImplementation((key: string) => permissionsSignal().includes(key)),
      logout: vi.fn()
    };

    const mockCashService = {
      currentSession: signal<any>(null),
      getCurrentSession: vi.fn().mockReturnValue(of(null))
    };

    await TestBed.configureTestingModule({
      imports: [NavbarComponent],
      providers: [
        provideRouter([
          { path: 'pos', component: DummyComponent },
          { path: 'sales', component: DummyComponent },
          { path: 'cash/history', component: DummyComponent },
          { path: 'products', component: DummyComponent },
          { path: 'categories', component: DummyComponent },
          { path: 'tags', component: DummyComponent },
          { path: 'suppliers', component: DummyComponent },
          { path: 'users', component: DummyComponent }
        ]),
        { provide: AuthService, useValue: mockAuthService },
        { provide: CashService, useValue: mockCashService }
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

  it('debe renderizar los 5 grupos principales (Ventas, Finanzas, Catálogo, Compras, Seguridad)', () => {
    const groupLabels = fixture.debugElement
      .queryAll(By.css('.group-label'))
      .map(el => el.nativeElement.textContent.trim());

    expect(groupLabels).toContain('Ventas');
    expect(groupLabels).toContain('Finanzas');
    expect(groupLabels).toContain('Catálogo');
    expect(groupLabels).toContain('Compras');
    expect(groupLabels).toContain('Seguridad');
    expect(groupLabels).not.toContain('Caja');
  });

  it('debe desplegar la opción Proveedores al abrir el grupo Compras', () => {
    component.openGroup('purchases');
    fixture.detectChanges();

    expect(component.openGroupId()).toBe('purchases');
    const menu = fixture.debugElement.query(By.css('.dropdown-menu'));
    expect(menu).toBeTruthy();
    const item = menu.query(By.css('.dropdown-item'));
    expect(item.nativeElement.textContent).toContain('Proveedores');
    expect(item.nativeElement.getAttribute('href')).toBe('/suppliers');
  });

  it('debe desplegar POS, Ventas e Historial de cajas al abrir el grupo Ventas', () => {
    component.openGroup('sales');
    fixture.detectChanges();

    expect(component.openGroupId()).toBe('sales');
    const menu = fixture.debugElement.query(By.css('.dropdown-menu'));
    expect(menu).toBeTruthy();
    const items = menu.queryAll(By.css('.dropdown-item'));
    expect(items.length).toBe(3);
    expect(items[0].nativeElement.textContent).toContain('POS');
    expect(items[1].nativeElement.textContent).toContain('Ventas');
    expect(items[2].nativeElement.textContent).toContain('Historial de cajas');
    expect(items[2].nativeElement.getAttribute('href')).toBe('/cash/history');
  });

  it('debe mostrar la etiqueta de estado dinámica para POS según sesión de caja', () => {
    // Inicialmente con caja cerrada -> Cerrada
    expect(component.getItemBadge({ label: 'POS', route: '/pos' })).toBe('Cerrada');

    // Simular caja abierta
    (component.cashService.currentSession as any).set({ status: 'ABIERTA' });
    fixture.detectChanges();
    expect(component.getItemBadge({ label: 'POS', route: '/pos' })).toBe('Abierta');
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
    expect(salesItems.length).toBe(3);

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
    expect(roleBadgeEl.nativeElement.textContent.trim()).toBe('Administrador');
  });

  it('debe invocar authService.logout() al hacer clic en Cerrar Sesión', () => {
    const logoutBtn = fixture.debugElement.query(By.css('.logout-btn'));
    logoutBtn.nativeElement.click();

    expect(mockAuthService.logout).toHaveBeenCalledTimes(1);
    expect(component.openGroupId()).toBeNull();
  });

  it('debe filtrar reactivamente los submenús y ocultar grupos no autorizados según permisos', () => {
    permissionsSignal.set(['POS', 'USERS']);
    fixture.detectChanges();

    const groupLabels = fixture.debugElement
      .queryAll(By.css('.group-label'))
      .map(el => el.nativeElement.textContent.trim());

    expect(groupLabels).toContain('Ventas');
    expect(groupLabels).toContain('Seguridad');
    expect(groupLabels).not.toContain('Finanzas');
    expect(groupLabels).not.toContain('Catálogo');
    expect(groupLabels).not.toContain('Compras');

    component.openGroup('sales');
    fixture.detectChanges();

    const items = fixture.debugElement.queryAll(By.css('.dropdown-item'));
    expect(items.length).toBe(1);
    expect(items[0].nativeElement.textContent).toContain('POS');
  });

  it('el operador ve caja, ventas, catálogo y compras, pero no finanzas ni seguridad', () => {
    permissionsSignal.set([
      'POS', 'SALES_HISTORY', 'CASH_HISTORY', 'PRODUCTS', 'CATEGORIES', 'TAGS', 'SUPPLIERS'
    ]);
    fixture.detectChanges();

    const groupLabels = fixture.debugElement
      .queryAll(By.css('.group-label'))
      .map(el => el.nativeElement.textContent.trim());

    expect(groupLabels).toEqual(expect.arrayContaining(['Ventas', 'Catálogo', 'Compras']));
    expect(groupLabels).not.toContain('Finanzas');
    expect(groupLabels).not.toContain('Seguridad');
  });
});
