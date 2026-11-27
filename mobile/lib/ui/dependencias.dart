import 'package:flutter/widgets.dart';

import '../api/servicios.dart';
import '../sesion/sesion.dart';

/// Hace disponibles la sesion y los servicios de la API para todas las pantallas.
class Dependencias extends InheritedWidget {
  const Dependencias({super.key, required this.sesion, required super.child});

  final Sesion sesion;

  Servicios get servicios => sesion.servicios;

  static Dependencias of(BuildContext context) {
    final resultado = context.dependOnInheritedWidgetOfExactType<Dependencias>();
    assert(resultado != null, 'Falta Dependencias arriba en el arbol');
    return resultado!;
  }

  @override
  bool updateShouldNotify(Dependencias oldWidget) => sesion != oldWidget.sesion;
}
