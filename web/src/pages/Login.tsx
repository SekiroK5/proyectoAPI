import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
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

const ESPERA_REENVIO = 60

/**
 * Inicio de sesión: usuario o correo + contraseña, confirmación del correo (solo si aún no está
 * confirmado) y el código de la app autenticadora. La primera vez se muestra el QR para darla de alta.
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
  const [qr, setQr] = useState<string | null>(null)
  const [codigo, setCodigo] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [info, setInfo] = useState<string | null>(estado?.mensaje ?? null)
  const [enviando, setEnviando] = useState(false)
  const [espera, setEspera] = useState(0)
  const [metodo, setMetodo] = useState<'app' | 'correo'>('app')
  const [codigoEnviado, setCodigoEnviado] = useState(false)

  useEffect(() => {
    if (!configuracion) return
    QRCode.toDataURL(configuracion.uriOtpauth, { width: 220, margin: 1 })
      .then(setQr)
      .catch(() => setQr(null))
  }, [configuracion])

  useEffect(() => {
    if (espera <= 0) return
    const t = setTimeout(() => setEspera((s) => s - 1), 1000)
    return () => clearTimeout(t)
  }, [espera])

  if (sesion) {
    const desde = estado?.desde
    return <Navigate to={desde && desde !== '/login' ? desde : '/catalogo'} replace />
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

  async function enviarCredenciales(e: FormEvent) {
    e.preventDefault()
    setError(null)
    setInfo(null)
    setEnviando(true)
    try {
      const r = await auth.login(usuario.trim(), contrasena)
      await avanzar(r.datos!)
      setContrasena('')
    } catch (err) {
      setError(mensajeDeError(err))
    } finally {
      setEnviando(false)
    }
  }

  async function enviarCodigoCorreo(e: FormEvent) {
    e.preventDefault()
    setError(null)
    setEnviando(true)
    try {
      const r = await auth.verificarCorreo(tokenTemporal, codigo)
      setInfo('¡Correo confirmado! Ahora configure su segundo factor.')
      await avanzar(r.datos!)
    } catch (err) {
      setError(mensajeDeError(err))
      setCodigo('')
    } finally {
      setEnviando(false)
    }
  }

  async function reenviar() {
    setError(null)
    setInfo(null)
    try {
      const r = await auth.reenviarCorreo(tokenTemporal)
      setTokenTemporal(r.datos!.tokenTemporal)
      setInfo(r.mensaje)
      setEspera(ESPERA_REENVIO)
    } catch (err) {
      setError(mensajeDeError(err))
    }
  }

  async function enviarCodigoA2F(e: FormEvent) {
    e.preventDefault()
    setError(null)
    setEnviando(true)
    try {
      const r = await auth.verificarA2F(tokenTemporal, codigo)
      iniciar(r.datos!)
    } catch (err) {
      setError(mensajeDeError(err))
      setCodigo('')
    } finally {
      setEnviando(false)
    }
  }

  function volver() {
    setPaso('credenciales')
    setTokenTemporal('')
    setConfiguracion(null)
    setQr(null)
    setCodigo('')
    setError(null)
    setInfo(null)
    setMetodo('app')
    setCodigoEnviado(false)
  }

  /** Segundo factor por correo: pide el código del inicio de sesión. */
  async function pedirCodigoInicioSesion() {
    setError(null)
    setInfo(null)
    setEnviando(true)
    try {
      const r = await auth.enviarCodigoInicioSesion(tokenTemporal)
      setTokenTemporal(r.datos!.tokenTemporal)
      setInfo(r.mensaje)
      setCodigoEnviado(true)
      setEspera(ESPERA_REENVIO)
    } catch (err) {
      setError(mensajeDeError(err))
    } finally {
      setEnviando(false)
    }
  }

  function elegirMetodo(nuevo: 'app' | 'correo') {
    setMetodo(nuevo)
    setCodigo('')
    setError(null)
    if (nuevo === 'correo' && !codigoEnviado) void pedirCodigoInicioSesion()
  }

  async function enviarCodigoCorreoLogin(e: FormEvent) {
    e.preventDefault()
    setError(null)
    setEnviando(true)
    try {
      const r = await auth.verificarCodigoInicioSesion(tokenTemporal, codigo)
      iniciar(r.datos!)
    } catch (err) {
      setError(mensajeDeError(err))
      setCodigo('')
    } finally {
      setEnviando(false)
    }
  }

  const indicePaso = paso === 'credenciales' ? 0 : paso === 'VERIFICAR_CORREO' ? 1 : 2

  return (
    <PantallaAcceso>
      <div className="acceso-encabezado">
        <h1>{paso === 'credenciales' ? 'Bienvenido de nuevo' : paso === 'VERIFICAR_CORREO' ? 'Confirme su correo' : 'Segundo factor'}</h1>
        <p className="tenue">
          {paso === 'credenciales' && 'Ingrese con su usuario o correo electrónico.'}
          {paso === 'VERIFICAR_CORREO' && 'Le enviamos un código de 6 dígitos a su correo. Vence en 15 minutos.'}
          {(paso === 'CONFIGURAR_A2F' || paso === 'VERIFICAR_A2F') &&
            (metodo === 'correo' ? 'Escriba el código que le enviamos a su correo.' : 'Use su app autenticadora o pida un código por correo.')}
        </p>
      </div>
      <Pasos pasos={['Contraseña', 'Correo', 'Código']} actual={indicePaso} />

      {aviso && paso === 'credenciales' && <Aviso tipo="info">{aviso}</Aviso>}
      {info && <Aviso tipo="exito">{info}</Aviso>}

      {paso === 'credenciales' && (
        <form onSubmit={enviarCredenciales} className="formulario" noValidate={false}>
          <label className="campo">
            <span>Usuario o correo electrónico</span>
            <span className="entrada">
              <Icono nombre="usuario" tamanio={18} />
              <input value={usuario} onChange={(e) => setUsuario(e.target.value)} autoComplete="username" required maxLength={100} autoFocus />
            </span>
          </label>
          <CampoContrasena etiqueta="Contraseña" valor={contrasena} alCambiar={setContrasena} autoComplete="current-password" required maxLength={64} />
          {error && <Aviso tipo="error">{error}</Aviso>}
          <button className="boton ancho" disabled={enviando || !usuario.trim() || !contrasena}>
            {enviando ? 'Validando…' : 'Continuar'}
          </button>
          <p className="pie">¿No tiene cuenta? <Link to="/registro">Regístrese</Link></p>
        </form>
      )}

      {paso === 'VERIFICAR_CORREO' && (
        <form onSubmit={enviarCodigoCorreo} className="formulario">
          <CampoCodigo etiqueta="Código del correo" valor={codigo} alCambiar={setCodigo} disabled={enviando} autoFocus />
          {error && <Aviso tipo="error">{error}</Aviso>}
          <button className="boton ancho" disabled={enviando || codigo.length !== 6}>{enviando ? 'Confirmando…' : 'Confirmar correo'}</button>
          <button type="button" className="boton fantasma" onClick={() => void reenviar()} disabled={espera > 0 || enviando}>
            <Icono nombre="correo" tamanio={18} />
            {espera > 0 ? `Reenviar código en ${espera} s` : 'Reenviar código'}
          </button>
          <button type="button" className="boton secundario" onClick={volver} disabled={enviando}>Volver</button>
        </form>
      )}

      {(paso === 'CONFIGURAR_A2F' || paso === 'VERIFICAR_A2F') && (
        <div className="segmentos" role="tablist" aria-label="Cómo recibir el código">
          <button type="button" role="tab" aria-selected={metodo === 'app'} className={metodo === 'app' ? 'activo' : ''}
            onClick={() => elegirMetodo('app')} disabled={enviando}>
            <Icono nombre="telefono" tamanio={18} /> App autenticadora
          </button>
          <button type="button" role="tab" aria-selected={metodo === 'correo'} className={metodo === 'correo' ? 'activo' : ''}
            onClick={() => elegirMetodo('correo')} disabled={enviando}>
            <Icono nombre="correo" tamanio={18} /> Código por correo
          </button>
        </div>
      )}

      {(paso === 'CONFIGURAR_A2F' || paso === 'VERIFICAR_A2F') && metodo === 'app' && (
        <form onSubmit={enviarCodigoA2F} className="formulario">
          {paso === 'CONFIGURAR_A2F' && configuracion && (
            <div className="configurar-a2f">
              <p className="chico">
                Escanee el código con Google Authenticator, Microsoft Authenticator u otra app TOTP y escriba el código que aparece.
              </p>
              {qr && <img className="qr" src={qr} alt="Código QR para la app autenticadora" width={200} height={200} />}
              <div className="secreto chico">
                <span className="tenue">¿No puede escanearlo? Clave:</span>
                <code>{configuracion.secreto}</code>
                <button
                  type="button"
                  className="boton-icono"
                  aria-label="Copiar clave"
                  title="Copiar clave"
                  onClick={() => void navigator.clipboard?.writeText(configuracion.secreto)}
                >
                  <Icono nombre="copiar" tamanio={16} />
                </button>
              </div>
            </div>
          )}
          <CampoCodigo valor={codigo} alCambiar={setCodigo} disabled={enviando} autoFocus />
          {error && <Aviso tipo="error">{error}</Aviso>}
          <button className="boton ancho" disabled={enviando || codigo.length !== 6}>{enviando ? 'Verificando…' : 'Entrar'}</button>
          <button type="button" className="boton secundario" onClick={volver} disabled={enviando}>Volver</button>
        </form>
      )}

      {(paso === 'CONFIGURAR_A2F' || paso === 'VERIFICAR_A2F') && metodo === 'correo' && (
        <form onSubmit={enviarCodigoCorreoLogin} className="formulario">
          <CampoCodigo etiqueta="Código del correo" valor={codigo} alCambiar={setCodigo} disabled={enviando || !codigoEnviado} autoFocus />
          {error && <Aviso tipo="error">{error}</Aviso>}
          <button className="boton ancho" disabled={enviando || !codigoEnviado || codigo.length !== 6}>
            {enviando ? 'Verificando…' : 'Entrar'}
          </button>
          <button type="button" className="boton fantasma" onClick={() => void pedirCodigoInicioSesion()} disabled={espera > 0 || enviando}>
            <Icono nombre="correo" tamanio={18} />
            {espera > 0 ? `Reenviar código en ${espera} s` : codigoEnviado ? 'Reenviar código' : 'Enviarme el código'}
          </button>
          <button type="button" className="boton secundario" onClick={volver} disabled={enviando}>Volver</button>
        </form>
      )}
    </PantallaAcceso>
  )
}
