import { api } from '@/lib/api-client'
import { PublicAvailabilityResponse } from './contracts'

type AvailabilityQuery = { branchId: string; serviceId: string; date: string; employeeId?: string }

export const availabilityApi = {
  find: (slug: string, query: AvailabilityQuery) => {
    const params = new URLSearchParams({
      branchId: query.branchId,
      serviceId: query.serviceId,
      date: query.date,
    })
    if (query.employeeId) params.set('employeeId', query.employeeId)
    return api<PublicAvailabilityResponse>(
      `/api/v1/public/businesses/${encodeURIComponent(slug)}/availability?${params.toString()}`,
    )
  },
}
