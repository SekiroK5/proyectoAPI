import type { ReactNode } from 'react'
import { Icono } from './Iconos'

/** Estructura de login y registro: panel de marca a la izquierda y formulario a la derecha. */
export function PantallaAcceso({ children, ancho }: { children: ReactNode; ancho?: boolean }) {
  return (
    <div className="pantalla-acceso">
      <aside className="panel-marca">
        <div className="marca">
          <span className="marca-icono" aria-hidden="true">G</span>
          <span>GestoPago</span>
        </div>
        <div>
          <h2>Paga tus servicios y recargas en un solo lugar.</h2>
          <ul>
            <li><span><Icono nombre="rayo" /></span>Recargas y pagos al instante con folio</li>
            <li><span><Icono nombre="escudo" /></span>Doble verificación en cada pago</li>
            <li><span><Icono nombre="recibo" /></span>Historial de tus operaciones</li>
          </ul>
        </div>
        <p className="lema chico">Tu misma cuenta funciona en la web y en la app móvil.</p>
      </aside>
      <main className="panel-formulario">
        <div className={ancho ? 'registro' : 'acceso'}>{children}</div>
      </main>
    </div>
  )
}
