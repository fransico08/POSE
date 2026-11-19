import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { beforeEach, expect, it, vi } from "vitest";
import { ApiError } from "@/lib/api/client";
import { CreateLeadDialog } from "@/components/inbox/CreateLeadDialog";

const state=vi.hoisted(()=>({error:null as Error|null, calls:[] as Array<[string,RequestInit]>}));
vi.mock("@/components/session/SessionGate",()=>({useSession:()=>({user:{id:"s1",organization:{id:"o1"},roles:["SALES_REP"],permissions:["lead.write","offering.read"]}})}));
vi.mock("@/lib/crm",async importOriginal=>({...await importOriginal<typeof import("@/lib/crm")>(),getScopedOfferings:()=>Promise.resolve([{id:"o1",name:"Khóa học"}])}));
vi.mock("@/lib/api",()=>({api:{request:vi.fn((url:string,init:RequestInit)=>{state.calls.push([url,init]);return state.error?Promise.reject(state.error):Promise.resolve({lead:{id:"lead1"},conversation:{id:"c1",leadId:"lead1"}});})}}));
beforeEach(()=>{state.error=null;state.calls=[];});
function show(onCreated=vi.fn()) {const client=new QueryClient({defaultOptions:{queries:{retry:false},mutations:{retry:false}}});render(<QueryClientProvider client={client}><CreateLeadDialog open onClose={()=>{}} conversationId="c1" assignedUserId="s1" messages={[{direction:"INBOUND",content:"Xin hỗ trợ 0912 345 678"}]} onCreated={onCreated}/></QueryClientProvider>);return onCreated;}
it("requires confirmation before using a suggested contact and sends the selected source",async()=>{
  const done=show();const dialog=screen.getByRole("dialog");expect(within(dialog).getByLabelText("Số điện thoại")).toHaveValue("");
  fireEvent.click(within(dialog).getByRole("button",{name:"Dùng số 0912 345 678"}));
  fireEvent.change(within(dialog).getByLabelText("Tên khách *"),{target:{value:"Khách thử"}});
  await within(dialog).findByRole("option",{name:"Khóa học"});fireEvent.change(within(dialog).getByLabelText("Sản phẩm/dịch vụ quan tâm *"),{target:{value:"o1"}});
  fireEvent.change(within(dialog).getByLabelText("Nguồn *"),{target:{value:"REFERRAL"}});fireEvent.click(within(dialog).getByRole("button",{name:"Tạo Lead"}));
  await waitFor(()=>expect(done).toHaveBeenCalledOnce());const body=JSON.parse(String(state.calls[0][1].body));expect(body).toMatchObject({displayName:"Khách thử",phone:"0912 345 678",source:"REFERRAL",offeringId:"o1"});
});
it("shows field and duplicate errors without losing the form",async()=>{
  state.error=new ApiError(400,"VALIDATION_ERROR","error",[{field:"phone",message:"Số điện thoại không hợp lệ."}]);show();
  fireEvent.change(screen.getByLabelText("Tên khách *"),{target:{value:"Khách thử"}});fireEvent.change(screen.getByLabelText("Số điện thoại"),{target:{value:"123"}});
  await screen.findByRole("option",{name:"Khóa học"});fireEvent.change(screen.getByLabelText("Sản phẩm/dịch vụ quan tâm *"),{target:{value:"o1"}});fireEvent.click(screen.getByRole("button",{name:"Tạo Lead"}));
  expect(await screen.findByText("Số điện thoại không hợp lệ.")).toBeVisible();expect(screen.getByLabelText("Số điện thoại")).toHaveAttribute("aria-invalid","true");
  state.error=new ApiError(409,"LEAD_DUPLICATE","error");fireEvent.click(screen.getByRole("button",{name:"Tạo Lead"}));expect(await screen.findByText(/Hãy dùng Gắn hồ sơ/)).toBeVisible();
});
