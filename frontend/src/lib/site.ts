/** Read only from server-rendered metadata and metadata routes. */
let warned = false;
export function getSiteUrl(): URL {
  if (process.env.NODE_ENV === "production" && !process.env.SITE_URL && !warned) {
    console.warn("POSE CRM: SITE_URL is missing in production. Set the public origin before deployment; using the local fallback.");
    warned = true;
  }
  const url = new URL(process.env.SITE_URL ?? "http://127.0.0.1:3000");
  if (!["http:", "https:"].includes(url.protocol)) throw new Error("SITE_URL must use HTTP or HTTPS");
  return new URL("/", url);
}
