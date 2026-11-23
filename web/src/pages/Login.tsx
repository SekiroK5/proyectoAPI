import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { Link, Navigate, useLocation } from 'react-router-dom'
import QRCode from 'qrcode'
import { mensajeDeError } from '../api/cliente'
import { auth } from '../api/servicios'
import type { ConfiguracionA2F, PasoLogin } from '../api/tipos'
import { Aviso, CampoCodigo } from '../components/Comunes'
import { useSesion } from '../sesion/Sesion'

type Paso = 'credenciales' | PasoLogin

/**
 * Inicio de sesión en dos pasos: usuario o correo + contraseña y después el código de la app
 * autenticadora. La primera vez se muestra el código QR para dar de alta la cuenta en la app.
 */
export function Login() {
  const { sesion, iniciar, aviso } = useSesion()
  const ubicacion = useLocation()
  const [paso, setPaso] = useState<Paso>('credenciales')
  const [usuario, setUsuario] = useState('')
  const [contrasena, setContrasena] = useState('')
  const [tokenTemporal, setTokenTemporal] = useState('')
  const [configuracion, setConfiguracion] = useState<ConfiguracionA2F | null>(null)
  const [qr, setQr] = useState<string | null>(null)
  const [codigo, setCodigo] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [enviando, setEnviando] = useState(false)

  useEffect(() => {
    if (!configuracion) return
    QRCode.toDataURL(configuracion.uriOtpauth, { width: 220, margin: 1 })
      .then(setQr)
      .catch(() => setQr(null))
  }, [configuracion])

  if (sesion) {
    const desde = (ubicacion.state as { desde?: string } | null)?.desde
    return <Navigate to={desde && desde !== '/login' ? desde : '/catalogo'} replace />
  }

  async function enviarCredenciales(e: FormEvent) {
    e.preventDefault()
    setError(null)
    setEnviando(true)
    try {
      const r = await auth.login(usuario.trim(), contrasena)
      const datos = r.datos!
      setTokenTemporal(datos.tokenTemporal)
      if (datos.siguientePaso === 'CONFIGURAR_A2F') {
        const c = await auth.configurarA2F(datos.tokenTemporal)
        setConfiguracion(c.datos ?? null)
      }
      setPaso(datos.siguientePaso)
      setContrasena('')
    } catch (err) {
      setError(mensajeDeError(err))
    } finally {
      setEnviando(false)
    }
  }

  async function enviarCodigo(e: FormEvent) {
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
  }

  return (
    <div className="pantalla-acceso">
      <div className="tarjeta acceso">
        <div className="marca grande">
          <span className="marca-icono" aria-hidden="true">₲</span>
          <span>GestoPago</span>
        </div>

        {paso === 'credenciales' && (
          <form onSubmit={enviarCredenciales} className="formulario">
            <h1>Iniciar sesión</h1>
            {aviso && <Aviso tipo="info">{aviso}</Aviso>}
            <label className="campo">
              <span>Usuario o correo electrónico</span>
              <input value={usuario} onChange={(e) => setUsuario(e.target.value)} autoComplete="username" required maxLength={100} />
            </label>
            <label className="campo">
              <span>Contraseña</span>
              <input type="password" value={contrasena} onChange={(e) => setContrasena(e.target.value)} autoComplete="current-password" required maxLength={64} />
            </label>
            {error && <Aviso tipo="error">{error}</Aviso>}
            <button className="boton" disabled={enviando}>{enviando ? 'Validando…' : 'Continuar'}</button>
            <p className="pie">
              ¿No tiene cuenta? <Link to="/registro">Regístrese</Link>
            </p>
          </form>
        )}

        {paso !== 'credenciales' && (
          <form onSubmit={enviarCodigo} className="formulario">
            <h1>Segundo factor</h1>
            {paso === 'CONFIGURAR_A2F' && configuracion && (
              <div className="configurar-a2f">
                <p>
                  Escanee este código con Google Authenticator, Microsoft Authenticator u otra app TOTP. Después escriba el
                  código de 6 dígitos que aparece en la app.
                </p>
                {qr && <img className="qr" src={qr} alt="Código QR para la app autenticadora" width={220} height={220} />}
                <p className="secreto">
                  ¿No puede escanearlo? Capture esta clave: <code>{configuracion.secreto}</code>
                </p>
              </div>
            )}
            {paso === 'VERIFICAR_A2F' && <p>Escriba el código de 6 dígitos de su app autenticadora.</p>}
            <CampoCodigo valor={codigo} alCambiar={setCodigo} disabled={enviando} />
            {error && <Aviso tipo="error">{error}</Aviso>}
            <button className="boton" disabled={enviando || codigo.length !== 6}>{enviando ? 'Verificando…' : 'Entrar'}</button>
            <button type="button" className="boton secundario" onClick={volver} disabled={enviando}>Volver</button>
          </form>
        )}
      </div>
    </div>
  )
}
