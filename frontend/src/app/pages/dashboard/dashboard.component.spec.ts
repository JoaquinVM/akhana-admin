import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Subject, of, throwError } from 'rxjs';
import { HttpErrorResponse } from '@angular/common/http';
import { Injector, runInInjectionContext } from '@angular/core';
import { DashboardComponent, changePercent, periodRange, storeToday } from './dashboard.component';
import { DashboardService } from '../../core/dashboard/dashboard.service';
import { Dashboard, DashboardIndicators } from '../../core/dashboard/models/dashboard.models';
import { AuthService } from '../../core/auth/auth.service';
import { homeRedirect } from '../../app.routes';
import { INSTALLED_APP } from '../../core/pwa/installed-app';

describe('DashboardComponent', () => {
  let component: DashboardComponent;
  let fixture: ComponentFixture<DashboardComponent>;
  let dashboardService: any;

  const indicators = (overrides: Partial<DashboardIndicators> = {}): DashboardIndicators => ({
    sales: 2745, cost: 2104.16, profit: 640.84, margin: 23.35, tickets: 30, averageTicket: 91.5,
    atCostSales: 1, atCostTotal: 104, consumptionTotal: 35.5,
    ...overrides
  });
  const dashboard: Dashboard = {
    from: '2026-10-01', to: '2026-10-03', previousFrom: '2026-09-01', previousTo: '2026-09-03',
    current: indicators(),
    previous: indicators({ sales: 2500, profit: 700, tickets: 30, averageTicket: 83.33 }),
    days: [
      { date: '2026-10-01', sales: 0, profit: 0 },
      { date: '2026-10-02', sales: 2745, profit: 640.84 },
      { date: '2026-10-03', sales: 0, profit: 0 }
    ],
    previousDays: [
      { date: '2026-09-01', sales: 1000, profit: 250 },
      { date: '2026-09-02', sales: 1500, profit: 450 },
      { date: '2026-09-03', sales: 0, profit: 0 }
    ],
    payments: { cash: 1500, qr: 1245, credit: 104, historicalTotal: 2100, historicalSales: 2 },
    topProductsBySales: [{ id: 'p1', name: 'Nuez pelada', quantity: 3.5, sales: 450, profit: 90 },
      { id: 'p2', name: 'Maní', quantity: 10, sales: 100, profit: 40 }],
    topProductsByProfit: [{ id: 'p1', name: 'Nuez pelada', quantity: 3.5, sales: 450, profit: 90 },
      { id: 'p2', name: 'Maní', quantity: 10, sales: 100, profit: 40 }],
    topSuppliersBySales: [{ id: 's1', name: 'Bohemia', quantity: 2, sales: 160, profit: 16 }],
    topSuppliersByProfit: [{ id: 's1', name: 'Bohemia', quantity: 2, sales: 160, profit: 16 }],
    expenses: [{ categoryId: 'c1', categoryName: 'Pedido', amount: 4100 },
      { categoryId: 'c2', categoryName: 'Comida', amount: 250 }],
    expenseTotal: 4350,
    now: {
      cash: { open: true, sessionNumber: 412, openedBy: 'ana', openedAt: '2026-10-30T12:30:00Z', openingAmount: 300,
        totalSales: 1300, salesCount: 17, expectedCash: 1200 },
      accounts: [{ id: 'a1', name: 'Caja', color: '#16a34a', balance: 1500 },
        { id: 'a2', name: 'Bisa', color: '#2563eb', balance: 3500 }],
      totalBalance: 5000, payables: 320, creditorsDebt: 200.25, receivables: 104
    },
    noAttentionDays: []
  };

  beforeEach(async () => {
    dashboardService = { getDashboard: vi.fn().mockReturnValue(of(dashboard)) };
    await TestBed.configureTestingModule({
      imports: [DashboardComponent],
      providers: [{ provide: DashboardService, useValue: dashboardService }]
    }).compileComponents();

    fixture = TestBed.createComponent(DashboardComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('DSH-01: abre en el mes en curso, del 1 a hoy en la hora de la tienda', () => {
    const today = storeToday();
    expect(dashboardService.getDashboard).toHaveBeenCalledWith({ from: `${today.slice(0, 7)}-01`, to: today });
    expect(component.period()).toBe('MONTH');
  });

  it('DSH-01: hoy, semana desde el lunes y mes desde el 1', () => {
    expect(periodRange('TODAY', '2026-10-30')).toEqual({ from: '2026-10-30', to: '2026-10-30' });
    expect(periodRange('WEEK', '2026-10-30')).toEqual({ from: '2026-10-26', to: '2026-10-30' });
    expect(periodRange('WEEK', '2026-11-01')).toEqual({ from: '2026-10-26', to: '2026-11-01' });
    expect(periodRange('MONTH', '2026-10-30')).toEqual({ from: '2026-10-01', to: '2026-10-30' });
    expect(storeToday(new Date('2026-10-31T02:00:00Z'))).toBe('2026-10-30');
  });

  it('DSH-01: muestra los indicadores con su cambio contra el período anterior', () => {
    const text = fixture.nativeElement.textContent;
    expect(text).toContain('Bs 2,745.00');
    expect(text).toContain('▲ 9.8 % vs anterior');
    expect(text).toContain('▼ 8.5 % vs anterior');
    expect(text).toContain('Margen 23.35 %');
    expect(text).toContain('Bs 91.50');
    expect(text).toContain('Consumos al costo: Bs 35.50');
    expect(changePercent(10, 0)).toBeNull();
    expect(changePercent(null, 5)).toBeNull();
  });

  it('DSH-02: dibuja la línea del período y la del anterior, con tabla y tooltip', () => {
    const paths = fixture.nativeElement.querySelectorAll('svg path');
    expect(paths.length).toBe(2);
    expect(component.currentPath().split('L').length).toBe(3);
    component.hoverIndex.set(1);
    fixture.detectChanges();
    const tooltip = fixture.nativeElement.querySelector('.tooltip').textContent;
    expect(tooltip).toContain('02/10/2026');
    expect(tooltip).toContain('Ventas Bs 2,745.00');
    expect(tooltip).toContain('02/09/2026: Bs 1,500.00');
    expect(fixture.nativeElement.querySelector('.table-view').textContent).toContain('640.84');
  });

  it('DSH-02: muestra cómo se cobró, con el crédito cuando lo hay', () => {
    const text = fixture.nativeElement.textContent;
    expect(text).toContain('Efectivo');
    expect(text).toContain('Bs 1,245.00');
    expect(text).toContain('Crédito');
    expect(text).toContain('Sin dato de cobro: Bs 2,100.00 de 2 días migrados de la planilla.');
    expect(text).toContain('Incluye 2 días migrados de la planilla (1 por día)');
  });

  it('DSH-03: cambia el orden de los productos entre venta y utilidad', () => {
    expect(component.products()).toBe(dashboard.topProductsBySales);
    const toggle = fixture.nativeElement.querySelector('[aria-label="Ordenar productos"]');
    toggle.querySelectorAll('button')[1].click();
    fixture.detectChanges();
    expect(component.productsBy()).toBe('profit');
    expect(component.products()).toBe(dashboard.topProductsByProfit);
    expect(fixture.nativeElement.textContent).toContain('Gastos por categoría');
    expect(fixture.nativeElement.textContent).toContain('Bs 4,350.00');
  });

  it('DSH-04: muestra la caja abierta, los saldos y las deudas', () => {
    const text = fixture.nativeElement.textContent;
    expect(text).toContain('Abierta · caja N.º 412');
    expect(text).toContain('Bs 1,200.00');
    expect(text).toContain('Bisa');
    expect(text).toContain('Bs 5,000.00');
    expect(text).toContain('Por pagar a proveedores');
    expect(text).toContain('Bs 200.25');
  });

  it('DSH-01: el rango propio pide las fechas elegidas', () => {
    component.selectPeriod('RANGE');
    fixture.detectChanges();
    component.onFromDate('2026-09-01');
    expect(dashboardService.getDashboard).toHaveBeenLastCalledWith({ from: '2026-09-01', to: '2026-10-03' });
  });

  it('muestra el error del servidor', () => {
    dashboardService.getDashboard.mockReturnValue(throwError(() => ({
      error: { message: 'El rango del reporte no puede superar un año.' }
    })));
    component.load();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('El rango del reporte no puede superar un año.');
    expect(component.dashboard()).toBeNull();
  });

  it('DSH-04: el inicio es el dashboard para quien lo tiene y el POS para los demás', () => {
    const injectorWith = (allowed: boolean) => Injector.create({
      providers: [{ provide: AuthService, useValue: { hasPermission: (key: string) => allowed && key === 'DASHBOARD' } }]
    });
    expect(runInInjectionContext(injectorWith(true), () => homeRedirect())).toBe('dashboard');
    expect(runInInjectionContext(injectorWith(false), () => homeRedirect())).toBe('pos');
  });

  it('CAJ-13: en Ver tabla, un día marcado dice «Sin atención» con su comentario, distinto de un día sin ventas', () => {
    const date = dashboard.days[0].date;
    dashboardService.getDashboard.mockReturnValue(of({ ...dashboard, noAttentionDays: [{ date, comment: 'Feriado' }] }));
    component.ngOnInit();
    fixture.detectChanges();

    const rows = Array.from(fixture.nativeElement.querySelectorAll('.table-view tbody tr')) as HTMLElement[];
    expect(rows[0].textContent).toContain('Sin atención');
    expect(rows[0].textContent).toContain('Feriado');
    expect(rows.slice(1).some(row => row.textContent?.includes('Sin atención'))).toBe(false);
  });

  it('CAJ-12: la caja actual de un día anterior lleva la marca; la de hoy no', () => {
    const withCash = (operationDate: string) => ({
      ...dashboard,
      now: { ...dashboard.now, cash: { ...dashboard.now.cash, openedAt: '2026-10-30T13:00:00Z', operationDate } }
    });
    dashboardService.getDashboard.mockReturnValue(of(withCash('2026-10-28')));
    component.ngOnInit();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('app-cash-day-mark').textContent).toContain('Día 28/10 · abierta el 30/10');

    dashboardService.getDashboard.mockReturnValue(of(withCash(storeToday())));
    component.ngOnInit();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.cash-day-mark')).toBeNull();
  });

  it('CAJ-12: «Caja actual» de hoy muestra la marca al pasar la medianoche de La Paz, sin recargar', async () => {
    // 23:59:30 del 02-11 en La Paz (UTC-4)
    vi.useFakeTimers({ now: new Date('2026-11-03T03:59:30Z') });
    try {
      dashboardService.getDashboard.mockReturnValue(of({
        ...dashboard,
        now: { ...dashboard.now, cash: { ...dashboard.now.cash, openedAt: '2026-11-02T13:00:00Z', operationDate: '2026-11-02' } }
      }));
      // Created under the fake clock: the fixture of beforeEach already has a mark on the real one
      const page = TestBed.createComponent(DashboardComponent);
      page.autoDetectChanges();
      await vi.advanceTimersByTimeAsync(0);
      expect(page.nativeElement.querySelector('.cash-day-mark')).toBeNull();

      await vi.advanceTimersByTimeAsync(60_100);
      expect(page.nativeElement.querySelector('.cash-day-mark').textContent).toContain('Día 02/11 · abierta el 02/11');
    } finally {
      vi.useRealTimers();
    }
  });
});

describe('DashboardComponent instalado (MOV-02)', () => {
  const today = storeToday();
  const now = {
    cash: { open: false }, accounts: [{ id: 'a1', name: 'Caja', color: null, balance: 1500 }],
    totalBalance: 1500, payables: 320, creditorsDebt: 200.25, receivables: 104
  };
  const zero = { sales: 0, cost: 0, profit: 0, margin: null, tickets: 0, averageTicket: null, atCostSales: 0, atCostTotal: 0, consumptionTotal: 0 };
  const dashboard = {
    from: today, to: today, previousFrom: today, previousTo: today, current: zero, previous: zero, days: [], previousDays: [],
    payments: { cash: 0, qr: 0, credit: 0, historicalTotal: 0, historicalSales: 0 },
    topProductsBySales: [], topProductsByProfit: [], topSuppliersBySales: [], topSuppliersByProfit: [],
    expenses: [], expenseTotal: 0, now, noAttentionDays: []
  } as unknown as Dashboard;

  function create(installed: boolean) {
    const getDashboard = vi.fn().mockReturnValue(of(dashboard));
    TestBed.configureTestingModule({
      imports: [DashboardComponent],
      providers: [
        { provide: DashboardService, useValue: { getDashboard } },
        { provide: INSTALLED_APP, useValue: installed }
      ]
    });
    const fixture = TestBed.createComponent(DashboardComponent);
    fixture.detectChanges();
    return { fixture, getDashboard, element: fixture.nativeElement as HTMLElement };
  }

  const titles = (element: HTMLElement) => Array.from(element.querySelectorAll('h2')).map(h => h.textContent?.trim());
  const follows = (first: Element, second: Element) =>
    (first.compareDocumentPosition(second) & Node.DOCUMENT_POSITION_FOLLOWING) !== 0;

  it('CA-04: abre en Hoy y «Ahora mismo» va antes de los filtros y los indicadores', () => {
    const { fixture, getDashboard, element } = create(true);
    expect(getDashboard).toHaveBeenCalledWith({ from: today, to: today });
    expect(fixture.componentInstance.period()).toBe('TODAY');
    const nowBlock = element.querySelector('section.now')!;
    expect(nowBlock.querySelector('h2')?.textContent).toContain('Ahora mismo');
    expect(follows(nowBlock, element.querySelector('.filters')!)).toBe(true);
    expect(follows(nowBlock, element.querySelector('.kpis')!)).toBe(true);
    expect(nowBlock.querySelector('#cash-title')).toBeTruthy();
    expect(nowBlock.querySelector('#balances-title')).toBeTruthy();
  });

  it('CA-04: caja y saldos se muestran una sola vez', () => {
    const { element } = create(true);
    expect(titles(element).filter(t => t === 'Caja actual')).toHaveLength(1);
    expect(titles(element).filter(t => t === 'Saldos y deudas')).toHaveLength(1);
  });

  it('CA-05: en el navegador abre en Mes, sin «Ahora mismo» y con el orden de siempre', () => {
    const { fixture, getDashboard, element } = create(false);
    expect(getDashboard).toHaveBeenCalledWith({ from: `${today.slice(0, 7)}-01`, to: today });
    expect(fixture.componentInstance.period()).toBe('MONTH');
    expect(element.querySelector('section.now')).toBeNull();
    expect(titles(element)).toEqual(['Ventas por día', 'Cómo se cobró', 'Caja actual', 'Top productos',
      'Top proveedores', 'Gastos por categoría', 'Saldos y deudas']);
  });

  it('CA-06: dentro de la app, Semana y Mes piden el mismo período que en el navegador', () => {
    const { fixture, getDashboard, element } = create(true);
    const button = (label: string) => Array.from(element.querySelectorAll<HTMLButtonElement>('.filter-pill'))
      .find(b => b.textContent?.trim() === label)!;
    button('Semana').click();
    fixture.detectChanges();
    expect(getDashboard).toHaveBeenLastCalledWith(periodRange('WEEK', today));
    button('Mes').click();
    fixture.detectChanges();
    expect(getDashboard).toHaveBeenLastCalledWith(periodRange('MONTH', today));
  });

  it('MOV-02: al volver de segundo plano recarga el período elegido, solo en la app', () => {
    const installed = create(true);
    installed.getDashboard.mockClear();
    document.dispatchEvent(new Event('visibilitychange'));
    expect(installed.getDashboard).toHaveBeenCalledWith({ from: today, to: today });
    installed.fixture.destroy();

    TestBed.resetTestingModule();
    const browser = create(false);
    browser.getDashboard.mockClear();
    document.dispatchEvent(new Event('visibilitychange'));
    expect(browser.getDashboard).not.toHaveBeenCalled();
  });

  it('MOV-02: oculta no recarga, y en Rango vuelve a pedir el mismo rango', () => {
    const { fixture, getDashboard } = create(true);
    getDashboard.mockClear();
    const visibility = vi.spyOn(document, 'visibilityState', 'get').mockReturnValue('hidden');
    document.dispatchEvent(new Event('visibilitychange'));
    expect(getDashboard).not.toHaveBeenCalled();
    visibility.mockRestore();

    fixture.componentInstance.selectPeriod('RANGE');
    fixture.componentInstance.fromDate.set('2026-10-01');
    fixture.componentInstance.toDate.set('2026-10-15');
    document.dispatchEvent(new Event('visibilitychange'));
    expect(fixture.componentInstance.period()).toBe('RANGE');
    expect(getDashboard).toHaveBeenCalledWith({ from: '2026-10-01', to: '2026-10-15' });
  });

  it.each([0, 502, 503, 504])('MOV-03: si al volver el servidor no responde (%i) recarga para mostrar la página sin conexión', status => {
    const { fixture, getDashboard } = create(true);
    const reload = vi.spyOn(fixture.componentInstance, 'reloadPage').mockImplementation(() => undefined);
    getDashboard.mockReturnValue(throwError(() => new HttpErrorResponse({ status })));
    document.dispatchEvent(new Event('visibilitychange'));
    expect(reload).toHaveBeenCalledOnce();
    expect(fixture.componentInstance.dashboard()).toEqual(dashboard);
  });

  it('MOV-03: un error del servidor al volver, o sin conexión al elegir un período, sigue mostrando su mensaje', () => {
    const { fixture, getDashboard } = create(true);
    const reload = vi.spyOn(fixture.componentInstance, 'reloadPage').mockImplementation(() => undefined);
    getDashboard.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
    document.dispatchEvent(new Event('visibilitychange'));
    expect(fixture.componentInstance.errorMessage()).toBeTruthy();

    getDashboard.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 0 })));
    fixture.componentInstance.selectPeriod('WEEK');
    expect(fixture.componentInstance.errorMessage()).toBeTruthy();
    expect(reload).not.toHaveBeenCalled();
  });

  it('CA-06: una respuesta lenta de otro período no reemplaza a la recién elegida', () => {
    const { fixture, getDashboard } = create(true);
    const slowToday = new Subject<Dashboard>();
    getDashboard.mockReturnValueOnce(slowToday);
    document.dispatchEvent(new Event('visibilitychange'));
    const month = { ...dashboard, from: `${today.slice(0, 7)}-01` };
    getDashboard.mockReturnValueOnce(of(month));
    fixture.componentInstance.selectPeriod('MONTH');
    slowToday.next(dashboard);
    expect(fixture.componentInstance.dashboard()).toEqual(month);
  });

  it('MOV-03: una petición pendiente al salir del Dashboard no recarga la pantalla a la que se fue', () => {
    const { fixture, getDashboard } = create(true);
    const reload = vi.spyOn(fixture.componentInstance, 'reloadPage').mockImplementation(() => undefined);
    const pending = new Subject<Dashboard>();
    getDashboard.mockReturnValue(pending);
    document.dispatchEvent(new Event('visibilitychange'));
    fixture.destroy();
    pending.error(new HttpErrorResponse({ status: 0 }));
    expect(reload).not.toHaveBeenCalled();
  });

  it('MOV-03: si al volver la petición queda colgada, a los 10 s recarga', () => {
    vi.useFakeTimers();
    try {
      const { fixture, getDashboard } = create(true);
      const reload = vi.spyOn(fixture.componentInstance, 'reloadPage').mockImplementation(() => undefined);
      getDashboard.mockReturnValue(new Subject<Dashboard>());
      document.dispatchEvent(new Event('visibilitychange'));
      vi.advanceTimersByTime(9999);
      expect(reload).not.toHaveBeenCalled();
      vi.advanceTimersByTime(1);
      expect(reload).toHaveBeenCalledOnce();
    } finally {
      vi.useRealTimers();
    }
  });

  it.each([
    ['al entrar', (c: DashboardComponent) => c.ngOnInit()],
    ['al elegir un período', (c: DashboardComponent) => c.selectPeriod('WEEK')],
    ['al cambiar el rango', (c: DashboardComponent) => c.onFromDate('2026-10-01')]
  ])('CA-11: instalada, si la API no responde en 10 s %s avisa qué revisar, sin recargar', (_, act) => {
    vi.useFakeTimers();
    try {
      const { fixture, getDashboard } = create(true);
      const component = fixture.componentInstance;
      const reload = vi.spyOn(component, 'reloadPage').mockImplementation(() => undefined);
      getDashboard.mockReturnValue(new Subject<Dashboard>());
      act(component);
      vi.advanceTimersByTime(9999);
      expect(component.errorMessage()).toBeNull();
      expect(component.isLoading()).toBe(true);
      vi.advanceTimersByTime(1);
      expect(component.errorMessage()).toBe('No se pudo conectar. Revisa tu internet y que Tailscale esté encendido.');
      expect(component.isLoading()).toBe(false);
      expect(reload).not.toHaveBeenCalled();
    } finally {
      vi.useRealTimers();
    }
  });

  it('CA-12: instalada, la respuesta que llega antes de los 10 s se ve sin aviso', () => {
    vi.useFakeTimers();
    try {
      const { fixture, getDashboard } = create(true);
      const component = fixture.componentInstance;
      const answer = new Subject<Dashboard>();
      getDashboard.mockReturnValue(answer);
      component.selectPeriod('WEEK');
      vi.advanceTimersByTime(9999);
      answer.next(dashboard);
      vi.advanceTimersByTime(1);
      expect(component.dashboard()).toEqual(dashboard);
      expect(component.errorMessage()).toBeNull();
    } finally {
      vi.useRealTimers();
    }
  });

  it('CA-13: en el navegador sigue sin límite de tiempo', () => {
    vi.useFakeTimers();
    try {
      const { fixture, getDashboard } = create(false);
      const component = fixture.componentInstance;
      getDashboard.mockReturnValue(new Subject<Dashboard>());
      component.selectPeriod('WEEK');
      vi.advanceTimersByTime(60000);
      expect(component.errorMessage()).toBeNull();
      expect(component.isLoading()).toBe(true);
    } finally {
      vi.useRealTimers();
    }
  });

  it('MOV-03: instalada, sin conexión al cargar dice qué revisar; en el navegador sigue el mensaje de siempre', () => {
    const installed = create(true);
    installed.getDashboard.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 502 })));
    installed.fixture.componentInstance.selectPeriod('WEEK');
    expect(installed.fixture.componentInstance.errorMessage()).toContain('Tailscale');
    installed.fixture.destroy();

    TestBed.resetTestingModule();
    const browser = create(false);
    browser.getDashboard.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 502 })));
    browser.fixture.componentInstance.selectPeriod('WEEK');
    expect(browser.fixture.componentInstance.errorMessage()).toBe('Error al cargar el dashboard. Intente nuevamente.');
  });
});
