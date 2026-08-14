'use client'

import Link from 'next/link'
import { ReactNode, useEffect } from 'react'
import { useRouter } from 'next/navigation'
import { Building2, Globe2, LayoutDashboard, Library, Store, UserRound, Users } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { useAuth } from './auth-provider'
import { useBusinesses } from './business-provider'

const links = [
  ['/dashboard', 'Tổng quan', LayoutDashboard],
  ['/dashboard/business', 'Business', Building2],
  ['/dashboard/branches', 'Chi nhánh', Store],
  ['/dashboard/employees', 'Nhân viên', UserRound],
  ['/dashboard/members', 'Thành viên', Users],
  ['/dashboard/services', 'Dịch vụ', Library],
] as const

export function DashboardShell({ children }: { children: ReactNode }) {
  const { authenticated, loading: authLoading, logout } = useAuth()
  const { businesses, selectedBusiness, selectedBusinessId, selectBusiness, loading: businessLoading, error, reloadBusinesses } = useBusinesses()
  const router = useRouter()

  useEffect(() => {
    if (!authLoading && !authenticated) router.replace('/login')
  }, [authLoading, authenticated, router])

  useEffect(() => {
    if (authenticated && !businessLoading && !error && businesses.length === 0) router.replace('/onboarding')
  }, [authenticated, businessLoading, businesses.length, error, router])

  if (authLoading || (authenticated && businessLoading)) return <main className="grid min-h-screen place-items-center">Đang tải workspace…</main>
  if (!authenticated) return null
  if (error) return <main className="grid min-h-screen place-items-center p-5"><section className="text-center"><p className="text-destructive">{error}</p><Button className="mt-4" onClick={() => void reloadBusinesses()}>Thử lại</Button></section></main>
  if (!selectedBusiness) return null

  return <div className="min-h-screen">
    <aside className="fixed inset-y-0 hidden w-64 border-r border-border bg-card p-5 lg:block">
      <p className="font-serif text-xl font-semibold">BookFlow</p>
      <p className="text-[10px] font-bold uppercase tracking-widest text-muted-foreground">Business workspace</p>
      <nav className="mt-10 grid gap-1">{links.map(([href, label, Icon]) => <Link key={href} href={href} className="flex items-center gap-3 rounded-lg px-3 py-2.5 text-sm hover:bg-muted"><Icon className="size-4" />{label}</Link>)}</nav>
    </aside>
    <main className="lg:ml-64">
      <header className="flex min-h-20 flex-wrap items-center justify-between gap-3 border-b border-border px-5 py-3 md:px-8">
        <div><p className="text-xs font-bold uppercase tracking-widest text-muted-foreground">Business workspace</p><h1 className="font-serif text-2xl font-semibold">{selectedBusiness.name}</h1></div>
        <div className="flex flex-wrap gap-2">
          <select aria-label="Chọn business" value={selectedBusinessId ?? ''} onChange={event => selectBusiness(event.target.value)} className="rounded-lg border border-border bg-background px-3 text-sm">{businesses.map(business => <option key={business.id} value={business.id}>{business.name}</option>)}</select>
          <span className="grid place-items-center rounded-lg bg-muted px-3 text-xs font-semibold">{selectedBusiness.membership.role}</span>
          <Link href={`/${selectedBusiness.slug}`}><Button variant="outline"><Globe2 />Public</Button></Link>
          <Button variant="outline" onClick={() => logout().finally(() => router.replace('/login'))}>Đăng xuất</Button>
        </div>
      </header>
      <div className="p-5 md:p-8">{children}</div>
    </main>
  </div>
}
