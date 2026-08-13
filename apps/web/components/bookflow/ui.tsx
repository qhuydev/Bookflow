'use client'

import { ReactNode } from 'react'
import { Badge } from '@/components/ui/badge'

export const Input = (props: React.InputHTMLAttributes<HTMLInputElement>) => <input {...props} className="h-10 w-full rounded-lg border border-border bg-background px-3 text-sm" />
export const Textarea = (props: React.TextareaHTMLAttributes<HTMLTextAreaElement>) => <textarea {...props} className="min-h-24 w-full rounded-lg border border-border bg-background p-3 text-sm" />
export function Field({ label, error, children }: { label: string; error?: string; children: ReactNode }) { return <label className="grid gap-1.5 text-sm font-medium">{label}{children}{error && <span className="text-xs font-normal text-destructive">{error}</span>}</label> }
export function StateBadge({ status }: { status: string }) { return <Badge variant={status === 'ACTIVE' ? 'secondary' : 'outline'}>{status === 'ACTIVE' ? 'Đang hoạt động' : status === 'ARCHIVED' ? 'Đã lưu trữ' : 'Đã thu hồi'}</Badge> }
export function Empty({ children }: { children: ReactNode }) { return <p className="rounded-xl border border-dashed border-border p-6 text-center text-sm text-muted-foreground">{children}</p> }
