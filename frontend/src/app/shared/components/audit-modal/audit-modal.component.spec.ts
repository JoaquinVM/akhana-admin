import { ComponentFixture, TestBed } from '@angular/core/testing';
import { AuditModalComponent } from './audit-modal.component';
import { By } from '@angular/platform-browser';

describe('AuditModalComponent', () => {
  let component: AuditModalComponent;
  let fixture: ComponentFixture<AuditModalComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AuditModalComponent]
    }).compileComponents();

    fixture = TestBed.createComponent(AuditModalComponent);
    component = fixture.componentInstance;
  });

  it('no debe renderizar nada cuando isOpen es false', () => {
    fixture.componentRef.setInput('isOpen', false);
    fixture.detectChanges();

    const backdrop = fixture.debugElement.query(By.css('.modal-backdrop'));
    expect(backdrop).toBeNull();
  });

  it('TEST-AUD-01: debe renderizar campos con valor y omitir nulos, vacíos o indefinidos (TEST-AUD-02)', () => {
    fixture.componentRef.setInput('isOpen', true);
    fixture.componentRef.setInput('title', 'Auditoría del Proveedor');
    fixture.componentRef.setInput('data', {
      createdBy: 'admin',
      createdAt: '2026-09-24T10:00:00Z',
      updatedBy: 'editor_carlos',
      updatedAt: '2026-09-25T14:30:00Z',
      deletedBy: null,
      deletedAt: undefined,
      emptyNote: '   '
    });
    fixture.detectChanges();

    const auditItems = fixture.debugElement.queryAll(By.css('.audit-item'));
    expect(auditItems.length).toBe(4);

    const keys = auditItems.map(item => item.nativeElement.getAttribute('data-key'));
    expect(keys).toContain('createdBy');
    expect(keys).toContain('createdAt');
    expect(keys).toContain('updatedBy');
    expect(keys).toContain('updatedAt');
    expect(keys).not.toContain('deletedBy');
    expect(keys).not.toContain('deletedAt');
    expect(keys).not.toContain('emptyNote');

    // Comprobar contenido del primer ítem
    const createdByItem = fixture.debugElement.query(By.css('[data-key="createdBy"]'));
    expect(createdByItem.nativeElement.textContent).toContain('Usuario de creación');
    expect(createdByItem.nativeElement.textContent).toContain('admin');
  });

  it('TEST-AUD-03: debe emitir evento close al hacer clic en el botón de cerrar', () => {
    fixture.componentRef.setInput('isOpen', true);
    fixture.detectChanges();

    let closeEmitted = false;
    component.close.subscribe(() => {
      closeEmitted = true;
    });

    const closeBtn = fixture.debugElement.query(By.css('.btn-primary'));
    closeBtn.nativeElement.click();

    expect(closeEmitted).toBe(true);
  });
});
