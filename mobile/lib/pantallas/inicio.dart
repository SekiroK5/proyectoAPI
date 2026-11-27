import 'package:flutter/material.dart';

import '../ui/dependencias.dart';
import '../ui/navegacion.dart';
import 'catalogo.dart';
import 'cuenta.dart';
import 'historial.dart';

/// Pantalla principal: barra inferior en telefonos y riel lateral en tabletas o pantallas anchas.
class PantallaInicio extends StatelessWidget {
  const PantallaInicio({super.key});

  static const _destinos = [
    (Icons.storefront_outlined, Icons.storefront, 'Catálogo'),
    (Icons.receipt_long_outlined, Icons.receipt_long, 'Historial'),
    (Icons.account_balance_wallet_outlined, Icons.account_balance_wallet, 'Mi cuenta'),
  ];

  @override
  Widget build(BuildContext context) {
    final sesion = Dependencias.of(context).sesion;
    return ValueListenableBuilder<Seccion>(
      valueListenable: seccionActual,
      builder: (context, seccion, _) {
        final indice = seccion.index;
        final cuerpo = switch (seccion) {
          Seccion.catalogo => const PantallaCatalogo(),
          Seccion.historial => const PantallaHistorial(),
          Seccion.cuenta => const PantallaCuenta(),
        };
        void elegir(int i) => seccionActual.value = Seccion.values[i];

        return LayoutBuilder(builder: (context, medidas) {
          final ancho = medidas.maxWidth >= 720;
          return Scaffold(
            appBar: AppBar(
              title: Text(_destinos[indice].$3),
              actions: [
                Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 8),
                  child: Center(child: Text(sesion.nombreUsuario ?? '')),
                ),
                IconButton(icon: const Icon(Icons.logout), tooltip: 'Cerrar sesión', onPressed: () => sesion.cerrar()),
              ],
            ),
            body: ancho
                ? Row(
                    children: [
                      NavigationRail(
                        selectedIndex: indice,
                        onDestinationSelected: elegir,
                        labelType: NavigationRailLabelType.all,
                        destinations: [
                          for (final (icono, activo, texto) in _destinos)
                            NavigationRailDestination(icon: Icon(icono), selectedIcon: Icon(activo), label: Text(texto)),
                        ],
                      ),
                      const VerticalDivider(width: 1),
                      Expanded(child: KeyedSubtree(key: ValueKey(seccion), child: cuerpo)),
                    ],
                  )
                : KeyedSubtree(key: ValueKey(seccion), child: cuerpo),
            bottomNavigationBar: ancho
                ? null
                : NavigationBar(
                    selectedIndex: indice,
                    onDestinationSelected: elegir,
                    destinations: [
                      for (final (icono, activo, texto) in _destinos)
                        NavigationDestination(icon: Icon(icono), selectedIcon: Icon(activo), label: texto),
                    ],
                  ),
          );
        });
      },
    );
  }
}
