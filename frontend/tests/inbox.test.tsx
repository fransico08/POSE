import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, expect, it, vi } from "vitest";
import { ApiError } from "@/lib/api/client";
import { Inbox, inboxPollingInterval } from "@/components/inbox/Inbox";
import { implementedNavigation } from "@/config/navigation";

const state=vi.hoisted(()=>({roles:["MANAGER"],permissions:["conversation.read","conversation.reply","conversation.assign","customer.read","lead.read"],empty:false,fail:"",holdList:false,holdReply:false,calls:[] as Array<[string,RequestInit|undefined]>}));
vi.mock("@/components/session/SessionGate",()=>({useSession:()=>({user:{id:"u1",organization:{id:"o1"},roles:state.roles,permissions:state.permissions}})}));
vi.mock("@/lib/api",()=>({api:{request:vi.fn((url:string,init?:RequestInit)=>{
  state.calls.push([url,init]);
  if((state.holdList && url.includes("/conversations?") ) || (state.holdReply && init?.method==="POST" && url.endsWith("/messages")))return new Promise(()=>{});
  if(state.fail && url.includes(state.fail.split(":")[0])) {const code=state.fail.split(":")[1]??"NETWORK_ERROR";return Promise.reject(new ApiError(code==="RESOURCE_NOT_FOUND"?404:403,code,"error"));}
  if(init?.method==="POST")return Promise.resolve({id:"c1",channelType:"ZALO_SIM",channelName:"Kênh thử",status:"OPEN",assignedUserId:"u1",leadId:null,customerId:"p1",lastMessageAt:null});
  if(url.includes("/messages"))return Promise.resolve({items:[{id:"m1",direction:"INBOUND",content:"Tin thử",deliveryStatus:"RECEIVED",occurredAt:"2026-11-16T00:00:00Z"}],page:0,totalPages:1,totalItems:1});
  if(url.includes("/assignees"))return Promise.resolve([{id:"u2",fullName:"Nhân viên"}]);
  if(url.includes("/customers"))return Promise.resolve({items:[{id:"p1",displayName:"Khách thử"}],page:0,totalPages:1,totalItems:1});
  return Promise.resolve({items:state.empty?[]:[{id:"c1",channelType:"ZALO_SIM",channelName:"Kênh thử",status:"OPEN",assignedUserId:"u1",leadId:null,customerId:null,lastMessageAt:null}],page:0,totalPages:1,totalItems:state.empty?0:1});
})}}));
function show(){const client=new QueryClient({defaultOptions:{queries:{retry:false},mutations:{retry:false}}});return {client,...render(<QueryClientProvider client={client}><Inbox/></QueryClientProvider>)};}
beforeEach(()=>{state.roles=["MANAGER"];state.permissions=["conversation.read","conversation.reply","conversation.assign","customer.read","lead.read"];state.empty=false;state.fail="";state.holdList=false;state.holdReply=false;state.calls=[];});
it("shows menu by permission and excludes Data Staff even with another role",()=>{
  expect(implementedNavigation(["conversation.read"],["SALES_REP"]).some(x=>x.label==="Hội thoại")).toBe(true);
  expect(implementedNavigation(["conversation.read"],["DATA_STAFF","MANAGER"]).some(x=>x.label==="Hội thoại")).toBe(false);
  expect(implementedNavigation([],["MANAGER"]).some(x=>x.label==="Hội thoại")).toBe(false);
});
it("does not fetch content for Data Staff",()=>{state.roles=["DATA_STAFF","MANAGER"];show();expect(screen.getByText("Bạn không có quyền xem hội thoại.")).toBeVisible();expect(state.calls).toHaveLength(0);});
it("shows empty and network error states",async()=>{state.empty=true;const first=show();expect(await screen.findByText("Chưa có hội thoại phù hợp.")).toBeVisible();first.unmount();state.empty=false;state.fail="conversations:NETWORK_ERROR";show();expect(await screen.findByRole("alert")).toBeVisible();});
it("filters and sends a nonempty reply",async()=>{
  show();fireEvent.change(screen.getByLabelText("Trạng thái"),{target:{value:"PENDING"}});fireEvent.click(screen.getByLabelText("Được giao cho tôi"));
  await waitFor(()=>expect(state.calls.some(([url])=>url.includes("status=PENDING")&&url.includes("assignedToMe=true"))).toBe(true));
  fireEvent.click(await screen.findByRole("button",{name:/Kênh thử/}));expect(await screen.findByText("Tin thử")).toBeVisible();expect(screen.getByRole("button",{name:"Gửi trả lời"})).toBeDisabled();
  fireEvent.change(screen.getByLabelText("Trả lời"),{target:{value:"  Xin chào  "}});fireEvent.click(screen.getByRole("button",{name:"Gửi trả lời"}));
  await waitFor(()=>expect(state.calls.some(([url,init])=>url.endsWith("/messages")&&init?.method==="POST"&&JSON.parse(String(init.body)).content==="Xin chào")).toBe(true));
});
it("links one selected customer and exposes reassignment only with permission",async()=>{
  show();fireEvent.click(await screen.findByRole("button",{name:/Kênh thử/}));expect(await screen.findByText("Tin thử")).toBeVisible();
  await screen.findByRole("option",{name:"Khách thử"});fireEvent.change(screen.getByLabelText("Chọn hồ sơ"),{target:{value:"p1"}});fireEvent.click(screen.getByRole("button",{name:"Gắn hồ sơ"}));
  await waitFor(()=>expect(state.calls.some(([url,init])=>url.endsWith("/link")&&JSON.stringify(JSON.parse(String(init?.body)))===JSON.stringify({customerId:"p1"}))).toBe(true));
  expect(await screen.findByText("Giao lại hội thoại")).toBeVisible();
});
it("shows a 404 without message content",async()=>{state.fail="/messages:RESOURCE_NOT_FOUND";show();fireEvent.click(await screen.findByRole("button",{name:/Kênh thử/}));expect(await screen.findByText("Không tìm thấy hội thoại hoặc bạn không có quyền xem.")).toBeVisible();expect(screen.queryByText("Tin thử")).toBeNull();});

