import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../ui/comunes.dart';
import '../ui/dependencias.dart';

/// Lo que el registro le devuelve al login: usuario para prellenar y el mensaje de la API.
class RegistroCompletado {
  const RegistroCompletado(this.usuario, this.mensaje);

  final String usuario;
  final String mensaje;
}

/// Registro de un cliente nuevo. Crea el usuario y su cuenta; las mismas credenciales sirven en web y movil.
/// Al terminar se envia un codigo al correo, que se confirma al iniciar sesion.
class PantallaRegistro extends StatefulWidget {
  const PantallaRegistro({super.key});

  @override
  State<PantallaRegistro> createState() => _PantallaRegistroState();
}

class _PantallaRegistroState extends State<PantallaRegistro> {
  final _form = GlobalKey<FormState>();
  final _c = <String, TextEditingController>{};
  String _sexo = 'M';
  String _estadoCivil = 'SOLTERO';
  DateTime? _nacimiento;
  String? _error;
  String? _exito;
  bool _enviando = false;

  static final _contrasena = RegExp(r'^(?=.*\p{Lu})(?=.*\p{Ll})(?=.*\d)(?=.*[^\p{L}\d])\S{8,64}$', unicode: true);
  static final _soloLetras = RegExp(r'^(?=.{2,50}$)\p{L}+( \p{L}+)*$', unicode: true);
  static final _curp = RegExp(r'^[A-Z][AEIOUX][A-Z]{2}\d{2}(0[1-9]|1[0-2])(0[1-9]|[12]\d|3[01])[HMX]'
      r'(AS|BC|BS|CC|CL|CM|CS|CH|DF|DG|GT|GR|HG|JC|MC|MN|MS|NT|NL|OC|PL|QT|QR|SP|SL|SR|TC|TS|TL|VZ|YN|ZS|NE)'
      r'[B-DF-HJ-NP-TV-Z]{3}[0-9A-Z]\d$');
  static final _rfc = RegExp(r'^[A-Z&Ñ]{3,4}\d{2}(0[1-9]|1[0-2])(0[1-9]|[12]\d|3[01])[A-Z0-9]{3}$');
  static final _requisitos = <(String, bool Function(String))>[
    ('De 8 a 64 caracteres', (c) => c.length >= 8 && c.length <= 64),
    ('Una mayúscula', (c) => RegExp(r'\p{Lu}', unicode: true).hasMatch(c)),
    ('Una minúscula', (c) => RegExp(r'\p{Ll}', unicode: true).hasMatch(c)),
    ('Un número', (c) => RegExp(r'\d').hasMatch(c)),
    ('Un carácter especial', (c) => RegExp(r'[^\p{L}\d\s]', unicode: true).hasMatch(c)),
    ('Sin espacios', (c) => c.isNotEmpty && !c.contains(RegExp(r'\s'))),
  ];

  String? _letras(String v) => _soloLetras.hasMatch(v) ? null : 'De 2 a 50 letras, sin números ni símbolos.';

  TextEditingController _ctl(String campo, [String inicial = '']) =>
      _c.putIfAbsent(campo, () => TextEditingController(text: inicial));

  String _v(String campo) => _ctl(campo).text.trim();

  @override
  void dispose() {
    for (final c in _c.values) {
      c.dispose();
    }
    super.dispose();
  }

  Future<void> _enviar() async {
    setState(() {
      _error = null;
      _exito = null;
    });
    if (!_form.currentState!.validate()) return;
    if (_nacimiento == null) {
      setState(() => _error = 'Seleccione su fecha de nacimiento.');
      return;
    }
    setState(() => _enviando = true);
    final alta = <String, dynamic>{
      'nombre': _v('nombre'),
      if (_v('segundoNombre').isNotEmpty) 'segundoNombre': _v('segundoNombre'),
      'apellidoPaterno': _v('apellidoPaterno'),
      'apellidoMaterno': _v('apellidoMaterno'),
      'fechaNacimiento': _nacimiento!.toIso8601String().substring(0, 10),
      'curp': _v('curp').toUpperCase(),
      'rfc': _v('rfc').toUpperCase(),
      'sexo': _sexo,
      'nacionalidad': _v('nacionalidad'),
      'estadoCivil': _estadoCivil,
      'correoElectronico': _v('correo'),
      'nombreUsuario': _v('usuario'),
      'contrasena': _ctl('contrasena').text,
      'telefonoMovil': _v('telefono'),
      if (_v('telefonoAlt').isNotEmpty) 'telefonoAlternativo': _v('telefonoAlt'),
      'ocupacion': _v('ocupacion'),
      'empresa': _v('empresa'),
      'ingresoMensual': double.parse(_v('ingreso')),
      'domicilio': {
        'calle': _v('calle'),
        'numeroExterior': _v('numExt'),
        if (_v('numInt').isNotEmpty) 'numeroInterior': _v('numInt'),
        'colonia': _v('colonia'),
        'municipio': _v('municipio'),
        'estado': _v('estado'),
        'codigoPostal': _v('cp'),
        'pais': _v('pais'),
      },
    };
    try {
      final mensaje = await Dependencias.of(context).servicios.registrar(alta);
      if (!mounted) return;
      Navigator.of(context).pop(RegistroCompletado(alta['nombreUsuario'] as String, mensaje));
    } catch (e) {
      setState(() => _error = mensajeDeError(e));
    } finally {
      if (mounted) setState(() => _enviando = false);
    }
  }

