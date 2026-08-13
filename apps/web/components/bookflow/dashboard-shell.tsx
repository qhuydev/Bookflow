'use client'

import Link from 'next/link'
import { ReactNode } from 'react'
import { Building2, Globe2, LayoutDashboard, Library, Store, UserRound, Users } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { useBookFlowMock } from './mock-provider'
import { useAuth } from './auth-provider'
import { useRouter } from 'next/navigation'

const links = [
  ['/dashboard', 'Tổng quan', LayoutDashboard],
  ['/dashboard/business', 'Business', Building2],
  ['/dashboard/branches', 'Chi nhánh', Store],
  ['/dashboard/employees', 'Nhân viên', UserRound],
  ['/dashboard/members', 'Thành viên', Users],
  ['/dashboard/services', 'Dịch vụ', Library],
] as const

export function DashboardShell({ children }: { children: ReactNode }) {
  const { role, setRole } = useBookFlowMock()
  const { authenticated, loading, logout } = useAuth(); const router = useRouter()
  if (!loading && !authenticated) { router.replace('/login'); return null }
  if (loading) return <main className="grid min-h-screen place-items-center">Đang kiểm tra phiên…</main>
  return <div className="min-h-screen">
    <aside className="fixed inset-y-0 hidden w-64 border-r border-border bg-card p-5 lg:block">
      <p className="font-serif text-xl font-semibold">BookFlow</p>
      <p className="text-[10px] font-bold uppercase tracking-widest text-muted-foreground">Business workspace</p>
      <nav className="mt-10 grid gap-1">{links.map(([href, label, Icon]) => <Link key={href} href={href} className="flex items-center gap-3 rounded-lg px-3 py-2.5 text-sm hover:bg-muted"><Icon className="size-4" />{label}</Link>)}</nav>
    </aside>
    <main className="lg:ml-64">
      <header className="flex min-h-20 items-center justify-between border-b border-border px-5 md:px-8">
        <div><p className="text-xs font-bold uppercase tracking-widest text-muted-foreground">Local mock workspace</p><h1 className="font-serif text-2xl font-semibold">An Nhiên Wellness</h1></div>
        <div className="flex gap-2"><select aria-label="Demo quyền" value={role} onChange={event => setRole(event.target.value as typeof role)} className="rounded-lg border border-border bg-background px-3 text-sm"><option>OWNER</option><option>ADMIN</option><option>STAFF</option></select><Link href="/an-nhien-wellness"><Button variant="outline"><Globe2 />Public</Button></Link><Button variant="outline" onClick={() => logout().finally(() => router.replace('/login'))}>Đăng xuất</Button></div>
      </header>
      <div className="p-5 md:p-8">{children}</div>
    </main>
  </div>
}
