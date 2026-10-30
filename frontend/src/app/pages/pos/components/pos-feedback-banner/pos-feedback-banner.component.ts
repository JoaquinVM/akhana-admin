import { Component, input, output } from '@angular/core';

export interface PosFeedback {
  type: 'success' | 'error';
  text: string;
}

// Success or error notice of the POS screens; the parent decides when it shows and hides
@Component({
  selector: 'app-pos-feedback-banner',
  standalone: true,
  templateUrl: './pos-feedback-banner.component.html',
  styleUrls: ['./pos-feedback-banner.component.css']
})
export class PosFeedbackBannerComponent {
  readonly message = input.required<PosFeedback>();
  readonly dismiss = output<void>();
}
