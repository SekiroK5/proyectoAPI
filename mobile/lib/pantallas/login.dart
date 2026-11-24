import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:qr_flutter/qr_flutter.dart';

import '../api/modelos.dart';
import '../ui/comunes.dart';
import '../ui/dependencias.dart';
import 'registro.dart';

/// Inicio de sesion en dos pasos: usuario o correo + contrasena, y despues el codigo de la app
/// autenticadora. La primera vez se muestra el QR para dar de alta la cuenta en la app.
class PantallaLogin extends StatefulWidget {
  const PantallaLogin({super.key});

  @override
  State<PantallaLogin> createState() => _PantallaLoginState();
}

class _PantallaLoginState extends State<PantallaLogin> {
  final _formCredenciales = GlobalKey<FormState>();
  final _formCodigo = GlobalKey<FormState>();
  final _usuario = TextEditingController();
  final _contrasena = TextEditingController();
  final _codigo = TextEditingController();

  String? _paso;
  String? _tokenTemporal;
  ConfiguracionA2F? _configuracion;
  String? _error;
  bool _enviando = false;
  bool _ocultar = true;

  @override
  void dispose() {
    _usuario.dispose();
    _contrasena.dispose();
    _codigo.dispose();
    super.dispose();
  }

  Future<void> _enviarCredenciales() async {
    if (!_formCredenciales.currentState!.validate()) return;
    final servicios = Dependencias.of(context).servicios;
    setState(() {
      _error = null;
      _enviando = true;
    });
    try {
      final r = await servicios.login(_usuario.text.trim(), _contrasena.text);
      ConfiguracionA2F? configuracion;
      if (r.datos.siguientePaso == 'CONFIGURAR_A2F') {
        configuracion = (await servicios.configurarA2F(r.datos.tokenTemporal)).datos;
      }
      setState(() {
        _tokenTemporal = r.datos.tokenTemporal;
        _configuracion = configuracion;
        _paso = r.datos.siguientePaso;
        _contrasena.clear();
      });
    } catch (e) {
      setState(() => _error = mensajeDeError(e));
    } finally {
      if (mounted) setState(() => _enviando = false);
    }
  }

  Future<void> _enviarCodigo() async {
    if (!_formCodigo.currentState!.validate()) return;
    final dependencias = Dependencias.of(context);
    setState(() {
      _error = null;
      _enviando = true;
    });
    try {
      final r = await dependencias.servicios.verificarA2F(_tokenTemporal!, _codigo.text);
      await dependencias.sesion.iniciar(r.datos);
    } catch (e) {
      setState(() {
        _error = mensajeDeError(e);
        _codigo.clear();
      });
    } finally {
      if (mounted) setState(() => _enviando = false);
    }
  }

  void _volver() => setState(() {
        _paso = null;
        _tokenTemporal = null;
        _configuracion = null;
        _codigo.clear();
        _error = null;
      });

