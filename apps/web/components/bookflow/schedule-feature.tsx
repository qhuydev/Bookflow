'use client'

import Link from 'next/link'
import { FormEvent, ReactNode, useEffect, useState } from 'react'
import { ArrowLeft, Plus, X } from 'lucide-react'
import { Button } from '@/components/ui/button'
import {
  ScheduleBreak,
  ScheduleBreakInput,
  ScheduleException,
  ScheduleExceptionInput,
  ScheduleExceptionType,
  Weekday,
  WorkingRuleInput,
  WorkingScheduleRule,
} from '@/lib/api/contracts'
import { schedulesApi } from '@/lib/api/schedules'
import { ApiError, useAuth } from './auth-provider'
import { useBusinesses } from './business-provider'
import { useCatalog } from './catalog-provider'
import { Empty, Field, Input, Textarea } from './ui'

const weekdays: { value: Weekday; label: string }[] = [
  { value: 'MONDAY', label: 'Thứ 2' }, { value: 'TUESDAY', label: 'Thứ 3' },
  { value: 'WEDNESDAY', label: 'Thứ 4' }, { value: 'THURSDAY', label: 'Thứ 5' },
  { value: 'FRIDAY', label: 'Thứ 6' }, { value: 'SATURDAY', label: 'Thứ 7' },
  { value: 'SUNDAY', label: 'Chủ nhật' },
]

const apiMessage = (cause: unknown) => {
  if (!(cause instanceof ApiError)) return 'Không thể kết nối dịch vụ lịch làm việc.'
  if (cause.problem.status === 409 || cause.problem.code === 'SCHEDULE_CONFLICT')
    return 'Khoảng thời gian này xung đột với lịch hiện có.'
  if (cause.problem.status === 403) return 'Vai trò hiện tại không có quyền thay đổi lịch.'
  if (cause.problem.status === 404) return 'Employee, branch hoặc lịch không còn khả dụng.'
  return cause.problem.detail ?? cause.problem.title ?? `Yêu cầu thất bại (${cause.problem.status}).`
}

async function fetchSchedule(request: Parameters<typeof schedulesApi.listRules>[0], businessId: string, employeeId: string) {
  const [rules, exceptions] = await Promise.all([
    schedulesApi.listRules(request, businessId, employeeId),
    schedulesApi.listExceptions(request, businessId, employeeId),
  ])
  const breakEntries = await Promise.all(rules.map(async rule => [
    rule.id,
    await schedulesApi.listBreaks(request, businessId, employeeId, rule.id),
  ] as const))
  return { rules, exceptions, breaks: Object.fromEntries(breakEntries) as Record<string, ScheduleBreak[]> }
}

function Modal({ title, close, children }: { title: string; close: () => void; children: ReactNode }) {
  return <div role="dialog" aria-modal="true" className="fixed inset-0 z-30 grid place-items-center bg-primary/30 p-5">
    <section className="max-h-[90vh] w-full max-w-xl overflow-auto rounded-2xl bg-card p-6 shadow-xl">
      <div className="flex justify-between"><h2 className="font-serif text-2xl font-semibold">{title}</h2><button type="button" aria-label="Đóng" onClick={close}><X /></button></div>
      {children}
    </section>
  </div>
}

function Failure({ message }: { message: string }) {
  return <p role="alert" className="rounded-lg bg-destructive/10 p-3 text-sm text-destructive">{message}</p>
}

