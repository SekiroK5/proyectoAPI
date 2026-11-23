import { useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { mensajeDeError } from '../api/cliente'
import { catalogo } from '../api/servicios'
import type { Producto } from '../api/tipos'
import { Aviso, Cargando, Logo, dinero } from '../components/Comunes'

type Filtro = 'TODOS' | 'RECARGA' | 'PAGO_SERVICIO'

/** Catálogo de GestoPago (obtenido de la API) con logotipo, categoría y búsqueda. */
export function Catalogo() {
  const [productos, setProductos] = useState<Producto[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [busqueda, setBusqueda] = useState('')
  const [tipo, setTipo] = useState<Filtro>('TODOS')
  const [categoria, setCategoria] = useState('')

  useEffect(() => {
    catalogo
      .listar()
      .then((lista) => setProductos(lista.filter((p) => p.tipoOperacion !== 'NO_SOPORTADA')))
      .catch((err) => setError(mensajeDeError(err)))
  }, [])

  const categorias = useMemo(
    () => [...new Set((productos ?? []).map((p) => p.categoria ?? 'Otros'))].sort((a, b) => a.localeCompare(b, 'es')),
    [productos],
  )

  const visibles = useMemo(() => {
    const texto = busqueda.trim().toLowerCase()
    return (productos ?? []).filter(
      (p) =>
        (tipo === 'TODOS' || p.tipoOperacion === tipo) &&
        (!categoria || p.categoria === categoria) &&
        (!texto || `${p.servicio} ${p.producto} ${p.categoria}`.toLowerCase().includes(texto)),
    )
  }, [productos, busqueda, tipo, categoria])

  if (error) return <Aviso tipo="error">{error}</Aviso>
  if (!productos) return <Cargando texto="Cargando catálogo…" />

  return (
    <section>
      <div className="titulo-seccion">
        <h1>Catálogo de servicios</h1>
        <p className="tenue">{visibles.length} de {productos.length} productos</p>
      </div>
      <div className="filtros">
        <input
          className="buscador"
          type="search"
          placeholder="Buscar servicio, producto o categoría"
          value={busqueda}
          onChange={(e) => setBusqueda(e.target.value)}
          aria-label="Buscar"
        />
        <div className="chips" role="group" aria-label="Tipo de operación">
          {(['TODOS', 'RECARGA', 'PAGO_SERVICIO'] as Filtro[]).map((t) => (
            <button key={t} type="button" className={`chip ${tipo === t ? 'activo' : ''}`} onClick={() => setTipo(t)}>
              {t === 'TODOS' ? 'Todos' : t === 'RECARGA' ? 'Recargas' : 'Pago de servicios'}
            </button>
          ))}
        </div>
        <select value={categoria} onChange={(e) => setCategoria(e.target.value)} aria-label="Categoría">
          <option value="">Todas las categorías</option>
          {categorias.map((c) => (
            <option key={c} value={c}>{c}</option>
          ))}
        </select>
      </div>

      {productos.length === 0 && (
        <Aviso tipo="info">
          El catálogo aún no se ha descargado de GestoPago. La API lo actualiza una vez al día; intente más tarde.
        </Aviso>
      )}

      <div className="rejilla-productos">
        {visibles.map((p) => (
          <Link key={`${p.idServicio}-${p.idProducto}`} className="tarjeta producto" to={`/pagar/${p.idServicio}/${p.idProducto}`}>
            <Logo url={p.logoUrl} nombre={p.servicio} />
            <div className="producto-info">
              <strong>{p.servicio}</strong>
              <span>{p.producto}</span>
              <span className="tenue chico">{p.categoria} · #{p.idServicio}-{p.idProducto}</span>
            </div>
            <div className="producto-pie">
              <span className={`insignia ${p.tipoOperacion === 'RECARGA' ? 'info' : 'neutra'}`}>
                {p.tipoOperacion === 'RECARGA' ? 'Recarga' : 'Servicio'}
              </span>
              {p.tipoOperacion === 'RECARGA' && <strong>{dinero(p.precio)}</strong>}
            </div>
          </Link>
        ))}
      </div>
    </section>
  )
}
