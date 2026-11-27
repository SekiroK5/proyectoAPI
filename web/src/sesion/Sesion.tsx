import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'
import type { ReactNode } from 'react'
import { alPerderSesion, establecerToken } from '../api/cliente'
import { auth } from '../api/servicios'
import type { TokenRespuesta } from '../api/tipos'

/**
 * Sesión del usuario. El token vive en memoria y en sessionStorage (se borra al cerrar la pestaña;
 * no se usa localStorage para no dejar el token guardado en el equipo). Al vencer, se cierra sola.
 */
export interface DatosSesion {
  token: string
  nombreUsuario: string
  numeroCuenta: string
  expiraEn: number
}

interface ContextoSesion {
  sesion: DatosSesion | null
  iniciar: (token: TokenRespuesta) => void
  cerrar: () => Promise<void>
  /** Mensaje para la pantalla de login cuando la sesión se cerró sola. */
  aviso: string | null
}

const CLAVE = 'gestopago.sesion'
const Contexto = createContext<ContextoSesion | null>(null)

function leerGuardada(): DatosSesion | null {
  try {
    const texto = sessionStorage.getItem(CLAVE)
    if (!texto) return null
    const datos = JSON.parse(texto) as DatosSesion
    return datos.expiraEn > Date.now() ? datos : null
  } catch {
    return null
  }
}

export function ProveedorSesion({ children }: { children: ReactNode }) {
  const [sesion, setSesion] = useState<DatosSesion | null>(() => {
    const guardada = leerGuardada()
    establecerToken(guardada?.token ?? null)
    return guardada
  })
  const [aviso, setAviso] = useState<string | null>(null)

  const limpiar = useCallback((mensaje: string | null) => {
    establecerToken(null)
    try {
      sessionStorage.removeItem(CLAVE)
    } catch {
      /* almacenamiento no disponible: la sesión solo vivía en memoria */
    }
    setSesion(null)
    setAviso(mensaje)
  }, [])

  const iniciar = useCallback((token: TokenRespuesta) => {
    const datos: DatosSesion = {
      token: token.tokenAcceso,
      nombreUsuario: token.nombreUsuario,
      numeroCuenta: token.numeroCuenta,
      expiraEn: Date.now() + token.expiraEnSegundos * 1000,
    }
    establecerToken(datos.token)
    try {
      sessionStorage.setItem(CLAVE, JSON.stringify(datos))
    } catch {
      /* sin sessionStorage la sesión dura mientras la página esté abierta */
    }
    setAviso(null)
    setSesion(datos)
  }, [])

  const cerrar = useCallback(async () => {
    try {
      // Revoca el token en el servidor para que no siga sirviendo
      await auth.cerrarSesion()
    } catch {
      /* aunque el servidor no responda, la sesión local se cierra */
    }
    limpiar(null)
  }, [limpiar])

  // Si la API responde que el token ya no es válido, se cierra la sesión local
  useEffect(() => {
    alPerderSesion(() => limpiar('Su sesión terminó. Inicie sesión de nuevo.'))
  }, [limpiar])

  // Cierre automático al vencer el token
  useEffect(() => {
    if (!sesion) return
    const restante = sesion.expiraEn - Date.now()
    const temporizador = window.setTimeout(() => limpiar('Su sesión venció. Inicie sesión de nuevo.'), Math.max(restante, 0))
    return () => window.clearTimeout(temporizador)
  }, [sesion, limpiar])

  const valor = useMemo(() => ({ sesion, iniciar, cerrar, aviso }), [sesion, iniciar, cerrar, aviso])
  return <Contexto.Provider value={valor}>{children}</Contexto.Provider>
}

export function useSesion(): ContextoSesion {
  const contexto = useContext(Contexto)
  if (!contexto) {
    throw new Error('useSesion debe usarse dentro de ProveedorSesion')
  }
  return contexto
}
