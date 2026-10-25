import { ComponentFixture, TestBed } from '@angular/core/testing';
import { VoidReasonModalComponent } from './void-reason-modal.component';

describe('VoidReasonModalComponent', () => {
  let component: VoidReasonModalComponent;
  let fixture: ComponentFixture<VoidReasonModalComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [VoidReasonModalComponent] }).compileComponents();
    fixture = TestBed.createComponent(VoidReasonModalComponent);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('isOpen', true);
    fixture.detectChanges();
  });

  it('COM-09: no confirma sin motivo', () => {
    const confirmed = vi.fn();
    component.confirmed.subscribe(confirmed);
    component.voidReason.set('   ');
    component.confirm();
    expect(confirmed).not.toHaveBeenCalled();
  });

  it('COM-09: emite el motivo sin espacios', () => {
    const confirmed = vi.fn();
    component.confirmed.subscribe(confirmed);
    component.voidReason.set('  Pago duplicado ');
    component.confirm();
    expect(confirmed).toHaveBeenCalledWith('Pago duplicado');
  });

  it('limpia el motivo al abrirse de nuevo', () => {
    component.voidReason.set('Algo');
    fixture.componentRef.setInput('isOpen', false);
    fixture.detectChanges();
    fixture.componentRef.setInput('isOpen', true);
    fixture.detectChanges();
    expect(component.voidReason()).toBe('');
  });
});
