import { parseDdMmYyyyHhMm } from "@/lib/date-format";

export function vietnamDateTimeToInstant(value: string): string | null {
  const local = parseDdMmYyyyHhMm(value);
  return local ? new Date(`${local}:00+07:00`).toISOString() : null;
}

export function instantToVietnamDateTime(value: string): string {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "";
  const parts = new Intl.DateTimeFormat("en-GB", { timeZone: "Asia/Ho_Chi_Minh", year: "numeric", month: "2-digit", day: "2-digit", hour: "2-digit", minute: "2-digit", hourCycle: "h23" }).formatToParts(date);
  const get = (type: string) => parts.find(part => part.type === type)?.value ?? "";
  return `${get("day")}/${get("month")}/${get("year")} ${get("hour")}:${get("minute")}`;
}
