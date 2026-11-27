import { useEffect, useState } from 'react'
import type { SubmitEvent } from 'react'
import { Link, useParams } from 'react-router-dom'
import { ApiError, mensajeDeError } from '../api/cliente'
import { catalogo, pagos } from '../api/servicios'
import type { Producto, Transaccion, Verificacion } from '../api/tipos'
import { Aviso, CampoCodigo, CodigoPorCorreo, Cargando, Logo, Pasos, agruparCuenta, dinero } from '../components/Comunes'
import { Icono } from '../components/Iconos'
import type { NombreIcono } from '../components/Iconos'
import { DetalleTransaccion } from '../components/DetalleTransaccion'
import { useSesion } from '../sesion/Sesion'

type Paso = 'datos' | 'resumen' | 'resultado'

const INDICE_PASO: Record<Paso, number> = { datos: 0, resumen: 1, resultado: 2 }
const IMPORTE = /^\d{1,10}(\.\d{1,2})?$/

/** Cómo se muestra cada resultado del pago. */
const VISTA_RESULTADO: Record<'exito' | 'pendiente' | 'rechazo', { clase: string; icono: NombreIcono; titulo: string }> = {
  exito: { clase: 'exito', icono: 'check', titulo: '¡Pago exitoso!' },
  pendiente: { clase: 'pendiente', icono: 'reloj', titulo: 'Pago pendiente de confirmar' },
  rechazo: { clase: 'error', icono: 'x', titulo: 'No se realizó el pago' },
}

/** Resultado final mostrado al usuario: nunca se muestra éxito sin la confirmación de GestoPago. */
type Resultado =
  | { tipo: 'exito'; tx: Transaccion; mensaje: string }
  | { tipo: 'pendiente'; tx: Transaccion; mensaje: string }
  | { tipo: 'rechazo'; mensaje: string }

/** Lo que distingue a cada producto para pedir sus datos. */
interface Tipo {
  esServicio: boolean
  /** Tarjetas de regalo y PINes (categorías 10 y 11): GestoPago no requiere teléfono. */
  sinTelefono: boolean
  /** La referencia del servicio es un teléfono de 10 dígitos. */
  telefonoReferencia: boolean
}

function tipoDe(p: Producto): Tipo {
  return {
    esServicio: p.tipoOperacion === 'PAGO_SERVICIO',
    sinTelefono: p.idCatTipoServicio === 10 || p.idCatTipoServicio === 11,
    telefonoReferencia: p.tipoReferencia === 'a',
  }
}

/** Valida los datos antes del resumen. Devuelve el mensaje de error o null si todo está bien. */
function errorDeDatos(tipo: Tipo, telefono: string, verificacion: Verificacion | null, monto: string): string | null {
  if (!tipo.esServicio && !tipo.sinTelefono && !/^\d{10}$/.test(telefono)) return 'Escriba un número de teléfono de 10 dígitos.'
  if (tipo.esServicio && !verificacion) return 'Primero verifique la referencia.'
  if (!IMPORTE.test(monto) || Number(monto) <= 0) return 'Escriba un importe válido (máximo 2 decimales).'
  return null
}

