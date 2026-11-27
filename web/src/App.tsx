import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import { Layout } from './components/Layout'
import { Catalogo } from './pages/Catalogo'
import { Cuenta } from './pages/Cuenta'
import { DetalleHistorial, Historial } from './pages/Historial'
import { Login } from './pages/Login'
import { Pago } from './pages/Pago'
import { Registro } from './pages/Registro'
import { ProveedorSesion } from './sesion/Sesion'

export default function App() {
  return (
    <ProveedorSesion>
      <BrowserRouter>
        <Routes>
          <Route path="/login" element={<Login />} />
          <Route path="/registro" element={<Registro />} />
          <Route element={<Layout />}>
            <Route path="/catalogo" element={<Catalogo />} />
            <Route path="/pagar/:idServicio/:idProducto" element={<Pago />} />
            <Route path="/historial" element={<Historial />} />
            <Route path="/historial/:id" element={<DetalleHistorial />} />
            <Route path="/cuenta" element={<Cuenta />} />
          </Route>
          <Route path="*" element={<Navigate to="/catalogo" replace />} />
        </Routes>
      </BrowserRouter>
    </ProveedorSesion>
  )
}
