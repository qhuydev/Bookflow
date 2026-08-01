import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "BookFlow",
  description: "Scheduling platform for service businesses",
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="vi">
      <body>{children}</body>
    </html>
  );
}
