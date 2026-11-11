import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Subject, of, throwError } from 'rxjs';
import { PurchasesComponent } from './purchases.component';
import { PurchaseService } from '../../core/purchase/purchase.service';
import { PurchaseReadingService } from '../../core/purchase/purchase-reading.service';
import { CreditorService } from '../../core/debt/creditor.service';
import { SupplierService } from '../../core/supplier/supplier.service';
import { ProductService } from '../../core/product/product.service';
import { CategoryService } from '../../core/category/category.service';
import { Purchase } from '../../core/purchase/models/purchase.models';

describe('PurchasesComponent', () => {
  let component: PurchasesComponent;
  let fixture: ComponentFixture<PurchasesComponent>;
  let purchaseService: any;
  let readingService: any;

  const purchase = {
    id: 'pur-1', purchaseNumber: 'PED-000001', supplierId: 'sup-1', supplierName: 'Granel', purchaseDate: '2026-10-25',
    status: 'REGISTRADO', itemsTotal: 300, shippingCost: 25, totalAmount: 325, paidAmount: 0, balance: 300,
    paymentStatus: 'POR_PAGAR', consignment: false, comment: null, shippingAccountId: null, updatedBy: null, updatedAt: null, revisions: [], shippingAccountName: null,
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
    readingService = {
      getStatus: vi.fn().mockReturnValue(of({ configured: true, capReached: false, spentUsd: 1.5, capUsd: 10 })),
      readText: vi.fn(),
      readPhoto: vi.fn(),
      uploadPhoto: vi.fn().mockReturnValue(of({ photoId: 'photo-1' })),
      getPhotoImage: vi.fn().mockReturnValue(of(new Blob()))
    };
    await TestBed.configureTestingModule({
      imports: [PurchasesComponent],
      providers: [
        { provide: PurchaseService, useValue: purchaseService },
        { provide: PurchaseReadingService, useValue: readingService },
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

  it('dibuja de a 100 pedidos y «Mostrar más» agrega los siguientes', () => {
    const many = Array.from({ length: 230 }, (_, i) => ({ ...purchase, id: 'pur-' + i, purchaseNumber: 'PED-' + i }));
    purchaseService.getPurchases.mockReturnValue(of(many));
    component.loadPurchases();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelectorAll('tbody tr').length).toBe(100);
    expect(fixture.nativeElement.querySelector('.show-more-row').textContent).toContain('Mostrar 100 más (130 sin mostrar)');

    component.showMore();
    component.showMore();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelectorAll('tbody tr').length).toBe(230);
    expect(fixture.nativeElement.querySelector('.show-more-row')).toBeNull();
  });

  it('COM-26: una respuesta vieja que llega tarde no pisa la búsqueda más nueva', () => {
    const slowAll = new Subject<Purchase[]>();
    const fastFiltered = new Subject<Purchase[]>();
    purchaseService.getPurchases.mockReturnValueOnce(slowAll).mockReturnValueOnce(fastFiltered);
    const other = { ...purchase, id: 'pur-2', purchaseNumber: 'PED-000002' };

    component.onSearch('h');
    component.onSearch('harinas');
    fastFiltered.next([purchase]);
    slowAll.next([purchase, other]);
    fixture.detectChanges();

    expect(component.purchases().map((p) => p.id)).toEqual(['pur-1']);
    expect(component.isLoading()).toBe(false);
  });

  it('al cambiar un filtro mantiene las filas mientras carga', () => {
    purchaseService.getPurchases.mockReturnValue(new Subject());
    component.setStatusFilter('PAGADO');
    fixture.detectChanges();
    expect(component.isLoading()).toBe(true);
    expect(fixture.nativeElement.textContent).not.toContain('Cargando pedidos');
    expect(fixture.nativeElement.querySelectorAll('tbody tr').length).toBe(1);
  });

  describe('COM-20: Desde foto y Desde texto', () => {
    const buttons = () => Array.from(fixture.nativeElement.querySelectorAll('.header-actions button')) as HTMLButtonElement[];
    const draft = { readingId: 'read-1', source: 'PHOTO', lines: [], pendingPurchases: [] } as any;
    const file = new File(['x'], 'factura.jpg', { type: 'image/jpeg' });

    function withStatus(status: object): void {
      readingService.getStatus.mockReturnValue(of(status));
      component.ngOnInit();
      fixture.detectChanges();
    }

    it('muestra los botones y al administrador el gasto del mes', () => {
      expect(buttons().map(b => b.textContent!.trim())).toEqual(['Desde foto', 'Desde texto', 'Nuevo pedido']);
      expect(fixture.nativeElement.textContent).toContain('US$ 1.50 de US$ 10.00');
    });

    it('COM-20: sin clave no aparecen y el pedido a mano sigue igual', () => {
      withStatus({ configured: false, capReached: false, spentUsd: null, capUsd: null });
      expect(buttons().map(b => b.textContent!.trim())).toEqual(['Nuevo pedido']);
    });

    it('CA-25: al llegar al tope se apagan, lo dice y el pedido a mano sigue', () => {
      withStatus({ configured: true, capReached: true, spentUsd: null, capUsd: null });
      expect(buttons().map(b => b.disabled)).toEqual([true, true, false]);
      expect(fixture.nativeElement.textContent).toContain('Se llegó al tope de este mes');
      expect(fixture.nativeElement.textContent).not.toContain('US$');
    });

    it('CA-21: la foto se sube recién cuando el pedido quedó registrado', () => {
      component.onDraftRead({ draft, file, completePurchaseId: null });
      expect(component.isFormOpen()).toBe(true);
      expect(readingService.uploadPhoto).not.toHaveBeenCalled();

      component.onPurchaseSaved(purchase);
      expect(readingService.uploadPhoto).toHaveBeenCalledWith('pur-1', file);
      expect(component.detailPurchaseId()).toBe('pur-1');
    });

    it('CA-22: si no se registra, la foto no se sube', () => {
      component.onDraftRead({ draft, file, completePurchaseId: null });
      component.closeForm();
      component.openForm();
      component.onPurchaseSaved(purchase);
      expect(readingService.uploadPhoto).not.toHaveBeenCalled();
    });

    it('CA-21: si la foto falla, el pedido sigue registrado y abre su detalle para agregarla', () => {
      readingService.uploadPhoto.mockReturnValue(throwError(() => ({ error: { message: 'No se pudo guardar la foto.' } })));
      component.onDraftRead({ draft, file, completePurchaseId: null });
      component.onPurchaseSaved(purchase);
      expect(component.detailPurchaseId()).toBe('pur-1');
    });

    it('CA-12: completar un pedido pendiente abre su llegada con la lectura', () => {
      component.onDraftRead({ draft, file, completePurchaseId: 'pur-1' });
      expect(purchaseService.getPurchaseById).toHaveBeenCalledWith('pur-1');
      expect(component.editPurchase()).toEqual(purchase);
      expect(component.formArrival()).toBe(true);
      expect(component.formDraft()).toEqual(draft);
    });

    it('CA-12: desde el formulario, completar un pedido pendiente conserva la lectura y la foto', () => {
      component.onDraftRead({ draft, file, completePurchaseId: null });
      component.onCompletePending('pur-1');

      expect(component.editPurchase()).toEqual(purchase);
      expect(component.formArrival()).toBe(true);
      expect(component.formDraft()).toEqual(draft);
      component.onPurchaseSaved(purchase);
      expect(readingService.uploadPhoto).toHaveBeenCalledWith('pur-1', file);
    });

    it('CA-09: la llegada con foto lee sobre ese pedido; a mano abre el formulario sin lectura', () => {
      component.onArrived({ purchase, withPhoto: true });
      expect(component.readingMode()).toBe('PHOTO');
      expect(component.readingFor()).toEqual(purchase);
      component.onDraftRead({ draft, file, completePurchaseId: null });
      expect(component.editPurchase()).toEqual(purchase);
      expect(component.formArrival()).toBe(true);

      component.closeForm();
      component.onArrived({ purchase, withPhoto: false });
      expect(component.editPurchase()).toEqual(purchase);
      expect(component.formDraft()).toBeNull();
      expect(component.formArrival()).toBe(true);
    });

    it('CA-24: leer mal y llenar a mano en una llegada sigue siendo la llegada', () => {
      component.onArrived({ purchase, withPhoto: true });
      component.onFillByHand();
      expect(component.formArrival()).toBe(true);
      expect(component.editPurchase()).toEqual(purchase);
    });

    it('CA-08: el pedido por confirmar se ve como estimado en la lista', () => {
      purchaseService.getPurchases.mockReturnValue(of([{ ...purchase, costToConfirm: true }]));
      component.loadPurchases();
      fixture.detectChanges();
      expect(fixture.nativeElement.textContent).toContain('Estimado · costo por confirmar');
    });
  });
});
