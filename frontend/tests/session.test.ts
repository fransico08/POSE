import { describe, expect, it, vi } from "vitest";
import { createApiClient } from "@/lib/api/client";
import { can, implementedNavigation, navigation, permittedNavigation } from "@/config/navigation";
import { safeReturnTo } from "@/lib/session/return-to";
import { sameOriginMutation } from "@/lib/server/csrf";
import { refreshOnce } from "@/lib/server/refresh-flight";

const expired = () => Response.json({ code: "AUTH_TOKEN_EXPIRED" }, { status: 401 });
describe("client session", () => {
  it("three concurrent expired requests share exactly one refresh then retry", async () => {
    let refreshed = false;
    const fetcher = vi.fn<typeof fetch>(async url => {
      if (url === "/api/session/refresh") { await new Promise(resolve => setTimeout(resolve, 10)); refreshed = true; return Response.json({ user: {} }); }
      return refreshed ? Response.json({ ok: true }) : expired();
    });
    const api = createApiClient({ fetcher, navigate: vi.fn(), onExpired: vi.fn() });
    await Promise.all([1, 2, 3].map(() => api.request("/api/backend/users")));
    expect(fetcher.mock.calls.filter(([url]) => url === "/api/session/refresh")).toHaveLength(1);
    expect(fetcher.mock.calls.filter(([url]) => url === "/api/backend/users")).toHaveLength(6);
  });
  it("retries the original request only once, then clears session and redirects", async () => {
    const fetcher = vi.fn<typeof fetch>(async url => url === "/api/session/refresh" || url === "/api/session/logout" ? Response.json({}) : expired());
    const navigate = vi.fn(), clear = vi.fn(); const api = createApiClient({ fetcher, navigate, onExpired: clear });
    await expect(api.request("/api/backend/users")).rejects.toMatchObject({ status: 401 });
    expect(fetcher.mock.calls.filter(([url]) => url === "/api/backend/users")).toHaveLength(2);
    expect(fetcher.mock.calls.filter(([url]) => url === "/api/session/refresh")).toHaveLength(1);
    expect(clear).toHaveBeenCalledOnce(); expect(navigate).toHaveBeenCalledWith("/dang-nhap?reason=expired");
  });
  it("failed refresh expires once for concurrent callers and never retries originals", async () => {
    const fetcher = vi.fn<typeof fetch>(async url => url === "/api/session/refresh" ? Response.json({ code: "AUTH_INVALID_REFRESH_TOKEN" }, { status: 401 }) : url === "/api/session/logout" ? Response.json({}) : expired());
    const navigate = vi.fn(), clear = vi.fn(); const api = createApiClient({ fetcher, navigate, onExpired: clear });
    await Promise.allSettled([1, 2, 3].map(() => api.request("/api/backend/users")));
    expect(clear).toHaveBeenCalledOnce(); expect(navigate).toHaveBeenCalledOnce();
    expect(fetcher.mock.calls.filter(([url]) => url === "/api/backend/users")).toHaveLength(3);
  });
  it("after acquiring the cross-tab lock, rechecks me and skips consumed-token refresh", async () => {
    let originalCalls = 0;
    const fetcher = vi.fn<typeof fetch>(async url => url === "/api/session/me" ? Response.json({ user: {} }) : ++originalCalls === 1 ? expired() : Response.json({}));
    const lock = vi.fn(async (_name: string, callback: () => Promise<void>) => callback());
    await createApiClient({ fetcher, navigate: vi.fn(), onExpired: vi.fn(), locks: { request: lock } }).request("/api/backend/users");
    expect(lock).toHaveBeenCalledWith("pose-session-refresh", expect.any(Function));
    expect(fetcher.mock.calls.some(([url]) => url === "/api/session/refresh")).toBe(false);
  });
  it("network errors preserve the session, permission denial goes to 403", async () => {
    const clear = vi.fn(), navigate = vi.fn();
    await expect(createApiClient({ fetcher: vi.fn().mockRejectedValue(new Error("offline")), navigate, onExpired: clear }).request("/api/session/me")).rejects.toMatchObject({ code: "NETWORK_ERROR" });
    expect(clear).not.toHaveBeenCalled();
    const api = createApiClient({ fetcher: vi.fn().mockResolvedValue(Response.json({ code: "FORBIDDEN_SCOPE" }, { status: 403 })), navigate, onExpired: clear });
    await expect(api.request("/api/backend/users")).rejects.toMatchObject({ status: 403 });
    expect(navigate).toHaveBeenCalledWith("/app/khong-co-quyen");
  });
});
describe("permissions and navigation", () => {
  it("uses permissions and any-of, never grants by role names", () => {
    expect(can(["ADMIN"], "identity.user.manage")).toBe(false);
    expect(can(["handover.approve"], ["handover.request", "handover.approve"])).toBe(true);
    expect(implementedNavigation(["identity.user.manage"]).map(item => item.route)).toEqual(["/app"]);
    expect(permittedNavigation([])).toHaveLength(1);
    expect(permittedNavigation(["identity.user.manage"]).map(item => item.label)).toContain("Người dùng");
    expect(navigation).toHaveLength(17); expect(navigation.every(item => !!item.icon)).toBe(true);
  });
});
describe("returnTo", () => {
  it.each(["https://evil.test", "//evil.test", "/\\evil.test", "/%2f%2fevil.test", "/app/%5cevil", "/app/%0aevil", "/api/session/logout", "/dang-nhap", "/application", "/app/../../evil", "/app/%"])("rejects %s", value => { expect(safeReturnTo(value)).toBe("/app"); });
  it("keeps an internal app path and query", () => expect(safeReturnTo("/app/khong-co-quyen?from=test")).toBe("/app/khong-co-quyen?from=test"));
});
describe("CSRF", () => {
  const request = (headers: Record<string, string>) => new Request("https://pose.test/api/session/logout", { method: "POST", headers });
  it("allows exactly matching Origin or same-origin fetch metadata", () => {
    expect(sameOriginMutation(request({ origin: "https://pose.test" }))).toBe(true);
    expect(sameOriginMutation(request({ "sec-fetch-site": "same-origin" }))).toBe(true);
  });
  it.each([{ origin: "https://evil.test" }, { origin: "null" }, { "sec-fetch-site": "same-site" }, {}, { origin: "https://evil.test", "sec-fetch-site": "same-origin" }])("rejects foreign, null and missing origin", headers => expect(sameOriginMutation(request(headers as Record<string, string>))).toBe(false));
});
describe("BFF refresh dedupe", () => {
  it("concurrent callers and a late stale-cookie caller share one backend rotation", async () => {
    const fetcher = vi.fn().mockResolvedValue(Response.json({ accessToken: "synthetic-access", refreshToken: "synthetic-next-refresh", expiresIn: 100, user: { organization: {}, permissions: [] } }));
    vi.stubGlobal("fetch", fetcher);
    try {
      const results = await Promise.all([1, 2, 3].map(() => refreshOnce("synthetic-old-refresh")));
      expect(results.every(result => result.status === 200)).toBe(true);
      await refreshOnce("synthetic-old-refresh"); expect(fetcher).toHaveBeenCalledOnce();
    } finally { vi.unstubAllGlobals(); }
  });
});
