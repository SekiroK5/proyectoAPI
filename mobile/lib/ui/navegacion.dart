import 'package:flutter/foundation.dart';

/// Secciones de la pantalla principal.
enum Seccion { catalogo, historial, cuenta }

/// Seccion visible; las pantallas de pago la cambian para llevar al usuario a su historial.
final seccionActual = ValueNotifier<Seccion>(Seccion.catalogo);
