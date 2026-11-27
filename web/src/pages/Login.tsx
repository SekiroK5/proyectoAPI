import { useEffect, useState } from 'react'
import type { ReactNode, SubmitEvent } from 'react'
import { Link, Navigate, useLocation } from 'react-router-dom'
import QRCode from 'qrcode'
import { mensajeDeError } from '../api/cliente'
import { auth } from '../api/servicios'
import type { ConfiguracionA2F, LoginRespuesta, PasoLogin } from '../api/tipos'
import { Aviso, CampoCodigo, CampoContrasena, Pasos } from '../components/Comunes'
import { Icono } from '../components/Iconos'
import { PantallaAcceso } from '../components/PantallaAcceso'
import { useSesion } from '../sesion/Sesion'

type Paso = 'credenciales' | PasoLogin
type Metodo = 'app' | 'correo'

const ESPERA_REENVIO = 60
const INDICE_PASO: Record<Paso, number> = {
  credenciales: 0, VERIFICAR_CORREO: 1, CONFIGURAR_A2F: 2, VERIFICAR_A2F: 2, VERIFICAR_CODIGO_CORREO: 2,
}
const TITULO_PASO: Record<Paso, string> = {
  credenciales: 'Bienvenido de nuevo', VERIFICAR_CORREO: 'Confirme su correo',
  CONFIGURAR_A2F: 'Segundo factor', VERIFICAR_A2F: 'Segundo factor', VERIFICAR_CODIGO_CORREO: 'Segundo factor',
}

function subtitulo(paso: Paso, metodo: Metodo): string {
  if (paso === 'credenciales') return 'Ingrese con su usuario o correo electrónico.'
  if (paso === 'VERIFICAR_CORREO') return 'Le enviamos un código de 6 dígitos a su correo. Vence en 15 minutos.'
  return metodo === 'correo' ? 'Escriba el código que le enviamos a su correo.' : 'Use su app autenticadora o pida un código por correo.'
}

function textoReenvio(espera: number, enviado: boolean): string {
  if (espera > 0) return `Reenviar código en ${espera} s`
  return enviado ? 'Reenviar código' : 'Enviarme el código'
}

/**
 * Inicio de sesión: usuario o correo + contraseña, confirmación del correo (solo si aún no está
 * confirmado) y el segundo factor: app autenticadora o código enviado al correo.
 */
