import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { PurchasesComponent } from './purchases.component';
import { PurchaseService } from '../../core/purchase/purchase.service';
import { CreditorService } from '../../core/debt/creditor.service';
import { SupplierService } from '../../core/supplier/supplier.service';
import { ProductService } from '../../core/product/product.service';
import { CategoryService } from '../../core/category/category.service';
import { Purchase } from '../../core/purchase/models/purchase.models';

describe('PurchasesComponent', () => {
  let component: PurchasesComponent;
  let fixture: ComponentFixture<PurchasesComponent>;
  let purchaseService: any;

  const purchase = {
    id: 'pur-1', purchaseNumber: 'PED-000001', supplierId: 'sup-1', supplierName: 'Granel', purchaseDate: '2026-10-25',
    status: 'REGISTRADO', itemsTotal: 300, shippingCost: 25, totalAmount: 325, paidAmount: 0, balance: 300,
    paymentStatus: 'POR_PAGAR', consignment: false, comment: null, shippingAccountName: null,
    deliveryType: 'TERMINAL', deliveryStatus: 'PENDIENTE', deliveryDate: null, createdBy: 'operator',
    createdAt: '2026-10-25T12:00:00Z', voidedBy: null, voidedAt: null, voidReason: null, items: [], payments: []
  } as Purchase;

  beforeEach(async () => {
    purchaseService = {
      getPurchases: vi.fn().mockReturnValue(of([purchase])),
      getPaymentAccounts: vi.fn().mockReturnValue(of([])),
      getPurchaseById: vi.fn().mockReturnValue(of(purchase)),
      getPayables: vi.fn().mockReturnValue(of({ totalBalance: 300, suppliers: [] }))
    };
    await TestBed.configureTestingModule({
      imports: [PurchasesComponent],
      providers: [
        { provide: PurchaseService, useValue: purchaseService },
        { provide: SupplierService, useValue: { getSuppliers: vi.fn().mockReturnValue(of([])) } },
        { provide: CreditorService, useValue: { getCreditors: vi.fn().mockReturnValue(of([])) } },
        { provide: ProductService, useValue: { getProducts: vi.fn().mockReturnValue(of([])) } },
        { provide: CategoryService, useValue: { getCategories: vi.fn().mockReturnValue(of([])) } }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(PurchasesComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('lista los pedidos con su estado de pago', () => {
    const text = fixture.nativeElement.textContent;
    expect(text).toContain('PED-000001');
    expect(text).toContain('Por pagar');
  });

  it('COM-04: filtra por estado de pago y proveedor en el servidor', () => {
    component.setStatusFilter('A_CUENTA');
    expect(purchaseService.getPurchases).toHaveBeenLastCalledWith(
      { supplierId: null, status: null, paymentStatus: 'A_CUENTA', deliveryStatus: null, search: '' });
    component.onSupplierFilter('sup-1');
    expect(purchaseService.getPurchases).toHaveBeenLastCalledWith(
      { supplierId: 'sup-1', status: null, paymentStatus: 'A_CUENTA', deliveryStatus: null, search: '' });
  });

  it('COM-07 / COM-09: filtra por entrega y por pedidos anulados', () => {
    component.onDeliveryFilter('ENTREGADO');
    component.setStatusFilter('ANULADO');
    expect(purchaseService.getPurchases).toHaveBeenLastCalledWith(
      { supplierId: null, status: 'ANULADO', paymentStatus: null, deliveryStatus: 'ENTREGADO', search: '' });
  });

  it('COM-07: muestra el tipo y estado de entrega', () => {
    const text = fixture.nativeElement.textContent;
    expect(text).toContain('Terminal');
    expect(text).toContain('Pendiente');
  });

  it('COM-08: la pestaña Por pagar carga los saldos por proveedor', () => {
    component.setTab('PAYABLES');
    fixture.detectChanges();
    expect(purchaseService.getPayables).toHaveBeenCalledWith(null);
    expect(fixture.nativeElement.textContent).toContain('Total por pagar');
  });

  it('actualiza la fila y la vista por pagar tras un pago o una anulación', () => {
    component.onUpdated({ ...purchase, paidAmount: 300, balance: 0, paymentStatus: 'PAGADO' });
    expect(component.purchases()[0].paymentStatus).toBe('PAGADO');
    expect(component.payablesRefresh()).toBe(1);
  });

  it('abre el detalle del pedido recién registrado', () => {
    component.onPurchaseSaved(purchase);
    expect(component.isFormOpen()).toBe(false);
    expect(component.detailPurchaseId()).toBe('pur-1');
  });
});
