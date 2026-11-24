import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:intl/intl.dart';

import '../api/api_cliente.dart';
import '../api/modelos.dart';

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
  const CampoCodigo({super.key, required this.controlador, this.habilitado = true});

  final TextEditingController controlador;
  final bool habilitado;

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
      decoration: const InputDecoration(labelText: 'Código de su app autenticadora', counterText: ''),
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
