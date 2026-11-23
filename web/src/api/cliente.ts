import type { ApiRespuesta } from './tipos'

/** URL de la API Spring. En desarrollo apunta a la API local; se cambia con VITE_API_URL. */
export const API_URL: string = import.meta.env.VITE_API_URL ?? 'http://localhost:8081'

/** Mensaje con el que la API responde 401 cuando falta el token o ya no es válido. */
const SESION_REQUERIDA = 'Se requiere iniciar sesión'

/** Error de la API con su código HTTP y el mensaje (ya pensado para el usuario) que manda el servidor. */
export class ApiError extends Error {
  readonly status: number
  /** true si la sesión ya no es válida (token vencido, revocado o ausente). */
  readonly sesionVencida: boolean

  constructor(status: number, mensaje: string) {
    super(mensaje)
    this.status = status
    this.sesionVencida = status === 401 && mensaje.startsWith(SESION_REQUERIDA)
  }
}

let tokenActual: string | null = null
let alVencerSesion: (() => void) | null = null

export function establecerToken(token: string | null): void {
  tokenActual = token
}

/** La capa de sesión se registra aquí para cerrar sesión cuando la API indica que el token ya no sirve. */
export function alPerderSesion(callback: () => void): void {
  alVencerSesion = callback
}

/**
 * Llama a la API y devuelve la respuesta {codigo, mensaje, datos}. Lanza ApiError si el HTTP no es 2xx,
 * con el mensaje del servidor; si no hay conexión, con un mensaje genérico.
 */
export async function llamar<T>(metodo: 'GET' | 'POST' | 'PATCH' | 'DELETE', ruta: string, cuerpo?: unknown): Promise<ApiRespuesta<T>> {
  const encabezados: Record<string, string> = {}
  if (cuerpo !== undefined) {
    encabezados['Content-Type'] = 'application/json'
  }
  if (tokenActual) {
    encabezados['Authorization'] = `Bearer ${tokenActual}`
  }

  let respuesta: Response
  try {
    respuesta = await fetch(`${API_URL}${ruta}`, {
      method: metodo,
      headers: encabezados,
      body: cuerpo !== undefined ? JSON.stringify(cuerpo) : undefined,
    })
  } catch {
    throw new ApiError(0, 'No hay conexión con el servidor. Verifique su red e intente de nuevo.')
  }

  let json: ApiRespuesta<T> | null = null
  try {
    json = (await respuesta.json()) as ApiRespuesta<T>
  } catch {
    json = null
  }

  if (!respuesta.ok) {
    const error = new ApiError(respuesta.status, json?.mensaje ?? `Error ${respuesta.status} del servidor.`)
    if (error.sesionVencida && alVencerSesion) {
      alVencerSesion()
    }
    throw error
  }
  return json ?? { codigo: 0, mensaje: '' }
}

/** Mensaje legible de cualquier error capturado. */
export function mensajeDeError(error: unknown): string {
  if (error instanceof ApiError) {
    return error.message
  }
  return 'Ocurrió un error inesperado.'
}
