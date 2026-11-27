import { useEffect, useState } from 'react'
import type { InputHTMLAttributes, ReactNode } from 'react'
import { mensajeDeError } from '../api/cliente'
import { auth } from '../api/servicios'
import type { EstatusTransaccion } from '../api/tipos'
import { Icono } from './Iconos'
import type { NombreIcono } from './Iconos'

/** Logotipo del proveedor; si la imagen de GestoPago no existe, muestra las iniciales del servicio. */
export function Logo({ url, nombre, tamanio = 56 }: { url?: string; nombre?: string; tamanio?: number }) {
  const [fallo, setFallo] = useState(false)
  const iniciales = (nombre ?? '?')
    .replace(/[^\p{L}\p{N}\s]/gu, '')
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((p) => p[0]?.toUpperCase())
    .join('')
  if (!url || fallo) {
    return (
      <div className="logo logo-iniciales" style={{ width: tamanio, height: tamanio }} role="img" aria-label={`Logotipo de ${nombre ?? 'servicio'}`}>
        {iniciales || '?'}
      </div>
    )
  }
  return (
    <img
      className="logo"
      src={url}
      alt={`Logotipo de ${nombre ?? 'servicio'}`}
      width={tamanio}
      height={tamanio}
      loading="lazy"
      decoding="async"
      onError={() => setFallo(true)}
    />
  )
}

const ESTATUS: Record<EstatusTransaccion, { texto: string; clase: string }> = {
  APLICADA: { texto: 'Exitosa', clase: 'exito' },
  RECHAZADA: { texto: 'Rechazada', clase: 'error' },
  PENDIENTE: { texto: 'En proceso', clase: 'pendiente' },
  PENDIENTE_CONFIRMACION: { texto: 'Pendiente de confirmar', clase: 'pendiente' },
  REVISION_MANUAL: { texto: 'En revisión', clase: 'pendiente' },
}

export function Estatus({ estatus }: { estatus: EstatusTransaccion }) {
  const e = ESTATUS[estatus] ?? { texto: estatus, clase: 'pendiente' }
  return <span className={`insignia ${e.clase}`}>{e.texto}</span>
}

type TipoAviso = 'error' | 'exito' | 'info' | 'pendiente'
const ICONO_AVISO: Record<TipoAviso, NombreIcono> = { error: 'alerta', exito: 'check', info: 'info', pendiente: 'reloj' }

export function Aviso({ tipo, children }: { tipo: TipoAviso; children: ReactNode }) {
  return (
    <div className={`aviso ${tipo}`} role={tipo === 'error' ? 'alert' : 'status'}>
      <Icono nombre={ICONO_AVISO[tipo]} tamanio={18} />
      <div>{children}</div>
    </div>
  )
}

export function Cargando({ texto = 'Cargando…' }: { texto?: string }) {
  return (
    <div className="cargando" role="status">
      <span className="giro" aria-hidden="true" />
      {texto}
    </div>
  )
}

/** Estado vacío con ícono, título, texto y una acción opcional. */
export function Vacio({ icono, titulo, children, accion }: { icono: NombreIcono; titulo: string; children?: ReactNode; accion?: ReactNode }) {
  return (
    <div className="vacio">
      <span className="vacio-icono"><Icono nombre={icono} tamanio={30} /></span>
      <h2>{titulo}</h2>
      {children && <p>{children}</p>}
      {accion}
    </div>
  )
}

/** Barra de pasos de un flujo (pago, inicio de sesión, registro). */
export function Pasos({ pasos, actual }: { pasos: string[]; actual: number }) {
  return (
    <ol className="pasos" aria-label={`Paso ${actual + 1} de ${pasos.length}`}>
      {pasos.map((p, i) => (
        <li key={p} className={i < actual ? 'hecho' : i === actual ? 'actual' : ''} aria-current={i === actual ? 'step' : undefined}>
          {p}
        </li>
      ))}
    </ol>
  )
}

