import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:qr_flutter/qr_flutter.dart';

import '../api/modelos.dart';
import '../ui/comunes.dart';
import '../ui/dependencias.dart';
import 'registro.dart';

/// Inicio de sesion: usuario o correo + contrasena, confirmacion del correo (si aun no esta confirmado)
/// y el codigo de la app autenticadora. La primera vez se muestra el QR para darla de alta.
class PantallaLogin extends StatefulWidget {
  const PantallaLogin({super.key});

  @override
  State<PantallaLogin> createState() => _PantallaLoginState();
}

class _PantallaLoginState extends State<PantallaLogin> {
  static const _esperaReenvio = 60;

  final _formCredenciales = GlobalKey<FormState>();
  final _formCodigo = GlobalKey<FormState>();
  final _usuario = TextEditingController();
  final _contrasena = TextEditingController();
  final _codigo = TextEditingController();

  String? _paso;
  String? _tokenTemporal;
  ConfiguracionA2F? _configuracion;
  String? _error;
  String? _info;
  bool _enviando = false;
  bool _ocultar = true;
  int _espera = 0;
  Timer? _temporizador;

  /// Segundo factor elegido: app autenticadora o codigo por correo.
  bool _porCorreo = false;
  bool _codigoEnviado = false;

  @override
  void dispose() {
    _temporizador?.cancel();
    _usuario.dispose();
    _contrasena.dispose();
    _codigo.dispose();
    super.dispose();
  }

  void _iniciarEspera() {
    _temporizador?.cancel();
    setState(() => _espera = _esperaReenvio);
    _temporizador = Timer.periodic(const Duration(seconds: 1), (t) {
      if (!mounted || _espera <= 1) {
        t.cancel();
        if (mounted) setState(() => _espera = 0);
        return;
      }
      setState(() => _espera--);
    });
  }

  /// Aplica el siguiente paso que devolvio la API.
  Future<void> _avanzar(LoginRespuesta datos) async {
    final servicios = Dependencias.of(context).servicios;
    ConfiguracionA2F? configuracion;
    if (datos.siguientePaso == 'CONFIGURAR_A2F') {
      configuracion = (await servicios.configurarA2F(datos.tokenTemporal)).datos;
    }
    if (datos.siguientePaso == 'VERIFICAR_CORREO') _iniciarEspera();
    setState(() {
      _tokenTemporal = datos.tokenTemporal;
      _configuracion = configuracion;
      _paso = datos.siguientePaso;
      _codigo.clear();
    });
  }

  Future<void> _ejecutar(Future<void> Function() accion) async {
    setState(() {
      _error = null;
      _enviando = true;
    });
    try {
      await accion();
    } catch (e) {
      if (mounted) {
        setState(() {
          _error = mensajeDeError(e);
          _codigo.clear();
        });
      }
    } finally {
      if (mounted) setState(() => _enviando = false);
    }
  }

  Future<void> _enviarCredenciales() async {
    if (!_formCredenciales.currentState!.validate()) return;
    final servicios = Dependencias.of(context).servicios;
    setState(() => _info = null);
    await _ejecutar(() async {
      final r = await servicios.login(_usuario.text.trim(), _contrasena.text);
      await _avanzar(r.datos);
      _contrasena.clear();
    });
  }

  Future<void> _enviarCodigoCorreo() async {
    if (!_formCodigo.currentState!.validate()) return;
    final servicios = Dependencias.of(context).servicios;
    await _ejecutar(() async {
      final r = await servicios.verificarCorreo(_tokenTemporal!, _codigo.text);
      setState(() => _info = '¡Correo confirmado! Ahora configure su segundo factor.');
      await _avanzar(r.datos);
    });
  }

  Future<void> _reenviar() async {
    final servicios = Dependencias.of(context).servicios;
    await _ejecutar(() async {
      final r = await servicios.reenviarCorreo(_tokenTemporal!);
      setState(() {
        _tokenTemporal = r.datos.tokenTemporal;
        _info = r.mensaje;
      });
      _iniciarEspera();
    });
  }

  Future<void> _enviarCodigoA2F() async {
    if (!_formCodigo.currentState!.validate()) return;
    final dependencias = Dependencias.of(context);
    await _ejecutar(() async {
      final r = await dependencias.servicios.verificarA2F(_tokenTemporal!, _codigo.text);
      await dependencias.sesion.iniciar(r.datos);
    });
  }

  Future<void> _pedirCodigoInicioSesion() async {
    final servicios = Dependencias.of(context).servicios;
    setState(() => _info = null);
    await _ejecutar(() async {
      final r = await servicios.enviarCodigoInicioSesion(_tokenTemporal!);
      setState(() {
        _tokenTemporal = r.datos.tokenTemporal;
        _info = r.mensaje;
        _codigoEnviado = true;
      });
      _iniciarEspera();
    });
  }

