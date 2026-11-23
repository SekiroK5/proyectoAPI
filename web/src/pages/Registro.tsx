import { useState } from 'react'
import type { ChangeEvent, FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { mensajeDeError } from '../api/cliente'
import { auth } from '../api/servicios'
import type { AltaCliente } from '../api/tipos'
import { Aviso } from '../components/Comunes'

const VACIO = {
  nombre: '', segundoNombre: '', apellidoPaterno: '', apellidoMaterno: '', fechaNacimiento: '',
  curp: '', rfc: '', sexo: 'M', nacionalidad: 'Mexicana', estadoCivil: 'SOLTERO',
  correoElectronico: '', nombreUsuario: '', contrasena: '', confirmar: '',
  telefonoMovil: '', telefonoAlternativo: '', ocupacion: '', empresa: '', ingresoMensual: '',
  calle: '', numeroExterior: '', numeroInterior: '', colonia: '', municipio: '', estado: '', codigoPostal: '', pais: 'México',
}
type Formulario = typeof VACIO

const CONTRASENA = /^(?=.*\p{Lu})(?=.*\p{Ll})(?=.*\d)(?=.*[^\p{L}\d])\S{8,64}$/u

/** Validaciones locales: las mismas reglas que valida la API, para avisar antes de enviar. */
function validar(f: Formulario): string | null {
  if (!/^[A-Z][AEIOUX][A-Z]{2}\d{6}[HMX][A-Z]{5}[0-9A-Z]\d$/.test(f.curp.toUpperCase())) return 'La CURP debe tener 18 caracteres con el formato oficial.'
  if (!/^[A-Z&Ñ]{3,4}\d{6}[A-Z0-9]{3}$/.test(f.rfc.toUpperCase())) return 'El RFC no tiene un formato válido.'
  if (!/^\d{10}$/.test(f.telefonoMovil)) return 'El teléfono debe tener 10 dígitos.'
  if (f.telefonoAlternativo && !/^\d{10}$/.test(f.telefonoAlternativo)) return 'El teléfono alternativo debe tener 10 dígitos.'
  if (!/^\d{5}$/.test(f.codigoPostal)) return 'El código postal debe tener 5 dígitos.'
  if (!/^[a-zA-Z0-9._-]{4,50}$/.test(f.nombreUsuario)) return 'El usuario debe tener de 4 a 50 caracteres (letras, números, ".", "_" o "-").'
  if (!CONTRASENA.test(f.contrasena)) return 'La contraseña debe tener de 8 a 64 caracteres, sin espacios, con mayúscula, minúscula, número y un carácter especial.'
  if (f.contrasena !== f.confirmar) return 'Las contraseñas no coinciden.'
  if (!(Number(f.ingresoMensual) > 0)) return 'El ingreso mensual debe ser mayor a cero.'
  return null
}

/** Registro de un cliente nuevo: crea el usuario y su cuenta (las mismas credenciales sirven en web y móvil). */
export function Registro() {
  const [f, setF] = useState<Formulario>(VACIO)
  const [error, setError] = useState<string | null>(null)
  const [exito, setExito] = useState<string | null>(null)
  const [enviando, setEnviando] = useState(false)

  const cambiar = (campo: keyof Formulario) => (e: ChangeEvent<HTMLInputElement | HTMLSelectElement>) =>
    setF((actual) => ({ ...actual, [campo]: e.target.value }))

  async function enviar(e: FormEvent) {
    e.preventDefault()
    const invalido = validar(f)
    if (invalido) {
      setError(invalido)
      return
    }
    setError(null)
    setEnviando(true)
    const alta: AltaCliente = {
      nombre: f.nombre.trim(),
      segundoNombre: f.segundoNombre.trim() || undefined,
      apellidoPaterno: f.apellidoPaterno.trim(),
      apellidoMaterno: f.apellidoMaterno.trim(),
      fechaNacimiento: f.fechaNacimiento,
      curp: f.curp.toUpperCase(),
      rfc: f.rfc.toUpperCase(),
      sexo: f.sexo as AltaCliente['sexo'],
      nacionalidad: f.nacionalidad.trim(),
      estadoCivil: f.estadoCivil as AltaCliente['estadoCivil'],
      correoElectronico: f.correoElectronico.trim(),
      nombreUsuario: f.nombreUsuario.trim(),
      contrasena: f.contrasena,
      telefonoMovil: f.telefonoMovil,
      telefonoAlternativo: f.telefonoAlternativo || undefined,
      ocupacion: f.ocupacion.trim(),
      empresa: f.empresa.trim(),
      ingresoMensual: Number(f.ingresoMensual),
      domicilio: {
        calle: f.calle.trim(),
        numeroExterior: f.numeroExterior.trim(),
        numeroInterior: f.numeroInterior.trim() || undefined,
        colonia: f.colonia.trim(),
        municipio: f.municipio.trim(),
        estado: f.estado.trim(),
        codigoPostal: f.codigoPostal,
        pais: f.pais.trim(),
      },
    }
    try {
      await auth.registrar(alta)
      setExito('Cuenta creada. Ya puede iniciar sesión; la primera vez configurará su segundo factor.')
      setF(VACIO)
    } catch (err) {
      setError(mensajeDeError(err))
    } finally {
      setEnviando(false)
    }
  }

  const texto = (campo: keyof Formulario, etiqueta: string, props: Record<string, unknown> = {}) => (
    <label className="campo">
      <span>{etiqueta}</span>
      <input value={f[campo]} onChange={cambiar(campo)} {...props} />
    </label>
  )

  return (
    <div className="pantalla-acceso">
      <form className="tarjeta registro formulario" onSubmit={enviar}>
        <h1>Crear cuenta</h1>
        <fieldset>
          <legend>Datos personales</legend>
          <div className="rejilla-campos">
            {texto('nombre', 'Nombre', { required: true, maxLength: 50 })}
            {texto('segundoNombre', 'Segundo nombre (opcional)', { maxLength: 50 })}
            {texto('apellidoPaterno', 'Apellido paterno', { required: true, maxLength: 50 })}
            {texto('apellidoMaterno', 'Apellido materno', { required: true, maxLength: 50 })}
            {texto('fechaNacimiento', 'Fecha de nacimiento', { type: 'date', required: true })}
            {texto('curp', 'CURP', { required: true, maxLength: 18 })}
            {texto('rfc', 'RFC', { required: true, maxLength: 13 })}
            <label className="campo">
              <span>Sexo</span>
              <select value={f.sexo} onChange={cambiar('sexo')}>
                <option value="M">Masculino</option>
                <option value="F">Femenino</option>
                <option value="O">Otro</option>
              </select>
            </label>
            {texto('nacionalidad', 'Nacionalidad', { required: true, maxLength: 50 })}
            <label className="campo">
              <span>Estado civil</span>
              <select value={f.estadoCivil} onChange={cambiar('estadoCivil')}>
                <option value="SOLTERO">Soltero(a)</option>
                <option value="CASADO">Casado(a)</option>
                <option value="DIVORCIADO">Divorciado(a)</option>
                <option value="VIUDO">Viudo(a)</option>
                <option value="UNION_LIBRE">Unión libre</option>
              </select>
            </label>
          </div>
        </fieldset>
        <fieldset>
          <legend>Acceso y contacto</legend>
          <div className="rejilla-campos">
            {texto('correoElectronico', 'Correo electrónico', { type: 'email', required: true, maxLength: 100 })}
            {texto('nombreUsuario', 'Nombre de usuario', { required: true, maxLength: 50, autoComplete: 'username' })}
            {texto('contrasena', 'Contraseña', { type: 'password', required: true, maxLength: 64, autoComplete: 'new-password' })}
            {texto('confirmar', 'Confirmar contraseña', { type: 'password', required: true, maxLength: 64, autoComplete: 'new-password' })}
            {texto('telefonoMovil', 'Teléfono móvil', { required: true, inputMode: 'numeric', maxLength: 10 })}
            {texto('telefonoAlternativo', 'Teléfono alternativo (opcional)', { inputMode: 'numeric', maxLength: 10 })}
          </div>
        </fieldset>
        <fieldset>
          <legend>Domicilio</legend>
          <div className="rejilla-campos">
            {texto('calle', 'Calle', { required: true, maxLength: 100 })}
            {texto('numeroExterior', 'Número exterior', { required: true, maxLength: 20 })}
            {texto('numeroInterior', 'Número interior (opcional)', { maxLength: 20 })}
            {texto('colonia', 'Colonia', { required: true, maxLength: 100 })}
            {texto('municipio', 'Municipio', { required: true, maxLength: 100 })}
            {texto('estado', 'Estado', { required: true, maxLength: 100 })}
            {texto('codigoPostal', 'Código postal', { required: true, inputMode: 'numeric', maxLength: 5 })}
            {texto('pais', 'País', { required: true, maxLength: 50 })}
          </div>
        </fieldset>
        <fieldset>
          <legend>Información laboral</legend>
          <div className="rejilla-campos">
            {texto('ocupacion', 'Ocupación', { required: true, maxLength: 100 })}
            {texto('empresa', 'Empresa', { required: true, maxLength: 100 })}
            {texto('ingresoMensual', 'Ingreso mensual', { type: 'number', min: '0.01', step: '0.01', required: true })}
          </div>
        </fieldset>
        {error && <Aviso tipo="error">{error}</Aviso>}
        {exito && <Aviso tipo="exito">{exito} <Link to="/login">Ir a iniciar sesión</Link></Aviso>}
        <button className="boton" disabled={enviando}>{enviando ? 'Creando cuenta…' : 'Crear cuenta'}</button>
        <p className="pie">
          ¿Ya tiene cuenta? <Link to="/login">Inicie sesión</Link>
        </p>
      </form>
    </div>
  )
}
