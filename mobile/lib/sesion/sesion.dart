import 'dart:async';
import 'dart:convert';

import 'package:flutter/foundation.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';

import '../api/api_cliente.dart';
import '../api/modelos.dart';
import '../api/servicios.dart';

/// Sesion del usuario. El token se guarda en el almacenamiento seguro del dispositivo (Keystore en
/// Android) y se cierra sola al vencer o cuando la API indica que ya no es valido.
class Sesion extends ChangeNotifier {
  Sesion(this.servicios, {FlutterSecureStorage? almacen}) : _almacen = almacen ?? const FlutterSecureStorage() {
    servicios.api.alPerderSesion = () => _limpiar('Su sesión terminó. Inicie sesión de nuevo.');
  }

  static const _clave = 'gestopago.sesion';

  final Servicios servicios;
  final FlutterSecureStorage _almacen;
  Timer? _temporizador;

  String? nombreUsuario;
  String? numeroCuenta;
  DateTime? _expira;

  /// Mensaje para la pantalla de inicio de sesion cuando la sesion se cerro sola.
  String? aviso;
  bool cargando = true;

  bool get activa => servicios.api.token != null;

  /// Recupera la sesion guardada si aun no vence.
  Future<void> cargar() async {
    try {
      final texto = await _almacen.read(key: _clave);
      if (texto != null) {
        final datos = jsonDecode(texto) as Map<String, dynamic>;
        final expira = DateTime.parse(datos['expira'] as String);
        if (expira.isAfter(DateTime.now())) {
          _aplicar(datos['token'] as String, datos['usuario'] as String, datos['cuenta'] as String, expira);
        } else {
          await _almacen.delete(key: _clave);
        }
      }
    } catch (_) {
      // Almacenamiento no disponible o dato corrupto: se inicia sin sesion
    }
    cargando = false;
    notifyListeners();
  }

  Future<void> iniciar(TokenRespuesta token) async {
    final expira = DateTime.now().add(Duration(seconds: token.expiraEnSegundos));
    _aplicar(token.tokenAcceso, token.nombreUsuario, token.numeroCuenta, expira);
    aviso = null;
    try {
      await _almacen.write(
        key: _clave,
        value: jsonEncode({
          'token': token.tokenAcceso,
          'usuario': token.nombreUsuario,
          'cuenta': token.numeroCuenta,
          'expira': expira.toIso8601String(),
        }),
      );
    } catch (_) {
      // Sin almacenamiento seguro la sesion dura mientras la app este abierta
    }
    notifyListeners();
  }

  Future<void> cerrar() async {
    try {
      // Revoca el token en el servidor para que no siga sirviendo
      await servicios.cerrarSesion();
    } on ApiException {
      // Aunque el servidor no responda, la sesion local se cierra
    }
    await _limpiar(null);
  }

  void _aplicar(String token, String usuario, String cuenta, DateTime expira) {
    servicios.api.token = token;
    nombreUsuario = usuario;
    numeroCuenta = cuenta;
    _expira = expira;
    _temporizador?.cancel();
    _temporizador = Timer(_expira!.difference(DateTime.now()), () => _limpiar('Su sesión venció. Inicie sesión de nuevo.'));
  }

  Future<void> _limpiar(String? mensaje) async {
    _temporizador?.cancel();
    servicios.api.token = null;
    nombreUsuario = null;
    numeroCuenta = null;
    _expira = null;
    aviso = mensaje;
    try {
      await _almacen.delete(key: _clave);
    } catch (_) {
      // Nada que borrar si el almacenamiento no esta disponible
    }
    notifyListeners();
  }

  @override
  void dispose() {
    _temporizador?.cancel();
    super.dispose();
  }
}
