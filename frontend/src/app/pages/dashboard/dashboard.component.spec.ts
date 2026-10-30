import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { Injector, runInInjectionContext } from '@angular/core';
import { DashboardComponent, changePercent, periodRange, storeToday } from './dashboard.component';
import { DashboardService } from '../../core/dashboard/dashboard.service';
import { Dashboard, DashboardIndicators } from '../../core/dashboard/models/dashboard.models';
import { AuthService } from '../../core/auth/auth.service';
import { homeRedirect } from '../../app.routes';

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
    payments: { cash: 1500, qr: 1245, credit: 104 },
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
    }
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
});
