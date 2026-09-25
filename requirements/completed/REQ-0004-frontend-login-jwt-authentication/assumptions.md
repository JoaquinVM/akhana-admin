# Supuestos y Condiciones Previas - REQ-0004

1. **Disponibilidad del Backend:**
   - Se asume que el backend Spring Boot corre en `http://localhost:8080`.
   - Se configura un archivo de entorno o constante base URL (`/api` o `http://localhost:8080/api`) para facilitar la comunicación y proxying de desarrollo si es necesario.
   - El endpoint `POST /api/auth/login` acepta `{ username: string, password: string }` y retorna `{ token: string, id: string, username: string, role: string, message: string }`.

2. **Almacenamiento Local Seguro en el Navegador:**
   - Se utiliza `localStorage` para persistir el JWT y los datos de sesión básica del usuario (`akhana_token` y `akhana_user`), permitiendo que la sesión se restaure automáticamente al recargar la página (`F5`).
   - El almacenamiento no almacena contraseñas ni datos sensibles adicionales.

3. **CORS:**
   - En `SecurityConfig.java` del backend, se verificará que las solicitudes provenientes de `http://localhost:4200` estén permitidas o se configurará proxy en Angular para evitar bloqueos por CORS en desarrollo.

4. **Identidad Visual del Logo:**
   - El archivo de imagen proporcionado se encuentra disponible en `frontend/public/images/akhana-logo.png`.
   - Paleta de color extraída de la imagen:
     - Color primario corporativo: Verde Bosque profundo (`#2E5B27`, `#1E3F19`).
     - Color secundario / Acento: Amarillo Dorado Zen (`#F5B800`, `#E6A800`).
     - Hojas botánicas: Verde fresco (`#4E873B`, `#7BB142`).
     - Fondos: Blanco cálido y degradados sutiles naturales (`#FAFAF8`, `#F2F5F0`).
