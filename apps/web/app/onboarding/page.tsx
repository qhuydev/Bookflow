'use client'

import { FormEvent, useEffect, useState } from 'react'
import { useRouter } from 'next/navigation'
import { Button } from '@/components/ui/button'
import { ApiError, useAuth } from '@/components/bookflow/auth-provider'
import { useBusinesses } from '@/components/bookflow/business-provider'
import { Field, Input } from '@/components/bookflow/ui'

export default function Page() {
  const router = useRouter()
  const { authenticated, loading: authLoading } = useAuth()
  const { createBusiness } = useBusinesses()
  const [form, setForm] = useState({ name: '', slug: '', type: 'SALON', timeZone: 'Asia/Ho_Chi_Minh' })
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')

  useEffect(() => {
    if (!authLoading && !authenticated) router.replace('/login')
  }, [authLoading, authenticated, router])

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    setError('')
    const normalized = { ...form, name: form.name.trim(), slug: form.slug.trim().toLowerCase(), timeZone: form.timeZone.trim() }
    if (!normalized.name) { setError('Tên business là bắt buộc.'); return }
    if (!/^[a-z0-9]+(?:-[a-z0-9]+)*$/.test(normalized.slug)) { setError('Slug chỉ gồm chữ thường, số và dấu gạch ngang.'); return }
    try { new Intl.DateTimeFormat('en-US', { timeZone: normalized.timeZone }).format() } catch { setError('Múi giờ IANA không hợp lệ.'); return }
    setSaving(true)
    try {
      await createBusiness(normalized)
      router.push('/dashboard')
    } catch (cause) {
      if (cause instanceof ApiError && cause.problem.status === 409) setError('Slug này đã được sử dụng. Hãy chọn slug khác.')
      else if (cause instanceof ApiError) setError(cause.problem.detail ?? 'Dữ liệu business không hợp lệ.')
      else setError('Không thể tạo business lúc này.')
    } finally {
      setSaving(false)
    }
  }

  if (authLoading || !authenticated) return <main className="grid min-h-screen place-items-center">Đang kiểm tra phiên…</main>
  return <main className="min-h-screen bg-muted p-6 md:p-12"><section className="mx-auto max-w-3xl rounded-3xl border border-border bg-card p-8"><p className="text-xs font-bold uppercase tracking-widest text-muted-foreground">Onboarding · Business</p><h1 className="mt-4 font-serif text-4xl font-semibold">Thiết lập business đầu tiên.</h1><p className="mt-3 text-sm text-muted-foreground">Business sẽ được lưu vào BookFlow. Chi nhánh, nhân viên và dịch vụ sẽ được thiết lập sau.</p>{error && <p className="mt-5 rounded-lg bg-destructive/10 p-3 text-sm text-destructive">{error}</p>}<form className="mt-7 grid gap-4" noValidate onSubmit={submit}><Field label="Tên business"><Input value={form.name} onChange={event => setForm({ ...form, name: event.target.value })} /></Field><Field label="Slug"><Input value={form.slug} onChange={event => setForm({ ...form, slug: event.target.value })} placeholder="an-nhien-wellness" /></Field><Field label="Loại hình"><select value={form.type} onChange={event => setForm({ ...form, type: event.target.value })} className="h-10 rounded-lg border border-border bg-background px-3"><option value="SALON">Salon</option><option value="SPA">Spa</option><option value="CLINIC">Phòng khám</option><option value="TUTORING_CENTER">Trung tâm gia sư</option><option value="STUDIO">Studio</option><option value="OTHER">Khác</option></select></Field><Field label="Múi giờ"><Input value={form.timeZone} onChange={event => setForm({ ...form, timeZone: event.target.value })} /></Field><Button disabled={saving} type="submit">{saving ? 'Đang tạo…' : 'Tạo business'}</Button></form></section></main>
}
