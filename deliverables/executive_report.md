# Informe Ejecutivo Técnico de Desarrollo

## Reglas de Diseño:

La eficiencia operativa en un entorno de Punto de Venta (POS) es un imperativo estratégico que trasciende la estética. En Akhana Admin, el diseño se articula bajo la premisa de que la velocidad de la interfaz impacta directamente en la productividad del vendedor y en la satisfacción del cliente final. Una reducción de milisegundos en la interacción no solo optimiza el flujo de transacciones, sino que minimiza el error humano en entornos de alta presión transaccional.

Siguiendo los lineamientos de UX/UI detallados en el protocolo de requerimientos (lock.md), el sistema implementa las siguientes capacidades de "ultra rapidez":

*   **Soporte Periférico Nativo:** Integración fluida para escaneo mediante teclado y dispositivos HID, permitiendo la entrada directa de códigos de barras en el campo de búsqueda sin clics adicionales.
*   **Entrada Unificada de Productos:** Navegación optimizada mediante atajos de teclado y acceso inmediato a una lista de "productos frecuentes" desde la pantalla principal.
*   **Gestión de Unidades Avanzada:** Soporte específico para productos pesables, utilizando los **gramos** como unidad de entrada técnica, garantizando precisión en el inventario.
*   **Personalización Visual Funcional:** Inclusión de selectores de color (color pickers) para categorías y etiquetas, permitiendo una jerarquía visual que agiliza la identificación de productos en el catálogo.

La arquitectura de visualización del carrito se apoya en **Angular Signals**. A diferencia de los modelos de detección de cambios tradicionales de Angular que pueden sobrecargar el hilo principal, los *Signals* permiten una reactividad granular y síncrona. Esto asegura que el cálculo de subtotales, descuentos y totales sea instantáneo, eliminando la latencia visual. El flujo concluye con la confirmación mediante modales técnicos que desglosan de forma transparente los totales, el cambio a entregar y los motivos de anulación en caso de ser necesarios. La competitividad de Akhana Admin reside precisamente en esta capacidad de ofrecer una experiencia de usuario de alto rendimiento sobre una infraestructura de bajo coste.

## Análisis de Mercado:

El mercado actual de sistemas POS para micro y pequeñas empresas (MYPE) se encuentra polarizado entre soluciones locales rudimentarias y plataformas SaaS corporativas con costes de suscripción prohibitivos. Akhana Admin se posiciona en el "punto dulce" de esta industria, ofreciendo potencia administrativa sin la carga financiera de servidores propietarios.

### 2.1. Comparativa Estratégica

| Criterio | Competidores (Loyverse, Square, Kyte) | Akhana Admin |
| :--- | :--- | :--- |
| **Costo Operativo** | Suscripciones mensuales + Comisiones. | **Cero costo de servidor** (Google Cloud). |
| **Infraestructura** | Bases de datos propietarias y cerradas. | Persistencia en **Google Sheets** (Soberanía de datos). |
| **Complejidad** | Curva de aprendizaje media/alta. | Interfaz simplificada y despliegue inmediato. |
| **Conectividad** | Frecuente dependencia de nube específica. | Arquitectura *Serverless* vía Apps Script. |

### 2.2. Business Model Canvas (Estructura de Consultoría)

*   **Propuesta de Valor:** Sistema POS de alta velocidad y nulo mantenimiento, con persistencia transparente de datos para el dueño del negocio.
*   **Segmentos de Cliente:** Microempresas del sector retail que buscan digitalización sin costes fijos de software.
*   **Canales:** Despliegue directo vía web (Cloud) y servicios de implementación técnica por partners.
*   **Relación con Clientes:** Autoservicio asistido y soporte técnico preventivo.
*   **Flujos de Ingresos:** Servicios de personalización de plantillas, implementación inicial y consultoría de análisis de datos.
*   **Actividades Clave:** Desarrollo de lógica reactiva en Angular, optimización de scripts de Apps Script y mantenimiento de la lógica de negocio en el "lock.md".
*   **Recursos Clave:** Ecosistema Angular 17+, Google Cloud Platform y desarrolladores especialistas en Apps Script.
*   **Socios Clave:** Google Workspace (como proveedor de infraestructura) y consultores de estrategia digital independientes.
*   **Estructura de Costos:** Inversión en horas de desarrollo (CAPEX) vs. costo operativo de mantenimiento cercano a cero (OPEX).

La viabilidad comercial de Akhana Admin se fundamenta en su capacidad de eliminar la barrera de entrada económica, posicionándose como una herramienta de soberanía operativa para el empresario.

## Nombre y Logotipo:

En el software de gestión, el nombre y la identidad visual deben proyectar orden, invulnerabilidad y dominio administrativo. El usuario debe sentir que el sistema es un aliado robusto que custodia su patrimonio.

### 3.1. Identidad de Marca: Akhana Admin

El nombre **Akhana** se deriva conceptualmente de raíces etimológicas que sugieren un "granero protegido" o "bóveda" (vault), transmitiendo la idea de un depósito seguro para la información comercial. El tono de comunicación es profesional y técnico, pero accesible, eliminando la intimidación del software corporativo tradicional.

