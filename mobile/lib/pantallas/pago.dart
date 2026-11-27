import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../api/api_cliente.dart';
import '../api/modelos.dart';
import '../ui/comunes.dart';
import '../ui/dependencias.dart';
import '../ui/navegacion.dart';

enum _Paso { datos, resumen, resultado }

enum _TipoResultado { exito, pendiente, rechazo }

/// Flujo de pago (igual que en la web):
/// - Recarga: telefono -> resumen -> confirmacion con 2FA -> resultado.
/// - Servicio: referencia -> verificacion (API) -> importe -> resumen -> confirmacion con 2FA -> resultado.
/// No se puede continuar con un servicio cuya referencia no se haya verificado.
class PantallaPago extends StatefulWidget {
  const PantallaPago({super.key, required this.producto});

  final Producto producto;

  @override
  State<PantallaPago> createState() => _PantallaPagoState();
}

class _PantallaPagoState extends State<PantallaPago> {
  final _telefono = TextEditingController();
  final _referencia = TextEditingController();
  final _monto = TextEditingController();
  final _codigo = TextEditingController();
  final _formCodigo = GlobalKey<FormState>();

  _Paso _paso = _Paso.datos;
  Verificacion? _verificacion;
  String? _error;
  bool _trabajando = false;

  _TipoResultado? _tipoResultado;
  String _mensajeResultado = '';
  Transaccion? _tx;

  Producto get p => widget.producto;

  @override
  void initState() {
    super.initState();
    if (p.esRecarga && p.precio != null) _monto.text = p.precio!.toStringAsFixed(2);
  }

  @override
  void dispose() {
    _telefono.dispose();
    _referencia.dispose();
    _monto.dispose();
    _codigo.dispose();
    super.dispose();
  }

  Future<void> _verificar() async {
    final referencia = _referencia.text.trim();
    setState(() {
      _error = null;
      _verificacion = null;
    });
    if (referencia.isEmpty) {
      setState(() => _error = 'Escriba la referencia.');
      return;
    }
    if (p.referenciaEsTelefono && !RegExp(r'^\d{10}$').hasMatch(referencia)) {
      setState(() => _error = 'La referencia de este servicio es un número de teléfono de 10 dígitos.');
      return;
    }
    setState(() => _trabajando = true);
    try {
      final r = await Dependencias.of(context).servicios.verificarReferencia(p.idServicio, p.idProducto, referencia);
      setState(() {
        _verificacion = r.datos;
        final pendiente = r.datos.saldoPendiente;
        if (pendiente != null && pendiente > 0) _monto.text = pendiente.toStringAsFixed(2);
      });
    } catch (e) {
      setState(() => _error = mensajeDeError(e));
    } finally {
      if (mounted) setState(() => _trabajando = false);
    }
  }

  void _aResumen() {
    setState(() => _error = null);
    if (p.esRecarga && !p.sinTelefono && !RegExp(r'^\d{10}$').hasMatch(_telefono.text)) {
      setState(() => _error = 'Escriba un número de teléfono de 10 dígitos.');
      return;
    }
    if (p.esServicio && _verificacion == null) {
      setState(() => _error = 'Primero verifique la referencia.');
      return;
    }
    final monto = _monto.text.trim();
    if (!RegExp(r'^\d{1,10}(\.\d{1,2})?$').hasMatch(monto) || double.parse(monto) <= 0) {
      setState(() => _error = 'Escriba un importe válido (máximo 2 decimales).');
      return;
    }
    setState(() {
      _codigo.clear();
      _paso = _Paso.resumen;
    });
  }

