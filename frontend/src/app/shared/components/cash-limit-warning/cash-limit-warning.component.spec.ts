import { ComponentFixture, TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { By } from '@angular/platform-browser';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { CashLimitWarningComponent } from './cash-limit-warning.component';
import { CashService } from '../../../core/cash/cash.service';

describe('CashLimitWarningComponent', () => {
  let fixture: ComponentFixture<CashLimitWarningComponent>;
  let component: CashLimitWarningComponent;
  let mockCashService: any;

  beforeEach(async () => {
    mockCashService = {
      currentSession: signal<any>({ id: 's1', expectedCash: 120.5 }),
      getCurrentSession: vi.fn().mockReturnValue(of({ id: 's1', expectedCash: 120.5 }))
    };
    await TestBed.configureTestingModule({
      imports: [CashLimitWarningComponent],
      providers: [{ provide: CashService, useValue: mockCashService }]
    }).compileComponents();
    fixture = TestBed.createComponent(CashLimitWarningComponent);
    component = fixture.componentInstance;
  });

  const warning = () => fixture.debugElement.query(By.css('.cash-limit-warning'));

  it('pide la caja actual al mostrarse y avisa solo si el monto supera el esperado', () => {
    component.amount = 120.5;
    fixture.detectChanges();
    expect(mockCashService.getCurrentSession).toHaveBeenCalled();
    expect(warning()).toBeNull();

    component.amount = '120.51';
    fixture.detectChanges();
    expect(warning().nativeElement.textContent).toContain('120.50');
  });

  it('no avisa si la salida no es de Caja o no hay caja abierta', () => {
    component.amount = 500;
    component.fromCash = false;
    fixture.detectChanges();
    expect(warning()).toBeNull();

    component.fromCash = true;
    mockCashService.currentSession.set(null);
    mockCashService.getCurrentSession.mockReturnValue(throwError(() => ({ status: 404 })));
    fixture.detectChanges();
    expect(warning()).toBeNull();
  });
});
