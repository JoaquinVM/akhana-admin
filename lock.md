# 🔒 Configuración del MVP de Antigravity (Plantilla de Ejemplo)

> **INSTRUCCIONES:** Copia este archivo a la raíz de tu proyecto cliente, renómbralo a `lock.md`, completa los campos y guarda los cambios. El motor de Antigravity detectará el archivo e iniciará automáticamente el flujo de desarrollo.

Nombre del Proyecto: Akhana Admin

Descripción General del Proyecto:

El objetivo del MVP es proporcionar una interfaz rápida y sencilla para que los vendedores puedan registrar ventas, buscar o escanear productos, gestionar el carrito, aplicar descuentos y registrar pagos en efectivo o mediante QR.

Descripción de Funcionalidades para el MVP:
1. **Gestión de Categorias:**
   - Registrar categorias con nombre (obligatorio), descripcion (opcional) y color (obligatorio).
   - Permitir editar el nombre, descripcion y color.
   - El color se debe poder elejir con una paleta de colores.
   - Permitir elimnar categorias.
   - El nombre de la categoria debe ser único.
   - Permitir buscar categorias por nombre.
   - Al eliminar una categoria, mostrar una alerta de confirmación.
   - Al editar una categoria, mostrar una alerta de confirmación.
   - Al crear una categoria, mostrar una alerta de confirmación.

2. **Gestión de Provedores:**
   - Registrar provedores con codigo (obligatorio), nombre (obligatorio), descripcion (opcional) y telefono (opcional).
   - Permitir editar el codigo, nombre, descripcion y telefono.
   - Permitir elimnar provedores.
   - El codigo y nombre del provedor debe ser único.
   - Permitir buscar provedores por codigo o nombre.
   - Al eliminar un provedor, mostrar una alerta de confirmación.
   - Al editar un provedor, mostrar una alerta de confirmación.
   - Al crear un provedor, mostrar una alerta de confirmación.

3. **Gestión de Grupos:**
   - Registrar grupos con nombre (obligatorio) y descripcion (opcional).
   - Permitir editar el nombre y descripcion.
   - Permitir elimnar grupos.
   - El nombre del grupo debe ser único.
   - Permitir buscar grupos por nombre.
   - Al eliminar un grupo, mostrar una alerta de confirmación.
   - Al editar un grupo, mostrar una alerta de confirmación.
   - Al crear un grupo, mostrar una alerta de confirmación.

4. **Gestión de Etiquetas:**
   - Registrar etiquetas con nombre (obligatorio), descripcion (opcional) y color (obligatorio).
   - El color se debe poder elejir con una paleta de colores.
   - Permitir editar el nombre, descripcion y color.
   - Permitir elimnar etiquetas.
   - El nombre de la etiqueta debe ser único.
   - Permitir buscar etiquetas por nombre.
   - Al eliminar una etiqueta, mostrar una alerta de confirmación.
   - Al editar una etiqueta, mostrar una alerta de confirmación.
   - Al crear una etiqueta, mostrar una alerta de confirmación.

5. **Gestión de Productos:**
   - Registrar productos con codigo(obligatorio), nombre (obligatorio), grupo (obligatorio), provedor (obligatorio), etiquetas (opcional, puede tener varias), costo (obligatorio),  precio de venta (obligatorio), tipo de venta (obligatorio, puede ser por unidad o por peso), producto frecuente (checkbox, por defecto desactivado) y codigo de barras (opcional).
   - Para productos vendidos por peso, utilizar gramos como unidad de entrada.
   - Permitir activar, desactivar, eliminar productos.
   - Permitir editar el codigo, nombre, grupo, provedor, etiquetas, costo, precio de venta, tipo de venta, producto frecuente y codigo de barras.
   - El precio de venta debe ser mayor al costo.
   - El código de barras debe ser único.
   - El codigo debe ser único.
   - Permitir buscar productos por nombre, codigo, codigo de barras, grupo, etiqueta, provedor o producto frecuente.
   - Se debe mostrar la utilidad (precio de venta - costo) en un campo disabled.
   - Se debe mostrar el porcentaje de utilidad (utilidad / costo * 100) en un campo disabled.
   - Si el precio de venta es menor al costo, mostrar un mensaje de advertencia.
   - Al eliminar un producto, mostrar una alerta de confirmación.
   - Al editar un producto, mostrar una alerta de confirmación.
   - Al crear un producto, mostrar una alerta de confirmación.