it("shows loading before the inbox responds",()=>{state.holdList=true;show();expect(screen.getByText("Đang tải hội thoại…")).toBeVisible();});
it("hides message content on permission failure",async()=>{state.fail="/messages:FORBIDDEN_PERMISSION";show();fireEvent.click(await screen.findByRole("button",{name:/Kênh thử/}));expect(await screen.findByText("Bạn không có quyền thực hiện thao tác này.")).toBeVisible();expect(screen.queryByText("Tin thử")).toBeNull();});
it("prevents a second reply while sending",async()=>{state.holdReply=true;show();fireEvent.click(await screen.findByRole("button",{name:/Kênh thử/}));fireEvent.change(await screen.findByLabelText("Trả lời"),{target:{value:"Phản hồi"}});fireEvent.click(screen.getByRole("button",{name:"Gửi trả lời"}));expect(await screen.findByText("Đang gửi tin…")).toBeVisible();expect(screen.getByRole("button",{name:"Gửi trả lời"})).toBeDisabled();});
it("explains an already linked conflict",async()=>{state.fail="/link:CONVERSATION_ALREADY_LINKED";show();fireEvent.click(await screen.findByRole("button",{name:/Kênh thử/}));await screen.findByRole("option",{name:"Khách thử"});fireEvent.change(screen.getByLabelText("Chọn hồ sơ"),{target:{value:"p1"}});fireEvent.click(screen.getByRole("button",{name:"Gắn hồ sơ"}));expect(await screen.findByText("Hội thoại đã được gắn với hồ sơ khác.")).toBeVisible();});
it("does not offer reassignment without permission",async()=>{state.permissions=state.permissions.filter(x=>x!=="conversation.assign");show();fireEvent.click(await screen.findByRole("button",{name:/Kênh thử/}));expect(await screen.findByText("Tin thử")).toBeVisible();expect(screen.queryByText("Giao lại hội thoại")).toBeNull();});
it("stops polling while the tab is hidden",()=>{const visibility=vi.spyOn(document,"visibilityState","get").mockReturnValue("hidden");try{expect(inboxPollingInterval()).toBe(false);visibility.mockReturnValue("visible");expect(inboxPollingInterval()).toBe(15000);}finally{visibility.mockRestore();}});
