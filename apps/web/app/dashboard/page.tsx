'use client'

import Link from 'next/link'
import { Button } from '@/components/ui/button'
import { useCatalog } from '@/components/bookflow/catalog-provider'

function DashboardSummary() {
  const { branches, employees, services, loading } = useCatalog()
  const values = [['Chi nhánh', branches.length], ['Nhân viên', employees.length], ['Dịch vụ', services.length]] as const
  return <section className="grid gap-4 md:grid-cols-3">{values.map(([label, value]) => <article key={label} className="rounded-2xl border border-border bg-card p-5"><p className="text-sm text-muted-foreground">{label}</p><p className="mt-3 text-3xl font-semibold">{loading ? '…' : value}</p></article>)}</section>
}

export default function Page() {
  return <><section className="rounded-3xl bg-primary p-7 text-primary-foreground"><p className="text-xs font-bold uppercase tracking-widest">Business Dashboard</p><h2 className="mt-4 font-serif text-4xl font-semibold">Quản lý business từ một nơi.</h2><p className="mt-3 text-sm text-primary-foreground/70">Dữ liệu catalog được đồng bộ trực tiếp với BookFlow API.</p><Link href="/onboarding" className="mt-5 inline-block"><Button variant="secondary">Tạo thêm business</Button></Link></section><div className="mt-6"><DashboardSummary /></div></>
}
