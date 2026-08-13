'use client'

import { ReactNode, createContext, useContext, useState } from 'react'
import { Branch, Employee, initialBranches, initialEmployees, initialMembers, initialServices, Member, Service } from '@/lib/bookflow-mock'

type Role = 'OWNER' | 'ADMIN' | 'STAFF'
type MockStore = {
  branches: Branch[]; setBranches: (items: Branch[]) => void
  employees: Employee[]; setEmployees: (items: Employee[]) => void
  members: Member[]; setMembers: (items: Member[]) => void
  services: Service[]; setServices: (items: Service[]) => void
  role: Role; setRole: (role: Role) => void
}

const MockContext = createContext<MockStore | null>(null)

export function BookFlowMockProvider({ children }: { children: ReactNode }) {
  const [branches, setBranches] = useState(initialBranches)
  const [employees, setEmployees] = useState(initialEmployees)
  const [members, setMembers] = useState(initialMembers)
  const [services, setServices] = useState(initialServices)
  const [role, setRole] = useState<Role>('OWNER')
  return <MockContext value={{ branches, setBranches, employees, setEmployees, members, setMembers, services, setServices, role, setRole }}>{children}</MockContext>
}

export function useBookFlowMock() {
  const context = useContext(MockContext)
  if (!context) throw new Error('BookFlowMockProvider is required.')
  return context
}

export const mockId = (prefix: string) => `${prefix}-${crypto.randomUUID()}`
export const activeOnly = <T extends { status: string }>(items: T[]) => items.filter(item => item.status === 'ACTIVE')