/** Campo para un código de 6 dígitos (app autenticadora o correo). */
export function CampoCodigo({
  valor,
  alCambiar,
  disabled,
  etiqueta = 'Código de su app autenticadora',
  autoFocus,
}: {
  valor: string
  alCambiar: (v: string) => void
  disabled?: boolean
  etiqueta?: string
  autoFocus?: boolean
}) {
  return (
    <label className="campo">
      <span>{etiqueta}</span>
      <input
        className="codigo"
        inputMode="numeric"
        autoComplete="one-time-code"
        maxLength={6}
        placeholder="000000"
        value={valor}
        disabled={disabled}
        autoFocus={autoFocus}
        onChange={(e) => alCambiar(e.target.value.replace(/\D/g, '').slice(0, 6))}
        required
      />
    </label>
  )
}

/**
 * Alternativa a la app autenticadora para pagos y depósitos: pide un código al correo del cliente.
 * El código que llega se escribe en el mismo campo.
 */
export function CodigoPorCorreo({ disabled }: { disabled?: boolean }) {
  const [espera, setEspera] = useState(0)
  const [estado, setEstado] = useState<{ tipo: 'exito' | 'error'; texto: string } | null>(null)
  const [enviando, setEnviando] = useState(false)

  useEffect(() => {
    if (espera <= 0) return
    const t = setTimeout(() => setEspera((s) => s - 1), 1000)
    return () => clearTimeout(t)
  }, [espera])

  async function pedir() {
    setEnviando(true)
    setEstado(null)
    try {
      const r = await auth.enviarCodigoOperacion()
      setEstado({ tipo: 'exito', texto: r.mensaje })
      setEspera(60)
    } catch (err) {
      setEstado({ tipo: 'error', texto: mensajeDeError(err) })
    } finally {
      setEnviando(false)
    }
  }

  return (
    <>
      <button type="button" className="enlace-boton" onClick={() => void pedir()} disabled={disabled || enviando || espera > 0}>
        {espera > 0 ? `¿No llegó? Reenviar en ${espera} s` : enviando ? 'Enviando código…' : '¿No tiene la app? Enviarme el código por correo'}
      </button>
      {estado && <Aviso tipo={estado.tipo}>{estado.texto}</Aviso>}
    </>
  )
}

/** Contraseña con botón para mostrarla u ocultarla. */
export function CampoContrasena({
  etiqueta,
  valor,
  alCambiar,
  error,
  ...props
}: { etiqueta: string; valor: string; alCambiar: (v: string) => void; error?: string | null } & Omit<InputHTMLAttributes<HTMLInputElement>, 'value' | 'onChange'>) {
  const [visible, setVisible] = useState(false)
  return (
    <label className="campo">
      <span>{etiqueta}</span>
      <span className="entrada">
        <Icono nombre="candado" tamanio={18} />
        <input
          {...props}
          type={visible ? 'text' : 'password'}
          value={valor}
          onChange={(e) => alCambiar(e.target.value)}
          aria-invalid={error ? true : undefined}
          style={{ paddingRight: '2.8rem' }}
        />
        <button
          type="button"
          className="boton-icono"
          onClick={() => setVisible((v) => !v)}
          aria-label={visible ? 'Ocultar contraseña' : 'Mostrar contraseña'}
          title={visible ? 'Ocultar contraseña' : 'Mostrar contraseña'}
        >
          <Icono nombre={visible ? 'ojoCerrado' : 'ojo'} tamanio={18} />
        </button>
      </span>
      {error && <span className="error-campo">{error}</span>}
    </label>
  )
}

export function dinero(valor?: string | number | null): string {
  const n = typeof valor === 'string' ? Number(valor) : (valor ?? 0)
  return new Intl.NumberFormat('es-MX', { style: 'currency', currency: 'MXN' }).format(Number.isFinite(n) ? n : 0)
}

export function fecha(valor?: string): string {
  if (!valor) return ''
  const d = new Date(valor)
  return Number.isNaN(d.getTime())
    ? valor
    : new Intl.DateTimeFormat('es-MX', { dateStyle: 'medium', timeStyle: 'short' }).format(d)
}

/** 1234567890123456 -> 1234 5678 9012 3456 */
export function agruparCuenta(numero?: string): string {
  return (numero ?? '').replace(/(.{4})(?=.)/g, '$1 ')
}
