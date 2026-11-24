import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:intl/date_symbol_data_local.dart';

import 'api/api_cliente.dart';
import 'api/servicios.dart';
import 'pantallas/inicio.dart';
import 'pantallas/login.dart';
import 'sesion/sesion.dart';
import 'ui/dependencias.dart';
import 'ui/navegacion.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  await initializeDateFormatting('es_MX');
  final sesion = Sesion(Servicios(ApiCliente()));
  // Al cerrar sesion, el siguiente usuario empieza en el catalogo
  sesion.addListener(() {
    if (!sesion.activa) seccionActual.value = Seccion.catalogo;
  });
  runApp(GestoPagoApp(sesion: sesion));
  await sesion.cargar();
}

class GestoPagoApp extends StatelessWidget {
  const GestoPagoApp({super.key, required this.sesion});

  final Sesion sesion;

  static const _semilla = Color(0xFF0B6E4F);

  ThemeData _tema(Brightness brillo) => ThemeData(
        colorScheme: ColorScheme.fromSeed(seedColor: _semilla, brightness: brillo),
        inputDecorationTheme: const InputDecorationTheme(border: OutlineInputBorder()),
      );

  @override
  Widget build(BuildContext context) {
    return Dependencias(
      sesion: sesion,
      child: MaterialApp(
        title: 'GestoPago Pagos',
        debugShowCheckedModeBanner: false,
        theme: _tema(Brightness.light),
        darkTheme: _tema(Brightness.dark),
        locale: const Locale('es', 'MX'),
        supportedLocales: const [Locale('es', 'MX'), Locale('en')],
        localizationsDelegates: GlobalMaterialLocalizations.delegates,
        home: ListenableBuilder(
          listenable: sesion,
          builder: (context, _) {
            if (sesion.cargando) return const Scaffold(body: Center(child: CircularProgressIndicator()));
            return sesion.activa ? const PantallaInicio() : const PantallaLogin();
          },
        ),
      ),
    );
  }
}
