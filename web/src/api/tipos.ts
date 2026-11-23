/** Tipos de las respuestas de la API (mismo contrato que usa la app Flutter). */

export interface ApiRespuesta<T> {
  codigo: number
  mensaje: string
  datos?: T
}

export interface Pagina<T> {
  contenido: T[]
  pagina: number
  tamanio: number
  totalElementos: number
  totalPaginas: number
}

export type TipoOperacion = 'RECARGA' | 'PAGO_SERVICIO' | 'NO_SOPORTADA'

export interface Producto {
  idServicio: number
  idProducto: number
  idCatTipoServicio?: number
  servicio?: string
  producto?: string
  precio?: number
  tipoFront?: number
  tipoReferencia?: string
  legend?: string
  logoUrl?: string
  imagenReferenciaUrl?: string
  categoria?: string
  tipoOperacion?: TipoOperacion
  requiereVerificacion?: boolean
  verificableEnGestoPago?: boolean
}

export type PasoLogin = 'CONFIGURAR_A2F' | 'VERIFICAR_A2F'

export interface LoginRespuesta {
  siguientePaso: PasoLogin
  metodoA2F: string
  tokenTemporal: string
  expiraEnSegundos: number
}

export interface ConfiguracionA2F {
  metodo: string
  secreto: string
  uriOtpauth: string
}

export interface TokenRespuesta {
  tokenAcceso: string
  tipoToken: string
  expiraEnSegundos: number
  nombreUsuario: string
  numeroCuenta: string
}

export type ResultadoVerificacion = 'VALIDA' | 'INVALIDA' | 'FORMATO_VALIDO'

export interface Verificacion {
  folio: string
  resultado: ResultadoVerificacion
  verificadaConGestoPago: boolean
  mensaje?: string
  saldoPendiente?: string
  idServicio: number
  idProducto: number
  servicio?: string
  producto?: string
  referencia: string
  logoUrl?: string
  expira: string
}

export type EstatusTransaccion =
  | 'PENDIENTE'
  | 'APLICADA'
  | 'RECHAZADA'
  | 'PENDIENTE_CONFIRMACION'
  | 'REVISION_MANUAL'

export interface Transaccion {
  id: number
  upc: string
  estatus: EstatusTransaccion
  numeroCuenta: string
  idServicio: number
  idProducto: number
  servicio?: string
  producto?: string
  categoria?: string
  logoUrl?: string
  telefono?: string
  referencia?: string
  monto: string
  saldoCuenta?: string
  numAutorizacion?: string
  idTx?: string
  pin?: string
  codigoGestoPago?: string
  mensajeGestoPago?: string
  fecha: string
}

export interface Cuenta {
  numeroCuenta: string
  saldo: string
  estatus: string
  fechaApertura?: string
  nombreCliente?: string
}

export interface Deposito {
  folio: number
  numeroCuenta: string
  monto: string
  saldo: string
  fecha: string
}

export interface AltaCliente {
  nombre: string
  segundoNombre?: string
  apellidoPaterno: string
  apellidoMaterno: string
  fechaNacimiento: string
  curp: string
  rfc: string
  sexo: 'M' | 'F' | 'O'
  nacionalidad: string
  estadoCivil: 'SOLTERO' | 'CASADO' | 'DIVORCIADO' | 'VIUDO' | 'UNION_LIBRE'
  correoElectronico: string
  nombreUsuario: string
  contrasena: string
  telefonoMovil: string
  telefonoAlternativo?: string
  ocupacion: string
  empresa: string
  ingresoMensual: number
  domicilio: {
    calle: string
    numeroExterior: string
    numeroInterior?: string
    colonia: string
    municipio: string
    estado: string
    codigoPostal: string
    pais: string
  }
}
