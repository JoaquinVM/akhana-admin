import { Component, computed, input, output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { PurchaseDraft } from '../../../../core/purchase/models/purchase.models';

// COM-20: what the reading asks to review before registering. Warnings: none of these blocks by itself (CA-18)
@Component({
  selector: 'app-reading-notice',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './reading-notice.component.html',
  styleUrls: ['./reading-notice.component.css']
})
export class ReadingNoticeComponent {
  draft = input.required<PurchaseDraft>();
  totalAmount = input<number>(0);
  linesComplete = input<boolean>(false);
  paidProposal = input<boolean>(false);
  isEdit = input<boolean>(false);

  dismissPaid = output<void>();

  // CA-18: in cents, so 0.1 + 0.2 does not read as a difference
  difference = computed<number | null>(() => {
    const read = this.draft().readTotal;
    if (read === null || !this.linesComplete()) {
      return null;
    }
    const cents = Math.round(this.totalAmount() * 100) - Math.round(read * 100);
    return cents === 0 ? null : cents / 100;
  });
}