export function Login() {
  const { sesion, iniciar, aviso } = useSesion()
  const ubicacion = useLocation()
  const estado = ubicacion.state as { desde?: string; mensaje?: string; usuario?: string } | null
  const [paso, setPaso] = useState<Paso>('credenciales')
  const [usuario, setUsuario] = useState(estado?.usuario ?? '')
  const [contrasena, setContrasena] = useState('')
  const [tokenTemporal, setTokenTemporal] = useState('')
  const [configuracion, setConfiguracion] = useState<ConfiguracionA2F | null>(null)
  const [codigo, setCodigo] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [info, setInfo] = useState<string | null>(estado?.mensaje ?? null)
  const [enviando, setEnviando] = useState(false)
  const [espera, setEspera] = useState(0)
  const [metodo, setMetodo] = useState<Metodo>('app')
  const [codigoEnviado, setCodigoEnviado] = useState(false)

  useEffect(() => {
    if (espera <= 0) return
    const t = setTimeout(() => setEspera((s) => s - 1), 1000)
    return () => clearTimeout(t)
  }, [espera])

  if (sesion) {
    const desde = estado?.desde
    return <Navigate to={desde && desde !== '/login' ? desde : '/catalogo'} replace />
  }

  /** Ejecuta una llamada mostrando "enviando" y el error de la API; si falla, limpia el código escrito. */
  async function ejecutar(accion: () => Promise<void>) {
    setError(null)
    setEnviando(true)
    try {
      await accion()
    } catch (err) {
      setError(mensajeDeError(err))
      setCodigo('')
    } finally {
      setEnviando(false)
    }
  }

  /** Aplica el siguiente paso que devolvió la API. */
  async function avanzar(datos: LoginRespuesta) {
    setTokenTemporal(datos.tokenTemporal)
    if (datos.siguientePaso === 'CONFIGURAR_A2F') {
      const c = await auth.configurarA2F(datos.tokenTemporal)
      setConfiguracion(c.datos ?? null)
    }
    if (datos.siguientePaso === 'VERIFICAR_CORREO') setEspera(ESPERA_REENVIO)
    setCodigo('')
    setPaso(datos.siguientePaso)
  }

  const alEnviar = (accion: () => Promise<void>) => (e: SubmitEvent<HTMLFormElement>) => {
    e.preventDefault()
    void ejecutar(accion)
  }

  const entrarConCredenciales = alEnviar(async () => {
    setInfo(null)
    const r = await auth.login(usuario.trim(), contrasena)
    await avanzar(r.datos!)
    setContrasena('')
  })

  const confirmarCorreo = alEnviar(async () => {
    const r = await auth.verificarCorreo(tokenTemporal, codigo)
    setInfo('¡Correo confirmado! Ahora elija su segundo factor.')
    await avanzar(r.datos!)
  })

  const entrarConApp = alEnviar(async () => {
    iniciar((await auth.verificarA2F(tokenTemporal, codigo)).datos!)
  })

  const entrarConCorreo = alEnviar(async () => {
    iniciar((await auth.verificarCodigoInicioSesion(tokenTemporal, codigo)).datos!)
  })

  const reenviarConfirmacion = () => ejecutar(async () => {
    setInfo(null)
    const r = await auth.reenviarCorreo(tokenTemporal)
    setTokenTemporal(r.datos!.tokenTemporal)
    setInfo(r.mensaje)
    setEspera(ESPERA_REENVIO)
  })

  const pedirCodigoInicioSesion = () => ejecutar(async () => {
    setInfo(null)
    const r = await auth.enviarCodigoInicioSesion(tokenTemporal)
    setTokenTemporal(r.datos!.tokenTemporal)
    setInfo(r.mensaje)
    setCodigoEnviado(true)
    setEspera(ESPERA_REENVIO)
  })

  function elegirMetodo(nuevo: Metodo) {
    setMetodo(nuevo)
    setCodigo('')
    setError(null)
    if (nuevo === 'correo' && !codigoEnviado) void pedirCodigoInicioSesion()
  }

  function volver() {
    setPaso('credenciales')
    setTokenTemporal('')
    setConfiguracion(null)
    setCodigo('')
    setError(null)
    setInfo(null)
    setMetodo('app')
    setCodigoEnviado(false)
  }

  const segundoFactor = paso === 'CONFIGURAR_A2F' || paso === 'VERIFICAR_A2F'
  const errorAviso = error && <Aviso tipo="error">{error}</Aviso>
  const botonVolver = <button type="button" className="boton secundario" onClick={volver} disabled={enviando}>Volver</button>

  return (
    <PantallaAcceso>
      <div className="acceso-encabezado">
        <h1>{TITULO_PASO[paso]}</h1>
        <p className="tenue">{subtitulo(paso, metodo)}</p>
      </div>
      <Pasos pasos={['Contraseña', 'Correo', 'Código']} actual={INDICE_PASO[paso]} />

      {aviso && paso === 'credenciales' && <Aviso tipo="info">{aviso}</Aviso>}
      {info && <Aviso tipo="exito">{info}</Aviso>}

      {paso === 'credenciales' && (
        <form onSubmit={entrarConCredenciales} className="formulario">
          <label className="campo">
            <span>Usuario o correo electrónico</span>
            <span className="entrada">
              <Icono nombre="usuario" tamanio={18} />
              <input value={usuario} onChange={(e) => setUsuario(e.target.value)} autoComplete="username" required maxLength={100} />
            </span>
          </label>
          <CampoContrasena etiqueta="Contraseña" valor={contrasena} alCambiar={setContrasena} autoComplete="current-password" required maxLength={64} />
          {errorAviso}
          <button type="submit" className="boton ancho" disabled={enviando || !usuario.trim() || !contrasena}>
            {enviando ? 'Validando…' : 'Continuar'}
          </button>
          <p className="pie">¿No tiene cuenta? <Link to="/registro">Regístrese</Link></p>
        </form>
      )}

      {paso === 'VERIFICAR_CORREO' && (
        <FormCodigo alEnviar={confirmarCorreo} etiqueta="Código del correo" codigo={codigo} setCodigo={setCodigo}
          enviando={enviando} textoBoton="Confirmar correo" error={errorAviso}>
          <BotonReenvio espera={espera} enviando={enviando} texto={espera > 0 ? `Reenviar código en ${espera} s` : 'Reenviar código'}
            alPulsar={() => void reenviarConfirmacion()} />
          {botonVolver}
        </FormCodigo>
      )}

      {segundoFactor && <SelectorMetodo metodo={metodo} enviando={enviando} alElegir={elegirMetodo} />}

      {segundoFactor && metodo === 'app' && (
        <FormCodigo alEnviar={entrarConApp} etiqueta="Código de su app autenticadora" codigo={codigo} setCodigo={setCodigo}
          enviando={enviando} textoBoton="Entrar" error={errorAviso}
          encabezado={paso === 'CONFIGURAR_A2F' && configuracion && <ConfigurarApp configuracion={configuracion} />}>
          {botonVolver}
        </FormCodigo>
      )}

      {segundoFactor && metodo === 'correo' && (
        <FormCodigo alEnviar={entrarConCorreo} etiqueta="Código del correo" codigo={codigo} setCodigo={setCodigo}
          enviando={enviando} bloqueado={!codigoEnviado} textoBoton="Entrar" error={errorAviso}>
          <BotonReenvio espera={espera} enviando={enviando} texto={textoReenvio(espera, codigoEnviado)}
            alPulsar={() => void pedirCodigoInicioSesion()} />
          {botonVolver}
        </FormCodigo>
      )}
    </PantallaAcceso>
  )
}

