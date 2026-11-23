import { useState } from 'react'
import type { ReactNode } from 'react'
import type { EstatusTransaccion } from '../api/tipos'

/** Logotipo del proveedor; si la imagen de GestoPago no existe, muestra las iniciales del servicio. */
export function Logo({ url, nombre, tamanio = 56 }: { url?: string; nombre?: string; tamanio?: number }) {
  const [fallo, setFallo] = useState(false)
  const iniciales = (nombre ?? '?')
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((p) => p[0]?.toUpperCase())
    .join('')
  if (!url || fallo) {
    return (
      <div className="logo logo-iniciales" style={{ width: tamanio, height: tamanio }} aria-label={nombre}>
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

export function Aviso({ tipo, children }: { tipo: 'error' | 'exito' | 'info' | 'pendiente'; children: ReactNode }) {
  return (
    <div className={`aviso ${tipo}`} role={tipo === 'error' ? 'alert' : 'status'}>
      {children}
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

/** Campo para el código de 6 dígitos de la app autenticadora. */
export function CampoCodigo({ valor, alCambiar, disabled }: { valor: string; alCambiar: (v: string) => void; disabled?: boolean }) {
  return (
    <label className="campo">
      <span>Código de su app autenticadora</span>
      <input
        className="codigo"
        inputMode="numeric"
        autoComplete="one-time-code"
        maxLength={6}
        placeholder="000000"
        value={valor}
        disabled={disabled}
        onChange={(e) => alCambiar(e.target.value.replace(/\D/g, '').slice(0, 6))}
        required
      />
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
