import { useCallback, useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { mensajeDeError } from '../api/cliente'
import { cuentas } from '../api/servicios'
import type { Cuenta as DatosCuenta } from '../api/tipos'
import { Aviso, CampoCodigo, Cargando, dinero } from '../components/Comunes'
import { useSesion } from '../sesion/Sesion'

/** Saldo de la cuenta y depósito a la cuenta propia (con 2FA). */
export function Cuenta() {
  const { sesion } = useSesion()
  const [cuenta, setCuenta] = useState<DatosCuenta | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [monto, setMonto] = useState('')
  const [codigo, setCodigo] = useState('')
  const [aviso, setAviso] = useState<{ tipo: 'exito' | 'error'; texto: string } | null>(null)
  const [enviando, setEnviando] = useState(false)

  const cargar = useCallback(() => {
    if (!sesion) return
    cuentas.consultar(sesion.numeroCuenta).then((r) => setCuenta(r.datos ?? null)).catch((err) => setError(mensajeDeError(err)))
  }, [sesion])

  useEffect(cargar, [cargar])

  async function depositar(e: FormEvent) {
    e.preventDefault()
    setAviso(null)
    if (!/^\d{1,10}(\.\d{1,2})?$/.test(monto) || !(Number(monto) > 0)) {
      setAviso({ tipo: 'error', texto: 'Escriba un monto válido (máximo 2 decimales).' })
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
  if (!cuenta) return <Cargando />

  return (
    <section className="cuenta">
      <h1>Mi cuenta</h1>
      <div className="tarjeta saldo">
        <span className="tenue">Saldo disponible</span>
        <strong className="importe grande">{dinero(cuenta.saldo)}</strong>
        <span className="mono tenue">Cuenta {cuenta.numeroCuenta} · {cuenta.estatus}</span>
      </div>
      <form className="tarjeta formulario" onSubmit={depositar}>
        <h2>Depositar a mi cuenta</h2>
        <label className="campo">
          <span>Monto</span>
          <input inputMode="decimal" value={monto} onChange={(e) => setMonto(e.target.value)} required />
        </label>
        <CampoCodigo valor={codigo} alCambiar={setCodigo} disabled={enviando} />
        {aviso && <Aviso tipo={aviso.tipo}>{aviso.texto}</Aviso>}
        <button className="boton" disabled={enviando || codigo.length !== 6}>{enviando ? 'Depositando…' : 'Depositar'}</button>
      </form>
    </section>
  )
}