*   **Isotipo y Logotipo:** La identidad se visualiza a través de un isotipo que combina geométricamente una **flecha ascendente** (símbolo de crecimiento y ventas) integrada con un **nodo de gráfico de barras** (control de datos). Las líneas son de grosor uniforme para garantizar legibilidad en pantallas de baja densidad y en la impresión térmica de recibos. El equilibrio simétrico del conjunto refuerza la percepción de una herramienta moderna y equilibrada.

Esta identidad visual actúa como un anclaje de confianza, facilitando la retención de marca al asociar el símbolo de Akhana con la seguridad de una transacción bien ejecutada.

## Identidad Visual:

La selección cromática de Akhana Admin no es ornamental, sino una capa de información técnica que facilita la lectura de estados críticos del sistema en entornos de alta transaccionalidad.

### 4.1. Paleta de Colores Técnica

| Categoría | HEX | HSL | Aplicación Estratégica |
| :--- | :--- | :--- | :--- |
| **Primario** | #1A73E8 | 214, 82%, 51% | Acciones de confirmación y marca. |
| **Éxito** | #34A853 | 136, 53%, 43% | Validación de escaneo y ventas completadas. |
| **Advertencia** | #FBBC04 | 45, 97%, 50% | Alertas de stock y aviso "Precio de Venta < Costo". |
| **Peligro** | #EA4335 | 4, 82%, 56% | Errores críticos, anulación y eliminación. |
| **Superficie** | #F8F9FA | 210, 17%, 98% | Fondos de interfaz y áreas de bajo contraste. |

### 4.2. Tipografía y Micro-animaciones

Se recomienda el uso de **Inter** o **Roboto Mono** para todos los campos numéricos. Estas fuentes poseen características de *tabular lining* (numerales de ancho constante), lo que asegura que las columnas de precios y denominaciones de caja (desde 200 hasta 0.1) se alineen perfectamente, facilitando la auditoría visual rápida.

Las micro-animaciones se reservan para eventos de validación de datos: un ligero pulso verde al detectar un código de barras correcto y transiciones fluidas en la actualización del carrito impulsadas por la reactividad de Signals. Este rigor visual prepara al usuario para la robustez técnica de la arquitectura subyacente.

## Especificación Técnica:

La elección del stack Angular 17+ y Google Apps Script (GAS) permite a Akhana Admin funcionar bajo un modelo de "Costo Total de Propiedad" (TCO) mínimo, aprovechando los límites gratuitos de Google Cloud para el tráfico de microempresas.

### 5.1. Arquitectura de Frontend y Datos

*   **Frontend:** Desarrollo basado en *Standalone Components* y gestión de estado mediante *Signals* para el carrito y *RxJS* para la comunicación asíncrona con GAS.
*   **Modelo de Datos (Google Sheets):** La persistencia se organiza en hojas de cálculo con estructuras estrictas. La hoja **Productos** incluye columnas críticas: `Codigo`, `Nombre`, `Grupo`, `Proveedor`, `Etiquetas`, `Costo`, `PrecioVenta`, `TipoVenta` (unidad/peso), `Frecuente`, `CodigoBarras`, `Utility` (calculado) y `Utility %` (calculado).
*   **Otras Hojas Obligatorias:** Categorías, Proveedores, Grupos, Etiquetas, Cajas, Ventas, DetalleVentas y Usuarios.

### 5.2. Backend y Reglas de Negocio

*   **Control de Concurrencia (LockService):** El backend en Apps Script (`doPost`, `doGet`) implementa `LockService` para evitar colisiones de datos durante escrituras simultáneas.
*   **Nota de Concurrencia:** Siguiendo la Decisión Arquitectónica del proyecto, se implementa una **Caja Única inicial** para mitigar los umbrales de escritura concurrente de Google Sheets, garantizando la integridad de los saldos.
*   **Arqueo de Caja:** Lógica de conteo físico por denominaciones exactas: **200, 100, 50, 20, 10, 5, 2, 1, 0.5, 0.2, 0.1**. El sistema diferencia entre efectivo esperado y pagos QR.
*   **Validaciones:** Bloqueo de ventas si `Precio < Costo`, límites de descuento configurables (5 por producto, 20 global) y anulación lógica de registros con trazabilidad de usuario y motivo.

Esta arquitectura asegura la escalabilidad del MVP de Antigravity, permitiendo un crecimiento modular según las necesidades del cliente.

## Fuentes y Referencias de Investigación:

Este informe ha sido elaborado bajo estándares industriales de desarrollo de software POS y arquitecturas basadas en la nube.

*   **Protocolo Akhana Admin "lock.md":** Fuente primaria que define las reglas de negocio, gestión de inventarios por peso (gramos) y flujos de caja.
*   **Angular Signal-Based Architecture (AppSignal):** Base metodológica para la implementación del carrito reactivo, optimizando la performance y eliminando la necesidad de pipes asíncronos complejos.
*   **Real-Time Inventory in Apps Script (MageSheet):** Referencia técnica para el uso de `LockService` y la creación de logs de auditoría en Google Sheets para prevenir la pérdida de datos.
*   **Oracle Store 360 Developer Guide:** Estándar industrial consultado para la aplicación de los patrones **MVC (Model-View-Controller)** y **Command Pattern** en la gestión de transacciones POS, asegurando que cada acción en el carrito sea una unidad lógica procesable y reversible.

La coherencia de la información presentada garantiza que Akhana Admin sea una solución de alto valor estratégico, técnicamente viable y comercialmente disruptiva.