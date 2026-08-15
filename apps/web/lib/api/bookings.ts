import { api, csrfToken } from '@/lib/api-client'
import { CreateBookingInput, CreatedBooking } from './contracts'

export const bookingApi = {
  create: async (slug: string, input: CreateBookingInput, idempotencyKey: string) => {
    const csrf = await csrfToken()
    return api<CreatedBooking>(
      `/api/v1/public/businesses/${encodeURIComponent(slug)}/bookings`,
      {
        method: 'POST',
        headers: { 'Idempotency-Key': idempotencyKey },
        body: JSON.stringify(input),
      },
      csrf,
    )
  },
}