export function ScheduleFeature({ employeeId }: { employeeId: string }) {
  const { protectedRequest } = useAuth()
  const { selectedBusiness, selectedBusinessId } = useBusinesses()
  const { employees, branches, loading: catalogLoading } = useCatalog()
  const employee = employees.find(item => item.id === employeeId)
  const employeeBranches = branches.filter(branch => employee?.branchIds.includes(branch.id))
  const [branchId, setBranchId] = useState('')
  const [rules, setRules] = useState<WorkingScheduleRule[]>([])
  const [breaks, setBreaks] = useState<Record<string, ScheduleBreak[]>>({})
  const [exceptions, setExceptions] = useState<ScheduleException[]>([])
  const [loading, setLoading] = useState(false)
  const [dataKey, setDataKey] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const [ruleEditor, setRuleEditor] = useState<WorkingScheduleRule | null | undefined>()
  const [breakEditor, setBreakEditor] = useState<{ rule: WorkingScheduleRule; value?: ScheduleBreak } | null>(null)
  const [exceptionEditor, setExceptionEditor] = useState<ScheduleException | null | undefined>()
  const editable = selectedBusiness?.membership.role === 'OWNER' || selectedBusiness?.membership.role === 'ADMIN'

  const expectedKey = selectedBusinessId && employee ? `${selectedBusinessId}:${employee.id}` : null
  const activeBranchId = employeeBranches.some(branch => branch.id === branchId) ? branchId : employeeBranches[0]?.id ?? ''

  useEffect(() => {
    if (!selectedBusinessId || !employee) return
    const loadedEmployeeId = employee.id
    let current = true
    Promise.resolve().then(() => { if (current) setLoading(true) })
      .then(() => fetchSchedule(protectedRequest, selectedBusinessId, loadedEmployeeId))
      .then(next => {
        if (!current) return
        setRules(next.rules); setExceptions(next.exceptions); setBreaks(next.breaks)
        setDataKey(`${selectedBusinessId}:${loadedEmployeeId}`); setError('')
      })
      .catch(cause => { if (current) { setError(apiMessage(cause)); setDataKey(`${selectedBusinessId}:${loadedEmployeeId}`) } })
      .finally(() => { if (current) setLoading(false) })
    return () => { current = false }
  }, [employee, protectedRequest, selectedBusinessId])

  const reload = async () => {
    if (!selectedBusinessId || !employee) return
    const next = await fetchSchedule(protectedRequest, selectedBusinessId, employee.id)
    setRules(next.rules); setExceptions(next.exceptions); setBreaks(next.breaks); setDataKey(`${selectedBusinessId}:${employee.id}`)
  }

  const mutate = async (work: () => Promise<unknown>, close: () => void) => {
    setSaving(true); setError('')
    try { await work(); await reload(); close() }
    catch (cause) { setError(apiMessage(cause)) }
    finally { setSaving(false) }
  }

  if (catalogLoading) return <p>Đang tải employee…</p>
  if (!employee || !selectedBusinessId) return <section><Link href="/dashboard/employees" className="text-sm underline">← Nhân viên</Link><Empty>Không tìm thấy nhân viên trong business hiện tại.</Empty></section>

  const scheduleReady = expectedKey !== null && dataKey === expectedKey
  const branchRules = scheduleReady ? rules.filter(rule => rule.branchId === activeBranchId) : []
  const branchExceptions = scheduleReady ? exceptions.filter(value => value.branchId === activeBranchId) : []
  return <section>
    <Link href="/dashboard/employees" className="flex items-center gap-2 text-sm text-muted-foreground"><ArrowLeft className="size-4" />Nhân viên</Link>
    <div className="mt-5 flex flex-wrap items-end justify-between gap-4">
      <div><p className="text-xs font-bold uppercase tracking-widest text-muted-foreground">Lịch làm việc</p><h1 className="font-serif text-3xl font-semibold">{employee.code} · {employee.fullName}</h1></div>
      <Field label="Chi nhánh"><select className="h-10 min-w-60 rounded-lg border bg-background px-3" value={activeBranchId} onChange={event => setBranchId(event.target.value)}>{employeeBranches.map(branch => <option key={branch.id} value={branch.id}>{branch.name}</option>)}</select></Field>
    </div>
    {!editable && <p className="mt-4 rounded-lg bg-muted p-3 text-sm">STAFF có quyền xem lịch nhưng không thể thay đổi.</p>}
    {error && <div className="mt-4"><Failure message={error} /></div>}
    {!employeeBranches.length ? <Empty>Nhân viên chưa được gán vào chi nhánh active.</Empty> : loading || !scheduleReady ? <p className="mt-8">Đang tải lịch làm việc…</p> : <>
      <div className="mt-8 flex justify-between"><h2 className="font-serif text-2xl font-semibold">Lịch hàng tuần</h2>{editable && <Button onClick={() => setRuleEditor(null)}><Plus />Thêm khung giờ</Button>}</div>
      <div className="mt-5 grid gap-4">{weekdays.map(day => {
        const values = branchRules.filter(rule => rule.weekday === day.value)
        return <article key={day.value} className="rounded-2xl border bg-card p-5"><h3 className="font-semibold">{day.label}</h3>{values.length ? <div className="mt-3 grid gap-3">{values.map(rule => <div key={rule.id} className="rounded-xl bg-muted/60 p-4"><div className="flex flex-wrap items-center justify-between gap-3"><div><b>{rule.startLocalTime.slice(0, 5)} → {rule.endLocalTime.slice(0, 5)}</b><p className="text-xs text-muted-foreground">Từ {rule.effectiveFrom}{rule.effectiveTo ? ` đến ${rule.effectiveTo}` : ''}</p></div>{editable && <div className="flex gap-2"><Button size="sm" variant="outline" onClick={() => setRuleEditor(rule)}>Sửa</Button><Button size="sm" variant="outline" onClick={() => void mutate(() => schedulesApi.deleteRule(protectedRequest, selectedBusinessId, employee.id, rule.id), () => undefined)}>Xóa</Button></div>}</div><div className="mt-3 border-t pt-3"><div className="flex justify-between"><span className="text-sm font-medium">Giờ nghỉ</span>{editable && <button className="text-sm underline" onClick={() => setBreakEditor({ rule })}>+ Thêm giờ nghỉ</button>}</div>{(breaks[rule.id] ?? []).length ? (breaks[rule.id] ?? []).map(item => <div key={item.id} className="mt-2 flex items-center justify-between text-sm"><span>{item.startLocalTime.slice(0, 5)} → {item.endLocalTime.slice(0, 5)}</span>{editable && <span className="flex gap-3"><button className="underline" onClick={() => setBreakEditor({ rule, value: item })}>Sửa</button><button className="underline" onClick={() => void mutate(() => schedulesApi.deleteBreak(protectedRequest, selectedBusinessId, employee.id, rule.id, item.id), () => undefined)}>Xóa</button></span>}</div>) : <p className="mt-2 text-sm text-muted-foreground">Không có giờ nghỉ.</p>}</div></div>)}</div> : <p className="mt-2 text-sm text-muted-foreground">Không có lịch.</p>}</article>
      })}</div>

      <div className="mt-10 flex justify-between"><h2 className="font-serif text-2xl font-semibold">Ngoại lệ</h2>{editable && <Button onClick={() => setExceptionEditor(null)}><Plus />Thêm ngoại lệ</Button>}</div>
      {branchExceptions.length ? <div className="mt-5 grid gap-3">{branchExceptions.map(item => <article key={item.id} className="flex flex-wrap items-center justify-between gap-3 rounded-2xl border bg-card p-5"><div><b>{item.date} · {item.type}</b><p className="text-sm text-muted-foreground">{item.startLocalTime ? `${item.startLocalTime.slice(0, 5)}–${item.endLocalTime?.slice(0, 5)}` : 'Cả ngày'}{item.note ? ` · ${item.note}` : ''}</p></div>{editable && <div className="flex gap-2"><Button size="sm" variant="outline" onClick={() => setExceptionEditor(item)}>Sửa</Button><Button size="sm" variant="outline" onClick={() => void mutate(() => schedulesApi.deleteException(protectedRequest, selectedBusinessId, employee.id, item.id), () => undefined)}>Xóa</Button></div>}</article>)}</div> : <Empty>Chi nhánh này chưa có ngoại lệ.</Empty>}
    </>}

    {ruleEditor !== undefined && <WorkingRuleForm initial={ruleEditor ?? undefined} branchId={activeBranchId} saving={saving} apiError={error} close={() => setRuleEditor(undefined)} save={body => mutate(() => ruleEditor ? schedulesApi.updateRule(protectedRequest, selectedBusinessId, employee.id, ruleEditor.id, body) : schedulesApi.createRule(protectedRequest, selectedBusinessId, employee.id, body), () => setRuleEditor(undefined))} />}
    {breakEditor && <BreakForm initial={breakEditor.value} saving={saving} apiError={error} close={() => setBreakEditor(null)} save={body => mutate(() => breakEditor.value ? schedulesApi.updateBreak(protectedRequest, selectedBusinessId, employee.id, breakEditor.rule.id, breakEditor.value.id, body) : schedulesApi.createBreak(protectedRequest, selectedBusinessId, employee.id, breakEditor.rule.id, body), () => setBreakEditor(null))} />}
    {exceptionEditor !== undefined && <ExceptionForm initial={exceptionEditor ?? undefined} branchId={activeBranchId} saving={saving} apiError={error} close={() => setExceptionEditor(undefined)} save={body => mutate(() => exceptionEditor ? schedulesApi.updateException(protectedRequest, selectedBusinessId, employee.id, exceptionEditor.id, body) : schedulesApi.createException(protectedRequest, selectedBusinessId, employee.id, body), () => setExceptionEditor(undefined))} />}
  </section>
}

