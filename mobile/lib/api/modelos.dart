/// Modelos de la API (mismo contrato que la app web). Los importes llegan como texto ("10.00").
library;

double? _numero(dynamic valor) {
  if (valor == null) return null;
  if (valor is num) return valor.toDouble();
  return double.tryParse(valor.toString());
}

int _entero(dynamic valor) => valor is int ? valor : int.tryParse('$valor') ?? 0;

String? _texto(dynamic valor) => valor?.toString();

class Producto {
  Producto.fromJson(Map<String, dynamic> j)
      : idServicio = _entero(j['idServicio']),
        idProducto = _entero(j['idProducto']),
        idCatTipoServicio = j['idCatTipoServicio'] as int?,
        servicio = _texto(j['servicio']) ?? 'Servicio',
        producto = _texto(j['producto']) ?? '',
        precio = _numero(j['precio']),
        tipoReferencia = _texto(j['tipoReferencia']),
        logoUrl = _texto(j['logoUrl']),
        imagenReferenciaUrl = _texto(j['imagenReferenciaUrl']),
        categoria = _texto(j['categoria']) ?? 'Otros',
        tipoOperacion = _texto(j['tipoOperacion']) ?? 'NO_SOPORTADA',
        legend = _texto(j['legend']),
        verificableEnGestoPago = j['verificableEnGestoPago'] == true;

  final int idServicio;
  final int idProducto;
  final int? idCatTipoServicio;
  final String servicio;
  final String producto;
  final double? precio;
  final String? tipoReferencia;
  final String? logoUrl;
  final String? imagenReferenciaUrl;
  final String categoria;
  final String tipoOperacion;
  final String? legend;
  final bool verificableEnGestoPago;

  bool get esServicio => tipoOperacion == 'PAGO_SERVICIO';
  bool get esRecarga => tipoOperacion == 'RECARGA';

  /// Tarjetas de regalo y PINes (categorias 10 y 11): GestoPago no requiere telefono.
  bool get sinTelefono => idCatTipoServicio == 10 || idCatTipoServicio == 11;

  /// La referencia del servicio es un telefono de 10 digitos.
  bool get referenciaEsTelefono => tipoReferencia == 'a';
}

class LoginRespuesta {
  LoginRespuesta.fromJson(Map<String, dynamic> j)
      : siguientePaso = j['siguientePaso'] as String,
        tokenTemporal = j['tokenTemporal'] as String;

  /// VERIFICAR_CORREO, CONFIGURAR_A2F o VERIFICAR_A2F.
  final String siguientePaso;
  final String tokenTemporal;
}

class ConfiguracionA2F {
  ConfiguracionA2F.fromJson(Map<String, dynamic> j)
      : secreto = j['secreto'] as String,
        uriOtpauth = j['uriOtpauth'] as String;

  final String secreto;
  final String uriOtpauth;
}

class TokenRespuesta {
  TokenRespuesta.fromJson(Map<String, dynamic> j)
      : tokenAcceso = j['tokenAcceso'] as String,
        expiraEnSegundos = _entero(j['expiraEnSegundos']),
        nombreUsuario = j['nombreUsuario'] as String,
        numeroCuenta = j['numeroCuenta'] as String;

  final String tokenAcceso;
  final int expiraEnSegundos;
  final String nombreUsuario;
  final String numeroCuenta;
}

class Verificacion {
  Verificacion.fromJson(Map<String, dynamic> j)
      : folio = j['folio'] as String,
        resultado = j['resultado'] as String,
        verificadaConGestoPago = j['verificadaConGestoPago'] == true,
        mensaje = _texto(j['mensaje']),
        saldoPendiente = _numero(j['saldoPendiente']),
        referencia = j['referencia'] as String;

  final String folio;

  /// VALIDA o FORMATO_VALIDO (una INVALIDA llega como error 422).
  final String resultado;
  final bool verificadaConGestoPago;
  final String? mensaje;
  final double? saldoPendiente;
  final String referencia;
}

class Transaccion {
  Transaccion.fromJson(Map<String, dynamic> j)
      : id = _entero(j['id']),
        upc = _texto(j['upc']) ?? '',
        estatus = _texto(j['estatus']) ?? 'PENDIENTE',
        idServicio = _entero(j['idServicio']),
        servicio = _texto(j['servicio']),
        producto = _texto(j['producto']),
        logoUrl = _texto(j['logoUrl']),
        telefono = _texto(j['telefono']),
        referencia = _texto(j['referencia']),
        monto = _numero(j['monto']) ?? 0,
        saldoCuenta = _numero(j['saldoCuenta']),
        numAutorizacion = _texto(j['numAutorizacion']),
        idTx = _texto(j['idTx']),
        pin = _texto(j['pin']),
        numeroCuenta = _texto(j['numeroCuenta']),
        mensajeGestoPago = _texto(j['mensajeGestoPago']),
        fecha = DateTime.tryParse('${j['fecha']}');

  final int id;
  final String upc;

  /// APLICADA, RECHAZADA, PENDIENTE, PENDIENTE_CONFIRMACION o REVISION_MANUAL.
  final String estatus;
  final int idServicio;
  final String? servicio;
  final String? producto;
  final String? logoUrl;
  final String? telefono;
  final String? referencia;
  final double monto;
  final double? saldoCuenta;
  final String? numAutorizacion;
  final String? idTx;
  final String? pin;
  final String? numeroCuenta;
  final String? mensajeGestoPago;
  final DateTime? fecha;

  String get nombreServicio => servicio ?? 'Servicio $idServicio';

  String get folio => numAutorizacion != null && numAutorizacion != '-1' ? numAutorizacion! : 'Sin folio';
}

class Pagina<T> {
  Pagina.fromJson(Map<String, dynamic> j, T Function(Map<String, dynamic>) mapeo)
      : contenido = (j['contenido'] as List).map((e) => mapeo(e as Map<String, dynamic>)).toList(),
        pagina = _entero(j['pagina']),
        totalPaginas = _entero(j['totalPaginas']);

  final List<T> contenido;
  final int pagina;
  final int totalPaginas;
}

class Cuenta {
  Cuenta.fromJson(Map<String, dynamic> j)
      : numeroCuenta = j['numeroCuenta'] as String,
        saldo = _numero(j['saldo']) ?? 0,
        estatus = _texto(j['estatus']) ?? '';

  final String numeroCuenta;
  final double saldo;
  final String estatus;
}
