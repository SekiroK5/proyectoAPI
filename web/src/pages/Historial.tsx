import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { mensajeDeError } from '../api/cliente'
import { pagos } from '../api/servicios'
import type { EstatusTransaccion, Pagina, Transaccion } from '../api/tipos'
import { Aviso, Cargando, Estatus, Logo, dinero, fecha } from '../components/Comunes'
import { DetalleTransaccion } from '../components/DetalleTransaccion'

const TAMANIO = 10

/** Historial de pagos del usuario de la sesión (la API solo devuelve sus operaciones). */
export function Historial() {
  const [pagina, setPagina] = useState(0)
  const [estatus, setEstatus] = useState<EstatusTransaccion | ''>('')
  const [datos, setDatos] = useState<Pagina<Transaccion> | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    setDatos(null)
    setError(null)
    pagos
      .historial(pagina, TAMANIO, estatus || undefined)
      .then((r) => setDatos(r.datos ?? null))
      .catch((err) => setError(mensajeDeError(err)))
  }, [pagina, estatus])

  return (
    <section>
      <div className="titulo-seccion">
        <h1>Historial de pagos</h1>
        <select value={estatus} onChange={(e) => { setPagina(0); setEstatus(e.target.value as EstatusTransaccion | '') }} aria-label="Estatus">
          <option value="">Todos los estatus</option>
          <option value="APLICADA">Exitosos</option>
          <option value="RECHAZADA">Rechazados</option>
          <option value="PENDIENTE_CONFIRMACION">Pendientes de confirmar</option>
          <option value="REVISION_MANUAL">En revisión</option>
        </select>
      </div>
      {error && <Aviso tipo="error">{error}</Aviso>}
      {!error && !datos && <Cargando texto="Cargando historial…" />}
      {datos && datos.contenido.length === 0 && <Aviso tipo="info">No hay pagos con ese filtro.</Aviso>}
      {datos && datos.contenido.length > 0 && (
        <>
          <ul className="lista-historial">
            {datos.contenido.map((tx) => (
              <li key={tx.id}>
                <Link to={`/historial/${tx.id}`} className="tarjeta fila-historial">
                  <Logo url={tx.logoUrl} nombre={tx.servicio} tamanio={44} />
                  <div className="fila-principal">
                    <strong>{tx.servicio ?? `Servicio ${tx.idServicio}`}</strong>
                    <span className="tenue chico">{fecha(tx.fecha)} · Ref. {tx.referencia ?? tx.telefono}</span>
                    <span className="tenue chico">Folio {tx.numAutorizacion && tx.numAutorizacion !== '-1' ? tx.numAutorizacion : '—'}</span>
                  </div>
                  <div className="fila-lateral">
                    <strong>{dinero(tx.monto)}</strong>
                    <Estatus estatus={tx.estatus} />
                  </div>
                </Link>
              </li>
            ))}
          </ul>
          <div className="paginacion">
            <button type="button" className="boton secundario chico" disabled={pagina === 0} onClick={() => setPagina(pagina - 1)}>Anterior</button>
            <span>Página {datos.pagina + 1} de {Math.max(datos.totalPaginas, 1)}</span>
            <button type="button" className="boton secundario chico" disabled={pagina + 1 >= datos.totalPaginas} onClick={() => setPagina(pagina + 1)}>Siguiente</button>
          </div>
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
    <section>
      <Link to="/historial" className="volver">← Historial</Link>
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
