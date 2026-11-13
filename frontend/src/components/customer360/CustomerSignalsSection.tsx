"use client";

import { useEffect, useRef, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useSession } from "@/components/session/SessionGate";
import { Alert } from "@/components/ui/Alert";
import { Button } from "@/components/ui/Button";
import { api } from "@/lib/api";
import { aiStatusKey, copilotErrorMessage, copilotInvokeFailureMessage, getAiStatus } from "@/lib/copilot";

type Signal = { id:string; signalType:"INTENT"|"SENTIMENT"; value:string; confidence:number|null; evidenceText:string|null; source:string; status:string };
type Extraction = { status:"SUCCEEDED"|"FAILED"|"DISABLED"|"BLOCKED_BUDGET"; errorCode:string|null };
const labels: Record<string,string> = { ASK_PRICE:"Hỏi giá", ASK_INFO:"Hỏi thông tin", BOOKING:"Đặt lịch", PURCHASE_READY:"Sẵn sàng mua", COMPLAINT:"Khiếu nại", SUPPORT:"Cần hỗ trợ", OTHER:"Khác", POSITIVE:"Tích cực", NEUTRAL:"Trung lập", NEGATIVE:"Tiêu cực" };
const values: Record<string,string[]> = {INTENT:["ASK_PRICE","ASK_INFO","BOOKING","PURCHASE_READY","COMPLAINT","SUPPORT","OTHER"],SENTIMENT:["POSITIVE","NEUTRAL","NEGATIVE"]};
const statuses: Record<string,string> = {PROPOSED:"Chờ duyệt",ACCEPTED:"Đã chấp nhận",EDITED:"Đã chỉnh sửa",REJECTED:"Đã từ chối"};

export function CustomerSignalsSection({customerId,subjectType="CUSTOMER"}:{customerId:string;subjectType?:"CUSTOMER"|"LEAD"}) {
  const {user}=useSession(); const cache=useQueryClient(); const [editing,setEditing]=useState<string|null>(null); const [draft,setDraft]=useState(""); const heading=useRef<HTMLHeadingElement>(null);
  useEffect(()=>{if(editing)document.getElementById(`signal-${editing}`)?.focus();},[editing]);
  const permitted=user.permissions.includes("conversation.read") && !user.roles.includes("DATA_STAFF") && (subjectType==="LEAD" || (user.permissions.includes("ai.assist.use") && user.permissions.includes("customer_360.read")));
  const canExtract=permitted && user.permissions.includes("ai.assist.use");
  const canReview=permitted && user.permissions.includes("ai.signal.review");
  const key=["customer-signals",user.organization.id,user.id,subjectType,customerId];
  const status=useQuery({queryKey:aiStatusKey(user),queryFn:getAiStatus,enabled:canExtract});
  const signals=useQuery({queryKey:key,queryFn:()=>api.request<Signal[]>(`/api/backend/ai/signals?subjectType=${subjectType}&subjectId=${encodeURIComponent(customerId)}`),enabled:permitted && (!canExtract || status.data?.enabled===true)});
  const extract=useMutation({mutationFn:()=>api.request<Extraction[]>("/api/backend/ai/signals/extract",{method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify({subjectType,subjectId:customerId})}),onSuccess:()=>void cache.invalidateQueries({queryKey:key})});
  const review=useMutation({mutationFn:({id,action,value}:{id:string;action:"accept"|"reject"|"edit";value?:string})=>api.request<Signal>(`/api/backend/ai/signals/${encodeURIComponent(id)}/${action}`,{method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify(action==="edit"?{value}:{})}),onSuccess:()=>{setEditing(null);heading.current?.focus();void cache.invalidateQueries({queryKey:key});}});
  if(!permitted)return null;
  if(canExtract && status.isPending)return <section aria-labelledby="signals-heading"><h2 id="signals-heading">Tín hiệu khách hàng</h2><p role="status">Đang kiểm tra AI…</p></section>;
  if(canExtract && status.isError)return <section aria-labelledby="signals-heading"><h2 id="signals-heading">Tín hiệu khách hàng</h2><Alert>{copilotErrorMessage(status.error)}</Alert></section>;
  if(canExtract && !status.data?.enabled)return null;
  return <section className="offering-section" aria-labelledby="signals-heading"><h2 id="signals-heading" ref={heading} tabIndex={-1}>Tín hiệu khách hàng</h2>
    {canExtract && <Button onClick={()=>extract.mutate()} busy={extract.isPending}>Trích tín hiệu</Button>}
    {extract.isPending && <p role="status">Đang trích tín hiệu…</p>}
    {extract.isSuccess && extract.data.length===0 && <p role="status">Không có tin nhắn mới để trích.</p>}
    {extract.isSuccess && extract.data.some(item=>item.status==="SUCCEEDED") && <p role="status">Đã trích tín hiệu từ tin nhắn.</p>}
    {extract.isError && <Alert>{copilotErrorMessage(extract.error)}</Alert>}
    {extract.data?.map((item,index)=>{const message=copilotInvokeFailureMessage(item.status,item.errorCode);return message?<Alert key={index}>{message}</Alert>:item.status==="DISABLED"?<p key={index}>AI chưa được bật.</p>:null;})}
    {signals.isPending?<p role="status">Đang tải tín hiệu…</p>:signals.isError?<Alert>{copilotErrorMessage(signals.error)}</Alert>:signals.data.length===0?<p>Chưa có tín hiệu khách hàng.</p>:<ul>{signals.data.map(signal=><li key={signal.id}>
      <h3>{signal.signalType==="INTENT"?"Ý định":"Cảm xúc"}: {labels[signal.value]??signal.value}</h3>
      <p>{signal.source==="AI" && <>Do AI tạo — cần kiểm tra. </>}{statuses[signal.status]??signal.status}{signal.confidence!==null?` · Độ tin cậy ${Math.round(signal.confidence*100)}%`:""}</p>
      {signal.evidenceText && <blockquote className="copilot-point">{signal.evidenceText}</blockquote>}
      {canReview && signal.status==="PROPOSED" && <div>
        {editing===signal.id?<><label htmlFor={`signal-${signal.id}`}>Giá trị tín hiệu</label><select id={`signal-${signal.id}`} value={draft} onChange={event=>setDraft(event.target.value)}>{(values[signal.signalType]??[]).map(value=><option key={value} value={value}>{labels[value]}</option>)}</select><Button onClick={()=>review.mutate({id:signal.id,action:"edit",value:draft})} busy={review.isPending}>Lưu chỉnh sửa</Button><Button variant="secondary" onClick={()=>{setEditing(null);heading.current?.focus();}}>Hủy</Button></>:<><Button onClick={()=>review.mutate({id:signal.id,action:"accept"})} busy={review.isPending}>Chấp nhận</Button><Button variant="secondary" onClick={()=>{setDraft(signal.value);setEditing(signal.id);}}>Sửa</Button><Button variant="secondary" onClick={()=>review.mutate({id:signal.id,action:"reject"})} busy={review.isPending}>Từ chối</Button></>}
      </div>}
    </li>)}</ul>}
    {review.isError && <Alert>{copilotErrorMessage(review.error)}</Alert>}
  </section>;
}
