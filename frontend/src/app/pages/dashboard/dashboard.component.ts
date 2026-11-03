import { Component, HostListener, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule, DOCUMENT } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { Observable, Subscription, TimeoutError, timeout } from 'rxjs';
import { DashboardService } from '../../core/dashboard/dashboard.service';
import { Dashboard, DashboardRankRow } from '../../core/dashboard/models/dashboard.models';
import { CashDayMarkComponent } from '../../shared/components/cash-day-mark/cash-day-mark.component';
import { INSTALLED_APP } from '../../core/pwa/installed-app';

export type DashboardPeriod = 'TODAY' | 'WEEK' | 'MONTH' | 'RANGE';
export type RankBy = 'sales' | 'profit';

interface ChartPoint {
  x: number;
  y: number;
}

const STORE_ZONE = 'America/La_Paz';
const CHART_WIDTH = 640;
const CHART_HEIGHT = 200;
const CHART_TOP = 12;
const CHART_BOTTOM = 188;
// The server cannot be reached: no network, Tailscale off or a gateway error (MOV-03, like public/sw.js)
const CONNECTION_ERRORS = [0, 502, 503, 504];
const CONNECTION_TIMEOUT_MS = 10000;
const CONNECTION_MESSAGE = 'No se pudo conectar. Revisa tu internet y que Tailscale esté encendido.';

function isConnectionError(err: unknown): boolean {
  return err instanceof TimeoutError || (err instanceof HttpErrorResponse && CONNECTION_ERRORS.includes(err.status));
}

/** Today in the store's time zone as YYYY-MM-DD, like the store days of the server. */
export function storeToday(now: Date = new Date()): string {
  return new Intl.DateTimeFormat('en-CA', { timeZone: STORE_ZONE }).format(now);
}

/** The range of a period ending today: today, Monday of this week or the 1st of this month. */
export function periodRange(period: Exclude<DashboardPeriod, 'RANGE'>, today: string): { from: string; to: string } {
  const [year, month, day] = today.split('-').map(Number);
  if (period === 'TODAY') {
    return { from: today, to: today };
  }
  if (period === 'MONTH') {
    return { from: `${today.slice(0, 7)}-01`, to: today };
  }
  const date = new Date(Date.UTC(year, month - 1, day));
  const sinceMonday = (date.getUTCDay() + 6) % 7;
  date.setUTCDate(date.getUTCDate() - sinceMonday);
  return { from: date.toISOString().slice(0, 10), to: today };
}

/** Change in % against the previous period; null when there is nothing to compare with. */
export function changePercent(current: number | null, previous: number | null): number | null {
  if (current == null || previous == null || previous === 0) {
    return null;
  }
  return Math.round(((current - previous) / Math.abs(previous)) * 1000) / 10;
}

// DSH-01 to DSH-04: the period's indicators and charts, and what is true right now (specs/dashboard.md)
@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule, FormsModule, CashDayMarkComponent],
  templateUrl: './dashboard.component.html',
  styleUrls: ['./dashboard.component.css']
})
export class DashboardComponent implements OnInit, OnDestroy {
  private readonly dashboardService = inject(DashboardService);
  private readonly document = inject(DOCUMENT);
  readonly installed = inject(INSTALLED_APP);
  private loadSubscription?: Subscription;

  readonly periods: { value: DashboardPeriod; label: string }[] = [
    { value: 'TODAY', label: 'Hoy' },
    { value: 'WEEK', label: 'Semana' },
    { value: 'MONTH', label: 'Mes' },
    { value: 'RANGE', label: 'Rango' }
  ];

  period = signal<DashboardPeriod>('MONTH');
  fromDate = signal<string>('');
  toDate = signal<string>('');
  dashboard = signal<Dashboard | null>(null);
  isLoading = signal<boolean>(false);
  errorMessage = signal<string | null>(null);
  productsBy = signal<RankBy>('sales');
  suppliersBy = signal<RankBy>('sales');
  hoverIndex = signal<number | null>(null);

  readonly chartWidth = CHART_WIDTH;
  readonly chartHeight = CHART_HEIGHT;

  readonly products = computed(() => {
    const d = this.dashboard();
    return d ? (this.productsBy() === 'sales' ? d.topProductsBySales : d.topProductsByProfit) : [];
  });

  readonly suppliers = computed(() => {
    const d = this.dashboard();
    return d ? (this.suppliersBy() === 'sales' ? d.topSuppliersBySales : d.topSuppliersByProfit) : [];
  });

  readonly paymentTotal = computed(() => {
    const p = this.dashboard()?.payments;
    return p ? p.cash + p.qr + p.credit : 0;
  });

  // CAJ-13: comment of each day without attention, so the table tells it from a day without sales
  readonly noAttentionComments = computed(() =>
    new Map((this.dashboard()?.noAttentionDays ?? []).map(day => [day.date, day.comment])));

  readonly expenseMax = computed(() => Math.max(0, ...(this.dashboard()?.expenses ?? []).map(e => e.amount)));

  // Line chart of sales per day: the period in solid and the previous one dashed, on the same scale
  private readonly chartMax = computed(() => {
    const d = this.dashboard();
    if (!d) {
      return 0;
    }
    return niceMax(Math.max(0, ...d.days.map(p => p.sales), ...d.previousDays.map(p => p.sales)));
  });

  readonly gridLines = computed(() => {
    const max = this.chartMax();
    return [0, 0.5, 1].map(f => ({ value: max * f, y: this.yOf(max * f) }));
  });

