# Estrategia de Pruebas Automatizadas - REQ-0005: Menú de Navegación Principal

## 🧪 Enfoque de Pruebas
Las pruebas unitarias y de componentes se implementarán con **Vitest** en el frontend de Angular 21.

---

## 📋 Matriz de Casos de Prueba

### 1. `NavbarComponent` (`src/app/layout/navbar/navbar.component.spec.ts`)
| ID | Descripción del Test | Comportamiento Esperado |
|:---|:---|:---|
| `TEST-NAV-01` | Renderizado de grupos principales | Renderiza los 3 grupos: "Ventas", "Catálogo" y "Seguridad". |
| `TEST-NAV-02` | Renderizado de subopciones y enlaces | Contiene enlaces a `/pos`, `/sales`, `/products`, `/categories`, `/tags`, `/users`. |
| `TEST-NAV-03` | Detección reactiva de grupo activo | Retorna `true` en `isGroupActive()` cuando la URL actual coincide con una subopción. |
| `TEST-NAV-04` | Visualización de sesión del usuario | Muestra el nombre de usuario y badge de rol provisto por `AuthService`. |
| `TEST-NAV-05` | Acción de cierre de sesión | Invoca `authService.logout()` al pulsar el botón "Cerrar Sesión". |

### 2. `SectionPageComponent` (`src/app/pages/section-page/section-page.component.spec.ts`)
| ID | Descripción del Test | Comportamiento Esperado |
|:---|:---|:---|
| `TEST-SEC-01` | Despliegue de título de ruta | Lee `route.data['title']` y lo renderiza en un encabezado `<h1>`. |
| `TEST-SEC-02` | Fallback de título | Si no hay título en la data de ruta, muestra un valor predeterminado seguro. |

### 3. `MainLayoutComponent` (`src/app/layout/main-layout/main-layout.component.spec.ts`)
| ID | Descripción del Test | Comportamiento Esperado |
|:---|:---|:---|
| `TEST-LAY-01` | Estructura de layout | Renderiza el selector `<app-navbar>` y el `<router-outlet>`. |

---

## 🎯 Criterio de Éxito
- 100% de pruebas pasando en Vitest sin regresiones en las suites existentes (`app.spec.ts`, `auth.*.spec.ts`, `login.component.spec.ts`).
