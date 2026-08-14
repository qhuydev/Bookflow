'use client'

import { ReactNode, createContext, useCallback, useContext, useEffect, useRef, useState } from 'react'
import { api, ApiError, csrfToken } from '@/lib/api-client'

type Login = { accessToken: string; tokenType: string; expiresIn: number }
type Auth = { accessToken: string | null; authenticated: boolean; loading: boolean; login: (email: string, password: string) => Promise<void>; register: (email: string, password: string) => Promise<void>; logout: () => Promise<void>; logoutAll: () => Promise<void>; refresh: () => Promise<void>; protectedRequest: <T>(path: string, init?: RequestInit) => Promise<T>; forgotPassword: (email: string) => Promise<void>; resetPassword: (token: string, newPassword: string) => Promise<void> }
const Context = createContext<Auth | null>(null)
export function AuthProvider({ children }: { children: ReactNode }) {
  const [accessToken, setAccessToken] = useState<string | null>(null); const [loading, setLoading] = useState(true); const refreshing = useRef<Promise<string> | null>(null)
  const mutation = useCallback(async <T,>(path: string, body?: unknown, authorization?: string) => { const csrf = await csrfToken(); return api<T>(path, { method: 'POST', body: body ? JSON.stringify(body) : undefined, headers: authorization ? { Authorization: `Bearer ${authorization}` } : undefined }, csrf) }, [])
  const refreshToken = useCallback(async () => { if (!refreshing.current) refreshing.current = mutation<Login>('/api/v1/auth/refresh').then(result => { setAccessToken(result.accessToken); return result.accessToken }).finally(() => { refreshing.current = null }); return refreshing.current }, [mutation])
  const refresh = useCallback(async () => { await refreshToken() }, [refreshToken])
  useEffect(() => { refresh().catch(() => setAccessToken(null)).finally(() => setLoading(false)) }, [refresh])
  const protectedRequest = useCallback(async <T,>(path: string, init: RequestInit = {}) => {
    if (!accessToken) throw new ApiError({ status: 401, detail: 'Authentication is required.' })
    const request = async (token: string) => {
      const method = (init.method ?? 'GET').toUpperCase()
      const csrf = ['POST', 'PUT', 'PATCH', 'DELETE'].includes(method) ? await csrfToken() : undefined
      return api<T>(path, { ...init, headers: { ...init.headers, Authorization: `Bearer ${token}` } }, csrf)
    }
    try { return await request(accessToken) } catch (error) {
      if (!(error instanceof ApiError) || error.problem.status !== 401) throw error
      try { return await request(await refreshToken()) } catch (retryError) { setAccessToken(null); throw retryError }
    }
  }, [accessToken, refreshToken])
  const value: Auth = { accessToken, authenticated: Boolean(accessToken), loading, refresh, protectedRequest, login: async (email, password) => { const result = await mutation<Login>('/api/v1/auth/login', { email, password }); setAccessToken(result.accessToken) }, register: async (email, password) => { await api('/api/v1/auth/register', { method: 'POST', body: JSON.stringify({ email, password }) }) }, logout: async () => { await mutation('/api/v1/auth/logout'); setAccessToken(null) }, logoutAll: async () => { if (accessToken) await mutation('/api/v1/auth/logout-all', undefined, accessToken); setAccessToken(null) }, forgotPassword: async email => { await mutation('/api/v1/auth/forgot-password', { email }) }, resetPassword: async (token, newPassword) => { await mutation('/api/v1/auth/reset-password', { token, newPassword }) } }
  return <Context value={value}>{children}</Context>
}
export function useAuth() { const value = useContext(Context); if (!value) throw new Error('AuthProvider is required.'); return value }
export { ApiError }
