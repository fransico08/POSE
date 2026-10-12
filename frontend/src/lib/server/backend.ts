import type { ApiErrorShape, SessionUser } from "@/lib/session/types";
export interface AuthTokens { accessToken: string; refreshToken: string; expiresIn: number; user: SessionUser }
export const LOGIN_ERROR = "Mã tổ chức, email hoặc mật khẩu không đúng.";
export function errorShape(code: string, message = "Không thể xử lý yêu cầu. Vui lòng thử lại."): ApiErrorShape {
  return { code, message, fieldErrors: [], traceId: "" };
}
export async function backendRequest(path: string, init: RequestInit = {}, accessToken?: string) {
  const base = new URL(process.env.BACKEND_URL ?? "http://127.0.0.1:8080");
  if (!["http:", "https:"].includes(base.protocol) || base.username || base.password) throw new Error("Invalid backend configuration");
  const headers = new Headers({ Accept: "application/json" });
  if (init.body) headers.set("Content-Type", "application/json");
  if (accessToken) headers.set("Authorization", `Bearer ${accessToken}`);
  return fetch(new URL(`/api/v1/${path}`, base), { ...init, headers, cache: "no-store", redirect: "error", signal: AbortSignal.timeout(10000) });
}
export async function readError(response: Response): Promise<ApiErrorShape> {
  const body = await response.json().catch(() => null);
  return { code: typeof body?.code === "string" ? body.code : "UPSTREAM_ERROR", message: "Không thể xử lý yêu cầu. Vui lòng thử lại.", fieldErrors: [], traceId: typeof body?.traceId === "string" ? body.traceId : "" };
}
export function publicUser(user: SessionUser): SessionUser {
  return { id: user.id, email: user.email, fullName: user.fullName, status: user.status, organization: { id: user.organization.id, name: user.organization.name }, roles: user.roles, permissions: user.permissions, teamIds: user.teamIds };
}
export function validTokens(value: AuthTokens): boolean {
  return typeof value.accessToken === "string" && typeof value.refreshToken === "string" && Number.isFinite(value.expiresIn) && value.expiresIn > 0 && !!value.user?.organization && Array.isArray(value.user.permissions);
}
