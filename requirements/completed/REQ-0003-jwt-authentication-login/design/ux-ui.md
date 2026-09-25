# UX/UI Design - REQ-0003: Autenticación JWT

## UI Impact: NONE

Este requerimiento es de naturaleza 100% backend (criptografía, generación de tokens JWT, filtros de seguridad de Spring Security y configuración de propiedades).
No altera ni añade vistas o componentes en la capa `frontend/`.

- **Stitch MCP:** Omitido (no aplica diseño visual de interfaz).
- **Consumo:** El token devuelto en `LoginResponse.token` servirá como insumo para el frontend en el requerimiento posterior donde se implemente la pantalla de login en Angular y el interceptor HTTP.
