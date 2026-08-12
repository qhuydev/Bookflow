import { Analytics } from '@vercel/analytics/next'
import { Noto_Sans, Noto_Serif } from 'next/font/google'
import type { Metadata, Viewport } from 'next'
import './globals.css'

const notoSans = Noto_Sans({ subsets: ['latin', 'vietnamese'], variable: '--font-noto-sans' })
const notoSerif = Noto_Serif({ subsets: ['latin', 'vietnamese'], variable: '--font-noto-serif' })

export const metadata: Metadata = {
  title: 'BookFlow — Quản lý tiệm sách tinh gọn',
  description: 'BookFlow giúp tiệm sách quản lý cửa hàng, thành viên và dịch vụ từ một nơi duy nhất.',
  generator: 'BookFlow',
  icons: {
    icon: [
      {
        url: '/icon-light-32x32.png',
        media: '(prefers-color-scheme: light)',
      },
      {
        url: '/icon-dark-32x32.png',
        media: '(prefers-color-scheme: dark)',
      },
      {
        url: '/icon.svg',
        type: 'image/svg+xml',
      },
    ],
    apple: '/apple-icon.png',
  },
}

export const viewport: Viewport = {
  colorScheme: 'light',
  themeColor: '#f5f7f4',
}

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode
}>) {
  return (
    <html lang="vi" className="bg-background">
      <body className={`${notoSans.variable} ${notoSerif.variable} antialiased`}>
        {children}
        {process.env.NODE_ENV === 'production' && <Analytics />}
      </body>
    </html>
  )
}
