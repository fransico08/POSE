import { cookies } from "next/headers";
import { sameOriginMutation } from "@/lib/server/csrf";
import { errorShape, publicUser } from "@/lib/server/backend";
import { refreshOnce } from "@/lib/server/refresh-flight";
import { clearSessionCookies, sessionResponse, setSessionCookies } from "@/lib/server/session-response";
export const runtime = "nodejs";
export async function POST(request: Request) {
  if (!sameOriginMutation(request)) return sessionResponse(errorShape("FORBIDDEN_ORIGIN"), 403);
  const token = (await cookies()).get("pose_rt")?.value;
  if (!token) return clearSessionCookies(sessionResponse(errorShape("AUTH_INVALID_REFRESH_TOKEN"), 401));
  const result = await refreshOnce(token);
  if (result.tokens) return setSessionCookies(sessionResponse({ user: publicUser(result.tokens.user) }), result.tokens);
  const response = sessionResponse(result.error, result.status);
  const invalidToken = [400, 401].includes(result.status) && result.error?.code !== "NETWORK_ERROR";
  return invalidToken ? clearSessionCookies(response) : response;
}
