import { useCallback, useEffect, useState } from 'react'
import type { SubmitEvent } from 'react'
import { mensajeDeError } from '../api/cliente'
import { cuentas } from '../api/servicios'
import type { Cuenta as DatosCuenta } from '../api/tipos'
import { Aviso, CampoCodigo, CodigoPorCorreo, Cargando, agruparCuenta, dinero } from '../components/Comunes'
import { Icono } from '../components/Iconos'
import { useSesion } from '../sesion/Sesion'

const MONTOS_RAPIDOS = [100, 200, 500, 1000]

/** Saldo de la cuenta y depósito a la cuenta propia (confirmado con el segundo factor). */
export function Cuenta() {
  const { sesion } = useSesion()
  const [cuenta, setCuenta] = useState<DatosCuenta | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [monto, setMonto] = useState('')
  const [codigo, setCodigo] = useState('')
  const [aviso, setAviso] = useState<{ tipo: 'exito' | 'error'; texto: string } | null>(null)
  const [enviando, setEnviando] = useState(false)
  const [copiado, setCopiado] = useState(false)

  const cargar = useCallback(() => {
    if (!sesion) return
    cuentas.consultar(sesion.numeroCuenta).then((r) => setCuenta(r.datos ?? null)).catch((err) => setError(mensajeDeError(err)))
  }, [sesion])

  useEffect(cargar, [cargar])

  async function copiar() {
    try {
      await navigator.clipboard.writeText(cuenta?.numeroCuenta ?? '')
      setCopiado(true)
      setTimeout(() => setCopiado(false), 1800)
    } catch {
      setCopiado(false)
    }
  }

  async function depositar(e: SubmitEvent<HTMLFormElement>) {
    e.preventDefault()
    setAviso(null)
    if (!/^\d{1,10}(\.\d{1,2})?$/.test(monto) || Number(monto) <= 0) {
      setAviso({ tipo: 'error', texto: 'Escriba un monto válido mayor a cero (máximo 2 decimales).' })
      return
    }
    setEnviando(true)
    try {
      const r = await cuentas.depositar(sesion!.numeroCuenta, Number(monto), codigo)
      setAviso({ tipo: 'exito', texto: `${r.mensaje} Nuevo saldo: ${dinero(r.datos?.saldo)}` })
      setMonto('')
      setCodigo('')
      cargar()
    } catch (err) {
      setAviso({ tipo: 'error', texto: mensajeDeError(err) })
      setCodigo('')
    } finally {
      setEnviando(false)
    }
  }

  if (error) return <Aviso tipo="error">{error}</Aviso>
  if (!cuenta) return <Cargando texto="Cargando su cuenta…" />

  return (
    <section className="cuenta">
      <h1>Mi cuenta</h1>
      <div className="tarjeta tarjeta-bancaria">
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <span className="etiqueta">Hola, {sesion?.nombreUsuario}</span>
          <span className="insignia sin-punto" style={{ background: 'rgb(255 255 255 / 18%)', color: '#fff' }}>{cuenta.estatus}</span>
        </div>
        <div>
          <span className="etiqueta">Saldo disponible</span>
          <div className="importe grande">{dinero(cuenta.saldo)}</div>
        </div>
        <div>
          <span className="etiqueta">Número de cuenta</span>
          <div className="numero">
            {agruparCuenta(cuenta.numeroCuenta)}
            <button type="button" className="boton-icono" onClick={() => void copiar()} aria-label="Copiar número de cuenta" title="Copiar">
              <Icono nombre={copiado ? 'check' : 'copiar'} tamanio={16} />
            </button>
          </div>
        </div>
      </div>

      <form className="tarjeta formulario" onSubmit={depositar}>
        <h2>Depositar a mi cuenta</h2>
        <label className="campo">
          <span>Monto (MXN)</span>
          <input inputMode="decimal" value={monto} placeholder="0.00"
            onChange={(e) => setMonto(e.target.value.replace(/[^\d.]/g, '').slice(0, 13))} required />
        </label>
        <fieldset className="montos-rapidos">
          <legend className="oculto-visual">Montos rápidos</legend>
          {MONTOS_RAPIDOS.map((m) => (
            <button key={m} type="button" className={`chip ${Number(monto) === m ? 'activo' : ''}`} onClick={() => setMonto(String(m))}>
              {dinero(m)}
            </button>
          ))}
        </fieldset>
        <CampoCodigo etiqueta="Código de autorización (app o correo)" valor={codigo} alCambiar={setCodigo} disabled={enviando} />
        <CodigoPorCorreo disabled={enviando} />
        {aviso && <Aviso tipo={aviso.tipo}>{aviso.texto}</Aviso>}
        <button type="submit" className="boton" disabled={enviando || codigo.length !== 6 || !monto}>
          <Icono nombre="mas" tamanio={18} />
          {enviando ? 'Depositando…' : 'Depositar'}
        </button>
      </form>
    </section>
  )
}
