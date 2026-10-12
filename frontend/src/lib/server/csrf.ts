export function sameOriginMutation(request: Request): boolean {
  if (["GET", "HEAD", "OPTIONS"].includes(request.method)) return true;
  const origin = request.headers.get("origin");
  if (origin !== null) {
    const url = new URL(request.url), host = request.headers.get("host");
    // Next's loopback URL can use localhost while the browser requests 127.0.0.1.
    // Host is the browser's actual destination, never taken from the Origin header.
    if (host) {
      if (!/^[a-zA-Z0-9.:[\]-]+$/u.test(host)) return false;
      url.host = host;
    }
    return origin === url.origin;
  }
  return request.headers.get("sec-fetch-site") === "same-origin";
}
