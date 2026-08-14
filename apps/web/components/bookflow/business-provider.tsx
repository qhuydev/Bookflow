'use client'

import { ReactNode, createContext, useContext, useEffect, useState } from 'react'
import { businessesApi } from '@/lib/api/businesses'
import { Business } from '@/lib/api/contracts'
import { ApiError, useAuth } from './auth-provider'

type CreateBusinessInput = { name: string; slug: string; type: string; timeZone: string }
type UpdateBusinessInput = Partial<Pick<Business, 'name' | 'slug' | 'type' | 'timeZone' | 'currencyCode' | 'cancellationPolicy' | 'maxBookingAdvanceDays'>>

type BusinessStore = {
  businesses: Business[]
  selectedBusiness: Business | null
  selectedBusinessId: string | null
  loading: boolean
  error: string | null
  reloadBusinesses: () => Promise<Business[]>
  selectBusiness: (id: string) => void
  createBusiness: (input: CreateBusinessInput) => Promise<Business>
  updateBusiness: (input: UpdateBusinessInput) => Promise<Business>
}

const BusinessContext = createContext<BusinessStore | null>(null)

const messageFor = (error: unknown) => error instanceof ApiError
  ? error.problem.detail ?? error.problem.title ?? 'Không thể tải dữ liệu business.'
  : 'Không thể kết nối business service.'

export function BusinessProvider({ children }: { children: ReactNode }) {
  const { accessToken, authenticated, loading: authLoading, protectedRequest } = useAuth()
  const [businesses, setBusinesses] = useState<Business[]>([])
  const [selectedBusinessId, setSelectedBusinessId] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)
  const [loadedAccessToken, setLoadedAccessToken] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)

  const load = async (preferredId?: string) => {
    await Promise.resolve()
    setLoading(true)
    setError(null)
    try {
      const next = await businessesApi.list(protectedRequest)
      setBusinesses(next)
      setSelectedBusinessId(current => {
        const candidate = preferredId ?? current
        return candidate && next.some(business => business.id === candidate) ? candidate : next[0]?.id ?? null
      })
      return next
    } catch (cause) {
      setError(messageFor(cause))
      throw cause
    } finally {
      setLoading(false)
      setLoadedAccessToken(accessToken)
    }
  }

  useEffect(() => {
    if (authLoading) return
    if (!authenticated || !accessToken) return
    let current = true
    businessesApi.list(protectedRequest)
      .then(next => {
        if (!current) return
        setError(null)
        setBusinesses(next)
        setSelectedBusinessId(selected => selected && next.some(business => business.id === selected) ? selected : next[0]?.id ?? null)
      })
      .catch(cause => { if (current) setError(messageFor(cause)) })
      .finally(() => { if (current) setLoadedAccessToken(accessToken) })
    return () => { current = false }
  }, [accessToken, authenticated, authLoading, protectedRequest])

  useEffect(() => {
    if (!authenticated || !selectedBusinessId || loadedAccessToken !== accessToken) return
    let current = true
    businessesApi.get(protectedRequest, selectedBusinessId)
      .then(detail => {
        if (current) setBusinesses(items => items.map(item => item.id === detail.id ? detail : item))
      })
      .catch(cause => {
        if (current) setError(messageFor(cause))
      })
    return () => { current = false }
    // selectedBusinessId and loadedAccessToken are the server-resource/session boundary.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [accessToken, authenticated, loadedAccessToken, selectedBusinessId])

  const selectBusiness = (id: string) => {
    if (businesses.some(business => business.id === id)) setSelectedBusinessId(id)
  }

  const createBusiness = async (input: CreateBusinessInput) => {
    setError(null)
    const created = await businessesApi.create(protectedRequest, input)
    await load(created.id)
    return created
  }

  const updateBusiness = async (input: UpdateBusinessInput) => {
    if (!selectedBusinessId) throw new Error('Chưa chọn business để cập nhật.')
    setError(null)
    try {
      const updated = await businessesApi.update(protectedRequest, selectedBusinessId, input)
      setBusinesses(current => current.map(business => business.id === updated.id ? updated : business))
      return updated
    } catch (cause) {
      setError(messageFor(cause))
      throw cause
    }
  }

  const ready = authenticated && loadedAccessToken === accessToken
  const visibleBusinesses = ready ? businesses : []
  const selectedBusiness = visibleBusinesses.find(business => business.id === selectedBusinessId) ?? null
  return <BusinessContext value={{ businesses:visibleBusinesses, selectedBusiness, selectedBusinessId:ready?selectedBusinessId:null, loading:loading || (authenticated && !ready), error, reloadBusinesses: load, selectBusiness, createBusiness, updateBusiness }}>{children}</BusinessContext>
}

export function useBusinesses() {
  const value = useContext(BusinessContext)
  if (!value) throw new Error('BusinessProvider is required.')
  return value
}
