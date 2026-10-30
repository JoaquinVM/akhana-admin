import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { DashboardService } from '../../core/dashboard/dashboard.service';
import { Dashboard, DashboardRankRow } from '../../core/dashboard/models/dashboard.models';

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
  imports: [CommonModule, FormsModule],
  templateUrl: './dashboard.component.html',
  styleUrls: ['./dashboard.component.css']
})
export class DashboardComponent implements OnInit {
  private readonly dashboardService = inject(DashboardService);

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
    this.selectPeriod('MONTH');
  }

  selectPeriod(period: DashboardPeriod): void {
    this.period.set(period);
    if (period !== 'RANGE') {
      const range = periodRange(period, storeToday());
      this.fromDate.set(range.from);
      this.toDate.set(range.to);
      this.load();
    }
  }

  onFromDate(value: string): void {
    this.fromDate.set(value);
    this.load();
  }

  onToDate(value: string): void {
    this.toDate.set(value);
    this.load();
  }

  load(): void {
    this.isLoading.set(true);
    this.errorMessage.set(null);
    this.hoverIndex.set(null);
    this.dashboardService.getDashboard({ from: this.fromDate() || null, to: this.toDate() || null }).subscribe({
      next: dashboard => {
        this.dashboard.set(dashboard);
        this.fromDate.set(dashboard.from);
        this.toDate.set(dashboard.to);
        this.isLoading.set(false);
      },
      error: err => {
        this.dashboard.set(null);
        this.errorMessage.set(err?.error?.message || 'Error al cargar el dashboard. Intente nuevamente.');
        this.isLoading.set(false);
      }
    });
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
