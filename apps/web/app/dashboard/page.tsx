'use client'

import Link from 'next/link'
import { Button } from '@/components/ui/button'
import { useBookFlowMock, activeOnly } from '@/components/bookflow/mock-provider'

function DashboardSummary() { const { branches, employees, services } = useBookFlowMock(); return <section className="grid gap-4 md:grid-cols-3">{[['Chi nhánh', activeOnly(branches).length], ['Nhân viên', activeOnly(employees).length], ['Dịch vụ', activeOnly(services).length]].map(([label, value]) => <article key={label as string} className="rounded-2xl border border-border bg-card p-5"><p className="text-sm text-muted-foreground">{label as string}</p><p className="mt-3 text-3xl font-semibold">{value as number}</p></article>)}</section> }
export default function Page() { return <><section className="rounded-3xl bg-primary p-7 text-primary-foreground"><p className="text-xs font-bold uppercase tracking-widest">Owner Dashboard</p><h2 className="mt-4 font-serif text-4xl font-semibold">Quản lý business từ một nơi.</h2><p className="mt-3 text-sm text-primary-foreground/70">Mock CRUD và assignment được giữ nguyên, chưa có API integration.</p><Link href="/onboarding" className="mt-5 inline-block"><Button variant="secondary">Mở onboarding</Button></Link></section><div className="mt-6"><DashboardSummary /></div></> }
