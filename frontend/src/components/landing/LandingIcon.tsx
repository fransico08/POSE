import { AcademicCapIcon, ArrowUpTrayIcon, ArrowsRightLeftIcon, ClipboardDocumentCheckIcon, ComputerDesktopIcon, CubeIcon, HeartIcon, HomeModernIcon, IdentificationIcon, KeyIcon, ShoppingBagIcon, TruckIcon } from "@heroicons/react/24/outline";
import type { LandingIconName } from "@/content/landing";
const icons = { AcademicCapIcon, ArrowUpTrayIcon, ArrowsRightLeftIcon, ClipboardDocumentCheckIcon, ComputerDesktopIcon, CubeIcon, HeartIcon, HomeModernIcon, IdentificationIcon, KeyIcon, ShoppingBagIcon, TruckIcon };
export function LandingIcon({ name }: { name: LandingIconName }) { const Icon = icons[name]; return <Icon aria-hidden="true" />; }
