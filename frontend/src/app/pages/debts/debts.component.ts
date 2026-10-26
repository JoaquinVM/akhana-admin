import { Component, signal } from '@angular/core';
import { CreditorsViewComponent } from './components/creditors-view/creditors-view.component';
import { CustomersViewComponent } from './components/customers-view/customers-view.component';

// Personas: lo que el negocio debe (DEU-01 a DEU-04). Clientes: lo que se cobra por ventas al costo a crédito (DEU-05 a DEU-07)
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
