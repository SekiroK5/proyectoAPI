import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:intl/intl.dart';

import '../api/api_cliente.dart';
import '../api/modelos.dart';
import 'dependencias.dart';

final _dinero = NumberFormat.currency(locale: 'es_MX', symbol: r'$');

String dinero(num? valor) => _dinero.format(valor ?? 0);

String fecha(DateTime? valor) => valor == null ? '' : DateFormat('d MMM y, HH:mm', 'es_MX').format(valor.toLocal());

String mensajeDeError(Object error) => error is ApiException ? error.mensaje : 'Ocurrió un error inesperado.';

/// Logotipo del proveedor (imagen publica de GestoPago); si no carga, muestra las iniciales.
class Logo extends StatelessWidget {
  const Logo({super.key, this.url, required this.nombre, this.tamanio = 52});

  final String? url;
  final String nombre;
  final double tamanio;

  @override
  Widget build(BuildContext context) {
    final colores = Theme.of(context).colorScheme;
    final iniciales = nombre
        .split(RegExp(r'\s+'))
        .where((p) => p.isNotEmpty)
        .take(2)
        .map((p) => p[0].toUpperCase())
        .join();
    final respaldo = Container(
      width: tamanio,
      height: tamanio,
      alignment: Alignment.center,
      decoration: BoxDecoration(color: colores.primaryContainer, borderRadius: BorderRadius.circular(12)),
      child: Text(iniciales.isEmpty ? '?' : iniciales,
          style: TextStyle(color: colores.onPrimaryContainer, fontWeight: FontWeight.bold)),
    );
    if (url == null) return respaldo;
    return ClipRRect(
      borderRadius: BorderRadius.circular(12),
      child: Container(
        color: Colors.white,
        width: tamanio,
        height: tamanio,
        child: Image.network(
          url!,
          fit: BoxFit.contain,
          semanticLabel: 'Logotipo de $nombre',
          errorBuilder: (_, _, _) => respaldo,
        ),
      ),
    );
  }
}

/// Estatus de una transaccion con color: exitosa, rechazada o pendiente.
class EstatusChip extends StatelessWidget {
  const EstatusChip({super.key, required this.estatus});

  final String estatus;

  @override
  Widget build(BuildContext context) {
    final (texto, color) = switch (estatus) {
      'APLICADA' => ('Exitosa', Colors.green),
      'RECHAZADA' => ('Rechazada', Colors.red),
      'PENDIENTE_CONFIRMACION' => ('Pendiente de confirmar', Colors.orange),
      'REVISION_MANUAL' => ('En revisión', Colors.orange),
      _ => ('En proceso', Colors.orange),
    };
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
      decoration: BoxDecoration(color: color.withValues(alpha: 0.15), borderRadius: BorderRadius.circular(999)),
      child: Text(texto, style: TextStyle(color: color.shade700, fontWeight: FontWeight.w600, fontSize: 12)),
    );
  }
}

/// Aviso de color: error, exito, informacion o pendiente.
class Aviso extends StatelessWidget {
  const Aviso(this.texto, {super.key, this.tipo = TipoAviso.info});

  final String texto;
  final TipoAviso tipo;

  @override
  Widget build(BuildContext context) {
    final esquema = Theme.of(context).colorScheme;
    final (fondo, frente, icono) = switch (tipo) {
      TipoAviso.error => (esquema.errorContainer, esquema.onErrorContainer, Icons.error_outline),
      TipoAviso.exito => (Colors.green.withValues(alpha: 0.15), Colors.green.shade800, Icons.check_circle_outline),
      TipoAviso.pendiente => (Colors.orange.withValues(alpha: 0.15), Colors.orange.shade900, Icons.schedule),
      TipoAviso.info => (esquema.secondaryContainer, esquema.onSecondaryContainer, Icons.info_outline),
    };
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(color: fondo, borderRadius: BorderRadius.circular(12)),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Icon(icono, color: frente, size: 20),
          const SizedBox(width: 8),
          Expanded(child: Text(texto, style: TextStyle(color: frente))),
        ],
      ),
    );
  }
}

