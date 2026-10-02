import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CloseCashModalComponent } from './close-cash-modal.component';

describe('CloseCashModalComponent', () => {
  let component: CloseCashModalComponent;
  let fixture: ComponentFixture<CloseCashModalComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [CloseCashModalComponent]
    }).compileComponents();

    fixture = TestBed.createComponent(CloseCashModalComponent);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('isOpen', true);
    fixture.componentRef.setInput('expectedCash', 100);
    fixture.detectChanges();
  });

  it('debe inicializarse con el formulario y lista de denominaciones', () => {
    expect(component).toBeTruthy();
    expect(component.denominations.length).toBe(11);
    expect(component.form.get('closingAmount')?.value).toBe(0);
    expect(component.difference()).toBe(-100); // 0 - 100
  });

  it('debe separar correctamente billetes (5) y monedas (6) para la vista en 2 columnas', () => {
    expect(component.billCuts().length).toBe(5);
    expect(component.coinCuts().length).toBe(6);
    expect(component.activeMode()).toBe('ARQUEO');
  });

  it('debe calcular correctamente subtotales de cortes y total acumulado', () => {
    // Simular 10 en caja y 40 en reserva para Bs 10 -> (10 + 40) * 10 = 500
    component.cutsData.update((rows) =>
      rows.map((r) =>
        r.denomination === 10
          ? { ...r, cashQuantity: 10, reserveQuantity: 40 }
          : r
      )
    );

    expect(component.totalCuts()).toBe(500);

    // Aplicar total al monto de cierre
    component.applyCutsToClosingAmount();
    expect(component.form.get('closingAmount')?.value).toBe(500);
    expect(component.difference()).toBe(400); // 500 - 100 = +400 (sobrante)
  });

  it('debe emitir confirmed con los datos ingresados al hacer submit', () => {
    let emittedResult: any = null;
    component.confirmed.subscribe((res) => {
      emittedResult = res;
    });

    component.form.patchValue({
      closingAmount: 150,
      closingComment: 'Cierre correcto'
    });

    component.onSubmit();

    expect(emittedResult).not.toBeNull();
    expect(emittedResult.closingAmount).toBe(150);
    expect(emittedResult.closingComment).toBe('Cierre correcto');
  });
});
