import 'api_cliente.dart';
import 'modelos.dart';

/// Endpoints de la API, uno por metodo. La app web consume exactamente los mismos.
class Servicios {
  Servicios(this.api);

  final ApiCliente api;

  Future<Respuesta<T>> _datos<T>(String metodo, String ruta, Object? cuerpo, T Function(Map<String, dynamic>) mapeo) async {
    final json = await api.llamar(metodo, ruta, cuerpo) as Map<String, dynamic>;
    return Respuesta(json['mensaje'] as String? ?? '', mapeo(json['datos'] as Map<String, dynamic>));
  }

  // ---------- Inicio de sesion y registro ----------

  Future<Respuesta<LoginRespuesta>> login(String usuario, String contrasena) =>
      _datos('POST', '/api/v1/auth/login', {'usuario': usuario, 'contrasena': contrasena}, LoginRespuesta.fromJson);

  Future<Respuesta<ConfiguracionA2F>> configurarA2F(String tokenTemporal) =>
      _datos('POST', '/api/v1/auth/a2f/configuracion', {'tokenTemporal': tokenTemporal}, ConfiguracionA2F.fromJson);

  Future<Respuesta<TokenRespuesta>> verificarA2F(String tokenTemporal, String codigo) => _datos(
      'POST', '/api/v1/auth/a2f/verificacion', {'tokenTemporal': tokenTemporal, 'codigo': codigo}, TokenRespuesta.fromJson);

  Future<Respuesta<LoginRespuesta>> verificarCorreo(String tokenTemporal, String codigo) => _datos(
      'POST', '/api/v1/auth/correo/verificacion', {'tokenTemporal': tokenTemporal, 'codigo': codigo}, LoginRespuesta.fromJson);

  Future<Respuesta<LoginRespuesta>> reenviarCorreo(String tokenTemporal) =>
      _datos('POST', '/api/v1/auth/correo/reenvio', {'tokenTemporal': tokenTemporal}, LoginRespuesta.fromJson);

  Future<Respuesta<LoginRespuesta>> enviarCodigoInicioSesion(String tokenTemporal) =>
      _datos('POST', '/api/v1/auth/a2f/correo/envio', {'tokenTemporal': tokenTemporal}, LoginRespuesta.fromJson);

  Future<Respuesta<TokenRespuesta>> verificarCodigoInicioSesion(String tokenTemporal, String codigo) => _datos(
      'POST', '/api/v1/auth/a2f/correo/verificacion', {'tokenTemporal': tokenTemporal, 'codigo': codigo}, TokenRespuesta.fromJson);

  /// Codigo por correo para autorizar un pago o deposito (en lugar de la app autenticadora).
  Future<String> enviarCodigoOperacion() async {
    final json = await api.llamar('POST', '/api/v1/auth/codigo-operacion') as Map<String, dynamic>;
    return json['mensaje'] as String? ?? 'Le enviamos un código a su correo.';
  }

  Future<void> cerrarSesion() => api.llamar('POST', '/api/v1/auth/logout');

  Future<String> registrar(Map<String, dynamic> alta) async {
    final json = await api.llamar('POST', '/api/v1/clientes', alta) as Map<String, dynamic>;
    return json['mensaje'] as String? ?? 'Cuenta creada.';
  }

  // ---------- Catalogo (GET /productosList devuelve la lista directa) ----------

  Future<List<Producto>> catalogo() async {
    final json = await api.llamar('GET', '/productosList');
    return (json as List? ?? const [])
        .map((e) => Producto.fromJson(e as Map<String, dynamic>))
        .where((p) => p.tipoOperacion != 'NO_SOPORTADA')
        .toList();
  }

  // ---------- Pagos ----------

  Future<Respuesta<Verificacion>> verificarReferencia(int idServicio, int idProducto, String referencia) => _datos(
      'POST',
      '/api/v1/referencias/verificacion',
      {'idServicio': idServicio, 'idProducto': idProducto, 'referencia': referencia},
      Verificacion.fromJson);

  Future<Respuesta<Transaccion>> pagar({
    required String numeroCuenta,
    required int idServicio,
    required int idProducto,
    required double monto,
    String? telefono,
    String? referencia,
    String? folioVerificacion,
    required String codigoA2F,
  }) =>
      _datos(
          'POST',
          '/api/v1/transaccion',
          {
            'numeroCuenta': numeroCuenta,
            'idServicio': idServicio,
            'idProducto': idProducto,
            'monto': monto,
            'telefono': ?telefono,
            'referencia': ?referencia,
            'folioVerificacion': ?folioVerificacion,
            'codigoA2F': codigoA2F,
          },
          Transaccion.fromJson);

  Future<Respuesta<Pagina<Transaccion>>> historial(int pagina, int tamanio, {String? estatus}) => _datos(
      'POST',
      '/api/v1/transaccion/historial',
      {'pagina': pagina, 'tamanio': tamanio, 'estatus': ?estatus},
      (j) => Pagina.fromJson(j, Transaccion.fromJson));

  Future<Respuesta<Transaccion>> detalle(int id) =>
      _datos('POST', '/api/v1/transaccion/detalle', {'id': id}, Transaccion.fromJson);

  // ---------- Cuenta ----------

  Future<Respuesta<Cuenta>> cuenta(String numeroCuenta) =>
      _datos('POST', '/api/v1/cuentas/consulta', {'numeroCuenta': numeroCuenta}, Cuenta.fromJson);

  Future<String> depositar(String numeroCuenta, double monto, String codigoA2F) async {
    final json = await api.llamar('POST', '/api/v1/cuentas/deposito',
        {'numeroCuenta': numeroCuenta, 'monto': monto, 'codigoA2F': codigoA2F}) as Map<String, dynamic>;
    return json['mensaje'] as String? ?? 'Depósito realizado.';
  }
}