  @override
  Widget build(BuildContext context) {
    final aviso = Dependencias.of(context).sesion.aviso;
    return Scaffold(
      body: SafeArea(
        child: Center(
          child: SingleChildScrollView(
            padding: const EdgeInsets.all(24),
            child: ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: 420),
              child: Card(
                child: Padding(
                  padding: const EdgeInsets.all(24),
                  child: _paso == null ? _credenciales(aviso) : _segundoFactor(),
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }

  Widget _marca() {
    final colores = Theme.of(context).colorScheme;
    return Row(
      mainAxisAlignment: MainAxisAlignment.center,
      children: [
        CircleAvatar(backgroundColor: colores.primary, child: Icon(Icons.payments, color: colores.onPrimary)),
        const SizedBox(width: 10),
        Text('GestoPago', style: Theme.of(context).textTheme.headlineSmall?.copyWith(fontWeight: FontWeight.bold)),
      ],
    );
  }

  Widget _credenciales(String? aviso) {
    return Form(
      key: _formCredenciales,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          _marca(),
          const SizedBox(height: 24),
          Text('Iniciar sesión', style: Theme.of(context).textTheme.titleLarge),
          const SizedBox(height: 16),
          if (aviso != null) ...[Aviso(aviso), const SizedBox(height: 12)],
          TextFormField(
            controller: _usuario,
            decoration: const InputDecoration(labelText: 'Usuario o correo electrónico', prefixIcon: Icon(Icons.person)),
            autofillHints: const [AutofillHints.username],
            textInputAction: TextInputAction.next,
            maxLength: 100,
            validator: (v) => v == null || v.trim().isEmpty ? 'Escriba su usuario o correo.' : null,
          ),
          TextFormField(
            controller: _contrasena,
            obscureText: _ocultar,
            decoration: InputDecoration(
              labelText: 'Contraseña',
              prefixIcon: const Icon(Icons.lock),
              suffixIcon: IconButton(
                icon: Icon(_ocultar ? Icons.visibility : Icons.visibility_off),
                tooltip: _ocultar ? 'Mostrar' : 'Ocultar',
                onPressed: () => setState(() => _ocultar = !_ocultar),
              ),
            ),
            autofillHints: const [AutofillHints.password],
            maxLength: 64,
            onFieldSubmitted: (_) => _enviarCredenciales(),
            validator: (v) => v == null || v.isEmpty ? 'Escriba su contraseña.' : null,
          ),
          if (_error != null) ...[Aviso(_error!, tipo: TipoAviso.error), const SizedBox(height: 12)],
          FilledButton(
            onPressed: _enviando ? null : _enviarCredenciales,
            child: Text(_enviando ? 'Validando…' : 'Continuar'),
          ),
          const SizedBox(height: 8),
          TextButton(
            onPressed: () => Navigator.of(context).push(MaterialPageRoute(builder: (_) => const PantallaRegistro())),
            child: const Text('¿No tiene cuenta? Regístrese'),
          ),
        ],
      ),
    );
  }

  Widget _segundoFactor() {
    return Form(
      key: _formCodigo,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          _marca(),
          const SizedBox(height: 24),
          Text('Segundo factor', style: Theme.of(context).textTheme.titleLarge),
          const SizedBox(height: 12),
          if (_paso == 'CONFIGURAR_A2F' && _configuracion != null) ...[
            const Text('Escanee este código con Google Authenticator, Microsoft Authenticator u otra app TOTP. '
                'Después escriba el código de 6 dígitos que aparece en la app.'),
            const SizedBox(height: 12),
            Center(
              child: Container(
                color: Colors.white,
                padding: const EdgeInsets.all(8),
                child: QrImageView(data: _configuracion!.uriOtpauth, size: 200, semanticsLabel: 'Código QR para la app autenticadora'),
              ),
            ),
            const SizedBox(height: 8),
            const Text('¿No puede escanearlo? Capture esta clave:'),
            Row(
              children: [
                Expanded(child: SelectableText(_configuracion!.secreto, style: const TextStyle(fontFamily: 'monospace'))),
                IconButton(
                  icon: const Icon(Icons.copy),
                  tooltip: 'Copiar clave',
                  onPressed: () => Clipboard.setData(ClipboardData(text: _configuracion!.secreto)),
                ),
              ],
            ),
          ],
          if (_paso == 'VERIFICAR_A2F') const Text('Escriba el código de 6 dígitos de su app autenticadora.'),
          const SizedBox(height: 12),
          CampoCodigo(controlador: _codigo, habilitado: !_enviando),
          const SizedBox(height: 12),
          if (_error != null) ...[Aviso(_error!, tipo: TipoAviso.error), const SizedBox(height: 12)],
          FilledButton(onPressed: _enviando ? null : _enviarCodigo, child: Text(_enviando ? 'Verificando…' : 'Entrar')),
          const SizedBox(height: 8),
          OutlinedButton(onPressed: _enviando ? null : _volver, child: const Text('Volver')),
        ],
      ),
    );
  }
}