/** Errores del 2FA (401/409/423): el usuario se queda en la confirmación para corregir el código. */
function esErrorDeCodigo(err: unknown): err is ApiError {
  return err instanceof ApiError && [401, 409, 423].includes(err.status) && /2FA|código|bloquead|intentos/i.test(err.message)
}

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
  if (!producto || !sesion) return <Cargando texto="Cargando producto…" />

  const tipo = tipoDe(producto)

  function aResumen(e: SubmitEvent<HTMLFormElement>) {
    e.preventDefault()
    const invalido = errorDeDatos(tipo, telefono, verificacion, monto)
    setError(invalido)
    if (!invalido) setPaso('resumen')
  }

  async function confirmar(e: SubmitEvent<HTMLFormElement>) {
    e.preventDefault()
    setError(null)
    setTrabajando(true)
    try {
      const r = await pagos.pagar({
        numeroCuenta: sesion!.numeroCuenta,
        idServicio: producto!.idServicio,
        idProducto: producto!.idProducto,
        monto: Number(monto),
        telefono: tipo.esServicio || tipo.sinTelefono ? undefined : telefono,
        referencia: tipo.esServicio ? verificacion!.referencia : undefined,
        folioVerificacion: tipo.esServicio ? verificacion!.folio : undefined,
        codigoA2F: codigo,
      })
      const tx = r.datos!
      setResultado({ tipo: tx.estatus === 'APLICADA' ? 'exito' : 'pendiente', tx, mensaje: r.mensaje })
      setPaso('resultado')
    } catch (err) {
      if (esErrorDeCodigo(err)) {
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

  function reintentar() {
    setResultado(null)
    setCodigo('')
    setPaso('datos')
  }

  return (
    <section className="pago">
      <Link to="/catalogo" className="volver"><Icono nombre="flechaIzq" tamanio={18} /> Catálogo</Link>
      <div className="tarjeta encabezado-producto">
        <Logo url={producto.logoUrl} nombre={producto.servicio} tamanio={64} />
        <div>
          <h1>{producto.servicio}</h1>
          <p>{producto.producto}</p>
          <p className="tenue chico">{producto.categoria} · {tipo.esServicio ? 'Pago de servicio' : 'Recarga'}</p>
        </div>
      </div>
      <Pasos pasos={['Datos', 'Confirmación', 'Resultado']} actual={INDICE_PASO[paso]} />

      {paso === 'datos' && (
        <div className="tarjeta">
          {tipo.esServicio && (
            <FormReferencia producto={producto} telefonoReferencia={tipo.telefonoReferencia} verificacion={verificacion}
              referencia={referencia} setReferencia={setReferencia}
              alVerificar={(v) => {
                setVerificacion(v)
                const pendiente = Number(v?.saldoPendiente)
                if (v && Number.isFinite(pendiente) && pendiente > 0) setMonto(pendiente.toFixed(2))
              }} />
          )}
          <FormImporte tipo={tipo} telefono={telefono} setTelefono={setTelefono} monto={monto} setMonto={setMonto}
            verificado={!!verificacion} error={error} alEnviar={aResumen} />
        </div>
      )}

      {paso === 'resumen' && (
        <form className="tarjeta formulario" onSubmit={confirmar}>
          <h2>Confirme la operación</h2>
          <dl className="lista-datos">
            <dt>Servicio</dt><dd>{producto.servicio} — {producto.producto}</dd>
            {tipo.esServicio && <><dt>Referencia</dt><dd>{verificacion?.referencia}</dd></>}
            {!tipo.esServicio && !tipo.sinTelefono && <><dt>Teléfono</dt><dd>{telefono}</dd></>}
            <dt>Cuenta de cargo</dt><dd className="mono">{agruparCuenta(sesion.numeroCuenta)}</dd>
          </dl>
          <div className="resumen-total">
            <span className="tenue">Total a cobrar</span>
            <strong className="importe grande">{dinero(monto)}</strong>
          </div>
          <CampoCodigo etiqueta="Código de autorización (app o correo)" valor={codigo} alCambiar={setCodigo} disabled={trabajando} />
          <CodigoPorCorreo disabled={trabajando} />
          {error && <Aviso tipo="error">{error}</Aviso>}
          <div className="acciones">
            <button type="button" className="boton secundario" disabled={trabajando} onClick={() => { setError(null); setPaso('datos') }}>Modificar</button>
            <button type="submit" className="boton" style={{ flex: 1 }} disabled={trabajando || codigo.length !== 6}>
              <Icono nombre="candado" tamanio={18} />
              {trabajando ? 'Procesando pago…' : 'Pagar ' + dinero(monto)}
            </button>
          </div>
          <p className="tenue chico">Al confirmar se aparta el importe de su cuenta. Si GestoPago rechaza el pago, se le devuelve.</p>
        </form>
      )}

      {paso === 'resultado' && resultado && <VistaResultado resultado={resultado} leyenda={producto.legend} alReintentar={reintentar} />}
    </section>
  )
}

/** Paso 1 de un servicio: referencia del recibo y su verificación. */
function FormReferencia({ producto, telefonoReferencia, verificacion, referencia, setReferencia, alVerificar }: Readonly<{
  producto: Producto
  telefonoReferencia: boolean
  referencia: string
  setReferencia: (v: string) => void
  verificacion: Verificacion | null
  alVerificar: (v: Verificacion | null) => void
}>) {
  const [error, setError] = useState<string | null>(null)
  const [trabajando, setTrabajando] = useState(false)
  const maximo = telefonoReferencia ? 10 : 100

  async function verificar(e: SubmitEvent<HTMLFormElement>) {
    e.preventDefault()
    setError(null)
    alVerificar(null)
    if (telefonoReferencia && !/^\d{10}$/.test(referencia)) {
      setError('La referencia de este servicio es un número de teléfono de 10 dígitos.')
      return
    }
    setTrabajando(true)
    try {
      const r = await pagos.verificarReferencia(producto.idServicio, producto.idProducto, referencia.trim())
      alVerificar(r.datos!)
    } catch (err) {
      setError(mensajeDeError(err))
    } finally {
      setTrabajando(false)
    }
  }

  const valida = verificacion?.resultado === 'VALIDA'
  return (
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
            const permitidos = telefonoReferencia ? /\D/g : /[^A-Za-z0-9-]/g
            setReferencia(e.target.value.replace(permitidos, '').slice(0, maximo))
            alVerificar(null)
            setError(null)
          }}
          required
          maxLength={maximo}
          inputMode={telefonoReferencia ? 'numeric' : 'text'}
        />
        <span className="ayuda-campo">Antes de pagar verificamos que la referencia sea válida.</span>
      </label>
      <button type="submit" className="boton secundario" disabled={trabajando || !referencia}>
        <Icono nombre="escudo" tamanio={18} />
        {trabajando ? 'Verificando…' : 'Verificar referencia'}
      </button>
      {error && <Aviso tipo="error">{error}</Aviso>}
      {verificacion && (
        <Aviso tipo={valida ? 'exito' : 'info'}>
          <strong>{valida ? 'Referencia válida.' : 'Formato válido.'}</strong>{' '}
          {verificacion.mensaje}
          {verificacion.saldoPendiente != null && <> · Adeudo informado: {dinero(verificacion.saldoPendiente)}</>}
        </Aviso>
      )}
    </form>
  )
}

