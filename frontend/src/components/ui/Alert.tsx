import { ExclamationCircleIcon, InformationCircleIcon } from "@heroicons/react/20/solid";
export function Alert({ children, tone = "danger" }: { children: React.ReactNode; tone?: "danger" | "info" }) {
  const Icon = tone === "danger" ? ExclamationCircleIcon : InformationCircleIcon;
  return <p className={`alert alert-${tone}`} role={tone === "danger" ? "alert" : "status"}><Icon aria-hidden="true" />{children}</p>;
}
