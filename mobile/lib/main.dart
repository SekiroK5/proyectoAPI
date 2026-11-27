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

  /// Mismo azul de marca que la app web.
  static const _semilla = Color(0xFF2754E6);

  ThemeData _tema(Brightness brillo) {
    final colores = ColorScheme.fromSeed(seedColor: _semilla, brightness: brillo);
    final redondeado = RoundedRectangleBorder(borderRadius: BorderRadius.circular(12));
    return ThemeData(
      colorScheme: colores,
      scaffoldBackgroundColor: brillo == Brightness.light ? const Color(0xFFF3F5FA) : const Color(0xFF0B1020),
      cardTheme: CardThemeData(
        elevation: 0,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(18),
          side: BorderSide(color: colores.outlineVariant),
        ),
      ),
      inputDecorationTheme: InputDecorationTheme(
        filled: true,
        fillColor: colores.surface,
        border: OutlineInputBorder(borderRadius: BorderRadius.circular(12)),
        enabledBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(12),
          borderSide: BorderSide(color: colores.outlineVariant),
        ),
      ),
      filledButtonTheme: FilledButtonThemeData(
        style: FilledButton.styleFrom(minimumSize: const Size.fromHeight(50), shape: redondeado,
            textStyle: const TextStyle(fontWeight: FontWeight.w700, fontSize: 16)),
      ),
      outlinedButtonTheme: OutlinedButtonThemeData(
        style: OutlinedButton.styleFrom(minimumSize: const Size.fromHeight(48), shape: redondeado),
      ),
      chipTheme: ChipThemeData(shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(999))),
      navigationBarTheme: NavigationBarThemeData(indicatorColor: colores.primaryContainer),
    );
  }

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
