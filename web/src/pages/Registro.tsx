import { useState } from 'react'
import type { ChangeEvent, ReactNode, SubmitEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { ApiError, mensajeDeError } from '../api/cliente'
import { auth } from '../api/servicios'
import type { AltaCliente } from '../api/tipos'
import { Aviso, CampoContrasena, Pasos } from '../components/Comunes'
import { Icono } from '../components/Iconos'
import { PantallaAcceso } from '../components/PantallaAcceso'

const VACIO = {
  nombre: '', segundoNombre: '', apellidoPaterno: '', apellidoMaterno: '', fechaNacimiento: '',
  curp: '', rfc: '', sexo: 'M', nacionalidad: 'Mexicana', estadoCivil: 'SOLTERO',
  correoElectronico: '', nombreUsuario: '', contrasena: '', confirmar: '',
  telefonoMovil: '', telefonoAlternativo: '', ocupacion: '', empresa: '', ingresoMensual: '',
  calle: '', numeroExterior: '', numeroInterior: '', colonia: '', municipio: '', estado: '', codigoPostal: '', pais: 'México',
}
type Formulario = typeof VACIO
type Campo = keyof Formulario
type Errores = Partial<Record<Campo, string>>

const NOMBRE = /^(?=.{2,50}$)\p{L}+( \p{L}+)*$/u
/** Estructura de la CURP: 4 letras, fecha, sexo, entidad, 3 consonantes, homoclave y dígito. */
const CURP_FORMA = /^[A-Z][AEIOUX][A-Z]{2}\d{6}[HMX][A-Z]{2}[B-DF-HJ-NP-TV-Z]{3}[0-9A-Z]\d$/
const ENTIDADES_CURP = new Set(['AS', 'BC', 'BS', 'CC', 'CL', 'CM', 'CS', 'CH', 'DF', 'DG', 'GT', 'GR', 'HG', 'JC', 'MC', 'MN',
  'MS', 'NT', 'NL', 'OC', 'PL', 'QT', 'QR', 'SP', 'SL', 'SR', 'TC', 'TS', 'TL', 'VZ', 'YN', 'ZS', 'NE'])

/** Misma regla que el backend: forma, mes 01-12, día 01-31 y entidad federativa válida. */
function curpValida(curp: string): boolean {
  if (!CURP_FORMA.test(curp)) return false
  const mes = Number(curp.slice(6, 8))
  const dia = Number(curp.slice(8, 10))
  return mes >= 1 && mes <= 12 && dia >= 1 && dia <= 31 && ENTIDADES_CURP.has(curp.slice(11, 13))
}
const RFC = /^[A-Z&Ñ]{3,4}\d{2}(0[1-9]|1[0-2])(0[1-9]|[12]\d|3[01])[A-Z0-9]{3}$/
const REQUISITOS: { texto: string; cumple: (c: string) => boolean }[] = [
  { texto: 'De 8 a 64 caracteres', cumple: (c) => c.length >= 8 && c.length <= 64 },
  { texto: 'Una letra mayúscula', cumple: (c) => /\p{Lu}/u.test(c) },
  { texto: 'Una letra minúscula', cumple: (c) => /\p{Ll}/u.test(c) },
  { texto: 'Un número', cumple: (c) => /\d/.test(c) },
  { texto: 'Un carácter especial', cumple: (c) => /[^\p{L}\d\s]/u.test(c) },
  { texto: 'Sin espacios', cumple: (c) => c.length > 0 && !/\s/.test(c) },
]

/** Fecha máxima de nacimiento: el cliente debe tener 18 años cumplidos. */
function fechaMaxima(): string {
  const hoy = new Date()
  hoy.setFullYear(hoy.getFullYear() - 18)
  return hoy.toISOString().slice(0, 10)
}

/** Reglas por paso: las mismas que valida la API, para avisar en cada campo antes de enviar. */
const VALIDACIONES: ((f: Formulario) => Errores)[] = [
  (f) => {
    const e: Errores = {}
    if (!NOMBRE.test(f.nombre.trim())) e.nombre = 'De 2 a 50 letras, sin números ni símbolos.'
    if (f.segundoNombre.trim() && !NOMBRE.test(f.segundoNombre.trim())) e.segundoNombre = 'De 2 a 50 letras, sin números ni símbolos.'
    if (!NOMBRE.test(f.apellidoPaterno.trim())) e.apellidoPaterno = 'De 2 a 50 letras, sin números ni símbolos.'
    if (!NOMBRE.test(f.apellidoMaterno.trim())) e.apellidoMaterno = 'De 2 a 50 letras, sin números ni símbolos.'
    if (!f.fechaNacimiento) e.fechaNacimiento = 'Seleccione su fecha de nacimiento.'
    else if (f.fechaNacimiento > fechaMaxima()) e.fechaNacimiento = 'Debe ser mayor de edad.'
    else if (f.fechaNacimiento < '1900-01-01') e.fechaNacimiento = 'Fecha no válida.'
    if (!curpValida(f.curp.toUpperCase())) e.curp = 'La CURP no cumple el formato oficial (18 caracteres).'
    if (!RFC.test(f.rfc.toUpperCase())) e.rfc = 'RFC no válido (12 o 13 caracteres).'
    if (!NOMBRE.test(f.nacionalidad.trim())) e.nacionalidad = 'Escriba su nacionalidad.'
    return e
  },
  (f) => {
    const e: Errores = {}
    if (!/^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$/.test(f.correoElectronico.trim())) e.correoElectronico = 'Correo electrónico no válido.'
    if (!/^[a-zA-Z0-9._-]{4,50}$/.test(f.nombreUsuario.trim())) e.nombreUsuario = 'De 4 a 50: letras, números, ".", "_" o "-".'
    if (!REQUISITOS.every((r) => r.cumple(f.contrasena))) e.contrasena = 'La contraseña no cumple los requisitos.'
    if (f.confirmar !== f.contrasena || !f.confirmar) e.confirmar = 'Las contraseñas no coinciden.'
    if (!/^\d{10}$/.test(f.telefonoMovil)) e.telefonoMovil = 'Debe tener 10 dígitos.'
    if (f.telefonoAlternativo && !/^\d{10}$/.test(f.telefonoAlternativo)) e.telefonoAlternativo = 'Debe tener 10 dígitos.'
    if (f.telefonoAlternativo && f.telefonoAlternativo === f.telefonoMovil) e.telefonoAlternativo = 'Debe ser distinto del teléfono móvil.'
    return e
  },
  (f) => {
    const e: Errores = {}
    for (const c of ['calle', 'numeroExterior', 'colonia', 'municipio', 'estado', 'pais'] as Campo[]) {
      if (!f[c].trim()) e[c] = 'Obligatorio.'
    }
    if (!/^\d{5}$/.test(f.codigoPostal)) e.codigoPostal = 'Debe tener 5 dígitos.'
    return e
  },
  (f) => {
    const e: Errores = {}
    if (!f.ocupacion.trim()) e.ocupacion = 'Obligatorio.'
    if (!f.empresa.trim()) e.empresa = 'Obligatorio.'
    if (!/^\d{1,10}(\.\d{1,2})?$/.test(f.ingresoMensual) || Number(f.ingresoMensual) <= 0) e.ingresoMensual = 'Monto mayor a cero, máximo 2 decimales.'
    return e
  },
]

/** Color de la barra de fuerza según cuántos requisitos cumple la contraseña. */
function colorDeFuerza(cumplidos: number): string {
  if (cumplidos <= 2) return 'var(--error)'
  return cumplidos <= 4 ? 'var(--pendiente)' : 'var(--exito)'
}

const TITULOS = ['Datos personales', 'Acceso y contacto', 'Domicilio', 'Información laboral']

/** Registro de un cliente nuevo en 4 pasos. Al terminar se envía un código al correo para confirmarlo. */
export function Registro() {
  const navegar = useNavigate()
  const [f, setF] = useState<Formulario>(VACIO)
  const [paso, setPaso] = useState(0)
  const [errores, setErrores] = useState<Errores>({})
  const [error, setError] = useState<string | null>(null)
  const [enviando, setEnviando] = useState(false)

  const cambiar = (campo: Campo, transformar?: (v: string) => string) => (e: ChangeEvent<HTMLInputElement | HTMLSelectElement>) => {
    const valor = transformar ? transformar(e.target.value) : e.target.value
    setF((actual) => ({ ...actual, [campo]: valor }))
    if (errores[campo]) setErrores(({ [campo]: _, ...resto }) => resto)
  }
  const digitos = (max: number) => (v: string) => v.replace(/\D/g, '').slice(0, max)
  const mayusculas = (v: string) => v.toUpperCase().replace(/\s/g, '')

  function validarPaso(): boolean {
    const e = VALIDACIONES[paso](f)
    setErrores(e)
    return Object.keys(e).length === 0
  }

  function siguiente() {
    setError(null)
    if (validarPaso()) setPaso((p) => p + 1)
  }

  async function enviar(e: SubmitEvent<HTMLFormElement>) {
    e.preventDefault()
    if (paso < TITULOS.length - 1) {
      siguiente()
      return
    }
    if (!validarPaso()) return
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
      const r = await auth.registrar(alta)
      void navegar('/login', { replace: true, state: { mensaje: r.mensaje, usuario: alta.nombreUsuario } })
    } catch (err) {
      setError(mensajeDeError(err))
      // Si el dato duplicado es de un paso anterior, se regresa a ese paso
      if (err instanceof ApiError && err.status === 409) {
        if (/CURP|RFC/i.test(err.message)) setPaso(0)
        else if (/correo|usuario/i.test(err.message)) setPaso(1)
      }
    } finally {
      setEnviando(false)
    }
  }

  const campo = (c: Campo, etiqueta: string, props: Record<string, unknown> = {}, ayuda?: ReactNode, transformar?: (v: string) => string) => (
    <label className="campo">
      <span>{etiqueta}</span>
      <input value={f[c]} onChange={cambiar(c, transformar)} aria-invalid={errores[c] ? true : undefined} {...props} />
      {errores[c] ? <span className="error-campo">{errores[c]}</span> : ayuda && <span className="ayuda-campo">{ayuda}</span>}
    </label>
  )

  const cumplidos = REQUISITOS.filter((r) => r.cumple(f.contrasena)).length
  const colorFuerza = colorDeFuerza(cumplidos)
  let textoEnviar = enviando ? 'Creando cuenta…' : 'Crear cuenta'
  if (paso < TITULOS.length - 1) textoEnviar = 'Siguiente'

  return (
    <PantallaAcceso ancho>
      <div className="acceso-encabezado">
        <h1>Crear cuenta</h1>
        <p className="tenue">Paso {paso + 1} de {TITULOS.length}: {TITULOS[paso]}</p>
      </div>
      <Pasos pasos={TITULOS} actual={paso} />
      <form className="tarjeta formulario" onSubmit={enviar} noValidate>
        {paso === 0 && (
          <fieldset>
            <legend>Datos personales</legend>
            <div className="rejilla-campos">
              {campo('nombre', 'Nombre', { maxLength: 50, autoComplete: 'given-name' })}
              {campo('segundoNombre', 'Segundo nombre (opcional)', { maxLength: 50 })}
              {campo('apellidoPaterno', 'Apellido paterno', { maxLength: 50, autoComplete: 'family-name' })}
              {campo('apellidoMaterno', 'Apellido materno', { maxLength: 50 })}
              {campo('fechaNacimiento', 'Fecha de nacimiento', { type: 'date', max: fechaMaxima(), min: '1900-01-01' }, 'Debe ser mayor de edad.')}
              {campo('curp', 'CURP', { maxLength: 18, autoCapitalize: 'characters' }, '18 caracteres', mayusculas)}
              {campo('rfc', 'RFC', { maxLength: 13, autoCapitalize: 'characters' }, '12 o 13 caracteres', mayusculas)}
              <label className="campo">
                <span>Sexo</span>
                <select value={f.sexo} onChange={cambiar('sexo')}>
                  <option value="M">Masculino</option>
                  <option value="F">Femenino</option>
                  <option value="O">Otro</option>
                </select>
              </label>
              {campo('nacionalidad', 'Nacionalidad', { maxLength: 50 })}
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
        )}

        {paso === 1 && (
          <fieldset>
            <legend>Acceso y contacto</legend>
            <div className="rejilla-campos">
              {campo('correoElectronico', 'Correo electrónico', { type: 'email', maxLength: 100, autoComplete: 'email' }, 'Le enviaremos un código para confirmarlo.')}
              {campo('nombreUsuario', 'Nombre de usuario', { maxLength: 50, autoComplete: 'username' })}
              <CampoContrasena etiqueta="Contraseña" valor={f.contrasena} alCambiar={(v) => setF((a) => ({ ...a, contrasena: v }))}
                error={errores.contrasena} maxLength={64} autoComplete="new-password" />
              <CampoContrasena etiqueta="Confirmar contraseña" valor={f.confirmar} alCambiar={(v) => setF((a) => ({ ...a, confirmar: v }))}
                error={errores.confirmar} maxLength={64} autoComplete="new-password" />
            </div>
            <div className="fuerza" aria-hidden="true" style={{ margin: '.9rem 0 .6rem' }}>
              <span style={{ width: `${(cumplidos / REQUISITOS.length) * 100}%`, background: colorFuerza }} />
            </div>
            <ul className="requisitos" aria-label="Requisitos de la contraseña">
              {REQUISITOS.map((r) => (
                <li key={r.texto} className={r.cumple(f.contrasena) ? 'cumple' : ''}>
                  <Icono nombre={r.cumple(f.contrasena) ? 'check' : 'x'} tamanio={14} />
                  {r.texto}
                </li>
              ))}
            </ul>
            <div className="rejilla-campos" style={{ marginTop: '1rem' }}>
              {campo('telefonoMovil', 'Teléfono móvil', { inputMode: 'numeric', maxLength: 10, autoComplete: 'tel-national' }, '10 dígitos', digitos(10))}
              {campo('telefonoAlternativo', 'Teléfono alternativo (opcional)', { inputMode: 'numeric', maxLength: 10 }, undefined, digitos(10))}
            </div>
          </fieldset>
        )}

        {paso === 2 && (
          <fieldset>
            <legend>Domicilio</legend>
            <div className="rejilla-campos">
              {campo('calle', 'Calle', { maxLength: 100, autoComplete: 'address-line1' })}
              {campo('numeroExterior', 'Número exterior', { maxLength: 20 })}
              {campo('numeroInterior', 'Número interior (opcional)', { maxLength: 20 })}
              {campo('colonia', 'Colonia', { maxLength: 100 })}
              {campo('municipio', 'Municipio o alcaldía', { maxLength: 100 })}
              {campo('estado', 'Estado', { maxLength: 100 })}
              {campo('codigoPostal', 'Código postal', { inputMode: 'numeric', maxLength: 5, autoComplete: 'postal-code' }, undefined, digitos(5))}
              {campo('pais', 'País', { maxLength: 50 })}
            </div>
          </fieldset>
        )}

        {paso === 3 && (
          <fieldset>
            <legend>Información laboral</legend>
            <div className="rejilla-campos">
              {campo('ocupacion', 'Ocupación', { maxLength: 100 })}
              {campo('empresa', 'Empresa', { maxLength: 100 })}
              {campo('ingresoMensual', 'Ingreso mensual (MXN)', { inputMode: 'decimal', maxLength: 13 }, 'Ejemplo: 18500.00', (v) => v.replace(/[^\d.]/g, ''))}
            </div>
          </fieldset>
        )}

        {error && <Aviso tipo="error">{error}</Aviso>}
        <div className="acciones fin">
          {paso > 0 && (
            <button type="button" className="boton secundario" onClick={() => { setErrores({}); setPaso((p) => p - 1) }} disabled={enviando}>
              <Icono nombre="flechaIzq" tamanio={18} /> Anterior
            </button>
          )}
          <button type="submit" className="boton" disabled={enviando}>
            {textoEnviar}
            {paso < TITULOS.length - 1 && <Icono nombre="flechaDer" tamanio={18} />}
          </button>
        </div>
      </form>
      <p className="pie">¿Ya tiene cuenta? <Link to="/login">Inicie sesión</Link></p>
    </PantallaAcceso>
  )
}
