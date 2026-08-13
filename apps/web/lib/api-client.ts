export type Problem = { status: number; title?: string; detail?: string; code?: string; errors?: Record<string, string> }
const baseUrl = () => process.env.NEXT_PUBLIC_API_BASE_URL ?? 'http://127.0.0.1:8080'

export class ApiError extends Error { constructor(public readonly problem: Problem) { super(problem.detail ?? problem.title ?? 'Request failed') } }
export async function api<T>(path: string, init: RequestInit = {}, csrf?: string): Promise<T> {
  const response = await fetch(`${baseUrl()}${path}`, { ...init, credentials: 'include', headers: { Accept: 'application/json', ...(init.body ? { 'Content-Type': 'application/json' } : {}), ...(csrf ? { 'X-XSRF-TOKEN': csrf } : {}), ...init.headers } })
  if (response.status === 204) return undefined as T
  const contentType = response.headers.get('content-type') ?? ''
  const payload = contentType.includes('json') ? await response.json() : undefined
  if (!response.ok) throw new ApiError({ status: response.status, ...(payload ?? { detail: response.statusText }) })
  return payload as T
}
export async function csrfToken() { const value = await api<{ token: string }>('/api/v1/auth/csrf'); return value.token }
