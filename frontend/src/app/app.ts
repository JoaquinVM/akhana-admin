import { Component, signal } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { ScreenLoadNoticeComponent } from './shared/components/screen-load-notice/screen-load-notice.component';
import { ToastComponent } from './shared/components/toast/toast.component';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, ScreenLoadNoticeComponent, ToastComponent],
  templateUrl: './app.html',
  styleUrl: './app.css'
})
export class App {
  protected readonly title = signal('akhana-frontend');
}
