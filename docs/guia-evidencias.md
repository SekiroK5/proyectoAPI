# Guía de ejecución y evidencias

Pasos para levantar la plataforma en local y escenarios a grabar o capturar como evidencia.

## 1. Requisitos

- Java 17 o superior, PostgreSQL 16 (base `practica1_db`)
- Node.js 20 o superior (app web)
- Flutter 3.47 o superior y un emulador o teléfono Android (app móvil)
- Una app autenticadora en el teléfono (Google Authenticator o Microsoft Authenticator)

## 2. Levantar los tres proyectos

### API

```bash
cp .env.example .env        # ajustar usuario/contraseña de PostgreSQL si es necesario
./gradlew bootRun           # Windows: gradlew.bat bootRun
```

La API queda en `http://localhost:8081` (Swagger en `/swagger-ui.html`). Flyway crea las tablas al arrancar.
Para que la app web y la móvil (si se prueba en navegador) puedan llamar a la API, `CORS_ORIGENES` debe incluir
su origen, por ejemplo `CORS_ORIGENES=http://localhost:5173`.

### App web

```bash
cd web
cp .env.example .env        # VITE_API_URL=http://localhost:8081
npm install
npm run dev                 # http://localhost:5173
```

### App móvil

```bash
cd mobile
flutter pub get
flutter run                                              # emulador: usa http://10.0.2.2:8081
flutter run --dart-define=API_URL=http://IP-DE-LA-PC:8081  # teléfono físico en la misma red
```

> **Importante:** GestoPago bloquea la IP si se rebasan sus límites (por ejemplo, más de 3 consultas del
> catálogo al día). La API ya aplica esos límites; no reiniciarla muchas veces seguidas sin necesidad. Si la IP
> está bloqueada, el catálogo aparece vacío y los pagos quedan pendientes: hay que pedir el desbloqueo a PuntoRed.

## 3. Escenarios de evidencia

Repetir cada escenario en la web y en la app móvil con **el mismo usuario** para demostrar que comparten
credenciales e información.

| # | Escenario | Pasos | Resultado esperado |
|---|---|---|---|
| 1 | Registro | Crear cuenta desde "Regístrese" | Mensaje de cuenta creada; la cuenta inicia con el saldo configurado |
| 2 | Primer inicio de sesión | Usuario/correo + contraseña | Se muestra el QR; escanearlo y escribir el código de 6 dígitos |
| 3 | Inicio de sesión normal | Usuario + contraseña + código | Entra al catálogo; el nombre de usuario aparece en la barra |
| 4 | Contraseña o código incorrecto | Escribir datos erróneos | Mensaje de error; tras 5 intentos el usuario se bloquea 15 min |
| 5 | Catálogo | Buscar, filtrar por Recargas/Servicios y por categoría | Tarjetas con logotipo, categoría y precio de recarga |
| 6 | Referencia obligatoria | Abrir un servicio sin verificar la referencia | El botón "Continuar" está deshabilitado |
| 7 | Verificación de referencia | Escribir referencia y "Verificar" | Aviso de referencia válida (o formato válido) y, si aplica, adeudo |
| 8 | Resumen y confirmación | Continuar con importe | Resumen con servicio, referencia, cuenta de cargo e importe; pide código 2FA |
| 9 | Código 2FA erróneo al pagar | Escribir un código incorrecto | Permanece en la confirmación con el error |
| 10 | Pago exitoso | Confirmar con el código correcto | Aviso verde, folio de autorización y saldo actualizado |
| 11 | Pago rechazado | Recarga a un número no válido o sin saldo | Aviso rojo con el motivo; el saldo no cambia |
| 12 | Pago pendiente | GestoPago no responde a tiempo | Aviso naranja; luego cambia a exitoso o rechazado en el historial |
| 13 | Historial | Abrir Historial, filtrar por estatus y abrir un detalle | Solo operaciones del usuario, con folio, UPC y fecha |
| 14 | Depósito | Mi cuenta → importe + código 2FA | Mensaje de depósito y saldo actualizado |
| 15 | Mismo usuario en ambas apps | Pagar en la web y abrir el historial en el móvil | La operación aparece en las dos |
| 16 | Cerrar sesión | Botón de salir | Regresa al inicio de sesión; el token anterior ya no sirve (401) |
| 17 | Diseño responsivo | Web en ancho de teléfono; móvil en vertical y tableta | Menú adaptado sin desbordes |

## 4. Checklist de entrega

- [ ] Repositorio con la API, `web/`, `mobile/` y `docs/`
- [ ] Documento técnico (`docs/documento-tecnico.md`)
- [ ] Video o capturas de los escenarios anteriores en web y móvil
- [ ] Sin credenciales reales en el repositorio (solo `.env.example`)
