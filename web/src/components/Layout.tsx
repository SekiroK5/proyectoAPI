import { Link, NavLink, Navigate, Outlet, useLocation } from 'react-router-dom'
import { useSesion } from '../sesion/Sesion'
import { Icono } from './Iconos'
import type { NombreIcono } from './Iconos'

const SECCIONES: { ruta: string; texto: string; icono: NombreIcono }[] = [
  { ruta: '/catalogo', texto: 'Catálogo', icono: 'tienda' },
  { ruta: '/historial', texto: 'Historial', icono: 'recibo' },
  { ruta: '/cuenta', texto: 'Mi cuenta', icono: 'cartera' },
]

/** Estructura de las pantallas con sesión: encabezado, contenido y barra inferior en teléfonos. */
export function Layout() {
  const { sesion, cerrar } = useSesion()
  const ubicacion = useLocation()

  if (!sesion) {
    return <Navigate to="/login" replace state={{ desde: ubicacion.pathname }} />
  }

  const inicial = sesion.nombreUsuario.slice(0, 1).toUpperCase()

  return (
    <div className="app">
      <header className="encabezado">
        <Link to="/catalogo" className="marca" aria-label="GestoPago, ir al catálogo">
          <span className="marca-icono" aria-hidden="true">G</span>
          <span>GestoPago</span>
        </Link>
        <nav className="navegacion" aria-label="Principal">
          {SECCIONES.map((s) => (
            <NavLink key={s.ruta} to={s.ruta}>
              <Icono nombre={s.icono} tamanio={18} />
              {s.texto}
            </NavLink>
          ))}
        </nav>
        <div className="usuario">
          <span className="avatar" aria-hidden="true">{inicial}</span>
          <span className="usuario-nombre">{sesion.nombreUsuario}</span>
          <button type="button" className="boton-icono" onClick={() => void cerrar()} aria-label="Cerrar sesión" title="Cerrar sesión">
            <Icono nombre="salir" />
          </button>
        </div>
      </header>
      <main className="contenido">
        <Outlet />
      </main>
      <nav className="barra-inferior" aria-label="Principal (móvil)">
        {SECCIONES.map((s) => (
          <NavLink key={s.ruta} to={s.ruta}>
            <Icono nombre={s.icono} tamanio={22} />
            {s.texto}
          </NavLink>
        ))}
      </nav>
    </div>
  )
}
