import { describe, expect, it, vi } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useQueryClient, type QueryClient } from "@tanstack/react-query";
import { Providers } from "@/components/session/Providers";
import { SessionGate, useSession } from "@/components/session/SessionGate";
import { clearClientSession } from "@/lib/api";
function Consumer({ onClient }: { onClient: (client: QueryClient) => void }) {
  const client = useQueryClient(), { user } = useSession();
  onClient(client);
  return <><p>{user.fullName}</p><button onClick={() => { client.setQueryData(["tenant-record"], { sensitive: "fixture" }); clearClientSession(); }}>Xóa phiên</button></>;
}
describe("session provider integration", () => {
  it("bootstraps through the root QueryClient, clears user state and all cached tenant data", async () => {
    const keyboard = userEvent.setup();
    const fixture = { user: { id: "fixture-user", fullName: "Nguyễn An", email: "person@example.test", roles: [], permissions: [], teamIds: [], organization: { id: "fixture-org", name: "Tổ chức kiểm thử" } }, organization: { id: "fixture-org", name: "Tổ chức kiểm thử", code: "fixture" } };
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(Response.json(fixture)));
    let client: QueryClient | undefined;
    try {
      render(<Providers><SessionGate><Consumer onClient={value => { client = value; }} /></SessionGate></Providers>);
      expect(screen.getByLabelText("Đang kiểm tra phiên đăng nhập")).toHaveAttribute("aria-busy", "true");
      await screen.findByText("Nguyễn An");
      await keyboard.click(screen.getByRole("button", { name: "Xóa phiên" }));
      await waitFor(() => expect(screen.queryByText("Nguyễn An")).not.toBeInTheDocument());
      expect(client?.getQueryData(["tenant-record"])).toBeUndefined();
      expect(client?.getQueryData(["session"])).toBeUndefined();
    } finally { vi.unstubAllGlobals(); }
  });
});
