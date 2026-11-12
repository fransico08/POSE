import { cookies } from "next/headers";
import { sameOriginMutation } from "@/lib/server/csrf";
import { backendRequest, errorShape, readError } from "@/lib/server/backend";
import { sessionResponse } from "@/lib/server/session-response";
const roots = new Set(["users", "roles", "permissions", "organization", "teams", "offerings", "sales-assignments", "leads", "customers", "customer-360", "opportunities", "interactions", "tasks", "handovers", "support-requests", "imports", "dashboard", "audit", "ai"]);
// Backend AI timeout is 30s with one retry; keep the proxy above it so a slow provider is not reported as a network failure.
const AI_TIMEOUT_MS = 70000;
type Context = { params: Promise<{ path: string[] }> };
async function forward(request: Request, { params }: Context) {
  if (!sameOriginMutation(request)) return sessionResponse(errorShape("FORBIDDEN_ORIGIN"), 403);
  const { path } = await params;
  if (!roots.has(path[0]) || path.some(segment => !/^[a-zA-Z0-9_-]+$/u.test(segment))) return sessionResponse(errorShape("RESOURCE_NOT_FOUND"), 404);
  const token = (await cookies()).get("pose_at")?.value;
  if (!token) return sessionResponse(errorShape("AUTH_TOKEN_EXPIRED"), 401);
  try {
    const mutation = !["GET", "HEAD"].includes(request.method);
    const body = mutation ? await request.text() : undefined;
    if (body && !request.headers.get("content-type")?.startsWith("application/json")) return sessionResponse(errorShape("VALIDATION_ERROR"), 415);
    if (body && body.length > 65536) return sessionResponse(errorShape("VALIDATION_ERROR"), 413);
    const upstream = await backendRequest(`${path.join("/")}${new URL(request.url).search}`, { method: request.method, body }, token, path[0] === "ai" ? AI_TIMEOUT_MS : undefined);
    if (!upstream.ok) return sessionResponse(await readError(upstream), upstream.status);
    if (upstream.status === 204 || request.method === "HEAD") return new Response(null, { status: upstream.status, headers: { "Cache-Control": "no-store" } });
    return sessionResponse(await upstream.json(), upstream.status);
  } catch { return sessionResponse(errorShape("NETWORK_ERROR"), 503); }
}
export { forward as GET, forward as HEAD, forward as POST, forward as PUT, forward as PATCH, forward as DELETE };
