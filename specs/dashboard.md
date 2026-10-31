# Dashboard

Detalle del Dashboard (DSH-01 a DSH-04 en la sección 3.11 de `specs/requirements.md`), con los mismos IDs. Una sola
pantalla que se lee de arriba abajo: indicadores del período, ventas por día, cobro, caja actual, top de productos y
proveedores, gastos por categoría, y saldos y deudas.

Moneda Bs, montos con escala 2 `HALF_UP`. Los días son días de la tienda (America/La_Paz). El dashboard solo lee:
no crea ni cambia registros. Lo anulado (ventas, consumos, movimientos, cobros) nunca cuenta. Ventas, costo,
utilidad, proveedores y gastos salen de los reportes (FIN-10, `reports.md`) sin cambiarlos, así que el dashboard y
Reportes siempre muestran los mismos números para el mismo rango.

## Permiso, pantalla e inicio

- Pantalla **Dashboard** (`/dashboard`, menú Finanzas → Dashboard), permiso nuevo `DASHBOARD`, solo Administrador
  (tabla 2.2, P-33). Se carga en diferido: no agranda el bundle inicial.
- **Cuando** un operador llama a `GET /api/dashboard` **entonces** recibe 403 y la pantalla no aparece en su menú.
- **Cuando** un administrador inicia sesión o entra a `/` **entonces** llega al Dashboard; **cuando** es un
  operador, llega al POS como hasta ahora.
- **Cuando** se abre **entonces** el período es el mes en curso, del 1 a hoy.
- **Cuando** la fecha desde es posterior a la fecha hasta, o el rango pasa de 366 días, **entonces** los mismos
  mensajes que Reportes.
- Responsive: en la PC de caja las tarjetas van de a 4 y los bloques de a 2; en el celular, tarjetas de a 2 y
  bloques uno debajo del otro.

## DSH-01 · Indicadores del período

Botones **Hoy · Semana · Mes · Rango**. Semana es del lunes a hoy; Rango muestra Desde y Hasta.

| Tarjeta | Valor |
| --- | --- |
| Ventas | Total de las ventas normales (después de descuentos), como Ventas normales → Total del reporte por día |
| Utilidad | Ventas − costo de las líneas (POS-05), con el margen % debajo |
| Tickets | Cantidad de ventas normales |
| Ticket promedio | Ventas / tickets |

- **Cuando** del 1 al 30 de octubre hay 30 ventas normales por Bs 2.745,00 con costo Bs 2.104,16 **entonces** Ventas
  Bs 2.745,00, Utilidad Bs 640,84, Margen 23,35 %, Tickets 30 y Ticket promedio Bs 91,50.
- **Cuando** hay una venta al costo de Bs 104,00 y consumos por Bs 35,50 **entonces** no cambian ventas, utilidad
  ni margen: van en una línea aparte («Al costo: 1 venta por Bs 104,00 · Consumos al costo: Bs 35,50»).
- **Cuando** no hay ventas en el período **entonces** margen y ticket promedio quedan en «—».
- **Cuando** el período incluye días migrados de la planilla (FIN-11) **entonces** cada día es una sola venta
  histórica (P-32): Tickets lo aclara («Incluye 25 días migrados de la planilla (1 por día)») y el ticket promedio
  solo es real desde el corte (27-10-2026).
- Cada tarjeta muestra el cambio % contra el período anterior («▲ 9,8 % vs anterior»), o «Sin comparación» si el
  anterior es 0. El período anterior es:
  - del 1 a un día posterior del mismo mes → los mismos días del mes anterior (1-30 oct con 1-30 sep; 1-31 mar
    con 1-28 feb);
  - del lunes a un día posterior de la misma semana → los mismos días de la semana anterior (lun 26 a vie 30 oct
    con lun 19 a vie 23 oct);
  - cualquier otro rango → la misma cantidad de días justo antes (un día con el día anterior; el 1 solo, con el
    último día del mes anterior).

## DSH-02 · Ventas por día y cobro

- Línea de ventas normales por día del período (continua) y del período anterior (punteada), en la misma escala.
  Cada día del período aparece, con 0 los días sin ventas. Al pasar el mouse (o tocar en el celular) se ven el día,
  sus ventas y utilidad, y las ventas del día equivalente del período anterior. «Ver tabla» muestra los números.
- **Cuando** el 02-10 hay ventas por Bs 100,00 con costo Bs 60,00 y el 01 y el 03 no hay **entonces** la línea
  tiene 3 puntos: 0, 100,00 (utilidad 40,00) y 0.
