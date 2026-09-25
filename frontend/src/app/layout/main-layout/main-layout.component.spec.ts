import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MainLayoutComponent } from './main-layout.component';
import { provideRouter } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { signal } from '@angular/core';
import { By } from '@angular/platform-browser';

describe('MainLayoutComponent', () => {
  let component: MainLayoutComponent;
  let fixture: ComponentFixture<MainLayoutComponent>;

  beforeEach(async () => {
    const mockAuthService = {
      currentUser: signal({ id: '1', username: 'admin', role: 'ADMIN' }),
      logout: vi.fn()
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

  it('debe renderizar el componente app-navbar', () => {
    const navbarEl = fixture.debugElement.query(By.css('app-navbar'));
    expect(navbarEl).toBeTruthy();
  });

  it('debe contener el elemento router-outlet', () => {
    const outletEl = fixture.debugElement.query(By.css('router-outlet'));
    expect(outletEl).toBeTruthy();
  });
});
