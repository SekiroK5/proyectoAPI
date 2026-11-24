import 'package:flutter/material.dart';

import '../api/modelos.dart';
import '../ui/comunes.dart';
import '../ui/dependencias.dart';

/// Historial de pagos del usuario (la API solo devuelve las operaciones de su cuenta), con filtro por estatus.
class PantallaHistorial extends StatefulWidget {
  const PantallaHistorial({super.key});

  @override
  State<PantallaHistorial> createState() => _PantallaHistorialState();
}

class _PantallaHistorialState extends State<PantallaHistorial> {
  static const _tamanio = 20;

  final _transacciones = <Transaccion>[];
  int _pagina = 0;
  int _totalPaginas = 1;
  String? _estatus;
  String? _error;
  bool _cargando = false;
  bool _iniciado = false;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    if (!_iniciado) {
      _iniciado = true;
      _reiniciar();
    }
  }

  Future<void> _reiniciar() async {
    setState(() {
      _transacciones.clear();
      _pagina = 0;
      _totalPaginas = 1;
    });
    await _cargarPagina();
  }

  Future<void> _cargarPagina() async {
    if (_cargando || _pagina >= _totalPaginas) return;
    setState(() {
      _cargando = true;
      _error = null;
    });
    try {
      final r = await Dependencias.of(context).servicios.historial(_pagina, _tamanio, estatus: _estatus);
      if (!mounted) return;
      setState(() {
        _transacciones.addAll(r.datos.contenido);
        _totalPaginas = r.datos.totalPaginas;
        _pagina++;
      });
    } catch (e) {
      if (mounted) setState(() => _error = mensajeDeError(e));
    } finally {
      if (mounted) setState(() => _cargando = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final hayMas = _pagina < _totalPaginas;
    return RefreshIndicator(
      onRefresh: _reiniciar,
      child: ListView.builder(
        padding: const EdgeInsets.all(16),
        itemCount: _transacciones.length + 2,
        itemBuilder: (context, i) {
          if (i == 0) return _filtros();
          if (i == _transacciones.length + 1) return _pie(hayMas);
          final tx = _transacciones[i - 1];
          return Card(
            child: ListTile(
              leading: Logo(url: tx.logoUrl, nombre: tx.nombreServicio, tamanio: 44),
              title: Text(tx.nombreServicio, maxLines: 1, overflow: TextOverflow.ellipsis),
              subtitle: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(fecha(tx.fecha), maxLines: 1, overflow: TextOverflow.ellipsis),
                  const SizedBox(height: 4),
                  EstatusChip(estatus: tx.estatus),
                ],
              ),
              isThreeLine: true,
              trailing: Text(dinero(tx.monto), style: const TextStyle(fontWeight: FontWeight.bold)),
              onTap: () => Navigator.of(context).push(MaterialPageRoute(builder: (_) => PantallaDetalle(id: tx.id))),
            ),
          );
        },
      ),
    );
  }

  Widget _filtros() {
    const opciones = <(String?, String)>[
      (null, 'Todos'),
      ('APLICADA', 'Exitosos'),
      ('RECHAZADA', 'Rechazados'),
      ('PENDIENTE_CONFIRMACION', 'Pendientes'),
    ];
    return Padding(
      padding: const EdgeInsets.only(bottom: 12),
      child: Wrap(
        spacing: 8,
        runSpacing: 8,
        children: [
          for (final (valor, etiqueta) in opciones)
            ChoiceChip(
              label: Text(etiqueta),
              selected: _estatus == valor,
              onSelected: (_) {
                setState(() => _estatus = valor);
                _reiniciar();
              },
            ),
        ],
      ),
    );
  }

  Widget _pie(bool hayMas) {
    if (_error != null) {
      return Column(children: [
        Aviso(_error!, tipo: TipoAviso.error),
        TextButton(onPressed: _cargarPagina, child: const Text('Reintentar')),
      ]);
    }
    if (_cargando) return const Padding(padding: EdgeInsets.all(24), child: Center(child: CircularProgressIndicator()));
    if (_transacciones.isEmpty) return const Aviso('Aún no tiene pagos registrados.');
    if (hayMas) return OutlinedButton(onPressed: _cargarPagina, child: const Text('Cargar más'));
    return const SizedBox.shrink();
  }
}

/// Detalle de una transaccion del historial.
class PantallaDetalle extends StatefulWidget {
  const PantallaDetalle({super.key, required this.id});

  final int id;

  @override
  State<PantallaDetalle> createState() => _PantallaDetalleState();
}

class _PantallaDetalleState extends State<PantallaDetalle> {
  Transaccion? _tx;
  String? _error;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    if (_tx == null && _error == null) _cargar();
  }

  Future<void> _cargar() async {
    try {
      final r = await Dependencias.of(context).servicios.detalle(widget.id);
      if (mounted) setState(() => _tx = r.datos);
    } catch (e) {
      if (mounted) setState(() => _error = mensajeDeError(e));
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Detalle del pago')),
      body: SafeArea(
        child: _error != null
            ? Padding(padding: const EdgeInsets.all(16), child: Aviso(_error!, tipo: TipoAviso.error))
            : _tx == null
                ? const Center(child: CircularProgressIndicator())
                : Center(
                    child: ConstrainedBox(
                      constraints: const BoxConstraints(maxWidth: 640),
                      child: ListView(
                        padding: const EdgeInsets.all(16),
                        children: [Card(child: Padding(padding: const EdgeInsets.all(16), child: DetalleTransaccion(tx: _tx!)))],
                      ),
                    ),
                  ),
      ),
    );
  }
}
