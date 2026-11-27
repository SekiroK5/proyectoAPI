import { useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { mensajeDeError } from '../api/cliente'
import { catalogo } from '../api/servicios'
import type { Producto } from '../api/tipos'
import { Aviso, Logo, Vacio, dinero } from '../components/Comunes'
import { Icono } from '../components/Iconos'

type Filtro = 'TODOS' | 'RECARGA' | 'PAGO_SERVICIO'

const POR_PAGINA = 48
const TIPOS: { valor: Filtro; texto: string }[] = [
  { valor: 'TODOS', texto: 'Todos' },
  { valor: 'RECARGA', texto: 'Recargas y PINes' },
  { valor: 'PAGO_SERVICIO', texto: 'Pago de servicios' },
]

/** Quita acentos para que "telefonica" encuentre "Telefónica". */
const normalizar = (t: string) => t.normalize('NFD').replace(/\p{Diacritic}/gu, '').toLowerCase()

/** Catálogo de GestoPago guardado por la API: logotipo, categoría, búsqueda y filtros. */
export function Catalogo() {
  const [productos, setProductos] = useState<Producto[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [busqueda, setBusqueda] = useState('')
  const [tipo, setTipo] = useState<Filtro>('TODOS')
  const [categoria, setCategoria] = useState('')
  const [limite, setLimite] = useState(POR_PAGINA)

  useEffect(() => {
    catalogo
      .listar()
      .then((lista) => setProductos(lista.filter((p) => p.tipoOperacion !== 'NO_SOPORTADA')))
      .catch((err) => setError(mensajeDeError(err)))
  }, [])

  // Al cambiar un filtro se regresa a la primera "página"
  useEffect(() => setLimite(POR_PAGINA), [busqueda, tipo, categoria])

  const indice = useMemo(
    () => (productos ?? []).map((p) => ({ p, texto: normalizar(`${p.servicio} ${p.producto} ${p.categoria}`) })),
    [productos],
  )

  const porTipo = useMemo(() => indice.filter(({ p }) => tipo === 'TODOS' || p.tipoOperacion === tipo), [indice, tipo])

  const categorias = useMemo(() => {
    const conteo = new Map<string, number>()
    porTipo.forEach(({ p }) => conteo.set(p.categoria ?? 'Otros', (conteo.get(p.categoria ?? 'Otros') ?? 0) + 1))
    return [...conteo.entries()].sort((a, b) => b[1] - a[1])
  }, [porTipo])

  const visibles = useMemo(() => {
    const texto = normalizar(busqueda.trim())
    return porTipo
      .filter(({ p, texto: t }) => (!categoria || (p.categoria ?? 'Otros') === categoria) && (!texto || t.includes(texto)))
      .map(({ p }) => p)
  }, [porTipo, busqueda, categoria])

  if (error) return <Aviso tipo="error">{error}</Aviso>

  return (
    <section>
      <div className="titulo-seccion">
        <div>
          <h1>Catálogo de servicios</h1>
          <p className="tenue">
            {productos ? `${visibles.length.toLocaleString('es-MX')} de ${productos.length.toLocaleString('es-MX')} productos` : 'Cargando productos…'}
          </p>
        </div>
      </div>

      <div className="filtros">
        <div className="fila-filtros">
          <label className="entrada buscador">
            <span className="oculto-visual">Buscar</span>
            <Icono nombre="buscar" tamanio={18} />
            <input
              type="search"
              placeholder="Buscar servicio, producto o categoría"
              value={busqueda}
              onChange={(e) => setBusqueda(e.target.value)}
              maxLength={80}
            />
          </label>
          <div className="chips" role="group" aria-label="Tipo de operación">
            {TIPOS.map((t) => (
              <button key={t.valor} type="button" className={`chip ${tipo === t.valor ? 'activo' : ''}`}
                aria-pressed={tipo === t.valor} onClick={() => { setTipo(t.valor); setCategoria('') }}>
                {t.texto}
              </button>
            ))}
          </div>
        </div>
        {categorias.length > 1 && (
          <div className="chips" role="group" aria-label="Categoría">
            <button type="button" className={`chip ${!categoria ? 'activo' : ''}`} aria-pressed={!categoria} onClick={() => setCategoria('')}>
              Todas
            </button>
            {categorias.map(([c, n]) => (
              <button key={c} type="button" className={`chip ${categoria === c ? 'activo' : ''}`} aria-pressed={categoria === c} onClick={() => setCategoria(c)}>
                {c} <span className="conteo">{n}</span>
              </button>
            ))}
          </div>
        )}
      </div>

      {!productos && (
        <div className="rejilla-productos" aria-hidden="true">
          {Array.from({ length: 12 }, (_, i) => (
            <div key={i} className="tarjeta producto">
              <div className="esqueleto" style={{ width: 56, height: 56 }} />
              <div className="esqueleto" style={{ height: 14, width: '70%' }} />
              <div className="esqueleto" style={{ height: 12, width: '90%' }} />
              <div className="esqueleto" style={{ height: 12, width: '40%' }} />
            </div>
          ))}
        </div>
      )}

      {productos && productos.length === 0 && (
        <Vacio icono="tienda" titulo="Catálogo sin productos">
          La API todavía no tiene el catálogo de GestoPago. Se actualiza una vez al día.
        </Vacio>
      )}

      {productos && productos.length > 0 && visibles.length === 0 && (
        <Vacio icono="buscar" titulo="Sin resultados"
          accion={<button type="button" className="boton secundario" onClick={() => { setBusqueda(''); setCategoria(''); setTipo('TODOS') }}>Limpiar filtros</button>}>
          No encontramos productos con esos filtros.
        </Vacio>
      )}

      <div className="rejilla-productos">
        {visibles.slice(0, limite).map((p) => (
          <Link key={`${p.idServicio}-${p.idProducto}`} className="tarjeta producto" to={`/pagar/${p.idServicio}/${p.idProducto}`}>
            <Logo url={p.logoUrl} nombre={p.servicio} />
            <div className="producto-info">
              <strong title={p.servicio}>{p.servicio}</strong>
              <span className="nombre-producto">{p.producto}</span>
              <span className="tenue chico">{p.categoria}</span>
            </div>
            <div className="producto-pie">
              <span className={`insignia sin-punto ${p.tipoOperacion === 'RECARGA' ? 'info' : 'neutra'}`}>
                {p.tipoOperacion === 'RECARGA' ? 'Recarga' : p.verificableEnGestoPago ? 'Servicio verificable' : 'Servicio'}
              </span>
              {p.tipoOperacion === 'RECARGA' ? <span className="precio">{dinero(p.precio)}</span> : <span className="tenue chico">Monto libre</span>}
            </div>
          </Link>
        ))}
      </div>

      {visibles.length > limite && (
        <div className="mas-resultados">
          <button type="button" className="boton secundario" onClick={() => setLimite((l) => l + POR_PAGINA)}>
            Mostrar más productos
          </button>
          <span className="tenue chico">Mostrando {limite} de {visibles.length}</span>
        </div>
      )}
    </section>
  )
}
