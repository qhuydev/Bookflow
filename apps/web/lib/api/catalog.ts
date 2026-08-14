import { Branch, Employee, Member, ProtectedRequest, Service } from './contracts'

const base = (businessId: string, resource: string) => `/api/v1/businesses/${businessId}/${resource}`

export const branchesApi = {
  list: (request: ProtectedRequest, businessId: string) => request<Branch[]>(base(businessId, 'branches')),
  get: (request: ProtectedRequest, businessId: string, id: string) => request<Branch>(`${base(businessId, 'branches')}/${id}`),
  create: (request: ProtectedRequest, businessId: string, body: unknown) => request<Branch>(base(businessId, 'branches'), { method: 'POST', body: JSON.stringify(body) }),
  update: (request: ProtectedRequest, businessId: string, id: string, body: unknown) => request<Branch>(`${base(businessId, 'branches')}/${id}`, { method: 'PATCH', body: JSON.stringify(body) }),
  archive: (request: ProtectedRequest, businessId: string, id: string) => request<void>(`${base(businessId, 'branches')}/${id}`, { method: 'DELETE' }),
}

export const employeesApi = {
  list: (request: ProtectedRequest, businessId: string) => request<Employee[]>(base(businessId, 'employees')),
  get: (request: ProtectedRequest, businessId: string, id: string) => request<Employee>(`${base(businessId, 'employees')}/${id}`),
  create: (request: ProtectedRequest, businessId: string, body: unknown) => request<Employee>(base(businessId, 'employees'), { method: 'POST', body: JSON.stringify(body) }),
  update: (request: ProtectedRequest, businessId: string, id: string, body: unknown) => request<Employee>(`${base(businessId, 'employees')}/${id}`, { method: 'PATCH', body: JSON.stringify(body) }),
  archive: (request: ProtectedRequest, businessId: string, id: string) => request<void>(`${base(businessId, 'employees')}/${id}`, { method: 'DELETE' }),
  branches: (request: ProtectedRequest, businessId: string, id: string) => request<string[]>(`${base(businessId, 'employees')}/${id}/branches`),
  assignBranch: (request: ProtectedRequest, businessId: string, id: string, branchId: string) => request<void>(`${base(businessId, 'employees')}/${id}/branches/${branchId}`, { method: 'PUT' }),
  unassignBranch: (request: ProtectedRequest, businessId: string, id: string, branchId: string) => request<void>(`${base(businessId, 'employees')}/${id}/branches/${branchId}`, { method: 'DELETE' }),
}

export const membersApi = {
  list: (request: ProtectedRequest, businessId: string) => request<Member[]>(base(businessId, 'members')),
  invite: (request: ProtectedRequest, businessId: string, body: unknown) => request<Member>(base(businessId, 'members'), { method: 'POST', body: JSON.stringify(body) }),
  role: (request: ProtectedRequest, businessId: string, id: string, role: string) => request<Member>(`${base(businessId, 'members')}/${id}/role`, { method: 'PATCH', body: JSON.stringify({ role }) }),
  revoke: (request: ProtectedRequest, businessId: string, id: string) => request<void>(`${base(businessId, 'members')}/${id}`, { method: 'DELETE' }),
  linkEmployee: (request: ProtectedRequest, businessId: string, id: string, employeeId: string) => request<void>(`${base(businessId, 'members')}/${id}/employee/${employeeId}`, { method: 'PUT' }),
  unlinkEmployee: (request: ProtectedRequest, businessId: string, id: string) => request<void>(`${base(businessId, 'members')}/${id}/employee`, { method: 'DELETE' }),
}

export const servicesApi = {
  list: (request: ProtectedRequest, businessId: string) => request<Service[]>(base(businessId, 'services')),
  get: (request: ProtectedRequest, businessId: string, id: string) => request<Service>(`${base(businessId, 'services')}/${id}`),
  create: (request: ProtectedRequest, businessId: string, body: unknown) => request<Service>(base(businessId, 'services'), { method: 'POST', body: JSON.stringify(body) }),
  update: (request: ProtectedRequest, businessId: string, id: string, body: unknown) => request<Service>(`${base(businessId, 'services')}/${id}`, { method: 'PATCH', body: JSON.stringify(body) }),
  archive: (request: ProtectedRequest, businessId: string, id: string) => request<void>(`${base(businessId, 'services')}/${id}`, { method: 'DELETE' }),
  branches: (request: ProtectedRequest, businessId: string, id: string) => request<string[]>(`${base(businessId, 'services')}/${id}/branches`),
  assignBranch: (request: ProtectedRequest, businessId: string, id: string, branchId: string) => request<void>(`${base(businessId, 'services')}/${id}/branches/${branchId}`, { method: 'PUT' }),
  unassignBranch: (request: ProtectedRequest, businessId: string, id: string, branchId: string) => request<void>(`${base(businessId, 'services')}/${id}/branches/${branchId}`, { method: 'DELETE' }),
  employees: (request: ProtectedRequest, businessId: string, id: string) => request<string[]>(`${base(businessId, 'services')}/${id}/employees`),
  assignEmployee: (request: ProtectedRequest, businessId: string, id: string, employeeId: string) => request<void>(`${base(businessId, 'services')}/${id}/employees/${employeeId}`, { method: 'PUT' }),
  unassignEmployee: (request: ProtectedRequest, businessId: string, id: string, employeeId: string) => request<void>(`${base(businessId, 'services')}/${id}/employees/${employeeId}`, { method: 'DELETE' }),
}
