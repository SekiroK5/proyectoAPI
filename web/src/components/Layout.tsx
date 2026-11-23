import { NavLink, Navigate, Outlet, useLocation } from 'react-router-dom'
import { useSesion } from '../sesion/Sesion'

/** Estructura de las pantallas con sesión: encabezado con navegación y contenido. */
export function Layout() {
  const { sesion, cerrar } = useSesion()
  const ubicacion = useLocation()

  if (!sesion) {
    return <Navigate to="/login" replace state={{ desde: ubicacion.pathname }} />
  }

  return (
    <div className="app">
      <header className="encabezado">
        <div className="marca">
          <span className="marca-icono" aria-hidden="true">₲</span>
          <span>GestoPago</span>
        </div>
        <nav className="navegacion" aria-label="Principal">
          <NavLink to="/catalogo">Catálogo</NavLink>
          <NavLink to="/historial">Historial</NavLink>
          <NavLink to="/cuenta">Mi cuenta</NavLink>
        </nav>
        <div className="usuario">
          <span className="usuario-nombre">{sesion.nombreUsuario}</span>
          <button type="button" className="boton secundario chico" onClick={() => void cerrar()}>
            Cerrar sesión
          </button>
        </div>
      </header>
      <main className="contenido">
        <Outlet />
      </main>
    </div>
  )
}
