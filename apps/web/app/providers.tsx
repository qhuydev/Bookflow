'use client'

import { ReactNode } from 'react'
import { BookFlowMockProvider } from '@/components/bookflow/mock-provider'
import { AuthProvider } from '@/components/bookflow/auth-provider'

export function Providers({ children }: { children: ReactNode }) {
  return <AuthProvider><BookFlowMockProvider>{children}</BookFlowMockProvider></AuthProvider>
}