function WorkingRuleForm({ initial, branchId, saving, apiError, close, save }: { initial?: WorkingScheduleRule; branchId: string; saving: boolean; apiError: string; close: () => void; save: (body: WorkingRuleInput) => Promise<void> }) {
  const [form, setForm] = useState({ weekday: initial?.weekday ?? 'MONDAY' as Weekday, start: initial?.startLocalTime.slice(0, 5) ?? '', end: initial?.endLocalTime.slice(0, 5) ?? '', from: initial?.effectiveFrom ?? '', to: initial?.effectiveTo ?? '' })
  const [validation, setValidation] = useState('')
  const submit = async (event: FormEvent) => { event.preventDefault(); let next = ''; if (!form.start || !form.end || !form.from) next = 'Vui lòng nhập đầy đủ ngày và giờ bắt buộc.'; else if (form.start >= form.end) next = 'Giờ bắt đầu phải trước giờ kết thúc.'; else if (form.to && form.from > form.to) next = 'Ngày hiệu lực bắt đầu phải trước ngày kết thúc.'; setValidation(next); if (next) return; await save({ branchId, weekday: form.weekday, startLocalTime: form.start, endLocalTime: form.end, effectiveFrom: form.from, effectiveTo: form.to || null }) }
  return <Modal title={initial ? 'Sửa khung giờ' : 'Thêm khung giờ'} close={close}><form noValidate className="mt-5 grid gap-4" onSubmit={submit}>{apiError && <Failure message={apiError} />}{validation && <Failure message={validation} />}<Field label="Ngày"><select className="h-10 rounded-lg border bg-background px-3" value={form.weekday} onChange={event => setForm({ ...form, weekday: event.target.value as Weekday })}>{weekdays.map(day => <option key={day.value} value={day.value}>{day.label}</option>)}</select></Field><div className="grid grid-cols-2 gap-3"><Field label="Bắt đầu"><Input type="time" value={form.start} onChange={event => setForm({ ...form, start: event.target.value })} /></Field><Field label="Kết thúc"><Input type="time" value={form.end} onChange={event => setForm({ ...form, end: event.target.value })} /></Field></div><div className="grid grid-cols-2 gap-3"><Field label="Hiệu lực từ"><Input type="date" value={form.from} onChange={event => setForm({ ...form, from: event.target.value })} /></Field><Field label="Hiệu lực đến"><Input type="date" value={form.to} onChange={event => setForm({ ...form, to: event.target.value })} /></Field></div><Button type="submit" disabled={saving}>{saving ? 'Đang lưu…' : 'Lưu'}</Button></form></Modal>
}

