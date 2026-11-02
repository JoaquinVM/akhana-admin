import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { vi } from 'vitest';
import { OpenCashModalComponent } from './open-cash-modal.component';
import { CashService } from '../../../../core/cash/cash.service';
import { CashOpeningProposal, OpenCashRequest } from '../../../../core/cash/models/cash.models';

// The example of the spec: the last session is #148 of Friday 30/10 and today is Tuesday 3/11
const PROPOSAL: CashOpeningProposal = {
  proposedAmount: 245.5,
  previousSessionNumber: 148,
  today: '2026-11-03',
  minDay: '2026-10-31',
  lastSessionNumber: 148,
  lastSessionDay: '2026-10-30',
  todayClosedSessionNumber: null,
  unresolvedDays: ['2026-10-31', '2026-11-02']
};

describe('OpenCashModalComponent', () => {
  let fixture: ComponentFixture<OpenCashModalComponent>;
  let component: OpenCashModalComponent;
  let emitted: OpenCashRequest[];

  const text = (): string => (fixture.nativeElement as HTMLElement).textContent ?? '';
  const element = <T extends HTMLElement>(selector: string): T | null => fixture.nativeElement.querySelector(selector);

  function open(proposal: CashOpeningProposal): void {
    TestBed.inject(CashService).getOpeningProposal = vi.fn().mockReturnValue(of(proposal));
    fixture.componentRef.setInput('isOpen', true);
    fixture.detectChanges();
    fixture.detectChanges();
  }

  function setValue(values: Record<string, unknown>): void {
    component.form.patchValue(values);
    fixture.detectChanges();
    fixture.detectChanges();
  }

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [OpenCashModalComponent],
      providers: [{ provide: CashService, useValue: { getOpeningProposal: vi.fn() } }]
    }).compileComponents();
    fixture = TestBed.createComponent(OpenCashModalComponent);
    component = fixture.componentInstance;
    emitted = [];
    component.confirmed.subscribe((request) => emitted.push(request));
  });

  describe('CAJ-12: elegir el día', () => {
    it('propone hoy y deja elegir solo desde el día siguiente a la última caja hasta hoy', () => {
      open(PROPOSAL);

      const day = element<HTMLInputElement>('#operationDate')!;
      expect(day.value).toBe('2026-11-03');
      expect(day.getAttribute('min')).toBe('2026-10-31');
      expect(day.getAttribute('max')).toBe('2026-11-03');
    });

    it('con el día de hoy, sin días que falten, no pide comentario', () => {
      open({ ...PROPOSAL, unresolvedDays: [] });

      expect(component.commentRequired()).toBe(false);
      component.onSubmit();

      expect(emitted).toEqual([{ openingAmount: 245.5, openingComment: null, operationDate: '2026-11-03',
        noAttentionDays: null, noAttentionComment: null }]);
    });

    it('un día anterior pide comentario, con su propio texto', () => {
      open(PROPOSAL);
      setValue({ operationDate: '2026-10-31' });

      expect(component.canSubmit()).toBe(false);
      component.onSubmit();
      fixture.detectChanges();

      expect(emitted).toEqual([]);
      expect(text()).toContain('Elegir un día anterior pide un comentario.');
    });

    it('un solo comentario basta para el día anterior y el monto cambiado', () => {
      open(PROPOSAL);
      setValue({ operationDate: '2026-10-31', openingAmount: 200, openingComment: ' Carga atrasada ' });

      expect(component.canSubmit()).toBe(true);
      component.onSubmit();

      expect(emitted[0].openingAmount).toBe(200);
      expect(emitted[0].openingComment).toBe('Carga atrasada');
      expect(emitted[0].operationDate).toBe('2026-10-31');
    });

    it('un día fuera del rango no se puede enviar', () => {
      open(PROPOSAL);
      setValue({ operationDate: '2026-11-04', openingComment: 'x' });
      expect(component.canSubmit()).toBe(false);

      setValue({ operationDate: '2026-10-30', openingComment: 'x' });
      expect(component.canSubmit()).toBe(false);
      expect(text()).toContain('Elige un día después de la última caja y hasta hoy.');
    });

    it('si la caja de hoy ya se cerró lo explica y no se puede abrir, sin enlace al historial', () => {
      open({ ...PROPOSAL, minDay: '2026-11-04', lastSessionNumber: 151, lastSessionDay: '2026-11-03',
        todayClosedSessionNumber: 151, unresolvedDays: [] });

      expect(text()).toContain('La caja de hoy (#151) ya se cerró. Si fue por error, puedes reabrirla desde el Historial de cajas.');
      expect(component.canSubmit()).toBe(false);
      expect(fixture.nativeElement.querySelector('a')).toBeNull();
    });

    it('con una última caja histórica de hoy explica el límite, sin ofrecer reabrirla', () => {
      open({ ...PROPOSAL, minDay: '2026-11-04', lastSessionNumber: 90, lastSessionDay: '2026-11-03',
        todayClosedSessionNumber: null, unresolvedDays: [] });

      expect(text()).toContain('El día debe ser posterior al de la última caja (#90, 3/11).');
      expect(text()).not.toContain('reabrirla');
      expect(component.canSubmit()).toBe(false);
    });

    it('sin cajas se abre con el día de hoy', () => {
      open({ ...PROPOSAL, minDay: null, lastSessionNumber: null, lastSessionDay: null, previousSessionNumber: null, unresolvedDays: [] });

      expect(element<HTMLInputElement>('#operationDate')!.getAttribute('min')).toBeNull();
      expect(component.canSubmit()).toBe(true);
    });

    it('muestra en el modal lo que respondió el servidor', () => {
      open(PROPOSAL);
      fixture.componentRef.setInput('serverError', 'Falta resolver: 31/10, 2/11.');
      fixture.detectChanges();

      expect(element('[role="alert"]')!.textContent).toContain('Falta resolver: 31/10, 2/11.');
    });
  });

  describe('CAJ-13: días que faltan', () => {
    it('elegir el 31/10 no pregunta nada sobre otros días', () => {
      open(PROPOSAL);
      setValue({ operationDate: '2026-10-31', openingComment: 'Carga atrasada' });

      expect(component.missingDays()).toEqual([]);
      expect(text()).not.toContain('¿No hubo atención esos días?');
      expect(component.canSubmit()).toBe(true);
    });

    it('elegir el 3/11 lista los días con su nombre, sin el domingo, y pregunta', () => {
      open(PROPOSAL);

      expect(text()).toContain('sábado 31/10');
      expect(text()).toContain('lunes 2/11');
      expect(text()).not.toContain('domingo');
      expect(text()).toContain('¿No hubo atención esos días?');
    });

    it('no se puede seguir sin confirmar ni sin comentario', () => {
      open(PROPOSAL);
      expect(component.canSubmit()).toBe(false);

      setValue({ noAttentionComment: 'Feriado' });
      expect(component.canSubmit()).toBe(false);

      setValue({ noAttentionConfirmed: true, noAttentionComment: '   ' });
      expect(component.canSubmit()).toBe(false);
      component.onSubmit();
      fixture.detectChanges();
      expect(emitted).toEqual([]);
      expect(text()).toContain('Confirmar los días sin atención pide un comentario.');
    });

    it('al confirmar envía los días y su comentario, aparte del de apertura', () => {
      open(PROPOSAL);
      setValue({ noAttentionConfirmed: true, noAttentionComment: ' Feriado ' });

      expect(component.canSubmit()).toBe(true);
      component.onSubmit();

      expect(emitted).toEqual([{ openingAmount: 245.5, openingComment: null, operationDate: '2026-11-03',
        noAttentionDays: ['2026-10-31', '2026-11-02'], noAttentionComment: 'Feriado' }]);
    });

    it('sin confirmar se puede elegir otro día, y la confirmación no vale para otros días', () => {
      open(PROPOSAL);
      setValue({ noAttentionConfirmed: true, noAttentionComment: 'Feriado' });

      setValue({ operationDate: '2026-11-02', openingComment: 'Carga' });

      expect(component.missingDays()).toEqual(['2026-10-31']);
      expect(component.form.get('noAttentionConfirmed')!.value).toBe(false);
      expect(component.canSubmit()).toBe(false);
    });

    it('elegir un domingo también resuelve el sábado que falta', () => {
      open(PROPOSAL);
      setValue({ operationDate: '2026-11-01', openingComment: 'Domingo' });

      expect(component.missingDayLabels()).toEqual(['sábado 31/10']);
    });
  });
});
