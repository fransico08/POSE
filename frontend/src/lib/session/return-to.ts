export function safeReturnTo(value: string | null | undefined): string {
  if (!value?.startsWith("/") || value.startsWith("//") || /[\\\u0000-\u0020\u007f]/u.test(value)) return "/app";
  try {
    const decoded = decodeURIComponent(value);
    if (decoded.startsWith("//") || /[\\\u0000-\u0020\u007f]/u.test(decoded)) return "/app";
    const url = new URL(value, "https://pose.invalid");
    if (url.origin !== "https://pose.invalid" || !/^\/app(?:\/|$)/u.test(url.pathname)) return "/app";
    return `${url.pathname}${url.search}${url.hash}`;
  } catch { return "/app"; }
}
