'use client'

import { ReactNode, createContext, useContext, useEffect, useState } from 'react'
import { Branch, Employee, Member, Service } from '@/lib/api/contracts'
import { branchesApi, employeesApi, membersApi, servicesApi } from '@/lib/api/catalog'
import { ApiError, useAuth } from './auth-provider'
import { useBusinesses } from './business-provider'

type CatalogStore = {
  branches: Branch[]; employees: Employee[]; members: Member[]; services: Service[]
  loading: boolean; mutating: boolean; error: string | null
  reloadAll: () => Promise<void>
  createBranch: (body: unknown) => Promise<void>; updateBranch: (id: string, body: unknown) => Promise<void>; archiveBranch: (id: string) => Promise<void>
  createEmployee: (body: unknown) => Promise<void>; updateEmployee: (id: string, body: unknown) => Promise<void>; archiveEmployee: (id: string) => Promise<void>; setEmployeeBranches: (id: string, branchIds: string[]) => Promise<void>
  inviteMember: (body: unknown) => Promise<void>; changeMemberRole: (id: string, role: 'ADMIN' | 'STAFF') => Promise<void>; revokeMember: (id: string) => Promise<void>; setMemberEmployee: (id: string, employeeId: string | null) => Promise<void>
  createService: (body: unknown) => Promise<void>; updateService: (id: string, body: unknown) => Promise<void>; archiveService: (id: string) => Promise<void>; setServiceAssignments: (id: string, branchIds: string[], employeeIds: string[]) => Promise<void>
}

const Context = createContext<CatalogStore | null>(null)
const messageFor = (cause: unknown) => cause instanceof ApiError ? cause.problem.detail ?? cause.problem.title ?? `Yêu cầu thất bại (${cause.problem.status}).` : 'Không thể kết nối dịch vụ dữ liệu.'