  void _elegirMetodo(bool porCorreo) {
    setState(() {
      _porCorreo = porCorreo;
      _codigo.clear();
      _error = null;
    });
    if (porCorreo && !_codigoEnviado) _pedirCodigoInicioSesion();
  }

  Future<void> _enviarCodigoCorreoLogin() async {
    if (!_formCodigo.currentState!.validate()) return;
    final dependencias = Dependencias.of(context);
    await _ejecutar(() async {
      final r = await dependencias.servicios.verificarCodigoInicioSesion(_tokenTemporal!, _codigo.text);
      await dependencias.sesion.iniciar(r.datos);
    });
  }

  void _volver() {
    _temporizador?.cancel();
    setState(() {
      _porCorreo = false;
      _codigoEnviado = false;
      _paso = null;
      _tokenTemporal = null;
      _configuracion = null;
      _codigo.clear();
      _error = null;
      _info = null;
      _espera = 0;
    });
  }

  Future<void> _abrirRegistro() async {
    final resultado = await Navigator.of(context).push<RegistroCompletado>(
      MaterialPageRoute(builder: (_) => const PantallaRegistro()),
    );
    if (resultado != null && mounted) {
      setState(() {
        _usuario.text = resultado.usuario;
        _info = resultado.mensaje;
      });
    }
  }

  int get _indicePaso => _paso == null ? 0 : (_paso == 'VERIFICAR_CORREO' ? 1 : 2);

