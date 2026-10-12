import { cookies } from "next/headers";
import { backendRequest, errorShape, publicUser, readError } from "@/lib/server/backend";
import { sessionResponse } from "@/lib/server/session-response";
export async function GET() {
  const token = (await cookies()).get("pose_at")?.value;
  if (!token) return sessionResponse(errorShape("AUTH_TOKEN_EXPIRED"), 401);
  try {
    const userResponse = await backendRequest("auth/me", {}, token);
    if (!userResponse.ok) return sessionResponse(await readError(userResponse), userResponse.status);
    const orgResponse = await backendRequest("organization", {}, token);
    if (!orgResponse.ok) return sessionResponse(await readError(orgResponse), orgResponse.status);
    const user = publicUser(await userResponse.json());
    const organization = await orgResponse.json();
    return sessionResponse({ user, organization: { id: organization.id, code: organization.code, name: organization.name } });
  } catch { return sessionResponse(errorShape("NETWORK_ERROR", "Không thể kết nối. Vui lòng thử lại."), 503); }
}
