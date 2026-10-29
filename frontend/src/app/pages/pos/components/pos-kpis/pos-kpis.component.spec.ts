import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { PosKpisComponent } from './pos-kpis.component';

describe('PosKpisComponent', () => {
  let fixture: ComponentFixture<PosKpisComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [PosKpisComponent] }).compileComponents();
    fixture = TestBed.createComponent(PosKpisComponent);
    fixture.componentRef.setInput('session', { openingAmount: 100, totalSalesCash: 50, totalSalesQr: 25 } as any);
    fixture.componentRef.setInput('expensesTotal', 20);
    fixture.componentRef.setInput('expectedCash', 130);
    fixture.detectChanges();
  });

  it('muestra las cinco cifras de la caja abierta', () => {
    const cards = fixture.debugElement.queryAll(By.css('.kpi-card')).map((c) => c.nativeElement.textContent);
    expect(cards.length).toBe(5);
    expect(cards[0]).toContain('100.00');
    expect(cards[1]).toContain('50.00');
    expect(cards[2]).toContain('25.00');
    expect(cards[3]).toContain('20.00');
    expect(cards[4]).toContain('130.00');
  });
});
