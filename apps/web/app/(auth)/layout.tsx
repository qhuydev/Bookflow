import { ReactNode } from 'react'

export default function AuthLayout({ children }: { children: ReactNode }) {
  return <div data-layout="auth">{children}</div>
}