- **Cómo se cobró**: suma de `amount_cash`, `amount_qr` y `credit_amount` de las ventas activas del período, de
  todo tipo (también al costo), con barra apilada y % de cada uno; Crédito solo aparece si hay. Las ventas
  históricas no guardan cómo se cobraron (P-32): su total va aparte, «Sin dato de cobro: Bs … de N días migrados de
  la planilla».
- **Cuando** hay una venta en efectivo de 50, una mixta 10 + 20 QR, una al costo a crédito de 15 y una anulada de
  99, y un día migrado de Bs 300,00 **entonces** Efectivo 60,00, QR 20,00, Crédito 15,00 y sin dato de cobro
  Bs 300,00 de 1 día migrado.

## DSH-03 · Top productos, proveedores y gastos

- Top 10 por venta o por utilidad (botones Venta / Utilidad en cada bloque). Solo ventas normales; la línea
  muestra el valor elegido y el otro debajo.
- Productos: cada línea con el descuento general de su venta repartido como en el reporte por proveedor (P-44:
  las líneas libres conservan su monto). Las líneas por asignar, sin identificar y de terceros no cuentan; una
  línea sin identificar ya asignada cuenta para su producto. La cantidad de un producto por peso está en kilos.
- **Cuando** una venta de Bs 35,00 tiene 2 × Bs 10,00 de Maní (costo 6) y 0,250 kg × Bs 80,00 de Nuez (costo 60/kg)
  con descuento general de Bs 5,00, más una línea por asignar de Bs 15,00, y otra venta tiene 1 kg de Nuez a
  Bs 80,00 (costo 60) **entonces** Nuez: 1,250 kg, Bs 97,50, utilidad 22,50; Maní: Bs 17,50, utilidad 5,50.
- Proveedores: los totales por proveedor del reporte de ventas por proveedor del mismo rango.
- Gastos por categoría: los gastos del reporte de movimientos por categoría del mismo rango, en positivo y de mayor
  a menor, con su total. Las transferencias (por ejemplo Caja → Efectivo) no son gastos.

## DSH-04 · Ahora mismo

No depende del período.

- Caja actual: abierta (número, quién y cuándo abrió, caja inicial, ventas y efectivo esperado, como el POS) o
  «Cerrada».
- Saldos: cada cuenta activa (Caja, Bisa, Efectivo) con su color y el total consolidado, como Finanzas → Resumen
  (FIN-09).
- Deudas: por pagar a proveedores (saldo de los pedidos, COM-08), por devolver a personas (DEU-01) y por cobrar a
  la tienda amiga (DEU-05).

## Día de operación (CAJ-11)

- **Cuando** una venta o un gasto de la caja del 31/10 se cargó pasada la medianoche **entonces** cuenta el 31/10
  en los indicadores, ventas por día, cómo se cobró, top de productos y proveedores y gastos por categoría. El
  dashboard no lleva marca. La caja abierta de «Ahora mismo» no cambia.
- Cobro y top de productos filtran por `sales.operation_date`; lo demás sale de los reportes, que ya usan el día de
  operación. Prueba en `DashboardRepositoryTest` (contra PostgreSQL) y `DashboardServiceTest`.

## API

`GET /api/dashboard?from=AAAA-MM-DD&to=AAAA-MM-DD` (permiso `DASHBOARD`; sin fechas, el mes en curso hasta hoy).
Devuelve el rango y el anterior, los indicadores de ambos, los días de ambos, el cobro, los cuatro top, los gastos y
el bloque «ahora». Todo se suma en la base y en el servidor; al navegador llegan solo los totales.

## Tareas

- [x] Permiso `DASHBOARD` (Administrador), `GET /api/dashboard` y pantalla `/dashboard` en diferido, menú
  Finanzas → Dashboard e inicio del administrador.
- [x] Indicadores y período anterior (DSH-01), días y cobro (DSH-02), top y gastos (DSH-03), caja, saldos y deudas
  (DSH-04). Pruebas en `DashboardServiceTest`, `DashboardRepositoryTest` (contra PostgreSQL: anuladas fuera, al
  costo fuera del top), `EndpointAuthorizationTest`, `dashboard.service.spec.ts` y `dashboard.component.spec.ts`.
- [x] Gráficos con SVG y HTML propios, sin librería (0 kB más en el bundle inicial por gráficos); colores de cobro
  revisados para daltonismo, con valores y % visibles al lado.
- [x] Revisión en el navegador con la base local (30-10-2026): del 1 al 30-10 ventas, utilidad, tickets, al costo y
  consumos iguales a Reportes → Totales; sin scroll horizontal a 375 px. Con el historial, el cobro solo sumaba lo
  del POS: se agregó el total sin dato de cobro de los días migrados.
- [ ] Revisar en producción con el dueño que Ventas y Utilidad del mes coincidan con Reportes → Totales.
