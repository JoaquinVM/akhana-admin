import { TestBed } from '@angular/core/testing';
import { CashRegisterService } from './cash-register.service';
import { StorageService } from './storage.service';

describe('CashRegisterService', () => {
  let service: CashRegisterService;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({});
    service = TestBed.inject(CashRegisterService);
  });

  it('should initialize with an open register or allow opening one', () => {
    expect(service.isRegisterOpen()).toBeTrue();
    const current = service.getCurrentRegister();
    expect(current?.initialAmount).toBe(200.0);
  });

  it('should calculate counted cash based on Boliviano denominations', () => {
    const breakdown = {
      '200': 1, // 200 Bs
      '100': 2, // 200 Bs
      '50': 2,  // 100 Bs
      '20': 5,  // 100 Bs
      '10': 10, // 100 Bs
      '5': 10,  // 50 Bs
      '2': 20,  // 40 Bs
      '1': 10,  // 10 Bs
      '0.5': 10 // 5 Bs
    };
    // Sum = 200 + 200 + 100 + 100 + 100 + 50 + 40 + 10 + 5 = 805 Bs
    const counted = service.calculateCountedCash(breakdown);
    expect(counted).toBe(805.0);
  });

  it('should close cash register and record difference', () => {
    const breakdown = {
      '200': 1 // 200 Bs
    };
    const closed = service.closeRegister('Juan Pérez', breakdown, 'Cierre normal');
    expect(closed.isOpen).toBeFalse();
    expect(closed.countedCash).toBe(200.0);
    expect(service.isRegisterOpen()).toBeFalse();
  });
});
