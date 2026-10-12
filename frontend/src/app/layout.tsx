import type { Metadata } from "next";
import { Poppins, Be_Vietnam_Pro } from "next/font/google";
import "@/styles/globals.css";

const poppins = Poppins({
  weight: ["400", "500", "600", "700"], subsets: ["latin", "latin-ext"],
  display: "swap", variable: "--font-poppins", adjustFontFallback: false, fallback: [],
});
const vietnam = Be_Vietnam_Pro({
  weight: ["400", "500", "600", "700"], subsets: ["vietnamese"],
  display: "swap", variable: "--font-be-vietnam-pro", adjustFontFallback: false, fallback: [],
});

export const metadata: Metadata = {
  title: { default: "POSE CRM", template: "%s | POSE CRM" },
  description: "Không gian làm việc CRM nội bộ đa ngành.",
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return <html lang="vi" className={`${poppins.variable} ${vietnam.variable}`}><body>{children}</body></html>;
}
