import { Component, input, output } from '@angular/core';

/** POS-17: says that something could not be loaded and lets the person try again, so a failure never reads as "there is none". */
@Component({
  selector: 'app-connection-notice',
  standalone: true,
  templateUrl: './connection-notice.component.html',
  styleUrls: ['./connection-notice.component.css']
})
export class ConnectionNoticeComponent {
  readonly message = input.required<string>();
  readonly retry = output<void>();
}