enum TipoAviso { error, exito, info, pendiente }

/// Campo del codigo de 6 digitos de la app autenticadora.
class CampoCodigo extends StatelessWidget {
  const CampoCodigo({super.key, required this.controlador, this.habilitado = true, this.etiqueta = 'Código de su app autenticadora'});

  final TextEditingController controlador;
  final bool habilitado;
  final String etiqueta;

  @override
  Widget build(BuildContext context) {
    return TextFormField(
      controller: controlador,
      enabled: habilitado,
      keyboardType: TextInputType.number,
      maxLength: 6,
      textAlign: TextAlign.center,
      style: const TextStyle(fontSize: 24, letterSpacing: 8, fontFamily: 'monospace'),
      inputFormatters: [FilteringTextInputFormatter.digitsOnly],
      autofillHints: const [AutofillHints.oneTimeCode],
      decoration: InputDecoration(labelText: etiqueta, counterText: ''),
      validator: (v) => v != null && RegExp(r'^\d{6}$').hasMatch(v) ? null : 'Escriba los 6 dígitos.',
    );
  }
}

/// Datos de una transaccion: resultado del pago y detalle del historial.
class DetalleTransaccion extends StatelessWidget {
  const DetalleTransaccion({super.key, required this.tx});

  final Transaccion tx;

  @override
  Widget build(BuildContext context) {
    final filas = <(String, String)>[
      ('Importe', dinero(tx.monto)),
      ('Fecha', fecha(tx.fecha)),
      if (tx.referencia != null) ('Referencia', tx.referencia!),
      if (tx.referencia == null && tx.telefono != null && !tx.telefono!.contains('1111111111')) ('Teléfono', tx.telefono!),
      ('Folio de autorización', tx.folio),
      if (tx.idTx != null) ('Id de transacción GestoPago', tx.idTx!),
      ('Identificador (UPC)', tx.upc),
      if (tx.pin != null) ('PIN / folio de canje', tx.pin!),
      if (tx.saldoCuenta != null) ('Saldo de la cuenta', dinero(tx.saldoCuenta)),
      if (tx.mensajeGestoPago != null) ('Respuesta de GestoPago', tx.mensajeGestoPago!),
    ];
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          children: [
            Logo(url: tx.logoUrl, nombre: tx.nombreServicio, tamanio: 56),
            const SizedBox(width: 12),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(tx.nombreServicio, style: Theme.of(context).textTheme.titleMedium),
                  if (tx.producto != null) Text(tx.producto!, style: Theme.of(context).textTheme.bodySmall),
                ],
              ),
            ),
            EstatusChip(estatus: tx.estatus),
          ],
        ),
        const SizedBox(height: 16),
        for (final (etiqueta, valor) in filas)
          Padding(
            padding: const EdgeInsets.symmetric(vertical: 6),
            child: Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                SizedBox(width: 150, child: Text(etiqueta, style: TextStyle(color: Theme.of(context).hintColor))),
                Expanded(child: SelectableText(valor, style: const TextStyle(fontWeight: FontWeight.w500))),
              ],
            ),
          ),
      ],
    );
  }
}

/// Logotipo y nombre de la plataforma con el degradado de la marca.
class EncabezadoMarca extends StatelessWidget {
  const EncabezadoMarca({super.key});

  @override
  Widget build(BuildContext context) {
    return Row(
      mainAxisAlignment: MainAxisAlignment.center,
      children: [
        Container(
          width: 44,
          height: 44,
          alignment: Alignment.center,
          decoration: BoxDecoration(gradient: degradadoMarca, borderRadius: BorderRadius.circular(13)),
          child: const Text('G', style: TextStyle(color: Colors.white, fontWeight: FontWeight.w800, fontSize: 20)),
        ),
        const SizedBox(width: 10),
        Text('GestoPago', style: Theme.of(context).textTheme.headlineSmall?.copyWith(fontWeight: FontWeight.w800)),
      ],
    );
  }
}

