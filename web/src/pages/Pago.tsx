import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { Link, useParams } from 'react-router-dom'
import { ApiError, mensajeDeError } from '../api/cliente'
import { catalogo, pagos } from '../api/servicios'
import type { Producto, Transaccion, Verificacion } from '../api/tipos'
import { Aviso, CampoCodigo, Cargando, Logo, dinero } from '../components/Comunes'
import { DetalleTransaccion } from '../components/DetalleTransaccion'
import { useSesion } from '../sesion/Sesion'

type Paso = 'datos' | 'resumen' | 'resultado'

/** Resultado final mostrado al usuario: nunca se muestra éxito sin la confirmación de GestoPago. */
type Resultado =
  | { tipo: 'exito'; tx: Transaccion; mensaje: string }
  | { tipo: 'pendiente'; tx: Transaccion; mensaje: string }
  | { tipo: 'rechazo'; mensaje: string }

/**
 * Flujo de pago:
 * - Recarga: teléfono → resumen → confirmación con 2FA → resultado.
 * - Servicio: referencia → verificación (API) → importe → resumen → confirmación con 2FA → resultado.
 * No se puede continuar con un servicio cuya referencia no se haya verificado.
 */
export function Pago() {
  const { idServicio, idProducto } = useParams()
  const { sesion } = useSesion()
  const [producto, setProducto] = useState<Producto | null>(null)
  const [errorCarga, setErrorCarga] = useState<string | null>(null)
  const [paso, setPaso] = useState<Paso>('datos')
  const [telefono, setTelefono] = useState('')
  const [referencia, setReferencia] = useState('')
  const [verificacion, setVerificacion] = useState<Verificacion | null>(null)
  const [monto, setMonto] = useState('')
  const [codigo, setCodigo] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [trabajando, setTrabajando] = useState(false)
  const [resultado, setResultado] = useState<Resultado | null>(null)

  useEffect(() => {
    catalogo
      .listar()
      .then((lista) => {
        const p = lista.find((x) => String(x.idServicio) === idServicio && String(x.idProducto) === idProducto)
        if (!p) {
          setErrorCarga('El producto ya no está en el catálogo.')
          return
        }
        setProducto(p)
        if (p.tipoOperacion === 'RECARGA' && p.precio != null) setMonto(String(p.precio))
      })
      .catch((err) => setErrorCarga(mensajeDeError(err)))
  }, [idServicio, idProducto])

  if (errorCarga) return <Aviso tipo="error">{errorCarga} <Link to="/catalogo">Volver al catálogo</Link></Aviso>
  if (!producto || !sesion) return <Cargando />

  const esServicio = producto.tipoOperacion === 'PAGO_SERVICIO'
  // Tarjetas de regalo y PINes (categorías 10 y 11): GestoPago no requiere teléfono
  const sinTelefono = producto.idCatTipoServicio === 10 || producto.idCatTipoServicio === 11
  const telefonoReferencia = producto.tipoReferencia === 'a'

  async function verificar(e: FormEvent) {
    e.preventDefault()
    setError(null)
    setVerificacion(null)
    if (telefonoReferencia && !/^\d{10}$/.test(referencia)) {
      setError('La referencia de este servicio es un número de teléfono de 10 dígitos.')
      return
    }
    setTrabajando(true)
    try {
      const r = await pagos.verificarReferencia(producto!.idServicio, producto!.idProducto, referencia.trim())
      const v = r.datos!
      setVerificacion(v)
      const pendiente = Number(v.saldoPendiente)
      if (Number.isFinite(pendiente) && pendiente > 0) setMonto(pendiente.toFixed(2))
    } catch (err) {
      setError(mensajeDeError(err))
    } finally {
      setTrabajando(false)
    }
  }

  function aResumen(e: FormEvent) {
    e.preventDefault()
    setError(null)
    if (!esServicio && !sinTelefono && !/^\d{10}$/.test(telefono)) {
      setError('Escriba un número de teléfono de 10 dígitos.')
      return
    }
    if (esServicio && !verificacion) {
      setError('Primero verifique la referencia.')
      return
    }
    const importe = Number(monto)
    if (!(importe > 0) || !/^\d{1,10}(\.\d{1,2})?$/.test(monto)) {
      setError('Escriba un importe válido (máximo 2 decimales).')
      return
    }
    setPaso('resumen')
  }

  async function confirmar(e: FormEvent) {
    e.preventDefault()
    setError(null)
    setTrabajando(true)
    try {
      const r = await pagos.pagar({
        numeroCuenta: sesion!.numeroCuenta,
        idServicio: producto!.idServicio,
        idProducto: producto!.idProducto,
        monto: Number(monto),
        telefono: esServicio || sinTelefono ? undefined : telefono,
        referencia: esServicio ? verificacion!.referencia : undefined,
        folioVerificacion: esServicio ? verificacion!.folio : undefined,
        codigoA2F: codigo,
      })
      const tx = r.datos!
      setResultado(tx.estatus === 'APLICADA'
        ? { tipo: 'exito', tx, mensaje: r.mensaje }
        : { tipo: 'pendiente', tx, mensaje: r.mensaje })
      setPaso('resultado')
    } catch (err) {
      // 401/409/423 del 2FA: se queda en la confirmación para corregir el código
      if (err instanceof ApiError && [401, 409, 423].includes(err.status) && /2FA|código|bloquead|intentos/i.test(err.message)) {
        setError(err.message)
        setCodigo('')
      } else {
        setResultado({ tipo: 'rechazo', mensaje: mensajeDeError(err) })
        setPaso('resultado')
      }
    } finally {
      setTrabajando(false)
    }
  }

  return (
    <section className="pago">
      <Link to="/catalogo" className="volver">← Catálogo</Link>
      <div className="tarjeta encabezado-producto">
        <Logo url={producto.logoUrl} nombre={producto.servicio} tamanio={64} />
        <div>
          <h1>{producto.servicio}</h1>
          <p>{producto.producto}</p>
          <p className="tenue chico">{producto.categoria} · {esServicio ? 'Pago de servicio' : 'Recarga'}</p>
        </div>
      </div>

      {paso === 'datos' && (
        <div className="tarjeta">
          {esServicio && (
            <form onSubmit={verificar} className="formulario">
              <h2>1. Referencia</h2>
              {producto.imagenReferenciaUrl && (
                <details className="ayuda">
                  <summary>¿Dónde encuentro la referencia?</summary>
                  <img src={producto.imagenReferenciaUrl} alt="Ubicación de la referencia en el recibo"
                    onError={(e) => ((e.target as HTMLImageElement).parentElement!.style.display = 'none')} />
                </details>
              )}
              <label className="campo">
                <span>{telefonoReferencia ? 'Número de teléfono (10 dígitos)' : 'Referencia del recibo'}</span>
                <input
                  value={referencia}
                  onChange={(e) => { setReferencia(e.target.value.replace(/[^A-Za-z0-9-]/g, '').slice(0, 100)); setVerificacion(null) }}
                  required
                  maxLength={100}
                  inputMode={telefonoReferencia ? 'numeric' : 'text'}
                />
              </label>
              <button className="boton secundario" disabled={trabajando || !referencia}>{trabajando ? 'Verificando…' : 'Verificar referencia'}</button>
              {verificacion && (
                <Aviso tipo={verificacion.resultado === 'VALIDA' ? 'exito' : 'info'}>
                  <strong>{verificacion.resultado === 'VALIDA' ? 'Referencia válida.' : 'Formato válido.'}</strong>{' '}
                  {verificacion.mensaje}
                  {verificacion.saldoPendiente != null && <> · Adeudo informado: {dinero(verificacion.saldoPendiente)}</>}
                </Aviso>
              )}
            </form>
          )}

          <form onSubmit={aResumen} className="formulario">
            <h2>{esServicio ? '2. Importe' : 'Datos de la recarga'}</h2>
            {!esServicio && !sinTelefono && (
              <label className="campo">
                <span>Número de teléfono</span>
                <input inputMode="numeric" maxLength={10} value={telefono}
                  onChange={(e) => setTelefono(e.target.value.replace(/\D/g, '').slice(0, 10))} required />
              </label>
            )}
            <label className="campo">
              <span>Importe</span>
              <input inputMode="decimal" value={monto} onChange={(e) => setMonto(e.target.value)}
                disabled={!esServicio} required />
              {!esServicio && <small className="tenue">El importe de la recarga es el precio del producto.</small>}
            </label>
            {error && <Aviso tipo="error">{error}</Aviso>}
            <button className="boton" disabled={esServicio && !verificacion}>Continuar</button>
          </form>
        </div>
      )}

      {paso === 'resumen' && (
        <form className="tarjeta formulario" onSubmit={confirmar}>
          <h2>Confirme la operación</h2>
          <dl className="lista-datos">
            <dt>Servicio</dt><dd>{producto.servicio} — {producto.producto}</dd>
            {esServicio ? (<><dt>Referencia</dt><dd>{verificacion?.referencia}</dd></>)
              : !sinTelefono && (<><dt>Teléfono</dt><dd>{telefono}</dd></>)}
            <dt>Cuenta de cargo</dt><dd className="mono">{sesion.numeroCuenta}</dd>
            <dt>Importe a cobrar</dt><dd className="importe">{dinero(monto)}</dd>
          </dl>
          <CampoCodigo valor={codigo} alCambiar={setCodigo} disabled={trabajando} />
          {error && <Aviso tipo="error">{error}</Aviso>}
          <button className="boton" disabled={trabajando || codigo.length !== 6}>{trabajando ? 'Procesando pago…' : `Pagar ${dinero(monto)}`}</button>
          <button type="button" className="boton secundario" disabled={trabajando} onClick={() => setPaso('datos')}>Modificar</button>
        </form>
      )}

      {paso === 'resultado' && resultado && (
        <div className="tarjeta">
          {resultado.tipo === 'exito' && <Aviso tipo="exito"><strong>¡Pago exitoso!</strong> {resultado.mensaje}</Aviso>}
          {resultado.tipo === 'pendiente' && (
            <Aviso tipo="pendiente">
              <strong>Pago pendiente de confirmar.</strong> {resultado.mensaje} Consulte el estatus en su historial.
            </Aviso>
          )}
          {resultado.tipo === 'rechazo' && <Aviso tipo="error"><strong>No se realizó el pago.</strong> {resultado.mensaje}</Aviso>}
          {'tx' in resultado && <DetalleTransaccion tx={resultado.tx} />}
          <div className="acciones">
            <Link className="boton" to="/historial">Ver historial</Link>
            <Link className="boton secundario" to="/catalogo">Volver al catálogo</Link>
          </div>
        </div>
      )}
    </section>
  )
}
