import 'dart:async';
import 'dart:convert';

import 'package:http/http.dart' as http;

import '../config.dart';

/// Error de la API con su codigo HTTP y el mensaje (ya pensado para el usuario) que manda el servidor.
class ApiException implements Exception {
  ApiException(this.status, this.mensaje);

  final int status;
  final String mensaje;

  /// Mensaje con el que la API responde 401 cuando falta el token o ya no es valido.
  static const _sesionRequerida = 'Se requiere iniciar sesión';

  /// true si la sesion ya no es valida (token vencido, revocado o ausente).
  bool get sesionVencida => status == 401 && mensaje.startsWith(_sesionRequerida);

  @override
  String toString() => mensaje;
}

/// Cliente HTTP de la API. Agrega el token de la sesion y convierte los errores en [ApiException].
class ApiCliente {
  ApiCliente({http.Client? cliente, String? baseUrl})
      : _http = cliente ?? http.Client(),
        _baseUrl = baseUrl ?? apiUrl;

  final http.Client _http;
  final String _baseUrl;

  /// Un pago puede tardar hasta ~70 s (GestoPago espera 62 s antes de responder codigo 82).
  static const _tiempoMaximo = Duration(seconds: 80);

  String? token;

  /// La sesion se registra aqui para cerrarse cuando la API indica que el token ya no sirve.
  void Function()? alPerderSesion;

  Future<dynamic> llamar(String metodo, String ruta, [Object? cuerpo]) async {
    final uri = Uri.parse('$_baseUrl$ruta');
    final encabezados = <String, String>{
      if (cuerpo != null) 'Content-Type': 'application/json',
      if (token != null) 'Authorization': 'Bearer $token',
    };

    http.Response respuesta;
    try {
      final peticion = http.Request(metodo, uri)..headers.addAll(encabezados);
      if (cuerpo != null) {
        peticion.body = jsonEncode(cuerpo);
      }
      respuesta = await http.Response.fromStream(await _http.send(peticion).timeout(_tiempoMaximo));
    } on TimeoutException {
      throw ApiException(0, 'El servidor tardó demasiado en responder. Revise su historial antes de reintentar.');
    } catch (_) {
      throw ApiException(0, 'No hay conexión con el servidor. Verifique su red e intente de nuevo.');
    }

    dynamic json;
    try {
      json = respuesta.body.isEmpty ? null : jsonDecode(utf8.decode(respuesta.bodyBytes));
    } catch (_) {
      json = null;
    }

    if (respuesta.statusCode < 200 || respuesta.statusCode >= 300) {
      final mensaje = json is Map && json['mensaje'] is String
          ? json['mensaje'] as String
          : 'Error ${respuesta.statusCode} del servidor.';
      final error = ApiException(respuesta.statusCode, mensaje);
      if (error.sesionVencida) {
        alPerderSesion?.call();
      }
      throw error;
    }
    return json;
  }
}

/// Respuesta uniforme {codigo, mensaje, datos} de la API.
class Respuesta<T> {
  Respuesta(this.mensaje, this.datos);

  final String mensaje;
  final T datos;
}