/// Degradado de la marca (igual que en la web).
const degradadoMarca = LinearGradient(
  colors: [Color(0xFF2754E6), Color(0xFF6B3FF0)],
  begin: Alignment.topLeft,
  end: Alignment.bottomRight,
);

/// Barra de pasos de un flujo (inicio de sesion, pago).
class IndicadorPasos extends StatelessWidget {
  const IndicadorPasos({super.key, required this.pasos, required this.actual});

  final List<String> pasos;
  final int actual;

  @override
  Widget build(BuildContext context) {
    final colores = Theme.of(context).colorScheme;
    return Semantics(
      label: 'Paso ${actual + 1} de ${pasos.length}',
      child: Row(
        children: [
          for (var i = 0; i < pasos.length; i++) ...[
            if (i > 0) const SizedBox(width: 8),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Container(
                    height: 4,
                    decoration: BoxDecoration(
                      color: i <= actual ? colores.primary : colores.outlineVariant,
                      borderRadius: BorderRadius.circular(4),
                    ),
                  ),
                  const SizedBox(height: 4),
                  Text(pasos[i],
                      style: TextStyle(
                        fontSize: 12,
                        fontWeight: FontWeight.w600,
                        color: i == actual ? colores.primary : colores.onSurfaceVariant,
                      )),
                ],
              ),
            ),
          ],
        ],
      ),
    );
  }
}

/// 1234567890123456 -> 1234 5678 9012 3456
String agruparCuenta(String? numero) =>
    RegExp(r'.{1,4}').allMatches(numero ?? '').map((m) => m.group(0)).join(' ');

/// Alternativa a la app autenticadora para pagos y depositos: pide un codigo al correo del cliente.
/// El codigo que llega se escribe en el mismo campo del codigo.
class CodigoPorCorreo extends StatefulWidget {
  const CodigoPorCorreo({super.key, this.habilitado = true});

  final bool habilitado;

  @override
  State<CodigoPorCorreo> createState() => _CodigoPorCorreoState();
}

class _CodigoPorCorreoState extends State<CodigoPorCorreo> {
  int _espera = 0;
  bool _enviando = false;
  String? _mensaje;
  bool _error = false;
  Timer? _temporizador;

  @override
  void dispose() {
    _temporizador?.cancel();
    super.dispose();
  }

  Future<void> _pedir() async {
    final servicios = Dependencias.of(context).servicios;
    setState(() {
      _enviando = true;
      _mensaje = null;
    });
    try {
      final mensaje = await servicios.enviarCodigoOperacion();
      if (!mounted) return;
      setState(() {
        _mensaje = mensaje;
        _error = false;
        _espera = 60;
      });
      _temporizador?.cancel();
      _temporizador = Timer.periodic(const Duration(seconds: 1), (t) {
        if (!mounted || _espera <= 1) {
          t.cancel();
          if (mounted) setState(() => _espera = 0);
          return;
        }
        setState(() => _espera--);
      });
    } catch (e) {
      if (mounted) {
        setState(() {
          _mensaje = mensajeDeError(e);
          _error = true;
        });
      }
    } finally {
      if (mounted) setState(() => _enviando = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        TextButton.icon(
          onPressed: !widget.habilitado || _enviando || _espera > 0 ? null : _pedir,
          icon: const Icon(Icons.mail_outline, size: 18),
          label: Text(_espera > 0
              ? '¿No llegó? Reenviar en $_espera s'
              : (_enviando ? 'Enviando código…' : '¿No tiene la app? Enviarme el código por correo')),
        ),
        if (_mensaje != null) Aviso(_mensaje!, tipo: _error ? TipoAviso.error : TipoAviso.exito),
      ],
    );
  }
}