  readonly currentPoints = computed(() => this.points(this.dashboard()?.days.map(p => p.sales) ?? []));
  readonly previousPoints = computed(() => this.points(this.dashboard()?.previousDays.map(p => p.sales) ?? []));
  readonly currentPath = computed(() => toPath(this.currentPoints()));
  readonly previousPath = computed(() => toPath(this.previousPoints()));

  readonly hoverSlots = computed(() => {
    const count = this.dashboard()?.days.length ?? 0;
    const width = count > 0 ? CHART_WIDTH / count : 0;
    return Array.from({ length: count }, (_, i) => ({ index: i, x: i * width, width }));
  });

  readonly hovered = computed(() => {
    const i = this.hoverIndex();
    const d = this.dashboard();
    if (i == null || !d || !d.days[i]) {
      return null;
    }
    const point = this.currentPoints()[i];
    return {
      day: d.days[i],
      previous: d.previousDays[i] ?? null,
      leftPercent: (point.x / CHART_WIDTH) * 100,
      x: point.x,
      y: point.y
    };
  });

  ngOnInit(): void {
    this.selectPeriod(this.installed ? 'TODAY' : 'MONTH');
  }

  // A request still pending after leaving the Dashboard must not reload the screen the user went to (MOV-03)
  ngOnDestroy(): void {
    this.loadSubscription?.unsubscribe();
  }

  // MOV-02: a phone resumes the app without opening it again, and it must not keep showing an old «Ahora mismo»
  @HostListener('document:visibilitychange')
  onVisibilityChange(): void {
    if (this.installed && this.document.visibilityState === 'visible') {
      const period = this.period();
      if (period !== 'RANGE') {
        this.setRange(period);
      }
      this.load(true);
    }
  }

  selectPeriod(period: DashboardPeriod): void {
    this.period.set(period);
    if (period !== 'RANGE') {
      this.setRange(period);
      this.load();
    }
  }

  private setRange(period: Exclude<DashboardPeriod, 'RANGE'>): void {
    const range = periodRange(period, storeToday());
    this.fromDate.set(range.from);
    this.toDate.set(range.to);
  }

  onFromDate(value: string): void {
    this.fromDate.set(value);
    this.load();
  }

  onToDate(value: string): void {
    this.toDate.set(value);
    this.load();
  }

  load(resuming = false): void {
    // A slower answer of a previous period must not replace the one just chosen
    this.loadSubscription?.unsubscribe();
    this.isLoading.set(true);
    this.errorMessage.set(null);
    this.hoverIndex.set(null);
    const request: Observable<Dashboard> =
      this.dashboardService.getDashboard({ from: this.fromDate() || null, to: this.toDate() || null });
    this.loadSubscription = (this.installed ? request.pipe(timeout(CONNECTION_TIMEOUT_MS)) : request).subscribe({
      next: dashboard => {
        this.dashboard.set(dashboard);
        this.fromDate.set(dashboard.from);
        this.toDate.set(dashboard.to);
        this.isLoading.set(false);
      },
      error: err => {
        // MOV-03: opening the page again lets the service worker show what to check
        if (resuming && isConnectionError(err)) {
          this.reloadPage();
          return;
        }
        this.dashboard.set(null);
        this.errorMessage.set(this.installed && isConnectionError(err)
          ? CONNECTION_MESSAGE
          : err?.error?.message || 'Error al cargar el dashboard. Intente nuevamente.');
        this.isLoading.set(false);
      }
    });
  }

  reloadPage(): void {
    this.document.location.reload();
  }

  readonly changePercent = changePercent;

  barPercent(value: number, rows: DashboardRankRow[], by: RankBy): number {
    const max = Math.max(0, ...rows.map(row => row[by]));
    return max > 0 ? Math.max(0, (value / max) * 100) : 0;
  }

  percentOf(value: number, total: number): number {
    return total > 0 ? (value / total) * 100 : 0;
  }

  // Store days as text, without going through the browser's time zone
  dayLabel(date: string | null | undefined): string {
    if (!date) {
      return '';
    }
    const [year, month, day] = date.split('-');
    return `${day}/${month}/${year}`;
  }

  shortDayLabel(date: string | null | undefined): string {
    return date ? this.dayLabel(date).slice(0, 5) : '';
  }

  private yOf(value: number): number {
    const max = this.chartMax();
    return max > 0 ? CHART_BOTTOM - (value / max) * (CHART_BOTTOM - CHART_TOP) : CHART_BOTTOM;
  }

  private points(values: number[]): ChartPoint[] {
    const count = this.dashboard()?.days.length ?? values.length;
    const step = count > 0 ? CHART_WIDTH / count : 0;
    return values.slice(0, count).map((value, i) => ({ x: step * i + step / 2, y: this.yOf(value) }));
  }
}

function toPath(points: ChartPoint[]): string {
  return points.map((p, i) => `${i === 0 ? 'M' : 'L'}${p.x.toFixed(1)},${p.y.toFixed(1)}`).join(' ');
}

/** A round top for the axis: 1, 2, 2.5 or 5 times a power of ten. */
function niceMax(value: number): number {
  if (value <= 0) {
    return 0;
  }
  const power = Math.pow(10, Math.floor(Math.log10(value)));
  const step = [1, 2, 2.5, 5, 10].find(s => s * power >= value) ?? 10;
  return step * power;
}
