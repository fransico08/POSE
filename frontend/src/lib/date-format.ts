const ISO_DATE_PATTERN = /^(\d{4})-(\d{2})-(\d{2})$/;
const DISPLAY_DATE_PATTERN = /^(\d{2})\/(\d{2})\/(\d{4})$/;
const LOCAL_DATE_TIME_PATTERN = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})$/;
const DISPLAY_DATE_TIME_PATTERN = /^(\d{2})\/(\d{2})\/(\d{4}) (\d{2}):(\d{2})$/;

function isValidDate(year: number, month: number, day: number) {
  const date = new Date(0);
  date.setUTCHours(0, 0, 0, 0);
  date.setUTCFullYear(year, month - 1, day);
  return date.getUTCFullYear() === year && date.getUTCMonth() === month - 1 && date.getUTCDate() === day;
}

export function parseDdMmYyyy(value: string): string | null {
  const match = DISPLAY_DATE_PATTERN.exec(value.trim());
  if (!match) return null;

  const [, dayText, monthText, yearText] = match;
  const day = Number(dayText);
  const month = Number(monthText);
  const year = Number(yearText);
  if (!isValidDate(year, month, day)) return null;

  return `${yearText}-${monthText}-${dayText}`;
}

export function formatIsoDateAsDdMmYyyy(value: string): string {
  const match = ISO_DATE_PATTERN.exec(value);
  if (!match) return value;

  const [, yearText, monthText, dayText] = match;
  if (!isValidDate(Number(yearText), Number(monthText), Number(dayText))) return value;
  return `${dayText}/${monthText}/${yearText}`;
}

export function parseDdMmYyyyHhMm(value: string): string | null {
  const match = DISPLAY_DATE_TIME_PATTERN.exec(value.trim());
  if (!match) return null;

  const [, dayText, monthText, yearText, hourText, minuteText] = match;
  const day = Number(dayText);
  const month = Number(monthText);
  const year = Number(yearText);
  const hour = Number(hourText);
  const minute = Number(minuteText);
  if (!isValidDate(year, month, day) || hour > 23 || minute > 59) return null;

  return `${yearText}-${monthText}-${dayText}T${hourText}:${minuteText}`;
}

export function formatLocalDateTimeAsDdMmYyyyHhMm(value: string): string {
  const match = LOCAL_DATE_TIME_PATTERN.exec(value);
  if (!match) return value;

  const [, yearText, monthText, dayText, hourText, minuteText] = match;
  if (!isValidDate(Number(yearText), Number(monthText), Number(dayText)) || Number(hourText) > 23 || Number(minuteText) > 59) return value;
  return `${dayText}/${monthText}/${yearText} ${hourText}:${minuteText}`;
}

export function formatInstantAsDdMmYyyyHhMm(value: string): string {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "—";

  const day = String(date.getDate()).padStart(2, "0");
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const year = String(date.getFullYear()).padStart(4, "0");
  const hour = String(date.getHours()).padStart(2, "0");
  const minute = String(date.getMinutes()).padStart(2, "0");
  return `${day}/${month}/${year} ${hour}:${minute}`;
}