/** Formulario de un código de 6 dígitos con su botón de envío y acciones extra. */
function FormCodigo(props: Readonly<{
  alEnviar: (e: SubmitEvent<HTMLFormElement>) => void
  etiqueta: string
  codigo: string
  setCodigo: (v: string) => void
  enviando: boolean
  bloqueado?: boolean
  textoBoton: string
  error: ReactNode
  encabezado?: ReactNode
  children?: ReactNode
}>) {
  const { alEnviar, etiqueta, codigo, setCodigo, enviando, bloqueado = false, textoBoton, error, encabezado, children } = props
  return (
    <form onSubmit={alEnviar} className="formulario">
      {encabezado}
      <CampoCodigo etiqueta={etiqueta} valor={codigo} alCambiar={setCodigo} disabled={enviando || bloqueado} />
      {error}
      <button type="submit" className="boton ancho" disabled={enviando || bloqueado || codigo.length !== 6}>
        {enviando ? 'Verificando…' : textoBoton}
      </button>
      {children}
    </form>
  )
}

function BotonReenvio({ espera, enviando, texto, alPulsar }: Readonly<{ espera: number; enviando: boolean; texto: string; alPulsar: () => void }>) {
  return (
    <button type="button" className="boton fantasma" onClick={alPulsar} disabled={espera > 0 || enviando}>
      <Icono nombre="correo" tamanio={18} />
      {texto}
    </button>
  )
}

/** Elección del segundo factor: app autenticadora o código por correo. */
function SelectorMetodo({ metodo, enviando, alElegir }: Readonly<{ metodo: Metodo; enviando: boolean; alElegir: (m: Metodo) => void }>) {
  const opciones: { valor: Metodo; icono: 'telefono' | 'correo'; texto: string }[] = [
    { valor: 'app', icono: 'telefono', texto: 'App autenticadora' },
    { valor: 'correo', icono: 'correo', texto: 'Código por correo' },
  ]
  return (
    <fieldset className="segmentos">
      <legend className="oculto-visual">Cómo recibir el código</legend>
      {opciones.map((o) => (
        <button key={o.valor} type="button" aria-pressed={metodo === o.valor} className={metodo === o.valor ? 'activo' : ''}
          onClick={() => alElegir(o.valor)} disabled={enviando}>
          <Icono nombre={o.icono} tamanio={18} /> {o.texto}
        </button>
      ))}
    </fieldset>
  )
}

/** QR y clave para dar de alta la cuenta en la app autenticadora (primera vez). */
function ConfigurarApp({ configuracion }: Readonly<{ configuracion: ConfiguracionA2F }>) {
  const [qr, setQr] = useState<string | null>(null)

  useEffect(() => {
    QRCode.toDataURL(configuracion.uriOtpauth, { width: 220, margin: 1 })
      .then(setQr)
      .catch(() => setQr(null))
  }, [configuracion])

  return (
    <div className="configurar-a2f">
      <p className="chico">
        Escanee el código con Google Authenticator, Microsoft Authenticator u otra app TOTP y escriba el código que aparece.
      </p>
      {qr && <img className="qr" src={qr} alt="Código QR para la app autenticadora" width={200} height={200} />}
      <div className="secreto chico">
        <span className="tenue">¿No puede escanearlo? Clave:</span>
        <code>{configuracion.secreto}</code>
        <button type="button" className="boton-icono" aria-label="Copiar clave" title="Copiar clave"
          onClick={() => void navigator.clipboard?.writeText(configuracion.secreto)}>
          <Icono nombre="copiar" tamanio={16} />
        </button>
      </div>
    </div>
  )
}
