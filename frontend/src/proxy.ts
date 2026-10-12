import { NextResponse, type NextRequest } from "next/server";
export function proxy(request: NextRequest) {
  if (request.nextUrl.pathname.startsWith("/app") && !request.cookies.has("pose_session_present")) {
    const login = new URL("/dang-nhap", request.url);
    login.searchParams.set("returnTo", `${request.nextUrl.pathname}${request.nextUrl.search}`);
    return NextResponse.redirect(login);
  }
  return NextResponse.next();
}
export const config = { matcher: ["/app/:path*"] };
