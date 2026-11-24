import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:gestopago_movil/api/api_cliente.dart';
import 'package:gestopago_movil/api/modelos.dart';
import 'package:gestopago_movil/api/servicios.dart';
import 'package:gestopago_movil/main.dart';
import 'package:gestopago_movil/sesion/sesion.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:intl/date_symbol_data_local.dart';

http.Response _json(int status, Object cuerpo) =>
    http.Response(jsonEncode(cuerpo), status, headers: {'content-type': 'application/json; charset=utf-8'});

void main() {
  setUpAll(() => initializeDateFormatting('es_MX'));

  group('ApiCliente', () {
    test('manda el token y convierte los errores en ApiException con el mensaje del servidor', () async {
      late http.Request recibida;
      final api = ApiCliente(
        baseUrl: 'http://api',
        cliente: MockClient((r) async {
          recibida = r;
          return _json(422, {'codigo': 1, 'mensaje': 'La referencia no es válida.'});
        }),
      )..token = 'abc';

      await expectLater(
        api.llamar('POST', '/api/v1/referencias/verificacion', {'referencia': 'x'}),
        throwsA(isA<ApiException>()
            .having((e) => e.status, 'status', 422)
            .having((e) => e.mensaje, 'mensaje', 'La referencia no es válida.')),
      );
      expect(recibida.headers['Authorization'], 'Bearer abc');
      expect(jsonDecode(recibida.body), {'referencia': 'x'});
    });

    test('un 401 de sesion avisa para cerrar la sesion local', () async {
      var avisado = false;
      final api = ApiCliente(
        baseUrl: 'http://api',
        cliente: MockClient((_) async => _json(401, {'codigo': 1, 'mensaje': 'Se requiere iniciar sesión con un token válido.'})),
      )..alPerderSesion = () => avisado = true;

      await expectLater(api.llamar('POST', '/api/v1/transaccion/historial', {}), throwsA(isA<ApiException>()));
      expect(avisado, isTrue);
    });

    test('un 401 por codigo 2FA incorrecto no cierra la sesion', () async {
      var avisado = false;
      final api = ApiCliente(
        baseUrl: 'http://api',
        cliente: MockClient((_) async => _json(401, {'codigo': 1, 'mensaje': 'El código 2FA no es válido.'})),
      )..alPerderSesion = () => avisado = true;

      await expectLater(api.llamar('POST', '/api/v1/transaccion', {}), throwsA(isA<ApiException>()));
      expect(avisado, isFalse);
    });
  });

  group('Modelos', () {
    test('Producto identifica recargas, servicios y PINes sin telefono', () {
      final pin = Producto.fromJson({
        'idServicio': 5,
        'idProducto': 9,
        'idCatTipoServicio': 10,
        'servicio': 'Tarjeta',
        'producto': 'PIN 100',
        'precio': '100.00',
        'tipoOperacion': 'RECARGA',
      });
      expect(pin.esRecarga, isTrue);
      expect(pin.sinTelefono, isTrue);
      expect(pin.precio, 100.0);

      final luz = Producto.fromJson({'idServicio': 1, 'idProducto': 2, 'servicio': 'CFE', 'tipoOperacion': 'PAGO_SERVICIO'});
      expect(luz.esServicio, isTrue);
      expect(luz.categoria, 'Otros');
    });

    test('Transaccion sin numero de autorizacion muestra "Sin folio"', () {
      final tx = Transaccion.fromJson({'id': 1, 'upc': 'AB12CD34EF', 'estatus': 'RECHAZADA', 'idServicio': 7, 'monto': '20.00', 'numAutorizacion': '-1'});
      expect(tx.folio, 'Sin folio');
      expect(tx.nombreServicio, 'Servicio 7');
      expect(tx.monto, 20.0);
    });
  });

  testWidgets('sin sesion muestra el inicio de sesion y valida los campos', (tester) async {
    final sesion = Sesion(Servicios(ApiCliente(baseUrl: 'http://api', cliente: MockClient((_) async => _json(500, {})))));
    await tester.pumpWidget(GestoPagoApp(sesion: sesion));
    await tester.runAsync(sesion.cargar);
    await tester.pumpAndSettle();

    expect(find.text('Iniciar sesión'), findsOneWidget);
    await tester.tap(find.text('Continuar'));
    await tester.pump();
    expect(find.text('Escriba su usuario o correo.'), findsOneWidget);
    expect(find.text('Escriba su contraseña.'), findsOneWidget);
  });

  testWidgets('credenciales correctas piden el codigo de la app autenticadora', (tester) async {
    final api = ApiCliente(
      baseUrl: 'http://api',
      cliente: MockClient((r) async => r.url.path == '/api/v1/auth/login'
          ? _json(200, {'codigo': 0, 'mensaje': 'ok', 'datos': {'siguientePaso': 'VERIFICAR_A2F', 'tokenTemporal': 't'}})
          : _json(500, {})),
    );
    final sesion = Sesion(Servicios(api));
    await tester.pumpWidget(GestoPagoApp(sesion: sesion));
    await tester.runAsync(sesion.cargar);
    await tester.pumpAndSettle();

    await tester.enterText(find.widgetWithText(TextFormField, 'Usuario o correo electrónico'), 'anaweb');
    await tester.enterText(find.widgetWithText(TextFormField, 'Contraseña'), 'Segura#2026');
    await tester.tap(find.text('Continuar'));
    await tester.runAsync(() => Future<void>.delayed(const Duration(milliseconds: 50)));
    await tester.pumpAndSettle();

    expect(find.text('Segundo factor'), findsOneWidget);
    expect(find.text('Escriba el código de 6 dígitos de su app autenticadora.'), findsOneWidget);
  });
}
