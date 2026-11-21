import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { of } from 'rxjs';
import { SaleService } from './sale.service';
import { CashService } from '../cash/cash.service';
import { Sale, SaleItem } from './models/sale.models';
import saleResponse from '../../../../../contracts/sale-response.json';

// Contract with SaleResponse (backend ResponseContractTest): the server answers contracts/sale-response.json and its
// fields must be exactly the model's. A field added on one side only fails here or does not compile.
const SALE_FIELDS = {
  id: true, saleNumber: true, cashSessionId: true, sessionNumber: true, status: true, subtotalAmount: true,
  discountItemsTotal: true, globalDiscountAmount: true, discountTotal: true, totalAmount: true, paymentMethod: true,
  amountCash: true, amountQr: true, amountReceived: true, changeGiven: true, description: true, createdBy: true,
  createdAt: true, voidedAt: true, voidedBy: true, voidReason: true, items: true, saleType: true, customerId: true,
  customerName: true, creditAmount: true, historical: true, operationDate: true, cashRounding: true,
} satisfies Record<keyof Sale, true>;

const SALE_ITEM_FIELDS = {
  id: true, productId: true, productName: true, productCode: true, unitPrice: true, discountPerUnit: true,
  finalUnitPrice: true, quantity: true, subtotal: true, saleUnit: true, presentationId: true, presentationName: true,
  presentationBaseQuantity: true, baseQuantity: true, freeLineType: true, freeName: true, assignmentStatus: true,
  assignedBy: true, assignedAt: true, creditorId: true, creditorName: true,
} satisfies Record<keyof SaleItem, true>;

const fields = (value: object) => Object.keys(value).sort();

describe('Contrato de SaleResponse', () => {
  let service: SaleService;
  let httpTesting: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: CashService, useValue: { getCurrentSession: vi.fn().mockReturnValue(of(null)) } },
      ],
    });
    service = TestBed.inject(SaleService);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpTesting.verify());

  it('la venta del servidor trae exactamente los campos del modelo Sale y SaleItem', () => {
    let sale: Sale | undefined;
    service.getSaleById(saleResponse.id).subscribe((result) => (sale = result));
    httpTesting.expectOne(`/api/sales/${saleResponse.id}`).flush(saleResponse);

    expect(fields(sale!)).toEqual(fields(SALE_FIELDS));
    expect(fields(sale!.items![0])).toEqual(fields(SALE_ITEM_FIELDS));
    // Amounts and quantities arrive as numbers, dates as ISO text
    expect(sale!.totalAmount).toBe(20);
    expect(sale!.items![0].quantity).toBe(0.25);
    expect(sale!.operationDate).toBe('2026-11-30');
  });
});
