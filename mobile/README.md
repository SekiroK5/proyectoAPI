# GestoPago Pagos — app móvil (Flutter)

App Android (Material Design 3) de la plataforma de pagos. Consume la **misma API Spring** que la app web,
así que los usuarios y credenciales son los mismos en ambas.

## Requisitos

- Flutter 3.47 / Dart 3.13 o superior
- La API corriendo (por defecto en el puerto 8081)

## Ejecutar

```bash
flutter pub get
# Emulador Android: 10.0.2.2 es el localhost de la computadora (valor por defecto)
flutter run
# Teléfono físico en la misma red: usar la IP de la computadora
flutter run --dart-define=API_URL=http://192.168.1.50:8081
```

La URL de la API se pasa con `--dart-define=API_URL=...`; no hay credenciales en el código.

## Pruebas

```bash
flutter analyze
flutter test
```

## Estructura

- `lib/api/` cliente HTTP, modelos y servicios (un método por endpoint)
- `lib/sesion/` sesión con el token en almacenamiento seguro (Keystore) y cierre automático al vencer
- `lib/pantallas/` login con 2FA, registro, catálogo, pago, historial/detalle y cuenta
- `lib/ui/` componentes comunes (logo, estatus, avisos, campo del código 2FA)
