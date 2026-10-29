import { Component, signal } from '@angular/core';
import { CreditorsViewComponent } from './components/creditors-view/creditors-view.component';
import { CustomersViewComponent } from './components/customers-view/customers-view.component';

// People: what the business owes (DEU-01 to DEU-04). Customers: what is collected for at-cost sales on credit (DEU-05 to DEU-07)
export type DebtsTab = 'CREDITORS' | 'CUSTOMERS';

@Component({
  selector: 'app-debts',
  standalone: true,
  imports: [CreditorsViewComponent, CustomersViewComponent],
  templateUrl: './debts.component.html',
  styleUrls: ['./debts.component.css']
})
export class DebtsComponent {
  activeTab = signal<DebtsTab>('CREDITORS');
}
