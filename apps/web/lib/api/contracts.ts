export type Business = { id: string; name: string; slug: string; type: string; timeZone: string; currencyCode: string; cancellationPolicy: string; maxBookingAdvanceDays: number; status: string; membership: { role: string; status: string }; createdAt: string }
export type Branch = { id: string; code: string; name: string; addressLine1?: string; addressLine2?: string; ward?: string; district?: string; city?: string; postalCode?: string; countryCode?: string; phone?: string; email?: string; timeZone: string; status: string; createdAt: string }
export type Employee = { id: string; businessId: string; code: string; fullName: string; phone?: string; email?: string; bio?: string; status: string; branchIds: string[]; createdAt: string; updatedAt: string }
export type Member = { id: string; userId: string; email: string; role: 'OWNER' | 'ADMIN' | 'STAFF'; status: string; employeeId?: string; createdAt: string; updatedAt: string }
export type Service = { id: string; businessId: string; name: string; description?: string; price: number; currency: string; durationMinutes: number; bufferBeforeMinutes: number; bufferAfterMinutes: number; status: string; branchIds: string[]; employeeIds: string[]; createdAt: string; updatedAt: string }
export type PublicBusiness = { slug: string; name: string; timeZone: string; currency: string }
export type PublicBranch = { id: string; code: string; name: string; addressLine1?: string; city?: string; timeZone: string }
export type PublicService = { id: string; name: string; description?: string; price: number; currency: string; durationMinutes: number; bufferBeforeMinutes: number; bufferAfterMinutes: number }
export type PublicEmployee = { id: string; fullName: string; bio?: string }
export type ProtectedRequest = <T>(path: string, init?: RequestInit) => Promise<T>
