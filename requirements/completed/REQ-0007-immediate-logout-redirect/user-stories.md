# Historias de Usuario - REQ-0007: Redirección Inmediata a Login al Cerrar Sesión

## US-01: Redirección Inmediata al Cerrar Sesión
**Como** usuario autenticado en el sistema,  
**Quiero** que al hacer clic en "Cerrar Sesión" sea enviado inmediatamente a la pantalla de Login,  
**Para** garantizar que mi sesión queda completamente terminada y nadie pueda continuar operando en la pantalla actual.

### Criterios de Aceptación (Gherkin):
```gherkin
Escenario: Cierre de sesión exitoso con redirección inmediata
  Dado que un usuario se encuentra autenticado en cualquier pantalla del sistema (ej. /suppliers)
  Cuando hace clic en el botón "Cerrar Sesión" en la barra superior
  Entonces se debe limpiar el almacenamiento local (tokens y datos de sesión)
  Y los signals de autenticación deben actualizarse a null/false
  Y el sistema debe redirigir inmediatamente a la ruta /login
  Y no se debe permitir la interacción en la pantalla previa sin volver a iniciar sesión
```