/** Teléfono (recargas) e importe; en servicios es el paso 2, después de verificar la referencia. */
function FormImporte({ tipo, telefono, setTelefono, monto, setMonto, verificado, error, alEnviar }: Readonly<{
  tipo: Tipo
  telefono: string
  setTelefono: (v: string) => void
  monto: string
  setMonto: (v: string) => void
  verificado: boolean
  error: string | null
  alEnviar: (e: SubmitEvent<HTMLFormElement>) => void
}>) {
  const pideTelefono = !tipo.esServicio && !tipo.sinTelefono
  return (
    <form onSubmit={alEnviar} className="formulario"
      style={tipo.esServicio ? { marginTop: '1.5rem', paddingTop: '1.5rem', borderTop: '1px solid var(--borde)' } : undefined}>
      <h2>{tipo.esServicio ? '2. Importe a pagar' : 'Datos de la recarga'}</h2>
      {pideTelefono && (
        <label className="campo">
          <span>Número de teléfono</span>
          <span className="entrada">
            <Icono nombre="telefono" tamanio={18} />
            <input inputMode="numeric" maxLength={10} value={telefono} autoComplete="tel-national"
              onChange={(e) => setTelefono(e.target.value.replace(/\D/g, '').slice(0, 10))} required />
          </span>
          <span className="ayuda-campo">10 dígitos, sin espacios.</span>
        </label>
      )}
      {!tipo.esServicio && tipo.sinTelefono && <Aviso tipo="info">Recibirá un PIN o folio de canje al terminar el pago.</Aviso>}
      <label className="campo">
        <span>Importe (MXN)</span>
        <input inputMode="decimal" value={monto} onChange={(e) => setMonto(e.target.value.replace(/[^\d.]/g, '').slice(0, 13))}
          disabled={!tipo.esServicio} required />
        {!tipo.esServicio && <span className="ayuda-campo">El importe de la recarga es el precio del producto.</span>}
      </label>
      {error && <Aviso tipo="error">{error}</Aviso>}
      <button type="submit" className="boton" disabled={tipo.esServicio && !verificado}>
        Continuar <Icono nombre="flechaDer" tamanio={18} />
      </button>
      {tipo.esServicio && !verificado && <p className="tenue chico">Verifique la referencia para continuar.</p>}
    </form>
  )
}

/** Resultado del pago: exitoso, pendiente o rechazado, con sus acciones. */
function VistaResultado({ resultado, leyenda, alReintentar }: Readonly<{ resultado: Resultado; leyenda?: string; alReintentar: () => void }>) {
  const vista = VISTA_RESULTADO[resultado.tipo]
  return (
    <div className="tarjeta">
      <div className="resultado">
        <span className={'resultado-icono ' + vista.clase}>
          <Icono nombre={vista.icono} tamanio={36} />
        </span>
        <h2>{vista.titulo}</h2>
        <p className="tenue">
          {resultado.mensaje}
          {resultado.tipo === 'pendiente' && ' Consulte el estatus en su historial.'}
        </p>
      </div>
      {'tx' in resultado && <DetalleTransaccion tx={resultado.tx} leyenda={leyenda} />}
      <div className="acciones" style={{ marginTop: '1.25rem' }}>
        {resultado.tipo === 'rechazo' && /saldo insuficiente/i.test(resultado.mensaje) && (
          <Link className="boton" to="/cuenta">Depositar a mi cuenta</Link>
        )}
        {resultado.tipo === 'rechazo' && (
          <button type="button" className="boton secundario" onClick={alReintentar}>Intentar de nuevo</button>
        )}
        {resultado.tipo !== 'rechazo' && <Link className="boton" to="/historial">Ver historial</Link>}
        <Link className="boton secundario" to="/catalogo">Volver al catálogo</Link>
      </div>
    </div>
  )
}
