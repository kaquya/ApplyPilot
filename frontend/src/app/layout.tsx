import type { Metadata } from 'next';
import './globals.css';
export const metadata: Metadata = {
  title: 'ApplyPilot — Your next chapter',
  description:
    'Your Swiss job search, thoughtfully organised. Applications, documents and your next step in one place.',
};
export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en">
      <body>{children}</body>
    </html>
  );
}
