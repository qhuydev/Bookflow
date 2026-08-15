'use client'

import { FormEvent, ReactNode, useRef, useState } from 'react'
import { Button } from '@/components/ui/button'
import { ApiError } from '@/lib/api-client'
import { bookingApi } from '@/lib/api/bookings'
import { AvailabilitySlot, CreateBookingInput, CreatedBooking, PublicService } from '@/lib/api/contracts'
import { Field, Input } from './ui'

type Props = {
  slug: string
  branchId: string
  service: PublicService
  employeeId?: string
  employeeName: (employeeId: string) => string
  slot?: AvailabilitySlot
  timeZone: string
  onCreated: () => void
  onSlotUnavailable: () => void
}

type FormErrors = Partial<Record<'name' | 'email' | 'phone' | 'slot', string>>

export function PublicBookingForm({ slug, branchId, service, employeeId, employeeName, slot,
  timeZone, onCreated, onSlotUnavailable }: Props) {
  const [name, setName] = useState('')
  const [email, setEmail] = useState('')
  const [phone, setPhone] = useState('')
  const [errors, setErrors] = useState<FormErrors>({})
  const [apiError, setApiError] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [created, setCreated] = useState<CreatedBooking | null>(null)
  const logicalSubmit = useRef<{ payload: string; key: string } | null>(null)
  const submitInFlight = useRef(false)

  const validate = () => {
    const next: FormErrors = {}
    const cleanName = name.trim()
    const cleanEmail = email.trim()
    const cleanPhone = phone.trim()
    if (!slot) next.slot = 'Vui lòng chọn lại một khung giờ còn trống.'
    if (!cleanName) next.name = 'Vui lòng nhập họ tên.'
    else if (cleanName.length > 200) next.name = 'Họ tên không được vượt quá 200 ký tự.'
    if (!cleanEmail && !cleanPhone) next.email = 'Cần ít nhất email hoặc số điện thoại.'
    if (cleanEmail && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(cleanEmail)) next.email = 'Email không hợp lệ.'
    if (cleanPhone && !/^[+()0-9 .-]{7,30}$/.test(cleanPhone)) next.phone = 'Số điện thoại không hợp lệ.'
    setErrors(next)
    return Object.keys(next).length === 0
  }

  const submit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (submitInFlight.current || created || !validate() || !slot) return
    setApiError('')
    const input: CreateBookingInput = {
      branchId,
      serviceId: service.id,
      ...(employeeId ? { employeeId } : {}),
      start: slot.start,
      customer: {
        name: name.trim(),
        ...(email.trim() ? { email: email.trim() } : {}),
        ...(phone.trim() ? { phone: phone.trim() } : {}),
      },
    }
    const payload = JSON.stringify(input)
    if (!logicalSubmit.current || logicalSubmit.current.payload !== payload) {
      logicalSubmit.current = { payload, key: `booking-${crypto.randomUUID()}` }
    }
    submitInFlight.current = true
    setSubmitting(true)
    try {
      const result = await bookingApi.create(slug, input, logicalSubmit.current.key)
      setCreated(result)
      onCreated()
    } catch (cause) {
      if (cause instanceof ApiError) {
        if (cause.problem.code === 'SLOT_UNAVAILABLE') {
          setApiError('Khung giờ vừa được người khác đặt. Lịch trống đã được cập nhật; vui lòng chọn giờ khác.')
          onSlotUnavailable()
        } else if (cause.problem.code === 'IDEMPOTENCY_KEY_REUSED') {
          setApiError('Nội dung lần thử lại đã thay đổi. Vui lòng kiểm tra thông tin và gửi lại.')
        } else {
          setApiError(cause.problem.detail ?? cause.problem.title ?? 'Không thể tạo booking.')
        }
      } else {
        setApiError('Không thể kết nối dịch vụ đặt lịch. Bạn có thể thử lại mà không tạo booking trùng.')
      }
    } finally {
      submitInFlight.current = false
      setSubmitting(false)
    }
  }

  if (created) {
    return <section aria-live="polite" className="mt-6 rounded-2xl border border-primary/30 bg-primary/5 p-5">
      <h3 className="font-serif text-2xl font-semibold">Đặt lịch thành công</h3>
      <dl className="mt-4 grid gap-2 text-sm md:grid-cols-2">
        <div><dt className="text-muted-foreground">Mã booking</dt><dd className="font-mono">{created.bookingId}</dd></div>
        <div><dt className="text-muted-foreground">Trạng thái</dt><dd>{created.status}</dd></div>
        <div><dt className="text-muted-foreground">Dịch vụ</dt><dd>{created.items[0]?.name ?? service.name}</dd></div>
        <div><dt className="text-muted-foreground">Nhân viên</dt><dd>{employeeName(created.employeeId)}</dd></div>
        <div><dt className="text-muted-foreground">Thời gian</dt><dd>{formatDateTime(created.start, timeZone)}</dd></div>
        <div><dt className="text-muted-foreground">Tổng tiền</dt><dd>{new Intl.NumberFormat('vi-VN').format(Number(created.totalAmount))} {created.currency}</dd></div>
        <div className="md:col-span-2"><dt className="text-muted-foreground">Giữ chỗ đến</dt><dd>{formatDateTime(created.expiresAt, timeZone)}</dd></div>
      </dl>
    </section>
  }

  return <form noValidate onSubmit={submit} className="mt-6 rounded-2xl border bg-background p-5">
    <h3 className="font-serif text-2xl font-semibold">Thông tin đặt lịch</h3>
    <p className="mt-1 text-sm text-muted-foreground">{slot ? `${formatDateTime(slot.start, timeZone)} · ${employeeId ? 'Nhân viên đã chọn' : 'Tự động chọn nhân viên phù hợp'}` : 'Khung giờ cũ không còn trống. Vui lòng chọn giờ khác.'}</p>
    <div className="mt-4 grid gap-4 md:grid-cols-3">
      <Field label="Họ tên"><Input value={name} onChange={event => setName(event.target.value)} aria-invalid={Boolean(errors.name)} />{errors.name && <FieldError>{errors.name}</FieldError>}</Field>
      <Field label="Email"><Input type="email" value={email} onChange={event => setEmail(event.target.value)} aria-invalid={Boolean(errors.email)} />{errors.email && <FieldError>{errors.email}</FieldError>}</Field>
      <Field label="Số điện thoại"><Input value={phone} onChange={event => setPhone(event.target.value)} aria-invalid={Boolean(errors.phone)} />{errors.phone && <FieldError>{errors.phone}</FieldError>}</Field>
    </div>
    {errors.slot && <FieldError className="mt-3">{errors.slot}</FieldError>}
    {apiError && <p role="alert" className="mt-4 rounded-lg bg-destructive/10 p-3 text-sm text-destructive">{apiError}</p>}
    <Button type="submit" disabled={submitting || !slot} className="mt-5">{submitting ? 'Đang đặt lịch…' : 'Đặt lịch'}</Button>
  </form>
}

function FieldError({ children, className = '' }: { children: ReactNode; className?: string }) {
  return <p role="alert" className={`${className} mt-1 text-xs text-destructive`}>{children}</p>
}

function formatDateTime(value: string, timeZone: string) {
  return new Intl.DateTimeFormat('vi-VN', { dateStyle: 'medium', timeStyle: 'short', timeZone }).format(new Date(value))
}
