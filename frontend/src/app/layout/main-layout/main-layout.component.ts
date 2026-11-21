import { Component, OnInit, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { NavbarComponent } from '../navbar/navbar.component';
import { AuthService } from '../../core/auth/auth.service';

@Component({
  selector: 'app-main-layout',
  standalone: true,
  imports: [RouterOutlet, NavbarComponent],
  templateUrl: './main-layout.component.html',
  styleUrl: './main-layout.component.css'
})
export class MainLayoutComponent implements OnInit {
  private readonly authService = inject(AuthService);

  // The stored session keeps the role of the login: ask once per entry so the menu shows the current role.
  // If it fails the stored one stays (an ended session is handled by the interceptor).
  ngOnInit(): void {
    this.authService.fetchCurrentUserProfile().subscribe({ error: () => undefined });
  }
}
