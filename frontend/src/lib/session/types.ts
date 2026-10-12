export interface SessionUser {
  id: string;
  email: string;
  fullName: string;
  status: string;
  organization: { id: string; name: string };
  roles: string[];
  permissions: string[];
  teamIds: string[];
}
export interface Session { user: SessionUser; organization: { id: string; code: string; name: string } }
export interface ApiErrorShape { code: string; message: string; fieldErrors: { field: string; message: string }[]; traceId: string }
