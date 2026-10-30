import { Component, output } from '@angular/core';

// Cash closed: sales stay disabled until the session is opened with its opening amount
@Component({
  selector: 'app-pos-closed-state',
  standalone: true,
  templateUrl: './pos-closed-state.component.html',
  styleUrls: ['./pos-closed-state.component.css']
})
export class PosClosedStateComponent {
  readonly openCash = output<void>();
}