export function CatalogProvider({ children }: { children: ReactNode }) {
  const { accessToken, protectedRequest } = useAuth()
  const { selectedBusinessId, selectedBusiness } = useBusinesses()
  const [branches, setBranches] = useState<Branch[]>([])
  const [employees, setEmployees] = useState<Employee[]>([])
  const [members, setMembers] = useState<Member[]>([])
  const [services, setServices] = useState<Service[]>([])
  const [dataKey, setDataKey] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)
  const [mutating, setMutating] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const requireBusiness = () => {
    if (!selectedBusinessId) throw new Error('Chưa chọn business.')
    return selectedBusinessId
  }
  const reloadBranches = async () => setBranches(await branchesApi.list(protectedRequest, requireBusiness()))
  const reloadEmployees = async () => setEmployees(await employeesApi.list(protectedRequest, requireBusiness()))
  const reloadMembers = async () => {
    if (selectedBusiness?.membership.role === 'STAFF') { setMembers([]); return }
    setMembers(await membersApi.list(protectedRequest, requireBusiness()))
  }
  const reloadServices = async () => setServices(await servicesApi.list(protectedRequest, requireBusiness()))
  const reloadAll = async () => {
    if (!selectedBusinessId) return
    setLoading(true); setError(null)
    try { await Promise.all([reloadBranches(), reloadEmployees(), reloadMembers(), reloadServices()]) }
    catch (cause) { setError(messageFor(cause)); throw cause }
    finally { setLoading(false) }
  }

  useEffect(() => {
    if (!selectedBusinessId || !accessToken) return
    let current = true
    const requestKey = `${accessToken}:${selectedBusinessId}`
    const memberCall = selectedBusiness?.membership.role === 'STAFF' ? Promise.resolve([] as Member[]) : membersApi.list(protectedRequest, selectedBusinessId)
    Promise.all([branchesApi.list(protectedRequest, selectedBusinessId), employeesApi.list(protectedRequest, selectedBusinessId), memberCall, servicesApi.list(protectedRequest, selectedBusinessId)])
      .then(([nextBranches, nextEmployees, nextMembers, nextServices]) => { if (current) { setBranches(nextBranches); setEmployees(nextEmployees); setMembers(nextMembers); setServices(nextServices) } })
      .catch(cause => { if (current) setError(messageFor(cause)) })
      .finally(() => { if (current) setDataKey(requestKey) })
    return () => { current = false }
    // The access token and selectedBusinessId together prevent stale data crossing sessions or tenants.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [accessToken, selectedBusinessId, selectedBusiness?.membership.role])

  const mutate = async (work: () => Promise<void>) => { setMutating(true); setError(null); try { await work() } catch (cause) { setError(messageFor(cause)); throw cause } finally { setMutating(false) } }
  const createBranch = (body: unknown) => mutate(async () => { await branchesApi.create(protectedRequest, requireBusiness(), body); await reloadBranches() })
  const updateBranch = (id: string, body: unknown) => mutate(async () => { await branchesApi.update(protectedRequest, requireBusiness(), id, body); await reloadBranches() })
  const archiveBranch = (id: string) => mutate(async () => { await branchesApi.archive(protectedRequest, requireBusiness(), id); await reloadBranches() })
  const createEmployee = (body: unknown) => mutate(async () => { await employeesApi.create(protectedRequest, requireBusiness(), body); await reloadEmployees() })
  const updateEmployee = (id: string, body: unknown) => mutate(async () => { await employeesApi.update(protectedRequest, requireBusiness(), id, body); await reloadEmployees() })
  const archiveEmployee = (id: string) => mutate(async () => { await employeesApi.archive(protectedRequest, requireBusiness(), id); await Promise.all([reloadEmployees(), reloadMembers(), reloadServices()]) })
  const setEmployeeBranches = (id: string, desired: string[]) => mutate(async () => { const businessId=requireBusiness(); try { const current=await employeesApi.branches(protectedRequest,businessId,id); for(const branchId of current.filter(value=>!desired.includes(value))) await employeesApi.unassignBranch(protectedRequest,businessId,id,branchId); for(const branchId of desired.filter(value=>!current.includes(value))) await employeesApi.assignBranch(protectedRequest,businessId,id,branchId) } finally { await reloadEmployees() } })
  const inviteMember = (body: unknown) => mutate(async () => { await membersApi.invite(protectedRequest, requireBusiness(), body); await reloadMembers() })
  const changeMemberRole = (id: string, role: 'ADMIN' | 'STAFF') => mutate(async () => { await membersApi.role(protectedRequest, requireBusiness(), id, role); await reloadMembers() })
  const revokeMember = (id: string) => mutate(async () => { await membersApi.revoke(protectedRequest, requireBusiness(), id); await reloadMembers() })
  const setMemberEmployee = (id: string, employeeId: string | null) => mutate(async () => { const businessId=requireBusiness(); const current=members.find(member=>member.id===id)?.employeeId; try { if(current&&current!==employeeId) await membersApi.unlinkEmployee(protectedRequest,businessId,id); if(employeeId) await membersApi.linkEmployee(protectedRequest,businessId,id,employeeId); else if(!current) await membersApi.unlinkEmployee(protectedRequest,businessId,id) } finally { await reloadMembers() } })
  const createService = (body: unknown) => mutate(async () => { await servicesApi.create(protectedRequest, requireBusiness(), body); await reloadServices() })
  const updateService = (id: string, body: unknown) => mutate(async () => { await servicesApi.update(protectedRequest, requireBusiness(), id, body); await reloadServices() })
  const archiveService = (id: string) => mutate(async () => { await servicesApi.archive(protectedRequest, requireBusiness(), id); await reloadServices() })
  const setServiceAssignments = (id: string, desiredBranches: string[], desiredEmployees: string[]) => mutate(async () => { const businessId=requireBusiness(); try { const currentBranches=await servicesApi.branches(protectedRequest,businessId,id); const currentEmployees=await servicesApi.employees(protectedRequest,businessId,id); for(const employeeId of currentEmployees.filter(value=>!desiredEmployees.includes(value))) await servicesApi.unassignEmployee(protectedRequest,businessId,id,employeeId); for(const branchId of desiredBranches.filter(value=>!currentBranches.includes(value))) await servicesApi.assignBranch(protectedRequest,businessId,id,branchId); for(const branchId of currentBranches.filter(value=>!desiredBranches.includes(value))) await servicesApi.unassignBranch(protectedRequest,businessId,id,branchId); for(const employeeId of desiredEmployees.filter(value=>!currentEmployees.includes(value))) await servicesApi.assignEmployee(protectedRequest,businessId,id,employeeId) } finally { await reloadServices() } })

  const requestKey = accessToken && selectedBusinessId ? `${accessToken}:${selectedBusinessId}` : null
  const tenantReady = requestKey !== null && dataKey === requestKey
  return <Context value={{ branches:tenantReady?branches:[], employees:tenantReady?employees:[], members:tenantReady?members:[], services:tenantReady?services:[], loading:loading||Boolean(requestKey&&!tenantReady), mutating, error:tenantReady?error:null, reloadAll, createBranch, updateBranch, archiveBranch, createEmployee, updateEmployee, archiveEmployee, setEmployeeBranches, inviteMember, changeMemberRole, revokeMember, setMemberEmployee, createService, updateService, archiveService, setServiceAssignments }}>{children}</Context>
}

export function useCatalog() { const value=useContext(Context); if(!value) throw new Error('CatalogProvider is required.'); return value }