7. Gestion de cajas
   - Permitir abrir una caja.
   - Registrar el monto inicial de la caja.
   - Asociar las ventas realizadas a la caja abierta.
   - Permitir cerrar la caja solicitando conteo de dinero (Mostrar una interfaz de conteo de dinero de 200, 100, 50, 20, 10, 5, 2, 1, 0.5, 0.2, 0.1.)
   - Mostrar durante el cierre:
     - Monto inicial.
     - Total cobrado en efectivo.
     - Total cobrado mediante QR.
     - Efectivo esperado.
     - Efectivo contado.
     - Diferencia entre efectivo esperado y contado.
   - Los pagos mediante QR no deben formar parte del efectivo físico esperado.

6. **Registro de Ventas:**
   - Crear una nueva venta siempre y cuando este abierta la caja.
   - Agregar productos mediante lector de código de barras.
   - Agregar productos mediante búsqueda.
   - Agregar productos desde una lista de productos frecuentes.
   - Modificar cantidades de productos.
   - Para productos por peso, ingresar la cantidad en gramos.
   - El sistema debe calcular automáticamente los subtotales.
   - Permitir eliminar productos del carrito.
   - Mostrar subtotal, descuentos y total de la venta.
   - Permitir aplicar descuentos por producto.
   - Mostrar el precio de venta con descuento.
   - Permitir aplicar un descuento global sobre la venta.
   - Los descuentos se deben expresar como monto fijo.
   - El límite máximo de descuento por producto debe ser configurable. (Valor Inicial 5 por producto)
   - El límite máximo de descuento global debe ser configurable. (Valor Inicial 20 por venta)
   - Permitir registrar pago en efectivo o una mezcla de ambos (si es una mezcla de ambos debe registrar monto en efectivo y monto QR).
   - No se permiten pagos negativos por ningun concepto. Por lo tanto el monto a pagar debe ser mayor o igual a 0.
   - Calcular automáticamente el cambio cuando corresponda.
   - Al crear la venta, mostrar una alerta de confirmación.

7. **Consulta, Edicion y Anulación de Ventas:**
   - Permitir consultar las ventas realizadas.
   - Permitir editar cualquier dato de la venta realizada 
   - Mostrar información básica de cada venta fecha, numero d productos, monto total, metodo de pago y estado.
   - Permitir anular una venta.
   - Una venta anulada no debe eliminarse físicamente de la base de datos.
   - Registrar usuario, fecha y motivo de la anulación.
   - Se debe permitir buscar ventas por codigos y nombres de productos o codigos de barras tambien.
   - Al anular una venta, mostrar una alerta de confirmación.
   - Al editar una venta, mostrar una alerta de confirmación.  

8. **Usuarios y Roles:**
   - Implementar autenticación básica.
   - Contemplar dos roles:
     - Administrador.
     - Vendedor.
   - El vendedor podrá realizar ventas y operaciones normales del POS.
   - El administrador podrá gestionar productos, configuración, consultar ventas y anular ventas.

9. **Interfaz del POS:**
   - La pantalla de venta debe estar optimizada para rapidez de operación.
   - El campo de búsqueda debe permitir recibir directamente la entrada de un lector de códigos de barras.
   - Mostrar claramente el carrito y el total.
   - El acceso a productos frecuentes debe estar disponible desde la pantalla principal.
   - La operación habitual de venta debe requerir la menor cantidad posible de pasos.

10. **Persistencia y trazabilidad básica:**
    - Todas las ventas confirmadas deben almacenarse permanentemente.
    - Cada venta debe estar asociada al usuario que la realizó.
    - Cada venta debe estar asociada a la caja correspondiente.
    - Registrar fecha y hora de las operaciones relevantes.
    - Las ventas confirmadas no deben modificarse directamente..

Stack Tecnológico: Angular (v17+ / TypeScript) + App Scripts + Google sheets