  @override
  Widget build(BuildContext context) {
    final aviso = Dependencias.of(context).sesion.aviso;
    return Scaffold(
      body: SafeArea(
        child: Center(
          child: SingleChildScrollView(
            padding: const EdgeInsets.all(20),
            child: ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: 440),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  const EncabezadoMarca(),
                  const SizedBox(height: 20),
                  Card(
                    child: Padding(
                      padding: const EdgeInsets.all(20),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.stretch,
                        children: [
                          Text(_titulo, style: Theme.of(context).textTheme.headlineSmall?.copyWith(fontWeight: FontWeight.w700)),
                          const SizedBox(height: 4),
                          Text(_subtitulo, style: TextStyle(color: Theme.of(context).colorScheme.onSurfaceVariant)),
                          const SizedBox(height: 16),
                          IndicadorPasos(pasos: const ['Contraseña', 'Correo', 'Código'], actual: _indicePaso),
                          const SizedBox(height: 16),
                          if (aviso != null && _paso == null) ...[Aviso(aviso), const SizedBox(height: 12)],
                          if (_info != null) ...[Aviso(_info!, tipo: TipoAviso.exito), const SizedBox(height: 12)],
                          switch (_paso) {
                            null => _credenciales(),
                            'VERIFICAR_CORREO' => _codigoCorreo(),
                            _ => _segundoFactor(),
                          },
                        ],
                      ),
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

  String get _titulo => switch (_paso) {
        null => 'Bienvenido de nuevo',
        'VERIFICAR_CORREO' => 'Confirme su correo',
        _ => 'Segundo factor',
      };

  String get _subtitulo => switch (_paso) {
        null => 'Ingrese con su usuario o correo electrónico.',
        'VERIFICAR_CORREO' => 'Le enviamos un código de 6 dígitos a su correo. Vence en 15 minutos.',
        _ when _porCorreo => 'Escriba el código que le enviamos a su correo.',
        _ => 'Use su app autenticadora o pida un código por correo.',
      };

  Widget _credenciales() {
    return Form(
      key: _formCredenciales,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          TextFormField(
            controller: _usuario,
            decoration: const InputDecoration(labelText: 'Usuario o correo electrónico', prefixIcon: Icon(Icons.person_outline)),
            autofillHints: const [AutofillHints.username],
            textInputAction: TextInputAction.next,
            maxLength: 100,
            buildCounter: _sinContador,
            validator: (v) => v == null || v.trim().isEmpty ? 'Escriba su usuario o correo.' : null,
          ),
          const SizedBox(height: 12),
          TextFormField(
            controller: _contrasena,
            obscureText: _ocultar,
            decoration: InputDecoration(
              labelText: 'Contraseña',
              prefixIcon: const Icon(Icons.lock_outline),
              suffixIcon: IconButton(
                icon: Icon(_ocultar ? Icons.visibility_outlined : Icons.visibility_off_outlined),
                tooltip: _ocultar ? 'Mostrar contraseña' : 'Ocultar contraseña',
                onPressed: () => setState(() => _ocultar = !_ocultar),
              ),
            ),
            autofillHints: const [AutofillHints.password],
            maxLength: 64,
            buildCounter: _sinContador,
            onFieldSubmitted: (_) => _enviarCredenciales(),
            validator: (v) => v == null || v.isEmpty ? 'Escriba su contraseña.' : null,
          ),
          const SizedBox(height: 16),
          if (_error != null) ...[Aviso(_error!, tipo: TipoAviso.error), const SizedBox(height: 12)],
          FilledButton(
            onPressed: _enviando ? null : _enviarCredenciales,
            child: Text(_enviando ? 'Validando…' : 'Continuar'),
          ),
          const SizedBox(height: 8),
          TextButton(onPressed: _enviando ? null : _abrirRegistro, child: const Text('¿No tiene cuenta? Regístrese')),
        ],
      ),
    );
  }

  Widget _codigoCorreo() {
    return Form(
      key: _formCodigo,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          CampoCodigo(controlador: _codigo, habilitado: !_enviando, etiqueta: 'Código del correo'),
          const SizedBox(height: 12),
          if (_error != null) ...[Aviso(_error!, tipo: TipoAviso.error), const SizedBox(height: 12)],
          FilledButton(onPressed: _enviando ? null : _enviarCodigoCorreo, child: Text(_enviando ? 'Confirmando…' : 'Confirmar correo')),
          const SizedBox(height: 8),
          TextButton.icon(
            onPressed: _espera > 0 || _enviando ? null : _reenviar,
            icon: const Icon(Icons.mail_outline),
            label: Text(_espera > 0 ? 'Reenviar código en $_espera s' : 'Reenviar código'),
          ),
          OutlinedButton(onPressed: _enviando ? null : _volver, child: const Text('Volver')),
        ],
      ),
    );
  }

  Widget _selectorMetodo() => SegmentedButton<bool>(
        segments: const [
          ButtonSegment(value: false, label: Text('App'), icon: Icon(Icons.phone_iphone)),
          ButtonSegment(value: true, label: Text('Correo'), icon: Icon(Icons.mail_outline)),
        ],
        selected: {_porCorreo},
        showSelectedIcon: false,
        onSelectionChanged: _enviando ? null : (s) => _elegirMetodo(s.first),
      );

  Widget _segundoFactor() {
    if (_porCorreo) return _segundoFactorCorreo();
    final configuracion = _configuracion;
    return Form(
      key: _formCodigo,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          _selectorMetodo(),
          const SizedBox(height: 16),
          if (_paso == 'CONFIGURAR_A2F' && configuracion != null) ...[
            const Text(
              'Escanee el código con Google Authenticator, Microsoft Authenticator u otra app TOTP y escriba el código que aparece.',
              textAlign: TextAlign.center,
            ),
            const SizedBox(height: 12),
            Center(
              child: Container(
                decoration: BoxDecoration(color: Colors.white, borderRadius: BorderRadius.circular(16)),
                padding: const EdgeInsets.all(10),
                child: QrImageView(data: configuracion.uriOtpauth, size: 190, semanticsLabel: 'Código QR para la app autenticadora'),
              ),
            ),
            const SizedBox(height: 8),
            Row(
              children: [
                Expanded(
                  child: SelectableText(configuracion.secreto,
                      textAlign: TextAlign.center, style: const TextStyle(fontFamily: 'monospace', fontSize: 13)),
                ),
                IconButton(
                  icon: const Icon(Icons.copy_rounded),
                  tooltip: 'Copiar clave',
                  onPressed: () => Clipboard.setData(ClipboardData(text: configuracion.secreto)),
                ),
              ],
            ),
            const SizedBox(height: 8),
          ],
          CampoCodigo(controlador: _codigo, habilitado: !_enviando),
          const SizedBox(height: 12),
          if (_error != null) ...[Aviso(_error!, tipo: TipoAviso.error), const SizedBox(height: 12)],
          FilledButton(onPressed: _enviando ? null : _enviarCodigoA2F, child: Text(_enviando ? 'Verificando…' : 'Entrar')),
          const SizedBox(height: 8),
          OutlinedButton(onPressed: _enviando ? null : _volver, child: const Text('Volver')),
        ],
      ),
    );
  }

  Widget _segundoFactorCorreo() {
    return Form(
      key: _formCodigo,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          _selectorMetodo(),
          const SizedBox(height: 16),
          CampoCodigo(controlador: _codigo, habilitado: !_enviando && _codigoEnviado, etiqueta: 'Código del correo'),
          const SizedBox(height: 12),
          if (_error != null) ...[Aviso(_error!, tipo: TipoAviso.error), const SizedBox(height: 12)],
          FilledButton(
            onPressed: _enviando || !_codigoEnviado ? null : _enviarCodigoCorreoLogin,
            child: Text(_enviando ? 'Verificando…' : 'Entrar'),
          ),
          const SizedBox(height: 8),
          TextButton.icon(
            onPressed: _espera > 0 || _enviando ? null : _pedirCodigoInicioSesion,
            icon: const Icon(Icons.mail_outline),
            label: Text(_espera > 0 ? 'Reenviar código en $_espera s' : (_codigoEnviado ? 'Reenviar código' : 'Enviarme el código')),
          ),
          OutlinedButton(onPressed: _enviando ? null : _volver, child: const Text('Volver')),
        ],
      ),
    );
  }

  static Widget? _sinContador(BuildContext context, {required int currentLength, required bool isFocused, required int? maxLength}) =>
      null;
}
