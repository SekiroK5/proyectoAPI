import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { Link, useParams } from 'react-router-dom'
import { ApiError, mensajeDeError } from '../api/cliente'
import { catalogo, pagos } from '../api/servicios'
import type { Producto, Transaccion, Verificacion } from '../api/tipos'
import { Aviso, CampoCodigo, CodigoPorCorreo, Cargando, Logo, Pasos, agruparCuenta, dinero } from '../components/Comunes'
import { Icono } from '../components/Iconos'
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
  const [errorReferencia, setErrorReferencia] = useState<string | null>(null)
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
  if (!producto || !sesion) return <Cargando texto="Cargando producto…" />

  const esServicio = producto.tipoOperacion === 'PAGO_SERVICIO'
  // Tarjetas de regalo y PINes (categorías 10 y 11): GestoPago no requiere teléfono
  const sinTelefono = producto.idCatTipoServicio === 10 || producto.idCatTipoServicio === 11
  const telefonoReferencia = producto.tipoReferencia === 'a'

  async function verificar(e: FormEvent) {
    e.preventDefault()
    setErrorReferencia(null)
    setVerificacion(null)
    if (telefonoReferencia && !/^\d{10}$/.test(referencia)) {
      setErrorReferencia('La referencia de este servicio es un número de teléfono de 10 dígitos.')
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
      setErrorReferencia(mensajeDeError(err))
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

  const indicePaso = paso === 'datos' ? 0 : paso === 'resumen' ? 1 : 2

  return (
    <section className="pago">
      <Link to="/catalogo" className="volver"><Icono nombre="flechaIzq" tamanio={18} /> Catálogo</Link>
      <div className="tarjeta encabezado-producto">
        <Logo url={producto.logoUrl} nombre={producto.servicio} tamanio={64} />
        <div>
          <h1>{producto.servicio}</h1>
          <p>{producto.producto}</p>
          <p className="tenue chico">{producto.categoria} · {esServicio ? 'Pago de servicio' : 'Recarga'}</p>
        </div>
      </div>
      <Pasos pasos={['Datos', 'Confirmación', 'Resultado']} actual={indicePaso} />

      {paso === 'datos' && (
        <div className="tarjeta">
          {esServicio && (
            <form onSubmit={verificar} className="formulario">
              <h2>1. Referencia del recibo</h2>
              {producto.imagenReferenciaUrl && (
                <details className="ayuda">
                  <summary>¿Dónde encuentro la referencia?</summary>
                  <img src={producto.imagenReferenciaUrl} alt="Ubicación de la referencia en el recibo"
                    onError={(e) => ((e.target as HTMLImageElement).parentElement!.style.display = 'none')} />
                </details>
              )}
              <label className="campo">
                <span>{telefonoReferencia ? 'Número de teléfono (10 dígitos)' : 'Referencia'}</span>
                <input
                  value={referencia}
                  onChange={(e) => {
                    setReferencia(e.target.value.replace(telefonoReferencia ? /\D/g : /[^A-Za-z0-9-]/g, '').slice(0, telefonoReferencia ? 10 : 100))
                    setVerificacion(null)
                    setErrorReferencia(null)
                  }}
                  required
                  maxLength={telefonoReferencia ? 10 : 100}
                  inputMode={telefonoReferencia ? 'numeric' : 'text'}
                  autoFocus
                />
                <span className="ayuda-campo">Antes de pagar verificamos que la referencia sea válida.</span>
              </label>
              <button className="boton secundario" disabled={trabajando || !referencia}>
                <Icono nombre="escudo" tamanio={18} />
                {trabajando ? 'Verificando…' : 'Verificar referencia'}
              </button>
              {errorReferencia && <Aviso tipo="error">{errorReferencia}</Aviso>}
              {verificacion && (
                <Aviso tipo={verificacion.resultado === 'VALIDA' ? 'exito' : 'info'}>
                  <strong>{verificacion.resultado === 'VALIDA' ? 'Referencia válida.' : 'Formato válido.'}</strong>{' '}
                  {verificacion.mensaje}
                  {verificacion.saldoPendiente != null && <> · Adeudo informado: {dinero(verificacion.saldoPendiente)}</>}
                </Aviso>
              )}
            </form>
          )}

          <form onSubmit={aResumen} className="formulario"
            style={esServicio ? { marginTop: '1.5rem', paddingTop: '1.5rem', borderTop: '1px solid var(--borde)' } : undefined}>
            <h2>{esServicio ? '2. Importe a pagar' : 'Datos de la recarga'}</h2>
            {!esServicio && !sinTelefono && (
              <label className="campo">
                <span>Número de teléfono</span>
                <span className="entrada">
                  <Icono nombre="telefono" tamanio={18} />
                  <input inputMode="numeric" maxLength={10} value={telefono} autoComplete="tel-national" autoFocus
                    onChange={(e) => setTelefono(e.target.value.replace(/\D/g, '').slice(0, 10))} required />
                </span>
                <span className="ayuda-campo">10 dígitos, sin espacios.</span>
              </label>
            )}
            {!esServicio && sinTelefono && <Aviso tipo="info">Recibirá un PIN o folio de canje al terminar el pago.</Aviso>}
            <label className="campo">
              <span>Importe (MXN)</span>
              <input inputMode="decimal" value={monto} onChange={(e) => setMonto(e.target.value.replace(/[^\d.]/g, '').slice(0, 13))}
                disabled={!esServicio} required />
              {!esServicio && <span className="ayuda-campo">El importe de la recarga es el precio del producto.</span>}
            </label>
            {error && <Aviso tipo="error">{error}</Aviso>}
            <button className="boton" disabled={esServicio && !verificacion}>
              Continuar <Icono nombre="flechaDer" tamanio={18} />
            </button>
            {esServicio && !verificacion && <p className="tenue chico">Verifique la referencia para continuar.</p>}
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
            <dt>Cuenta de cargo</dt><dd className="mono">{agruparCuenta(sesion.numeroCuenta)}</dd>
          </dl>
          <div className="resumen-total">
            <span className="tenue">Total a cobrar</span>
            <strong className="importe grande">{dinero(monto)}</strong>
          </div>
          <CampoCodigo etiqueta="Código de autorización (app o correo)" valor={codigo} alCambiar={setCodigo} disabled={trabajando} autoFocus />
          <CodigoPorCorreo disabled={trabajando} />
          {error && <Aviso tipo="error">{error}</Aviso>}
          <div className="acciones">
            <button type="button" className="boton secundario" disabled={trabajando} onClick={() => { setError(null); setPaso('datos') }}>Modificar</button>
            <button className="boton" style={{ flex: 1 }} disabled={trabajando || codigo.length !== 6}>
              <Icono nombre="candado" tamanio={18} />
              {trabajando ? 'Procesando pago…' : 'Pagar ' + dinero(monto)}
            </button>
          </div>
          <p className="tenue chico">Al confirmar se aparta el importe de su cuenta. Si GestoPago rechaza el pago, se le devuelve.</p>
        </form>
      )}

      {paso === 'resultado' && resultado && (
        <div className="tarjeta">
          <div className="resultado">
            <span className={'resultado-icono ' + (resultado.tipo === 'exito' ? 'exito' : resultado.tipo === 'rechazo' ? 'error' : 'pendiente')}>
              <Icono nombre={resultado.tipo === 'exito' ? 'check' : resultado.tipo === 'rechazo' ? 'x' : 'reloj'} tamanio={36} />
            </span>
            <h2>{resultado.tipo === 'exito' ? '¡Pago exitoso!' : resultado.tipo === 'rechazo' ? 'No se realizó el pago' : 'Pago pendiente de confirmar'}</h2>
            <p className="tenue">
              {resultado.mensaje}
              {resultado.tipo === 'pendiente' && ' Consulte el estatus en su historial.'}
            </p>
          </div>
          {'tx' in resultado && <DetalleTransaccion tx={resultado.tx} leyenda={producto.legend} />}
          <div className="acciones" style={{ marginTop: '1.25rem' }}>
            {resultado.tipo === 'rechazo' ? (
              <>
                {/saldo insuficiente/i.test(resultado.mensaje) && <Link className="boton" to="/cuenta">Depositar a mi cuenta</Link>}
                <button type="button" className="boton secundario" onClick={() => { setResultado(null); setCodigo(''); setPaso('datos') }}>
                  Intentar de nuevo
                </button>
                <Link className="boton secundario" to="/catalogo">Volver al catálogo</Link>
              </>
            ) : (
              <>
                <Link className="boton" to="/historial">Ver historial</Link>
                <Link className="boton secundario" to="/catalogo">Volver al catálogo</Link>
              </>
            )}
          </div>
        </div>
      )}
    </section>
  )
}