  Future<void> _confirmar() async {
    if (!_formCodigo.currentState!.validate()) return;
    final dependencias = Dependencias.of(context);
    setState(() {
      _error = null;
      _trabajando = true;
    });
    try {
      final r = await dependencias.servicios.pagar(
        numeroCuenta: dependencias.sesion.numeroCuenta!,
        idServicio: p.idServicio,
        idProducto: p.idProducto,
        monto: double.parse(_monto.text.trim()),
        telefono: p.esServicio || p.sinTelefono ? null : _telefono.text,
        referencia: p.esServicio ? _verificacion!.referencia : null,
        folioVerificacion: p.esServicio ? _verificacion!.folio : null,
        codigoA2F: _codigo.text,
      );
      // Nunca se muestra exito sin la confirmacion de GestoPago (estatus APLICADA)
      setState(() {
        _tx = r.datos;
        _mensajeResultado = r.mensaje;
        _tipoResultado = r.datos.estatus == 'APLICADA' ? _TipoResultado.exito : _TipoResultado.pendiente;
        _paso = _Paso.resultado;
      });
    } on ApiException catch (e) {
      // Errores del 2FA: se queda en la confirmacion para corregir el codigo
      final esDel2FA = [401, 409, 423].contains(e.status) &&
          RegExp('2FA|código|bloquead|intentos', caseSensitive: false).hasMatch(e.mensaje);
      setState(() {
        if (esDel2FA) {
          _error = e.mensaje;
          _codigo.clear();
        } else {
          _tipoResultado = _TipoResultado.rechazo;
          _mensajeResultado = e.mensaje;
          _paso = _Paso.resultado;
        }
      });
    } catch (e) {
      setState(() {
        _tipoResultado = _TipoResultado.rechazo;
        _mensajeResultado = mensajeDeError(e);
        _paso = _Paso.resultado;
      });
    } finally {
      if (mounted) setState(() => _trabajando = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return PopScope(
      canPop: !_trabajando,
      child: Scaffold(
        appBar: AppBar(title: Text(p.esServicio ? 'Pago de servicio' : 'Recarga')),
        body: SafeArea(
          child: Center(
            child: ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: 640),
              child: ListView(
                padding: const EdgeInsets.all(16),
                children: [
                  _encabezado(),
                  const SizedBox(height: 12),
                  IndicadorPasos(pasos: const ['Datos', 'Confirmación', 'Resultado'], actual: _paso.index),
                  const SizedBox(height: 12),
                  Card(
                    child: Padding(
                      padding: const EdgeInsets.all(16),
                      child: switch (_paso) {
                        _Paso.datos => _datos(),
                        _Paso.resumen => _resumen(),
                        _Paso.resultado => _resultado(),
                      },
                    ),
                  ),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }

  Widget _encabezado() => Card(
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Row(
            children: [
              Logo(url: p.logoUrl, nombre: p.servicio, tamanio: 64),
              const SizedBox(width: 12),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(p.servicio, style: Theme.of(context).textTheme.titleLarge),
                    Text(p.producto),
                    Text('${p.categoria} · ${p.esServicio ? 'Pago de servicio' : 'Recarga'}',
                        style: TextStyle(fontSize: 12, color: Theme.of(context).hintColor)),
                  ],
                ),
              ),
            ],
          ),
        ),
      );

  Widget _datos() {
    final titulo = Theme.of(context).textTheme.titleMedium;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        if (p.esServicio) ...[
          Text('1. Referencia', style: titulo),
          if (p.imagenReferenciaUrl != null)
            ExpansionTile(
              tilePadding: EdgeInsets.zero,
              title: const Text('¿Dónde encuentro la referencia?'),
              children: [
                Image.network(
                  p.imagenReferenciaUrl!,
                  semanticLabel: 'Ubicación de la referencia en el recibo',
                  errorBuilder: (_, _, _) => const Text('Imagen no disponible.'),
                ),
              ],
            ),
          TextField(
            controller: _referencia,
            maxLength: 100,
            keyboardType: p.referenciaEsTelefono ? TextInputType.number : TextInputType.text,
            inputFormatters: [FilteringTextInputFormatter.allow(RegExp(r'[A-Za-z0-9-]'))],
            decoration: InputDecoration(
              labelText: p.referenciaEsTelefono ? 'Número de teléfono (10 dígitos)' : 'Referencia del recibo',
            ),
            onChanged: (_) {
              if (_verificacion != null) setState(() => _verificacion = null);
            },
          ),
          OutlinedButton(
            onPressed: _trabajando ? null : _verificar,
            child: Text(_trabajando ? 'Verificando…' : 'Verificar referencia'),
          ),
          if (_verificacion != null) ...[
            const SizedBox(height: 8),
            Aviso(
              [
                _verificacion!.resultado == 'VALIDA' ? 'Referencia válida.' : 'Formato válido.',
                if (_verificacion!.mensaje != null) _verificacion!.mensaje!,
                if (_verificacion!.saldoPendiente != null) 'Adeudo informado: ${dinero(_verificacion!.saldoPendiente)}',
              ].join(' '),
              tipo: _verificacion!.resultado == 'VALIDA' ? TipoAviso.exito : TipoAviso.info,
            ),
          ],
          const SizedBox(height: 20),
        ],
        Text(p.esServicio ? '2. Importe' : 'Datos de la recarga', style: titulo),
        const SizedBox(height: 8),
        if (p.esRecarga && !p.sinTelefono)
          TextField(
            controller: _telefono,
            maxLength: 10,
            keyboardType: TextInputType.phone,
            inputFormatters: [FilteringTextInputFormatter.digitsOnly],
            decoration: const InputDecoration(labelText: 'Número de teléfono', prefixIcon: Icon(Icons.phone)),
          ),
        TextField(
          controller: _monto,
          enabled: p.esServicio,
          keyboardType: const TextInputType.numberWithOptions(decimal: true),
          inputFormatters: [FilteringTextInputFormatter.allow(RegExp(r'[0-9.]'))],
          decoration: InputDecoration(
            labelText: 'Importe',
            prefixText: r'$ ',
            helperText: p.esServicio ? null : 'El importe de la recarga es el precio del producto.',
          ),
        ),
        const SizedBox(height: 12),
        if (_error != null) ...[Aviso(_error!, tipo: TipoAviso.error), const SizedBox(height: 12)],
        FilledButton(
          onPressed: _trabajando || (p.esServicio && _verificacion == null) ? null : _aResumen,
          child: const Text('Continuar'),
        ),
      ],
    );
  }

  Widget _resumen() {
    final sesion = Dependencias.of(context).sesion;
    final monto = double.parse(_monto.text.trim());
    final filas = <(String, String)>[
      ('Servicio', '${p.servicio} — ${p.producto}'),
      if (p.esServicio) ('Referencia', _verificacion!.referencia),
      if (p.esRecarga && !p.sinTelefono) ('Teléfono', _telefono.text),
      ('Cuenta de cargo', agruparCuenta(sesion.numeroCuenta)),
      ('Importe a cobrar', dinero(monto)),
    ];
    return Form(
      key: _formCodigo,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Text('Confirme la operación', style: Theme.of(context).textTheme.titleMedium),
          const SizedBox(height: 8),
          for (final (etiqueta, valor) in filas)
            Padding(
              padding: const EdgeInsets.symmetric(vertical: 6),
              child: Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  SizedBox(width: 140, child: Text(etiqueta, style: TextStyle(color: Theme.of(context).hintColor))),
                  Expanded(child: Text(valor, style: const TextStyle(fontWeight: FontWeight.w600))),
                ],
              ),
            ),
          const SizedBox(height: 12),
          CampoCodigo(controlador: _codigo, etiqueta: 'Código de autorización (app o correo)', habilitado: !_trabajando),
          CodigoPorCorreo(habilitado: !_trabajando),
          const SizedBox(height: 12),
          if (_error != null) ...[Aviso(_error!, tipo: TipoAviso.error), const SizedBox(height: 12)],
          FilledButton(
            onPressed: _trabajando ? null : _confirmar,
            child: Text(_trabajando ? 'Procesando pago…' : 'Pagar ${dinero(monto)}'),
          ),
          const SizedBox(height: 8),
          OutlinedButton(
            onPressed: _trabajando ? null : () => setState(() => _paso = _Paso.datos),
            child: const Text('Modificar'),
          ),
        ],
      ),
    );
  }

  Widget _resultado() {
    final (titulo, icono, color) = switch (_tipoResultado!) {
      _TipoResultado.exito => ('¡Pago exitoso!', Icons.check_rounded, Colors.green),
      _TipoResultado.pendiente => ('Pago pendiente de confirmar', Icons.schedule_rounded, Colors.orange),
      _TipoResultado.rechazo => ('No se realizó el pago', Icons.close_rounded, Colors.red),
    };
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Center(
          child: CircleAvatar(
            radius: 36,
            backgroundColor: color.withValues(alpha: 0.15),
            child: Icon(icono, size: 40, color: color.shade700),
          ),
        ),
        const SizedBox(height: 12),
        Text(titulo, textAlign: TextAlign.center, style: Theme.of(context).textTheme.titleLarge?.copyWith(fontWeight: FontWeight.w700)),
        const SizedBox(height: 4),
        Text(
          _tipoResultado == _TipoResultado.pendiente ? '$_mensajeResultado Consulte el estatus en su historial.' : _mensajeResultado,
          textAlign: TextAlign.center,
          style: TextStyle(color: Theme.of(context).colorScheme.onSurfaceVariant),
        ),
        if (_tx != null) ...[const SizedBox(height: 16), DetalleTransaccion(tx: _tx!)],
        if (_tx?.pin != null && p.legend != null) ...[
          const SizedBox(height: 12),
          Text('Instrucciones de canje', style: Theme.of(context).textTheme.titleSmall),
          const SizedBox(height: 4),
          Text(p.legend!, style: TextStyle(color: Theme.of(context).colorScheme.onSurfaceVariant)),
        ],
        const SizedBox(height: 16),
        if (_tipoResultado == _TipoResultado.rechazo) ...[
          if (RegExp('saldo insuficiente', caseSensitive: false).hasMatch(_mensajeResultado)) ...[
            FilledButton(
              onPressed: () {
                seccionActual.value = Seccion.cuenta;
                Navigator.of(context).pop();
              },
              child: const Text('Depositar a mi cuenta'),
            ),
            const SizedBox(height: 8),
          ],
          OutlinedButton(
            onPressed: () => setState(() {
              _codigo.clear();
              _tipoResultado = null;
              _paso = _Paso.datos;
            }),
            child: const Text('Intentar de nuevo'),
          ),
        ] else
          FilledButton(
            onPressed: () {
              seccionActual.value = Seccion.historial;
              Navigator.of(context).pop();
            },
            child: const Text('Ver historial'),
          ),
        const SizedBox(height: 8),
        OutlinedButton(onPressed: () => Navigator.of(context).pop(), child: const Text('Volver al catálogo')),
      ],
    );
  }
}