  Widget _campo(String campo, String etiqueta,
      {String inicial = '',
      bool obligatorio = true,
      int? max,
      TextInputType? teclado,
      bool oculto = false,
      String? Function(String)? validar,
      List<TextInputFormatter>? formato}) {
    return SizedBox(
      width: 320,
      child: TextFormField(
        controller: _ctl(campo, inicial),
        decoration: InputDecoration(labelText: obligatorio ? etiqueta : '$etiqueta (opcional)'),
        maxLength: max,
        keyboardType: teclado,
        obscureText: oculto,
        inputFormatters: formato,
        validator: (valor) {
          final v = (valor ?? '').trim();
          if (v.isEmpty) return obligatorio ? 'Obligatorio.' : null;
          return validar?.call(v);
        },
      ),
    );
  }

  Widget _seccion(String titulo, List<Widget> campos) => Padding(
        padding: const EdgeInsets.only(bottom: 16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(titulo, style: Theme.of(context).textTheme.titleMedium),
            const SizedBox(height: 8),
            Wrap(spacing: 16, runSpacing: 4, children: campos),
          ],
        ),
      );

  @override
  Widget build(BuildContext context) {
    final digitos = [FilteringTextInputFormatter.digitsOnly];
    return Scaffold(
      appBar: AppBar(title: const Text('Crear cuenta')),
      body: SafeArea(
        child: Form(
          key: _form,
          child: ListView(
            padding: const EdgeInsets.all(16),
            children: [
              _seccion('Datos personales', [
                _campo('nombre', 'Nombre', max: 50, validar: _letras),
                _campo('segundoNombre', 'Segundo nombre', obligatorio: false, max: 50, validar: _letras),
                _campo('apellidoPaterno', 'Apellido paterno', max: 50, validar: _letras),
                _campo('apellidoMaterno', 'Apellido materno', max: 50, validar: _letras),
                SizedBox(
                  width: 320,
                  child: OutlinedButton.icon(
                    icon: const Icon(Icons.cake),
                    label: Text(_nacimiento == null
                        ? 'Fecha de nacimiento'
                        : 'Nacimiento: ${_nacimiento!.toIso8601String().substring(0, 10)}'),
                    onPressed: () async {
                      final hoy = DateTime.now();
                      final elegida = await showDatePicker(
                        context: context,
                        firstDate: DateTime(1900),
                        lastDate: DateTime(hoy.year - 18, hoy.month, hoy.day),
                        initialDate: DateTime(hoy.year - 25),
                      );
                      if (elegida != null) setState(() => _nacimiento = elegida);
                    },
                  ),
                ),
                _campo('curp', 'CURP', max: 18,
                    validar: (v) => _curp.hasMatch(v.toUpperCase()) ? null : 'La CURP no cumple el formato oficial (18 caracteres).'),
                _campo('rfc', 'RFC', max: 13,
                    validar: (v) => _rfc.hasMatch(v.toUpperCase()) ? null : 'RFC no válido (12 o 13 caracteres).'),
                SizedBox(
                  width: 320,
                  child: DropdownButtonFormField<String>(
                    initialValue: _sexo,
                    decoration: const InputDecoration(labelText: 'Sexo'),
                    items: const [
                      DropdownMenuItem(value: 'M', child: Text('Masculino')),
                      DropdownMenuItem(value: 'F', child: Text('Femenino')),
                      DropdownMenuItem(value: 'O', child: Text('Otro')),
                    ],
                    onChanged: (v) => setState(() => _sexo = v!),
                  ),
                ),
                _campo('nacionalidad', 'Nacionalidad', inicial: 'Mexicana', max: 50, validar: _letras),
                SizedBox(
                  width: 320,
                  child: DropdownButtonFormField<String>(
                    initialValue: _estadoCivil,
                    decoration: const InputDecoration(labelText: 'Estado civil'),
                    items: const [
                      DropdownMenuItem(value: 'SOLTERO', child: Text('Soltero(a)')),
                      DropdownMenuItem(value: 'CASADO', child: Text('Casado(a)')),
                      DropdownMenuItem(value: 'DIVORCIADO', child: Text('Divorciado(a)')),
                      DropdownMenuItem(value: 'VIUDO', child: Text('Viudo(a)')),
                      DropdownMenuItem(value: 'UNION_LIBRE', child: Text('Unión libre')),
                    ],
                    onChanged: (v) => setState(() => _estadoCivil = v!),
                  ),
                ),
              ]),
              _seccion('Acceso y contacto', [
                _campo('correo', 'Correo electrónico', max: 100, teclado: TextInputType.emailAddress,
                    validar: (v) => RegExp(r'^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$').hasMatch(v) ? null : 'Correo no válido.'),
                _campo('usuario', 'Nombre de usuario', max: 50,
                    validar: (v) => RegExp(r'^[a-zA-Z0-9._-]{4,50}$').hasMatch(v) ? null : 'De 4 a 50: letras, números, ".", "_" o "-".'),
                _campo('contrasena', 'Contraseña', max: 64, oculto: true,
                    validar: (v) => _contrasena.hasMatch(v)
                        ? null
                        : '8 a 64, sin espacios, con mayúscula, minúscula, número y carácter especial.'),
                _campo('confirmar', 'Confirmar contraseña', max: 64, oculto: true,
                    validar: (v) => v == _ctl('contrasena').text ? null : 'Las contraseñas no coinciden.'),
                _campo('telefono', 'Teléfono móvil', max: 10, teclado: TextInputType.phone, formato: digitos,
                    validar: (v) => v.length == 10 ? null : '10 dígitos.'),
                _campo('telefonoAlt', 'Teléfono alternativo', obligatorio: false, max: 10, teclado: TextInputType.phone,
                    formato: digitos, validar: (v) => v.length == 10 ? null : '10 dígitos.'),
              ]),
              ValueListenableBuilder<TextEditingValue>(
                valueListenable: _ctl('contrasena'),
                builder: (context, valor, _) => Padding(
                  padding: const EdgeInsets.only(bottom: 16),
                  child: Wrap(
                    spacing: 12,
                    runSpacing: 4,
                    children: [
                      for (final (texto, cumple) in _requisitos)
                        Row(mainAxisSize: MainAxisSize.min, children: [
                          Icon(cumple(valor.text) ? Icons.check_circle : Icons.radio_button_unchecked,
                              size: 16, color: cumple(valor.text) ? Colors.green : Theme.of(context).hintColor),
                          const SizedBox(width: 4),
                          Text(texto, style: const TextStyle(fontSize: 12)),
                        ]),
                    ],
                  ),
                ),
              ),
              _seccion('Domicilio', [
                _campo('calle', 'Calle', max: 100),
                _campo('numExt', 'Número exterior', max: 20),
                _campo('numInt', 'Número interior', obligatorio: false, max: 20),
                _campo('colonia', 'Colonia', max: 100),
                _campo('municipio', 'Municipio', max: 100),
                _campo('estado', 'Estado', max: 100),
                _campo('cp', 'Código postal', max: 5, teclado: TextInputType.number, formato: digitos,
                    validar: (v) => v.length == 5 ? null : '5 dígitos.'),
                _campo('pais', 'País', inicial: 'México', max: 50),
              ]),
              _seccion('Información laboral', [
                _campo('ocupacion', 'Ocupación', max: 100),
                _campo('empresa', 'Empresa', max: 100),
                _campo('ingreso', 'Ingreso mensual', teclado: const TextInputType.numberWithOptions(decimal: true),
                    validar: (v) => (double.tryParse(v) ?? 0) > 0 ? null : 'Mayor a cero.'),
              ]),
              if (_error != null) ...[Aviso(_error!, tipo: TipoAviso.error), const SizedBox(height: 12)],
              if (_exito != null) ...[Aviso(_exito!, tipo: TipoAviso.exito), const SizedBox(height: 12)],
              FilledButton(onPressed: _enviando ? null : _enviar, child: Text(_enviando ? 'Creando cuenta…' : 'Crear cuenta')),
              if (_exito != null)
                TextButton(onPressed: () => Navigator.of(context).pop(), child: const Text('Ir a iniciar sesión')),
            ],
          ),
        ),
      ),
    );
  }
}
