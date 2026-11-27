import { llamar } from './cliente'
import type {
  AltaCliente,
  ConfiguracionA2F,
  Cuenta,
  Deposito,
  EstatusTransaccion,
  LoginRespuesta,
  Pagina,
  Producto,
  TokenRespuesta,
  Transaccion,
  Verificacion,
} from './tipos'

/** Endpoints de la API, uno por función. La app Flutter consume exactamente los mismos. */

export const auth = {
  login: (usuario: string, contrasena: string) =>
    llamar<LoginRespuesta>('POST', '/api/v1/auth/login', { usuario, contrasena }),
  configurarA2F: (tokenTemporal: string) =>
    llamar<ConfiguracionA2F>('POST', '/api/v1/auth/a2f/configuracion', { tokenTemporal }),
  verificarA2F: (tokenTemporal: string, codigo: string) =>
    llamar<TokenRespuesta>('POST', '/api/v1/auth/a2f/verificacion', { tokenTemporal, codigo }),
  verificarCorreo: (tokenTemporal: string, codigo: string) =>
    llamar<LoginRespuesta>('POST', '/api/v1/auth/correo/verificacion', { tokenTemporal, codigo }),
  reenviarCorreo: (tokenTemporal: string) =>
    llamar<LoginRespuesta>('POST', '/api/v1/auth/correo/reenvio', { tokenTemporal }),
  enviarCodigoInicioSesion: (tokenTemporal: string) =>
    llamar<LoginRespuesta>('POST', '/api/v1/auth/a2f/correo/envio', { tokenTemporal }),
  verificarCodigoInicioSesion: (tokenTemporal: string, codigo: string) =>
    llamar<TokenRespuesta>('POST', '/api/v1/auth/a2f/correo/verificacion', { tokenTemporal, codigo }),
  /** Código por correo para autorizar un pago o depósito (en lugar de la app). */
  enviarCodigoOperacion: () => llamar<void>('POST', '/api/v1/auth/codigo-operacion'),
  cerrarSesion: () => llamar<void>('POST', '/api/v1/auth/logout'),
  registrar: (alta: AltaCliente) => llamar<{ correoElectronico?: string; nombreUsuario?: string }>('POST', '/api/v1/clientes', alta),
}

export const catalogo = {
  /** GET /productosList: catálogo de GestoPago guardado por la API (con logotipo y categoría). */
  listar: async (): Promise<Producto[]> => {
    // Este endpoint devuelve la lista directa, sin el envoltorio {codigo, mensaje, datos}
    const respuesta = (await llamar<Producto[]>('GET', '/productosList')) as unknown
    return Array.isArray(respuesta) ? (respuesta as Producto[]) : []
  },
}

export const pagos = {
  verificarReferencia: (idServicio: number, idProducto: number, referencia: string) =>
    llamar<Verificacion>('POST', '/api/v1/referencias/verificacion', { idServicio, idProducto, referencia }),
  pagar: (pago: {
    numeroCuenta: string
    idServicio: number
    idProducto: number
    monto: number
    telefono?: string
    referencia?: string
    folioVerificacion?: string
    codigoA2F: string
  }) => llamar<Transaccion>('POST', '/api/v1/transaccion', pago),
  historial: (pagina: number, tamanio: number, estatus?: EstatusTransaccion) =>
    llamar<Pagina<Transaccion>>('POST', '/api/v1/transaccion/historial', { pagina, tamanio, estatus }),
  detalle: (id: number) => llamar<Transaccion>('POST', '/api/v1/transaccion/detalle', { id }),
}

export const cuentas = {
  consultar: (numeroCuenta: string) => llamar<Cuenta>('POST', '/api/v1/cuentas/consulta', { numeroCuenta }),
  depositar: (numeroCuenta: string, monto: number, codigoA2F: string) =>
    llamar<Deposito>('POST', '/api/v1/cuentas/deposito', { numeroCuenta, monto, codigoA2F }),
}
