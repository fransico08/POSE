import { NextResponse } from "next/server";
import type { AuthTokens } from "./backend";
export function sessionResponse(body: unknown, status = 200) {
  return NextResponse.json(body, { status, headers: { "Cache-Control": "no-store", "X-Content-Type-Options": "nosniff" } });
}
const cookies = [ ["pose_at", "/api"], ["pose_rt", "/api/session"], ["pose_session_present", "/"] ] as const;
export function clearSessionCookies(response: NextResponse) {
  for (const [name, path] of cookies) response.cookies.set(name, "", { httpOnly: true, sameSite: "strict", secure: process.env.NODE_ENV === "production", path, maxAge: 0 });
  return response;
}
export function setSessionCookies(response: NextResponse, tokens: AuthTokens) {
  const options = { httpOnly: true, sameSite: "strict" as const, secure: process.env.NODE_ENV === "production" };
  response.cookies.set("pose_at", tokens.accessToken, { ...options, path: "/api", maxAge: Math.floor(tokens.expiresIn) });
  response.cookies.set("pose_rt", tokens.refreshToken, { ...options, path: "/api/session", maxAge: 7 * 24 * 60 * 60 });
  response.cookies.set("pose_session_present", "1", { ...options, path: "/", maxAge: 7 * 24 * 60 * 60 });
  return response;
}
