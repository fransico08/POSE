/** Read only from server-rendered metadata and metadata routes. */
export function getSiteUrl(): URL {
  const url = new URL(process.env.SITE_URL ?? "http://127.0.0.1:3000");
  if (!["http:", "https:"].includes(url.protocol)) throw new Error("SITE_URL must use HTTP or HTTPS");
  return new URL("/", url);
}
