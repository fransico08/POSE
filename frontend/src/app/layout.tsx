import type { Metadata } from "next";
import localFont from "next/font/local";
import "@/styles/globals.css";

const vietnam = localFont({
  src: [
    { path: "../fonts/be-vietnam-pro/BeVietnamPro-Regular.ttf", weight: "400", style: "normal" },
    { path: "../fonts/be-vietnam-pro/BeVietnamPro-Medium.ttf", weight: "500", style: "normal" },
    { path: "../fonts/be-vietnam-pro/BeVietnamPro-SemiBold.ttf", weight: "600", style: "normal" },
    { path: "../fonts/be-vietnam-pro/BeVietnamPro-Bold.ttf", weight: "700", style: "normal" },
  ],
  display: "swap", variable: "--font-be-vietnam-pro", adjustFontFallback: false, fallback: [],
});

export const metadata: Metadata = {
  title: { default: "POSE CRM", template: "%s | POSE CRM" },
  description: "Không gian làm việc CRM nội bộ đa ngành.",
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  // Browser extensions add attributes to <html> before hydration; this only silences that one element's attributes.
  return <html lang="vi" className={vietnam.variable} suppressHydrationWarning><body>{children}</body></html>;
}
