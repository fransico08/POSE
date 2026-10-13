import { sameOriginMutation } from "@/lib/server/csrf";
import { backendRequest, errorShape, LOGIN_ERROR, publicUser, validTokens, type AuthTokens } from "@/lib/server/backend";
import { sessionResponse, setSessionCookies } from "@/lib/server/session-response";
export async function POST(request: Request) {
  if (!sameOriginMutation(request)) return sessionResponse(errorShape("FORBIDDEN_ORIGIN"), 403);
  try {
    if (!request.headers.get("content-type")?.startsWith("application/json")) return sessionResponse(errorShape("AUTH_INVALID_CREDENTIALS", LOGIN_ERROR), 400);
    const body = await request.text();
    if (body.length > 4096) return sessionResponse(errorShape("AUTH_INVALID_CREDENTIALS", LOGIN_ERROR), 400);
    let input: Record<string, unknown>;
    try { input = JSON.parse(body); }
    catch { return sessionResponse(errorShape("AUTH_INVALID_CREDENTIALS", LOGIN_ERROR), 400); }
    if (!input || typeof input !== "object") return sessionResponse(errorShape("AUTH_INVALID_CREDENTIALS", LOGIN_ERROR), 400);
    const { organizationCode, email, password } = input;
    if (![organizationCode, email, password].every(value => typeof value === "string" && value.length > 0)) return sessionResponse(errorShape("AUTH_INVALID_CREDENTIALS", LOGIN_ERROR), 400);
    const response = await backendRequest("auth/login", { method: "POST", body: JSON.stringify({ organizationCode, email, password }) });
    if (!response.ok) {
      if ([400, 401].includes(response.status)) return sessionResponse(errorShape("AUTH_INVALID_CREDENTIALS", LOGIN_ERROR), 401);
      return sessionResponse(errorShape("NETWORK_ERROR", "Không thể kết nối. Vui lòng thử lại."), 503);
    }
    const tokens: AuthTokens = await response.json();
    if (!validTokens(tokens)) return sessionResponse(errorShape("NETWORK_ERROR", "Không thể kết nối. Vui lòng thử lại."), 503);
    return setSessionCookies(sessionResponse({ user: publicUser(tokens.user) }), tokens);
  } catch { return sessionResponse(errorShape("NETWORK_ERROR", "Không thể kết nối. Vui lòng thử lại."), 503); }
}
