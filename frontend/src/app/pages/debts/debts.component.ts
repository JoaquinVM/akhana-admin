import { Component, signal } from '@angular/core';
import { CreditorsViewComponent } from './components/creditors-view/creditors-view.component';

// Personas: lo que el negocio debe (DEU-01 a DEU-04). Clientes: por cobrar, llega con la venta al costo (DEU-05 a DEU-07)
export type DebtsTab = 'CREDITORS' | 'CUSTOMERS';

@Component({
  selector: 'app-debts',
  standalone: true,
  imports: [CreditorsViewComponent],
  templateUrl: './debts.component.html',
  styleUrls: ['./debts.component.css']
})
export class DebtsComponent {
  activeTab = signal<DebtsTab>('CREDITORS');
}
