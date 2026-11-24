import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../api/modelos.dart';
import '../ui/comunes.dart';
import '../ui/dependencias.dart';

/// Datos de la cuenta de 16 digitos, saldo y deposito (confirmado con el segundo factor).
class PantallaCuenta extends StatefulWidget {
  const PantallaCuenta({super.key});

  @override
  State<PantallaCuenta> createState() => _PantallaCuentaState();
}

class _PantallaCuentaState extends State<PantallaCuenta> {
  final _form = GlobalKey<FormState>();
  final _monto = TextEditingController();
  final _codigo = TextEditingController();

  Cuenta? _cuenta;
  String? _error;
  String? _errorDeposito;
  String? _exito;
  bool _depositando = false;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    if (_cuenta == null && _error == null) _cargar();
  }

  @override
  void dispose() {
    _monto.dispose();
    _codigo.dispose();
    super.dispose();
  }

  Future<void> _cargar() async {
    final dependencias = Dependencias.of(context);
    try {
      final r = await dependencias.servicios.cuenta(dependencias.sesion.numeroCuenta!);
      if (mounted) setState(() => _cuenta = r.datos);
    } catch (e) {
      if (mounted) setState(() => _error = mensajeDeError(e));
    }
  }

  Future<void> _depositar() async {
    setState(() {
      _errorDeposito = null;
      _exito = null;
    });
    if (!_form.currentState!.validate()) return;
    final dependencias = Dependencias.of(context);
    setState(() => _depositando = true);
    try {
      final mensaje = await dependencias.servicios
          .depositar(dependencias.sesion.numeroCuenta!, double.parse(_monto.text.trim()), _codigo.text);
      _monto.clear();
      setState(() => _exito = mensaje);
      await _cargar();
    } catch (e) {
      setState(() => _errorDeposito = mensajeDeError(e));
    } finally {
      _codigo.clear();
      if (mounted) setState(() => _depositando = false);
    }
  }

  String _agrupar(String numero) =>
      RegExp(r'.{1,4}').allMatches(numero).map((m) => m.group(0)).join(' ');

  @override
  Widget build(BuildContext context) {
    final sesion = Dependencias.of(context).sesion;
    final colores = Theme.of(context).colorScheme;
    return RefreshIndicator(
      onRefresh: _cargar,
      child: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          Center(
            child: ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: 640),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  Card(
                    color: colores.primaryContainer,
                    child: Padding(
                      padding: const EdgeInsets.all(20),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text('Hola, ${sesion.nombreUsuario ?? ''}', style: TextStyle(color: colores.onPrimaryContainer)),
                          const SizedBox(height: 12),
                          Text('Cuenta', style: TextStyle(color: colores.onPrimaryContainer)),
                          SelectableText(_agrupar(sesion.numeroCuenta ?? ''),
                              style: TextStyle(fontFamily: 'monospace', fontSize: 18, color: colores.onPrimaryContainer)),
                          const SizedBox(height: 12),
                          Text('Saldo disponible', style: TextStyle(color: colores.onPrimaryContainer)),
                          if (_error != null)
                            Aviso(_error!, tipo: TipoAviso.error)
                          else if (_cuenta == null)
                            const LinearProgressIndicator()
                          else
                            Text(dinero(_cuenta!.saldo),
                                style: Theme.of(context)
                                    .textTheme
                                    .headlineMedium
                                    ?.copyWith(fontWeight: FontWeight.bold, color: colores.onPrimaryContainer)),
                        ],
                      ),
                    ),
                  ),
                  const SizedBox(height: 12),
                  Card(
                    child: Padding(
                      padding: const EdgeInsets.all(16),
                      child: Form(
                        key: _form,
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.stretch,
                          children: [
                            Text('Depositar a mi cuenta', style: Theme.of(context).textTheme.titleMedium),
                            const SizedBox(height: 8),
                            TextFormField(
                              controller: _monto,
                              keyboardType: const TextInputType.numberWithOptions(decimal: true),
                              inputFormatters: [FilteringTextInputFormatter.allow(RegExp(r'[0-9.]'))],
                              decoration: const InputDecoration(labelText: 'Importe', prefixText: r'$ '),
                              validator: (v) {
                                final texto = (v ?? '').trim();
                                if (!RegExp(r'^\d{1,10}(\.\d{1,2})?$').hasMatch(texto) || double.parse(texto) <= 0) {
                                  return 'Importe válido, máximo 2 decimales.';
                                }
                                return null;
                              },
                            ),
                            const SizedBox(height: 8),
                            CampoCodigo(controlador: _codigo, habilitado: !_depositando),
                            const SizedBox(height: 12),
                            if (_errorDeposito != null) ...[
                              Aviso(_errorDeposito!, tipo: TipoAviso.error),
                              const SizedBox(height: 12),
                            ],
                            if (_exito != null) ...[Aviso(_exito!, tipo: TipoAviso.exito), const SizedBox(height: 12)],
                            FilledButton(
                              onPressed: _depositando ? null : _depositar,
                              child: Text(_depositando ? 'Depositando…' : 'Depositar'),
                            ),
                          ],
                        ),
                      ),
                    ),
                  ),
                  const SizedBox(height: 12),
                  OutlinedButton.icon(
                    icon: const Icon(Icons.logout),
                    label: const Text('Cerrar sesión'),
                    onPressed: () => sesion.cerrar(),
                  ),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }
}
