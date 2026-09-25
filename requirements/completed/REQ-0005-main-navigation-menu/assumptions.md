# Supuestos Técnicos - REQ-0005: Menú de Navegación Principal

## Supuestos Establecidos

1. **Arquitectura de Layout:**
   - La aplicación utilizará un layout autenticado común (`MainLayoutComponent` o componente de navegación superior insertado en el flujo de la aplicación) que envuelve el encabezado superior con el menú y un `<router-outlet />` para las secciones internas.
   - Las pantallas que no requieran este menú (como `/login`) se mantienen independientes sin el encabezado superior.

2. **Manejo del Hover y Accesibilidad:**
   - El despliegue de las opciones del menú se gestionará principalmente mediante CSS (`:hover` y `:focus-within`) junto con control de estado si es necesario para evitar cierres accidentales al mover el puntero a través del dropdown.
   - Cada enlace utiliza directivas `routerLink` y `routerLinkActive` nativas de Angular para máxima consistencia y rendimiento.

3. **Determinación del Grupo Activo:**
   - Se evaluará reactivamente la URL actual del `Router` frente a las rutas hijas de cada grupo para asignar la clase `.active` al botón o disparador del grupo padre.

4. **Componente de Contenedor de Sección (*Section Shell*):**
   - Para no crear 6 componentes repetitivos vacíos de un solo renglón, se creará un componente reutilizable o vistas limpias standalone (`SectionPlaceholderComponent` o componentes por ruta) que muestren el título estilizado correspondiente con la tipografía y paleta de Akhana.

5. **Compatibilidad con `AuthService`:**
   - El botón de "Cerrar sesión" y la identificación del usuario (`currentUser`, `userRole`) se integrarán en el extremo derecho de la barra de navegación superior, permitiendo que el usuario conserve la funcionalidad de logout probada en `REQ-0004`.
