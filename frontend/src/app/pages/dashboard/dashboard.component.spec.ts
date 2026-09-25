import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { signal } from '@angular/core';
import { DashboardComponent } from './dashboard.component';
import { AuthService } from '../../core/auth/auth.service';
import { UserSession } from '../../core/auth/models/auth.models';

describe('DashboardComponent', () => {
  let component: DashboardComponent;
  let fixture: ComponentFixture<DashboardComponent>;
  let authServiceSpy: {
    logout: ReturnType<typeof vi.fn>;
    currentUser: ReturnType<typeof signal<UserSession | null>>;
    userRole: ReturnType<typeof signal<'ADMIN' | 'SELLER' | null>>;
  };
  let routerSpy: { navigate: ReturnType<typeof vi.fn> };

  beforeEach(async () => {
    const userSignal = signal<UserSession | null>({
      id: 'uuid-admin-123',
      username: 'admin',
      role: 'ADMIN'
    });
    const roleSignal = signal<'ADMIN' | 'SELLER' | null>('ADMIN');

    authServiceSpy = {
      logout: vi.fn(),
      currentUser: userSignal,
      userRole: roleSignal
    };
    routerSpy = {
      navigate: vi.fn()
    };

    await TestBed.configureTestingModule({
      imports: [DashboardComponent],
      providers: [
        { provide: AuthService, useValue: authServiceSpy },
        { provide: Router, useValue: routerSpy }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(DashboardComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create and render user details', () => {
    expect(component).toBeTruthy();
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('¡Bienvenido, admin!');
    expect(compiled.textContent).toContain('ADMIN');
  });

  it('should call authService.logout and navigate to /login when clicking logout button', () => {
    component.logout();
    expect(authServiceSpy.logout).toHaveBeenCalled();
    expect(routerSpy.navigate).toHaveBeenCalledWith(['/login']);
  });
});
