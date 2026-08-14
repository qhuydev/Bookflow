'use client'

import Link from 'next/link'
import { useEffect, useMemo, useState } from 'react'
import { ArrowLeft, Building2, CalendarDays, Clock3, Globe2 } from 'lucide-react'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { ApiError } from '@/lib/api-client'
import { availabilityApi } from '@/lib/api/availability'
import { PublicAvailabilityResponse, PublicBranch, PublicBusiness, PublicEmployee, PublicService } from '@/lib/api/contracts'
import { publicCatalogApi } from '@/lib/api/public-catalog'
import { Empty, Field, Input } from './ui'

const messageFor = (cause: unknown, fallback: string) => cause instanceof ApiError
  ? cause.problem.detail ?? cause.problem.title ?? fallback
  : fallback

export function PublicCatalog({ slug }: { slug: string }) {
  const [business, setBusiness] = useState<PublicBusiness | null>(null)
  const [branches, setBranches] = useState<PublicBranch[]>([])
  const [branchId, setBranchId] = useState('')
  const [services, setServices] = useState<PublicService[]>([])
  const [serviceId, setServiceId] = useState('')
  const [employees, setEmployees] = useState<PublicEmployee[]>([])
  const [employeeId, setEmployeeId] = useState('')
  const [date, setDate] = useState('')
  const [availability, setAvailability] = useState<PublicAvailabilityResponse | null>(null)
  const [selectedSlot, setSelectedSlot] = useState<string | null>(null)
  const [catalogError, setCatalogError] = useState('')
  const [availabilityError, setAvailabilityError] = useState('')
  const [loadingAvailability, setLoadingAvailability] = useState(false)

  useEffect(() => {
    let current = true
    Promise.all([publicCatalogApi.business(slug), publicCatalogApi.branches(slug)])
      .then(([nextBusiness, nextBranches]) => {
        if (!current) return
        setBusiness(nextBusiness); setBranches(nextBranches); setBranchId(nextBranches[0]?.id ?? '')
      })
      .catch(cause => { if (current) setCatalogError(cause instanceof ApiError && cause.problem.status === 404 ? 'Không tìm thấy business.' : 'Không thể tải catalog.') })
    return () => { current = false }
  }, [slug])

  useEffect(() => {
    if (!branchId) return
    let current = true
    publicCatalogApi.services(slug, branchId)
      .then(values => { if (current) setServices(values) })
      .catch(cause => { if (current) setAvailabilityError(messageFor(cause, 'Không thể tải dịch vụ.')) })
    return () => { current = false }
  }, [branchId, slug])

  useEffect(() => {
    if (!branchId || !serviceId) return
    let current = true
    publicCatalogApi.employees(slug, branchId, serviceId)
      .then(values => { if (current) setEmployees(values) })
      .catch(cause => { if (current) setAvailabilityError(messageFor(cause, 'Không thể tải nhân viên.')) })
    return () => { current = false }
  }, [branchId, serviceId, slug])

  useEffect(() => {
    if (!branchId || !serviceId || !date) return
    let current = true
    Promise.resolve().then(() => { if (current) setLoadingAvailability(true) })
      .then(() => availabilityApi.find(slug, { branchId, serviceId, date, employeeId: employeeId || undefined }))
      .then(value => { if (current) setAvailability(value) })
      .catch(cause => {
        if (!current) return
        setAvailabilityError(cause instanceof ApiError && cause.problem.status === 404
          ? 'Lựa chọn hiện tại không còn khả dụng. Vui lòng chọn lại branch, dịch vụ hoặc nhân viên.'
          : messageFor(cause, 'Không thể kiểm tra lịch trống.'))
      })
      .finally(() => { if (current) setLoadingAvailability(false) })
    return () => { current = false }
  }, [branchId, date, employeeId, serviceId, slug])

  const selectedService = services.find(service => service.id === serviceId)
  const selected = availability?.slots.find(slot => slot.start === selectedSlot)
  const formatter = useMemo(() => new Intl.DateTimeFormat('vi-VN', {
    hour: '2-digit', minute: '2-digit', hourCycle: 'h23', timeZone: availability?.timeZone ?? business?.timeZone,
  }), [availability?.timeZone, business?.timeZone])
  const formatTime = (value: string) => formatter.format(new Date(value))
  const selectBranch = (value: string) => {
    setBranchId(value); setServiceId(''); setServices([]); setEmployeeId(''); setEmployees([])
    setAvailability(null); setSelectedSlot(null); setAvailabilityError(''); setLoadingAvailability(false)
  }
  const selectService = (value: string) => {
    setServiceId(value); setEmployeeId(''); setEmployees([]); setAvailability(null); setSelectedSlot(null); setAvailabilityError(''); setLoadingAvailability(false)
  }
  const selectEmployee = (value: string) => { setEmployeeId(value); setAvailability(null); setSelectedSlot(null); setAvailabilityError(''); setLoadingAvailability(false) }
  const selectDate = (value: string) => { setDate(value); setAvailability(null); setSelectedSlot(null); setAvailabilityError(''); setLoadingAvailability(false) }

  if (catalogError) return <main className="mx-auto max-w-3xl p-10"><h1 className="font-serif text-4xl font-semibold">{catalogError}</h1><Link className="mt-6 inline-block underline" href="/">← Khám phá</Link></main>
  if (!business) return <main className="grid min-h-screen place-items-center">Đang tải catalog…</main>
  const branch = branches.find(value => value.id === branchId)

  return <main className="min-h-screen">
    <header className="flex items-center justify-between border-b px-5 py-5 md:px-10"><Link href="/" className="flex gap-2 text-sm text-muted-foreground"><ArrowLeft className="size-4" />Khám phá</Link><b className="font-serif text-xl">BookFlow</b><Link href="/register"><Button variant="outline" size="sm">Đăng ký thành viên</Button></Link></header>
    <main className="mx-auto max-w-6xl px-5 py-10 md:px-10 md:py-16">
      <section className="rounded-3xl bg-accent p-7 md:p-12"><Badge variant="secondary">Wellness & trải nghiệm</Badge><h1 className="mt-5 font-serif text-5xl font-semibold md:text-7xl">{business.name}</h1><p className="mt-4 max-w-lg text-muted-foreground">Một nơi để bạn dành thời gian cho những điều có ý nghĩa.</p><div className="mt-7 flex flex-wrap gap-5 text-sm"><span className="flex gap-2"><Globe2 className="size-4" />{business.timeZone}</span><span className="flex gap-2"><Clock3 className="size-4" />Lịch trống được cập nhật từ BookFlow</span><span>{business.currency}</span></div></section>
      <section className="mt-8 rounded-3xl border bg-card p-6">
        <p className="text-xs font-bold uppercase tracking-widest text-accent-foreground">Tìm lịch trống</p>
        <div className="mt-4 grid gap-4 md:grid-cols-2 lg:grid-cols-4">
          <Field label="Chi nhánh"><select value={branchId} onChange={event => selectBranch(event.target.value)} className="h-10 w-full rounded-lg border bg-background px-3"><option value="">Chọn chi nhánh</option>{branches.map(value => <option key={value.id} value={value.id}>{value.name}</option>)}</select></Field>
          <Field label="Dịch vụ"><select value={serviceId} onChange={event => selectService(event.target.value)} className="h-10 w-full rounded-lg border bg-background px-3"><option value="">Chọn dịch vụ</option>{services.map(value => <option key={value.id} value={value.id}>{value.name}</option>)}</select></Field>
          <Field label="Nhân viên"><select disabled={!serviceId} value={employeeId} onChange={event => selectEmployee(event.target.value)} className="h-10 w-full rounded-lg border bg-background px-3"><option value="">Tất cả nhân viên</option>{employees.map(value => <option key={value.id} value={value.id}>{value.fullName}</option>)}</select></Field>
          <Field label="Ngày"><Input type="date" value={date} onChange={event => selectDate(event.target.value)} /></Field>
        </div>
        {availabilityError && <p role="alert" className="mt-4 rounded-lg bg-destructive/10 p-3 text-sm text-destructive">{availabilityError}</p>}
        <div className="mt-6"><h2 className="font-serif text-2xl font-semibold">Giờ còn trống</h2>{loadingAvailability ? <p className="mt-4">Đang kiểm tra lịch trống...</p> : availability && availability.slots.length === 0 ? <Empty>Không có giờ phù hợp trong ngày này.</Empty> : availability ? <div className="mt-4 flex flex-wrap gap-2">{availability.slots.map(slot => <button key={`${slot.start}-${slot.end}`} onClick={() => setSelectedSlot(slot.start)} className={`rounded-xl border px-4 py-3 text-left text-sm ${selectedSlot === slot.start ? 'border-primary bg-primary text-primary-foreground' : 'bg-background hover:bg-muted'}`}><b>{formatTime(slot.start)}</b>{!employeeId && slot.employeeIds.length > 1 && <span className="ml-2 text-xs opacity-75">{slot.employeeIds.length} nhân viên</span>}</button>)}</div> : <p className="mt-4 text-sm text-muted-foreground">Chọn branch, dịch vụ và ngày để xem lịch trống.</p>}</div>
        {selected && <div className="mt-6 rounded-2xl bg-muted p-4"><p className="text-sm">Đã chọn: <b>{formatTime(selected.start)}–{formatTime(selected.end)}</b></p><Button disabled className="mt-3">Tiếp tục — Đặt lịch sắp ra mắt</Button></div>}
      </section>
      <div className="mt-10 grid gap-8 lg:grid-cols-[1fr_360px]"><section><p className="text-xs font-bold uppercase tracking-widest text-accent-foreground">Dịch vụ</p><h2 className="mt-2 font-serif text-3xl font-semibold">Chọn trải nghiệm của bạn</h2>{services.length ? <div className="mt-6 grid gap-3">{services.map(value => <article key={value.id} className={`flex justify-between rounded-2xl border bg-card p-5 ${serviceId === value.id ? 'border-primary' : ''}`}><div><h3 className="font-semibold">{value.name}</h3><p className="mt-1 text-sm text-muted-foreground">{value.description} · {value.durationMinutes} phút</p><p className="mt-3 text-sm">{new Intl.NumberFormat('vi-VN').format(Number(value.price))} {value.currency}</p></div><Button size="sm" variant="outline" onClick={() => selectService(value.id)}>{serviceId === value.id ? 'Đã chọn' : 'Chọn'}</Button></article>)}</div> : <Empty>Chi nhánh này chưa có dịch vụ đang hoạt động.</Empty>}<h2 className="mt-10 font-serif text-3xl font-semibold">Đội ngũ</h2>{employees.length ? <div className="mt-5 grid gap-3 md:grid-cols-2">{employees.map(value => <article key={value.id} className="rounded-2xl border p-5"><h3 className="font-semibold">{value.fullName}</h3><p className="mt-2 text-sm text-muted-foreground">{value.bio}</p></article>)}</div> : <Empty>{serviceId ? 'Chưa có nhân viên phù hợp.' : 'Chọn dịch vụ để xem nhân viên phù hợp.'}</Empty>}</section><aside className="rounded-3xl border bg-card p-6"><h3 className="text-lg font-semibold">Thông tin địa điểm</h3><div className="mt-5 grid gap-4 text-sm"><p className="flex gap-3"><Building2 className="size-4" />{branch?.addressLine1 ?? branch?.city ?? 'Chi nhánh trung tâm'}</p><p className="flex gap-3"><CalendarDays className="size-4" />{selectedService ? `${selectedService.durationMinutes} phút · ${selectedService.name}` : 'Chọn dịch vụ và ngày'}</p></div><Button disabled className="mt-7 w-full">Đặt lịch — Sắp ra mắt</Button></aside></div>
    </main>
  </main>
}
