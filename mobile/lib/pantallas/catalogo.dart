import 'package:flutter/material.dart';

import '../api/modelos.dart';
import '../ui/comunes.dart';
import '../ui/dependencias.dart';
import 'pago.dart';

/// Catalogo de GestoPago (obtenido de la API) con logotipos, categorias, busqueda y filtros.
class PantallaCatalogo extends StatefulWidget {
  const PantallaCatalogo({super.key});

  @override
  State<PantallaCatalogo> createState() => _PantallaCatalogoState();
}

class _PantallaCatalogoState extends State<PantallaCatalogo> {
  List<Producto>? _productos;
  String? _error;
  String _busqueda = '';
  String _tipo = 'TODOS';
  String? _categoria;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    if (_productos == null && _error == null) _cargar();
  }

  Future<void> _cargar() async {
    setState(() => _error = null);
    try {
      final lista = await Dependencias.of(context).servicios.catalogo();
      if (mounted) setState(() => _productos = lista);
    } catch (e) {
      if (mounted) setState(() => _error = mensajeDeError(e));
    }
  }

  @override
  Widget build(BuildContext context) {
    if (_error != null) {
      return _centrado(Column(mainAxisSize: MainAxisSize.min, children: [
        Aviso(_error!, tipo: TipoAviso.error),
        const SizedBox(height: 12),
        FilledButton(onPressed: _cargar, child: const Text('Reintentar')),
      ]));
    }
    final productos = _productos;
    if (productos == null) return const Center(child: CircularProgressIndicator());

    final categorias = productos.map((p) => p.categoria).toSet().toList()..sort();
    final texto = _busqueda.toLowerCase();
    final visibles = productos
        .where((p) =>
            (_tipo == 'TODOS' || p.tipoOperacion == _tipo) &&
            (_categoria == null || p.categoria == _categoria) &&
            (texto.isEmpty || '${p.servicio} ${p.producto} ${p.categoria}'.toLowerCase().contains(texto)))
        .toList();

    return RefreshIndicator(
      onRefresh: _cargar,
      child: CustomScrollView(
        slivers: [
          SliverPadding(
            padding: const EdgeInsets.all(16),
            sliver: SliverToBoxAdapter(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  TextField(
                    decoration: const InputDecoration(prefixIcon: Icon(Icons.search), hintText: 'Buscar servicio, producto o categoría'),
                    onChanged: (v) => setState(() => _busqueda = v.trim()),
                  ),
                  const SizedBox(height: 12),
                  SingleChildScrollView(
                    scrollDirection: Axis.horizontal,
                    child: SegmentedButton<String>(
                      segments: const [
                        ButtonSegment(value: 'TODOS', label: Text('Todos')),
                        ButtonSegment(value: 'RECARGA', label: Text('Recargas'), icon: Icon(Icons.phone_android)),
                        ButtonSegment(value: 'PAGO_SERVICIO', label: Text('Servicios'), icon: Icon(Icons.receipt_long)),
                      ],
                      selected: {_tipo},
                      onSelectionChanged: (s) => setState(() => _tipo = s.first),
                    ),
                  ),
                  const SizedBox(height: 12),
                  DropdownButton<String?>(
                    value: _categoria,
                    isExpanded: true,
                    hint: const Text('Todas las categorías'),
                    items: [
                      const DropdownMenuItem(value: null, child: Text('Todas las categorías')),
                      for (final c in categorias) DropdownMenuItem(value: c, child: Text(c)),
                    ],
                    onChanged: (v) => setState(() => _categoria = v),
                  ),
                  const SizedBox(height: 8),
                  Text('${visibles.length} de ${productos.length} productos', style: TextStyle(color: Theme.of(context).hintColor)),
                  if (productos.isEmpty) ...[
                    const SizedBox(height: 12),
                    const Aviso('El catálogo aún no se ha descargado de GestoPago. La API lo actualiza una vez al día; intente más tarde.'),
                  ],
                ],
              ),
            ),
          ),
          SliverPadding(
            padding: const EdgeInsets.fromLTRB(16, 0, 16, 24),
            sliver: SliverGrid.builder(
              gridDelegate: const SliverGridDelegateWithMaxCrossAxisExtent(
                maxCrossAxisExtent: 240,
                mainAxisExtent: 200,
                crossAxisSpacing: 12,
                mainAxisSpacing: 12,
              ),
              itemCount: visibles.length,
              itemBuilder: (context, i) => _TarjetaProducto(producto: visibles[i]),
            ),
          ),
        ],
      ),
    );
  }

  Widget _centrado(Widget hijo) => Center(child: Padding(padding: const EdgeInsets.all(24), child: hijo));
}

class _TarjetaProducto extends StatelessWidget {
  const _TarjetaProducto({required this.producto});

  final Producto producto;

  @override
  Widget build(BuildContext context) {
    final p = producto;
    return Card(
      clipBehavior: Clip.antiAlias,
      child: InkWell(
        onTap: () => Navigator.of(context).push(MaterialPageRoute(builder: (_) => PantallaPago(producto: p))),
        child: Padding(
          padding: const EdgeInsets.all(12),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Logo(url: p.logoUrl, nombre: p.servicio),
              const SizedBox(height: 8),
              Text(p.servicio, maxLines: 1, overflow: TextOverflow.ellipsis, style: const TextStyle(fontWeight: FontWeight.bold)),
              Text(p.producto, maxLines: 2, overflow: TextOverflow.ellipsis),
              Text('${p.categoria} · #${p.idServicio}-${p.idProducto}',
                  maxLines: 1, overflow: TextOverflow.ellipsis, style: TextStyle(fontSize: 12, color: Theme.of(context).hintColor)),
              const Spacer(),
              Row(
                children: [
                  Chip(
                    label: Text(p.esRecarga ? 'Recarga' : 'Servicio'),
                    visualDensity: VisualDensity.compact,
                    padding: EdgeInsets.zero,
                  ),
                  const Spacer(),
                  if (p.esRecarga && p.precio != null) Text(dinero(p.precio), style: const TextStyle(fontWeight: FontWeight.bold)),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }
}
