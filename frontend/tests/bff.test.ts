import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { POST as login } from "@/app/api/session/login/route";
import { POST as logout } from "@/app/api/session/logout/route";
import { GET as me } from "@/app/api/session/me/route";
import { POST as refresh } from "@/app/api/session/refresh/route";
import { POST as proxyPost, GET as proxyGet } from "@/app/api/backend/[...path]/route";
import { backendRequest } from "@/lib/server/backend";
import { setSessionCookies, sessionResponse } from "@/lib/server/session-response";
const store = vi.hoisted(() => new Map<string, string>());
vi.mock("next/headers", () => ({ cookies: async () => ({ get: (name: string) => store.has(name) ? { value: store.get(name) } : undefined }) }));
vi.mock("@/lib/server/backend", async importOriginal => ({ ...await importOriginal<typeof import("@/lib/server/backend")>(), backendRequest: vi.fn() }));
const user = { id: "fixture-user", email: "person@example.test", fullName: "Nguyễn An", status: "ACTIVE", roles: [], permissions: [], teamIds: [], organization: { id: "fixture-org", name: "Tổ chức kiểm thử" } };
const tokens = { accessToken: "fixture-access", refreshToken: "fixture-refresh", expiresIn: 900, user };
const request = (path: string, body: object = {}, origin = "https://pose.test") => new Request(`https://pose.test${path}`, { method: "POST", headers: { origin, "content-type": "application/json" }, body: JSON.stringify(body) });
beforeEach(() => { store.clear(); vi.mocked(backendRequest).mockReset(); });
afterEach(() => vi.unstubAllEnvs());
describe("BFF boundaries", () => {
  it("refresh network failure retains cookies and a manual retry can recover", async () => {
    store.set("pose_rt", "synthetic-network-refresh");
    vi.mocked(backendRequest).mockRejectedValueOnce(new TypeError("offline")).mockResolvedValueOnce(Response.json(tokens));
    const response = await refresh(request("/api/session/refresh"));
    expect(response.status).toBe(503); expect((await response.json()).code).toBe("NETWORK_ERROR");
    expect(response.cookies.getAll()).toHaveLength(0);
    const retry = await refresh(request("/api/session/refresh"));
    expect(retry.status).toBe(200); expect(retry.cookies.get("pose_rt")?.maxAge).toBe(604800);
  });
  it.each([500, 503])("refresh backend %i retains cookies", async status => {
    store.set("pose_rt", `synthetic-upstream-refresh-${status}`);
    vi.mocked(backendRequest).mockResolvedValue(Response.json({ code: "UPSTREAM_ERROR" }, { status }));
    const response = await refresh(request("/api/session/refresh"));
    expect(response.status).toBe(status); expect(response.cookies.getAll()).toHaveLength(0);
  });
  it.each([400, 401])("refresh invalid token %i clears cookies", async status => {
    store.set("pose_rt", `synthetic-invalid-refresh-${status}`);
    vi.mocked(backendRequest).mockResolvedValue(Response.json({ code: "AUTH_INVALID_REFRESH_TOKEN" }, { status }));
    const response = await refresh(request("/api/session/refresh"));
    expect(response.status).toBe(status); expect(response.cookies.getAll()).toHaveLength(3);
    expect(response.cookies.getAll().every(cookie => cookie.maxAge === 0)).toBe(true);
  });
  it("login returns only public user and sets HttpOnly cookies at their exact paths", async () => {
    vi.mocked(backendRequest).mockResolvedValue(Response.json(tokens));
    const response = await login(request("/api/session/login", { organizationCode: "fixture", email: user.email, password: "synthetic input" }));
    expect(await response.json()).toEqual({ user });
    expect(response.headers.get("cache-control")).toBe("no-store");
    expect(response.cookies.get("pose_at")).toMatchObject({ value: tokens.accessToken, path: "/api", httpOnly: true, sameSite: "strict", maxAge: 900 });
    expect(response.cookies.get("pose_rt")).toMatchObject({ path: "/api/session", maxAge: 604800, httpOnly: true });
    expect(response.cookies.get("pose_session_present")).toMatchObject({ value: "1", path: "/", httpOnly: true });
  });
  it("all production cookies are Secure", () => {
    vi.stubEnv("NODE_ENV", "production");
    const response = setSessionCookies(sessionResponse({ user }), tokens);
    expect(response.cookies.getAll().every(cookie => cookie.secure)).toBe(true);
  });
  it.each([401, 400])("normalizes credential and upstream validation errors", async status => {
    vi.mocked(backendRequest).mockResolvedValue(Response.json({ code: "AUTH_INVALID_CREDENTIALS", message: "Private upstream detail" }, { status }));
    const response = await login(request("/api/session/login", { organizationCode: "fixture", email: user.email, password: "synthetic input" }));
    expect(response.status).toBe(401);
    expect(await response.json()).toMatchObject({ code: "AUTH_INVALID_CREDENTIALS", message: "Mã tổ chức, email hoặc mật khẩu không đúng." });
  });
  it.each([500, 503])("login upstream %i exposes only a connection error", async status => {
    vi.mocked(backendRequest).mockResolvedValue(Response.json({ code: "PRIVATE_BACKEND_ERROR", message: "Private backend detail" }, { status }));
    const response = await login(request("/api/session/login", { organizationCode: "fixture", email: user.email, password: "synthetic input" }));
    expect(response.status).toBe(503); expect(response.cookies.getAll()).toHaveLength(0);
    expect(await response.json()).toMatchObject({ code: "NETWORK_ERROR", message: "Không thể kết nối. Vui lòng thử lại." });
  });
  it.each(["offline", "timeout"])("login %s is distinct from invalid credentials", async failure => {
    vi.mocked(backendRequest).mockRejectedValue(failure === "timeout" ? new DOMException("Timed out", "TimeoutError") : new TypeError("Offline"));
    const response = await login(request("/api/session/login", { organizationCode: "fixture", email: user.email, password: "synthetic input" }));
    expect(response.status).toBe(503); expect((await response.json()).code).toBe("NETWORK_ERROR");
  });
  it("malformed upstream login success is a connection failure, not a credential error", async () => {
    vi.mocked(backendRequest).mockResolvedValue(Response.json({ user }));
    const response = await login(request("/api/session/login", { organizationCode: "fixture", email: user.email, password: "synthetic input" }));
    expect(response.status).toBe(503); expect((await response.json()).code).toBe("NETWORK_ERROR");
  });
  it("logout clears every cookie even when the upstream request fails", async () => {
    store.set("pose_at", "fixture-access"); store.set("pose_rt", "fixture-refresh");
    vi.mocked(backendRequest).mockRejectedValue(new Error("offline"));
    const response = await logout(request("/api/session/logout"));
    expect(response.cookies.getAll()).toHaveLength(3);
    expect(response.cookies.getAll().every(cookie => cookie.maxAge === 0)).toBe(true);
    expect(response.cookies.get("pose_at")?.path).toBe("/api");
    expect(response.cookies.get("pose_rt")?.path).toBe("/api/session");
  });
  it("missing access cookie enables the client refresh path", async () => {
    const response = await me();
    expect(response.status).toBe(401); expect((await response.json()).code).toBe("AUTH_TOKEN_EXPIRED");
    expect(backendRequest).not.toHaveBeenCalled();
  });
  it("rejects foreign-Origin mutations in login, logout and proxy before backend access", async () => {
    const foreign = request("/api/session/logout", {}, "https://evil.test");
    expect((await logout(foreign)).status).toBe(403);
    expect((await login(request("/api/session/login", {}, "https://evil.test"))).status).toBe(403);
    expect((await proxyPost(request("/api/backend/users", {}, "https://evil.test"), { params: Promise.resolve({ path: ["users"] }) })).status).toBe(403);
    expect(backendRequest).not.toHaveBeenCalled();
  });
  it("proxy blocks auth endpoints and traversal rather than returning tokens", async () => {
    for (const path of [["auth", "login"], ["users", ".."], ["users", "%2e%2e"]]) {
      expect((await proxyGet(new Request("https://pose.test/api/backend/users"), { params: Promise.resolve({ path }) })).status).toBe(404);
    }
    expect(backendRequest).not.toHaveBeenCalled();
  });
  it("proxy allows customer-360 reconciliation root with access cookie", async () => {
    store.set("pose_at", "fixture-access");
    vi.mocked(backendRequest).mockResolvedValue(Response.json({ totals: {}, matched: 20, mismatched: 0, differences: { items: [], page: 0, size: 20, totalItems: 0, totalPages: 0 } }));
    const response = await proxyGet(new Request("https://pose.test/api/backend/customer-360/reconciliation?page=0&size=20"), { params: Promise.resolve({ path: ["customer-360", "reconciliation"] }) });
    expect(response.status).toBe(200);
    expect(backendRequest).toHaveBeenCalledWith("customer-360/reconciliation?page=0&size=20", { method: "GET", body: undefined }, "fixture-access");
  });
  it("proxy allows ai root for status and Copilot endpoints with access cookie", async () => {
    store.set("pose_at", "fixture-access");
    vi.mocked(backendRequest).mockResolvedValueOnce(Response.json({ enabled: true, provider: "mock", model: "mock-v1" }));
    const statusRes = await proxyGet(new Request("https://pose.test/api/backend/ai/status"), { params: Promise.resolve({ path: ["ai", "status"] }) });
    expect(statusRes.status).toBe(200);
    expect(backendRequest).toHaveBeenCalledWith("ai/status", { method: "GET", body: undefined }, "fixture-access");

    vi.mocked(backendRequest).mockResolvedValueOnce(Response.json({ status: "SUCCEEDED", reused: false, invocationId: "inv-1", suggestion: null }));
    const summaryRes = await proxyPost(request("/api/backend/ai/copilot/customers/11111111-1111-4111-8111-111111111111/summary", { force: false }), {
      params: Promise.resolve({ path: ["ai", "copilot", "customers", "11111111-1111-4111-8111-111111111111", "summary"] }),
    });
    expect(summaryRes.status).toBe(200);
    expect(backendRequest).toHaveBeenLastCalledWith(
      "ai/copilot/customers/11111111-1111-4111-8111-111111111111/summary",
      { method: "POST", body: JSON.stringify({ force: false }) },
      "fixture-access",
    );
  });
});