function BreakForm({ initial, saving, apiError, close, save }: { initial?: ScheduleBreak; saving: boolean; apiError: string; close: () => void; save: (body: ScheduleBreakInput) => Promise<void> }) {
  const [start, setStart] = useState(initial?.startLocalTime.slice(0, 5) ?? '')
  const [end, setEnd] = useState(initial?.endLocalTime.slice(0, 5) ?? '')
  const [validation, setValidation] = useState('')
  const submit = async (event: FormEvent) => { event.preventDefault(); const next = !start || !end ? 'Vui lòng nhập đầy đủ giờ nghỉ.' : start >= end ? 'Giờ bắt đầu phải trước giờ kết thúc.' : ''; setValidation(next); if (!next) await save({ startLocalTime: start, endLocalTime: end }) }
  return <Modal title={initial ? 'Sửa giờ nghỉ' : 'Thêm giờ nghỉ'} close={close}><form noValidate className="mt-5 grid gap-4" onSubmit={submit}>{apiError && <Failure message={apiError} />}{validation && <Failure message={validation} />}<Field label="Bắt đầu"><Input type="time" value={start} onChange={event => setStart(event.target.value)} /></Field><Field label="Kết thúc"><Input type="time" value={end} onChange={event => setEnd(event.target.value)} /></Field><Button type="submit" disabled={saving}>{saving ? 'Đang lưu…' : 'Lưu'}</Button></form></Modal>
}

