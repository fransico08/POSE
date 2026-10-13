import type { ApiErrorShape } from "@/lib/session/types";
export class ApiError extends Error {
  constructor(public status: number, public code: string, message: string) { super(message); }
}
interface Dependencies {
  fetcher: typeof fetch;
  navigate: (path: string) => void;
  onExpired: () => void | Promise<void>;
  locks?: { request: (name: string, callback: () => Promise<void>) => Promise<void> };
}
export function createApiClient({ fetcher, navigate, onExpired, locks }: Dependencies) {
  let refreshFlight: Promise<void> | undefined, expiration: Promise<void> | undefined;
  const networkError = () => new ApiError(503, "NETWORK_ERROR", "Không thể kết nối. Vui lòng thử lại.");
  const raw = (url: string, init?: RequestInit) => fetcher(url, { ...init, credentials: "same-origin", cache: "no-store", signal: init?.signal ?? AbortSignal.timeout(15000) });
  function expire() {
    expiration ??= (async () => {
      await onExpired();
      try { await raw("/api/session/logout", { method: "POST", signal: AbortSignal.timeout(5000) }); } catch { /* Navigation still clears client state. */ }
      navigate("/dang-nhap?reason=expired");
    })();
    return expiration;
  }
  function refresh() {
    if (!refreshFlight) {
      const rotate = async () => {
        const response = await raw("/api/session/refresh", { method: "POST" });
        if (response.ok) return;
        const error = await response.json().catch(() => null);
        if (response.status === 401 && error?.code === "AUTH_INVALID_REFRESH_TOKEN") throw new ApiError(401, "AUTH_INVALID_REFRESH_TOKEN", "Phiên làm việc đã hết hạn.");
        throw networkError();
      };
      const operation = locks ? locks.request("pose-session-refresh", async () => {
        // The previous tab may already have rotated both cookies while this tab waited.
        const current = await raw("/api/session/me");
        if (current.ok) return;
        if (current.status !== 401) throw networkError();
        const error = await current.json().catch(() => null);
        if (error?.code !== "AUTH_TOKEN_EXPIRED") throw new ApiError(401, "AUTH_INVALID_TOKEN", "Phiên không hợp lệ.");
        await rotate();
      }) : rotate();
      refreshFlight = operation.catch(async error => {
        if (error instanceof ApiError && error.status === 401) { await expire(); throw error; }
        throw networkError();
      }).finally(() => { refreshFlight = undefined; });
    }
    return refreshFlight;
  }
  async function request<T>(url: string, init?: RequestInit): Promise<T> {
    if (!url.startsWith("/api/")) throw new Error("Only same-origin BFF requests are supported");
    let response: Response;
    try { response = await raw(url, init); }
    catch { throw new ApiError(503, "NETWORK_ERROR", "Không thể kết nối. Vui lòng thử lại."); }
    let data = response.status === 204 ? undefined : await response.json().catch(() => null);
    if (response.status === 401 && data?.code === "AUTH_TOKEN_EXPIRED") {
      await refresh();
      try { response = await raw(url, init); }
      catch { throw new ApiError(503, "NETWORK_ERROR", "Không thể kết nối. Vui lòng thử lại."); }
      data = response.status === 204 ? undefined : await response.json().catch(() => null);
    }
    if (response.status === 401) await expire();
    if (response.status === 403 && String(data?.code).startsWith("FORBIDDEN_")) navigate("/app/khong-co-quyen");
    if (!response.ok) {
      const error = data as ApiErrorShape | null;
      throw new ApiError(response.status, error?.code ?? "UNKNOWN_ERROR", "Không thể xử lý yêu cầu. Vui lòng thử lại.");
    }
    return data as T;
  }
  return { request };
}
