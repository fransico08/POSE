import { cookies } from "next/headers";
import { sameOriginMutation } from "@/lib/server/csrf";
import { backendRequest, errorShape } from "@/lib/server/backend";
import { clearSessionCookies, sessionResponse } from "@/lib/server/session-response";
export async function POST(request: Request) {
  if (!sameOriginMutation(request)) return sessionResponse(errorShape("FORBIDDEN_ORIGIN"), 403);
  const store = await cookies(), accessToken = store.get("pose_at")?.value, refreshToken = store.get("pose_rt")?.value;
  try { if (accessToken && refreshToken) await backendRequest("auth/logout", { method: "POST", body: JSON.stringify({ refreshToken }) }, accessToken); }
  catch { /* Local session must clear even when the backend is unreachable. */ }
  return clearSessionCookies(sessionResponse({ loggedOut: true }));
}
