'use client'
import Link from 'next/link'
import { FormEvent, useState } from 'react'
import { useRouter } from 'next/navigation'
import { Button } from '@/components/ui/button'
import { ApiError, useAuth } from './auth-provider'
import { Field, Input } from './ui'
export function AuthScreen({ mode }: { mode: 'login' | 'register' | 'forgot' | 'reset' }) {
  const router = useRouter(); const auth = useAuth(); const [email, setEmail] = useState(''); const [password, setPassword] = useState(''); const [token, setToken] = useState(''); const [error, setError] = useState('')
  const title = mode === 'login' ? 'Đăng nhập' : mode === 'register' ? 'Tạo tài khoản' : mode === 'forgot' ? 'Khôi phục mật khẩu' : 'Đặt mật khẩu mới'
  const submit = async (event: FormEvent) => {
    event.preventDefault(); setError('')
    const normalizedEmail = email.trim().toLowerCase()
    if (mode !== 'reset' && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(normalizedEmail)) { setError('Email không hợp lệ.'); return }
    if (mode === 'reset' && !token.trim()) { setError('Reset token là bắt buộc.'); return }
    if (mode === 'login' && !password) { setError('Mật khẩu là bắt buộc.'); return }
    if ((mode === 'register' || mode === 'reset') && (password.length < 12 || password.length > 128 || !/[A-Z]/.test(password) || !/[a-z]/.test(password) || !/[0-9]/.test(password) || !/[^A-Za-z0-9]/.test(password))) { setError('Mật khẩu phải dài 12–128 ký tự và có chữ hoa, chữ thường, số, ký tự đặc biệt.'); return }
    try {
      if (mode === 'login') { await auth.login(normalizedEmail, password); router.push('/dashboard') }
      else if (mode === 'register') { await auth.register(normalizedEmail, password); router.push('/login') }
      else if (mode === 'forgot') await auth.forgotPassword(normalizedEmail)
      else await auth.resetPassword(token.trim(), password)
    } catch (value) { setError(value instanceof ApiError ? value.message : 'Không thể kết nối authentication service.') }
  }
  return <main className="grid min-h-screen place-items-center bg-muted p-5"><section className="w-full max-w-md rounded-3xl border border-border bg-card p-7"><Link href="/an-nhien-wellness" className="text-sm text-muted-foreground">← Public Catalog</Link><h1 className="mt-8 font-serif text-3xl font-semibold">{title}</h1>{error && <p className="mt-4 rounded-lg bg-destructive/10 p-3 text-sm text-destructive">{error}</p>}<form className="mt-6 grid gap-4" noValidate onSubmit={submit}>{mode !== 'reset' && <Field label="Email"><Input type="email" value={email} onChange={event => setEmail(event.target.value)} /></Field>}{mode === 'reset' && <Field label="Reset token"><Input value={token} onChange={event => setToken(event.target.value)} /></Field>}{mode !== 'forgot' && <Field label={mode === 'reset' ? 'Mật khẩu mới' : 'Mật khẩu'}><Input type="password" value={password} onChange={event => setPassword(event.target.value)} /></Field>}<Button type="submit">{mode === 'login' ? 'Đăng nhập' : 'Tiếp tục'}</Button></form></section></main>
}
