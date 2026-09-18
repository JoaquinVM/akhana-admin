import { TestBed } from '@angular/core/testing';
import { PosService } from './pos.service';
import { StorageService } from './storage.service';
import { CashRegisterService } from './cash-register.service';
import { AuthService } from './auth.service';
import { Product } from '../models/domain.model';

describe('PosService', () => {
  let service: PosService;
  let cashRegisterService: CashRegisterService;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({});
    service = TestBed.inject(PosService);
    cashRegisterService = TestBed.inject(CashRegisterService);
  });

  it('should initialize with design cart items and Bolivianos currency', () => {
    const items = service.cartItems;
    expect(items.length).toBeGreaterThan(0);
    const totals = service.calculateTotals();
    expect(totals.netTotal).toBeGreaterThan(0);
  });

  it('should calculate gross subtotals and discounts accurately', () => {
    service.clearCart();
    const mockProduct: Product = {
      id: 'test-1',
      code: 'TST-001',
      name: 'Test Product',
      groupId: 'g1',
      providerId: 'p1',
      tagIds: [],
      cost: 10,
      salePrice: 20,
      saleType: 'unit',
      isFrequent: false,
      isActive: true,
      createdAt: ''
    };

    service.addProductToCart(mockProduct, 2);
    expect(service.cartItems.length).toBe(1);
    expect(service.cartItems[0].grossSubtotal).toBe(40.0);

    // Apply valid discount (Bs 3.00)
    service.updateItemDiscount(0, 3.0);
    expect(service.cartItems[0].discount).toBe(3.0);
    expect(service.cartItems[0].finalSubtotal).toBe(37.0);

    // Totals
    const totals = service.calculateTotals();
    expect(totals.grossSubtotal).toBe(40.0);
    expect(totals.itemsDiscountTotal).toBe(3.0);
    expect(totals.netTotal).toBe(37.0);
  });

  it('should reject discounts exceeding maximum allowed limit', () => {
    service.clearCart();
    const mockProduct: Product = {
      id: 'test-2',
      code: 'TST-002',
      name: 'Expensive Item',
      groupId: 'g1',
      providerId: 'p1',
      tagIds: [],
      cost: 50,
      salePrice: 100,
      saleType: 'unit',
      isFrequent: false,
      isActive: true,
      createdAt: ''
    };

    service.addProductToCart(mockProduct, 1);
    // Config max item discount is 5 Bs. Trying 10 Bs should throw.
    expect(() => service.updateItemDiscount(0, 10.0)).toThrow();
  });

  it('should calculate weighed products by grams accurately (base per 100g)', () => {
    service.clearCart();
    const weighedProduct: Product = {
      id: 'test-3',
      code: 'TST-003',
      name: 'Queso Weighed',
      groupId: 'g1',
      providerId: 'p1',
      tagIds: [],
      cost: 2.0,
      salePrice: 4.0, // 4.0 Bs por 100g
      saleType: 'weight',
      isFrequent: false,
      isActive: true,
      createdAt: ''
    };

    // 250 grams -> (250/100) * 4.0 = 10.0 Bs
    service.addProductToCart(weighedProduct, 250);
    expect(service.cartItems[0].grossSubtotal).toBe(10.0);
  });
});
