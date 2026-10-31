import type { Metadata } from "next";
import { Be_Vietnam_Pro } from "next/font/google";
import "@/styles/globals.css";

const vietnam = Be_Vietnam_Pro({
  weight: ["400", "500", "600", "700"], subsets: ["vietnamese"],
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
