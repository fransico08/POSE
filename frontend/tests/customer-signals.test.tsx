import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, expect, it, vi } from "vitest";
import { CustomerSignalsSection } from "@/components/customer360/CustomerSignalsSection";

const state=vi.hoisted(()=>({roles:["MANAGER"],permissions:["ai.assist.use","ai.signal.review","conversation.read","customer_360.read"],enabled:true,fail:false,empty:false,calls:[] as Array<[string,RequestInit|undefined]>}));
vi.mock("@/components/session/SessionGate",()=>({useSession:()=>({user:{id:"u1",organization:{id:"org1"},roles:state.roles,permissions:state.permissions}})}));
vi.mock("@/lib/api",()=>({api:{request:vi.fn((url:string,init?:RequestInit)=>{
  state.calls.push([url,init]); if(state.fail)return Promise.reject(new Error("network"));
  if(init?.method==="POST")return Promise.resolve(url.endsWith("/extract")?{results:[{status:"FAILED",errorCode:"TIMEOUT"}],remaining:2}:{status:"ACCEPTED"});
  return Promise.resolve(state.empty?[]:[{id:"s1",signalType:"INTENT",value:"ASK_PRICE",confidence:0.9,evidenceText:"Cho tôi hỏi giá",source:"AI",status:"PROPOSED"}]);
})}}));
vi.mock("@/lib/copilot",()=>({aiStatusKey:()=>["status"],getAiStatus:()=>Promise.resolve({enabled:state.enabled}),copilotErrorMessage:()=>"Không thể tải tín hiệu.",copilotInvokeFailureMessage:(status:string,code:string)=>status==="FAILED"&&code==="TIMEOUT"?"Dịch vụ AI phản hồi quá chậm.":null}));
function show(){return render(<QueryClientProvider client={new QueryClient({defaultOptions:{queries:{retry:false},mutations:{retry:false}}})}><CustomerSignalsSection customerId="customer-1" /></QueryClientProvider>)}
beforeEach(()=>{state.roles=["MANAGER"];state.permissions=["ai.assist.use","ai.signal.review","conversation.read","customer_360.read"];state.enabled=true;state.fail=false;state.empty=false;state.calls=[];});
it("shows labels, evidence and accessible review controls",async()=>{
  show();expect(await screen.findByText("Ý định: Hỏi giá")).toBeVisible();expect(screen.getByText("Cho tôi hỏi giá")).toBeVisible();expect(screen.getByText(/Độ tin cậy 90%/)).toBeVisible();
  fireEvent.click(screen.getByRole("button",{name:"Sửa"}));expect(screen.getByLabelText("Giá trị tín hiệu")).toHaveValue("ASK_PRICE");expect(screen.getByLabelText("Giá trị tín hiệu")).toHaveFocus();
  fireEvent.change(screen.getByLabelText("Giá trị tín hiệu"),{target:{value:"ASK_INFO"}});fireEvent.click(screen.getByRole("button",{name:"Lưu chỉnh sửa"}));
  await waitFor(()=>expect(state.calls.some(([url,init])=>url.endsWith("/s1/edit")&&JSON.parse(String(init?.body)).value==="ASK_INFO")).toBe(true));
});
it("shows provider failures and supports extraction",async()=>{
  const timeout=vi.spyOn(AbortSignal,"timeout");
  try { show();await screen.findByText("Ý định: Hỏi giá");fireEvent.click(screen.getByRole("button",{name:"Trích tín hiệu"}));expect(await screen.findByText("Dịch vụ AI phản hồi quá chậm.")).toBeVisible();expect(screen.getByText("Còn 2 tin chưa trích")).toBeVisible();expect(timeout).toHaveBeenCalledWith(75000); } finally { timeout.mockRestore(); }
});
it("hides content for Data Staff and review controls without permission",async()=>{
  state.roles=["DATA_STAFF","MANAGER"];const first=show();expect(screen.queryByText("Tín hiệu khách hàng")).toBeNull();expect(state.calls).toHaveLength(0);first.unmount();
  state.roles=["MANAGER"];state.permissions=["ai.assist.use","conversation.read","customer_360.read"];show();await screen.findByText("Ý định: Hỏi giá");expect(screen.queryByRole("button",{name:"Chấp nhận"})).toBeNull();
});
it("hides the section when AI is disabled",async()=>{state.enabled=false;show();await waitFor(()=>expect(screen.queryByText("Tín hiệu khách hàng")).toBeNull());});
it("shows an empty state when there are no signals",async()=>{state.empty=true;show();expect(await screen.findByText("Chưa có tín hiệu khách hàng.")).toBeVisible();});
it("shows a safe message when the signal request fails",async()=>{state.fail=true;show();expect(await screen.findByText("Không thể tải tín hiệu.")).toBeVisible();});
it("hides customer signals without the Customer 360 permission",()=>{
  state.permissions=["conversation.read","ai.assist.use","ai.signal.review"];show();expect(screen.queryByText("Tín hiệu khách hàng")).toBeNull();expect(state.calls).toHaveLength(0);
});
it("extracts a linked Lead using the Lead subject",async()=>{
  state.permissions=["conversation.read","ai.assist.use","ai.signal.review"];
  render(<QueryClientProvider client={new QueryClient({defaultOptions:{queries:{retry:false},mutations:{retry:false}}})}><CustomerSignalsSection customerId="lead-1" subjectType="LEAD" /></QueryClientProvider>);
  await screen.findByText("Ý định: Hỏi giá");fireEvent.click(screen.getByRole("button",{name:"Trích tín hiệu"}));
  await waitFor(()=>expect(state.calls.some(([url,init])=>url.endsWith("/extract") && JSON.parse(String(init?.body)).subjectType==="LEAD")).toBe(true));
});