function ExceptionForm({ initial, branchId, saving, apiError, close, save }: { initial?: ScheduleException; branchId: string; saving: boolean; apiError: string; close: () => void; save: (body: ScheduleExceptionInput) => Promise<void> }) {
  const [form, setForm] = useState({ date: initial?.date ?? '', type: initial?.type ?? 'TIME_OFF' as ScheduleExceptionType, fullDay: initial?.type === 'TIME_OFF' && !initial.startLocalTime, start: initial?.startLocalTime?.slice(0, 5) ?? '', end: initial?.endLocalTime?.slice(0, 5) ?? '', note: initial?.note ?? '' })
  const [validation, setValidation] = useState('')
  const submit = async (event: FormEvent) => { event.preventDefault(); const needsTime = form.type === 'WORKING_OVERRIDE' || !form.fullDay; let next = !form.date ? 'Ngày ngoại lệ là bắt buộc.' : ''; if (!next && needsTime && (!form.start || !form.end)) next = 'Khoảng giờ là bắt buộc.'; if (!next && needsTime && form.start >= form.end) next = 'Giờ bắt đầu phải trước giờ kết thúc.'; setValidation(next); if (next) return; await save({ branchId, date: form.date, type: form.type, startLocalTime: needsTime ? form.start : null, endLocalTime: needsTime ? form.end : null, note: form.note.trim() || null }) }
  return <Modal title={initial ? 'Sửa ngoại lệ' : 'Thêm ngoại lệ'} close={close}><form noValidate className="mt-5 grid gap-4" onSubmit={submit}>{apiError && <Failure message={apiError} />}{validation && <Failure message={validation} />}<Field label="Ngày"><Input type="date" value={form.date} onChange={event => setForm({ ...form, date: event.target.value })} /></Field><Field label="Loại"><select className="h-10 rounded-lg border bg-background px-3" value={form.type} onChange={event => { const type = event.target.value as ScheduleExceptionType; setForm({ ...form, type, fullDay: type === 'TIME_OFF' && form.fullDay }) }}><option value="TIME_OFF">TIME_OFF</option><option value="WORKING_OVERRIDE">WORKING_OVERRIDE</option></select></Field>{form.type === 'TIME_OFF' && <label className="flex gap-2 text-sm"><input type="checkbox" checked={form.fullDay} onChange={event => setForm({ ...form, fullDay: event.target.checked })} />Nghỉ cả ngày</label>}{(form.type === 'WORKING_OVERRIDE' || !form.fullDay) && <div className="grid grid-cols-2 gap-3"><Field label="Bắt đầu"><Input type="time" value={form.start} onChange={event => setForm({ ...form, start: event.target.value })} /></Field><Field label="Kết thúc"><Input type="time" value={form.end} onChange={event => setForm({ ...form, end: event.target.value })} /></Field></div>}<Field label="Ghi chú"><Textarea value={form.note} onChange={event => setForm({ ...form, note: event.target.value })} /></Field><Button type="submit" disabled={saving}>{saving ? 'Đang lưu…' : 'Lưu'}</Button></form></Modal>
}
