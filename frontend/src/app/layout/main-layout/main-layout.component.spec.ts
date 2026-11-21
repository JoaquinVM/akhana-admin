import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MainLayoutComponent } from './main-layout.component';
import { provideRouter } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { signal } from '@angular/core';
import { By } from '@angular/platform-browser';
import { of } from 'rxjs';

describe('MainLayoutComponent', () => {
  let component: MainLayoutComponent;
  let fixture: ComponentFixture<MainLayoutComponent>;

  let mockAuthService: any;

  beforeEach(async () => {
    mockAuthService = {
      currentUser: signal({ id: '1', username: 'admin', role: 'ADMINISTRATOR' }),
      logout: vi.fn(),
      fetchCurrentUserProfile: vi.fn().mockReturnValue(of({ id: '1', username: 'admin', role: 'ADMINISTRATOR' }))
    };

    await TestBed.configureTestingModule({
      imports: [MainLayoutComponent],
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: mockAuthService }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(MainLayoutComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('debe crearse exitosamente', () => {
    expect(component).toBeTruthy();
  });

  it('al entrar pide una vez el perfil actual para que el menú refleje el rol', () => {
    expect(mockAuthService.fetchCurrentUserProfile).toHaveBeenCalledTimes(1);
  });

  it('debe renderizar el componente app-navbar', () => {
    const navbarEl = fixture.debugElement.query(By.css('app-navbar'));
    expect(navbarEl).toBeTruthy();
  });

  it('debe contener el elemento router-outlet', () => {
    const outletEl = fixture.debugElement.query(By.css('router-outlet'));
    expect(outletEl).toBeTruthy();
  });
});
