import { Component, inject } from '@angular/core';
import { IconComponent } from '../icon/icon.component';
import { ScreenLoadNoticeService } from './screen-load-notice.service';

@Component({
  selector: 'app-screen-load-notice',
  standalone: true,
  imports: [IconComponent],
  templateUrl: './screen-load-notice.component.html',
  styleUrls: ['./screen-load-notice.component.css']
})
export class ScreenLoadNoticeComponent {
  readonly notice = inject(ScreenLoadNoticeService);
}
