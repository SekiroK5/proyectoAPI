import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { mensajeDeError } from '../api/cliente'
import { pagos } from '../api/servicios'
import type { EstatusTransaccion, Pagina, Transaccion } from '../api/tipos'
import { Aviso, Cargando, Estatus, Logo, Vacio, dinero, fecha } from '../components/Comunes'
import { DetalleTransaccion } from '../components/DetalleTransaccion'
import { Icono } from '../components/Iconos'

const TAMANIO = 10
const FILTROS: { valor: EstatusTransaccion | ''; texto: string }[] = [
  { valor: '', texto: 'Todos' },
  { valor: 'APLICADA', texto: 'Exitosos' },
  { valor: 'RECHAZADA', texto: 'Rechazados' },
  { valor: 'PENDIENTE_CONFIRMACION', texto: 'Pendientes' },
  { valor: 'REVISION_MANUAL', texto: 'En revisión' },
]

/** Historial de pagos del usuario de la sesión (la API solo devuelve sus operaciones). */
export function Historial() {
  const [pagina, setPagina] = useState(0)
  const [estatus, setEstatus] = useState<EstatusTransaccion | ''>('')
  const [datos, setDatos] = useState<Pagina<Transaccion> | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let vigente = true
    setDatos(null)
    setError(null)
    pagos
      .historial(pagina, TAMANIO, estatus || undefined)
      .then((r) => vigente && setDatos(r.datos ?? null))
      .catch((err) => vigente && setError(mensajeDeError(err)))
    return () => { vigente = false }
  }, [pagina, estatus])

  return (
    <section>
      <div className="titulo-seccion">
        <div>
          <h1>Historial de pagos</h1>
          <p className="tenue">{datos ? `${datos.totalElementos} operación(es)` : 'Sus recargas y pagos de servicios.'}</p>
        </div>
      </div>
      <div className="chips" role="group" aria-label="Filtrar por estatus" style={{ marginBottom: '1.25rem' }}>
        {FILTROS.map((f) => (
          <button key={f.valor || 'todos'} type="button" className={`chip ${estatus === f.valor ? 'activo' : ''}`}
            aria-pressed={estatus === f.valor} onClick={() => { setPagina(0); setEstatus(f.valor) }}>
            {f.texto}
          </button>
        ))}
      </div>
      {error && <Aviso tipo="error">{error}</Aviso>}
      {!error && !datos && <Cargando texto="Cargando historial…" />}
      {datos && datos.contenido.length === 0 && (
        <Vacio icono="recibo" titulo={estatus ? 'Sin pagos con ese estatus' : 'Aún no tiene pagos'}
          accion={<Link className="boton" to="/catalogo">Ir al catálogo</Link>}>
          {estatus ? 'Pruebe con otro filtro.' : 'Cuando haga una recarga o pague un servicio aparecerá aquí.'}
        </Vacio>
      )}
      {datos && datos.contenido.length > 0 && (
        <>
          <ul className="lista-historial">
            {datos.contenido.map((tx) => (
              <li key={tx.id}>
                <Link to={`/historial/${tx.id}`} className="tarjeta fila-historial">
                  <Logo url={tx.logoUrl} nombre={tx.servicio} tamanio={44} />
                  <div className="fila-principal">
                    <strong>{tx.servicio ?? `Servicio ${tx.idServicio}`}</strong>
                    <span className="tenue chico">{fecha(tx.fecha)}</span>
                    <span className="tenue chico">
                      {tx.referencia ? `Ref. ${tx.referencia}` : tx.telefono && !tx.telefono.includes('1111111111') ? `Tel. ${tx.telefono}` : tx.producto}
                    </span>
                  </div>
                  <div className="fila-lateral">
                    <strong>{dinero(tx.monto)}</strong>
                    <Estatus estatus={tx.estatus} />
                  </div>
                </Link>
              </li>
            ))}
          </ul>
          {datos.totalPaginas > 1 && (
            <div className="paginacion">
              <button type="button" className="boton secundario chico" disabled={pagina === 0} onClick={() => setPagina(pagina - 1)}>
                <Icono nombre="flechaIzq" tamanio={16} /> Anterior
              </button>
              <span className="tenue chico">Página {datos.pagina + 1} de {datos.totalPaginas}</span>
              <button type="button" className="boton secundario chico" disabled={pagina + 1 >= datos.totalPaginas} onClick={() => setPagina(pagina + 1)}>
                Siguiente <Icono nombre="flechaDer" tamanio={16} />
              </button>
            </div>
          )}
        </>
      )}
    </section>
  )
}

/** Detalle de una transacción propia. */
export function DetalleHistorial() {
  const { id } = useParams()
  const [tx, setTx] = useState<Transaccion | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    const numero = Number(id)
    if (!Number.isInteger(numero) || numero <= 0) {
      setError('Transacción no válida.')
      return
    }
    pagos.detalle(numero).then((r) => setTx(r.datos ?? null)).catch((err) => setError(mensajeDeError(err)))
  }, [id])

  return (
    <section style={{ maxWidth: 680, margin: '0 auto' }}>
      <Link to="/historial" className="volver"><Icono nombre="flechaIzq" tamanio={18} /> Historial</Link>
      {error && <Aviso tipo="error">{error}</Aviso>}
      {!error && !tx && <Cargando />}
      {tx && (
        <div className="tarjeta">
          <DetalleTransaccion tx={tx} />
        </div>
      )}
    </section>
  )
}
