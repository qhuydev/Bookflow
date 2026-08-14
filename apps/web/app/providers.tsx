'use client'

import { ReactNode } from 'react'
import { AuthProvider } from '@/components/bookflow/auth-provider'
import { BusinessProvider } from '@/components/bookflow/business-provider'
import { CatalogProvider } from '@/components/bookflow/catalog-provider'

export function Providers({ children }: { children: ReactNode }) {
  return <AuthProvider><BusinessProvider><CatalogProvider>{children}</CatalogProvider></BusinessProvider></AuthProvider>
}
