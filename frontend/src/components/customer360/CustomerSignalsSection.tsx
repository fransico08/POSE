"use client";

import { useEffect, useRef, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useSession } from "@/components/session/SessionGate";
import { Alert } from "@/components/ui/Alert";
import { Button } from "@/components/ui/Button";
import { api } from "@/lib/api";
import { ApiError } from "@/lib/api/client";
import { crmFieldErrors } from "@/lib/crm";
import { aiStatusKey, copilotErrorMessage, copilotInvokeFailureMessage, getAiStatus } from "@/lib/copilot";

type Signal = { id:string; signalType:"INTENT"|"SENTIMENT"; value:string; confidence:number|null; evidenceText:string|null; source:string; status:string };
type ExtractionResponse = { results: Extraction[]; remaining: number };
type Extraction = { status:"SUCCEEDED"|"FAILED"|"DISABLED"|"BLOCKED_BUDGET"; errorCode:string|null };
const labels: Record<string,string> = { ASK_PRICE:"Hỏi giá", ASK_INFO:"Hỏi thông tin", BOOKING:"Đặt lịch", PURCHASE_READY:"Sẵn sàng mua", COMPLAINT:"Khiếu nại", SUPPORT:"Cần hỗ trợ", OTHER:"Khác", POSITIVE:"Tích cực", NEUTRAL:"Trung lập", NEGATIVE:"Tiêu cực" };
const values: Record<string,string[]> = {INTENT:["ASK_PRICE","ASK_INFO","BOOKING","PURCHASE_READY","COMPLAINT","SUPPORT","OTHER"],SENTIMENT:["POSITIVE","NEUTRAL","NEGATIVE"]};
const statuses: Record<string,string> = {PROPOSED:"Chờ duyệt",ACCEPTED:"Đã chấp nhận",EDITED:"Đã chỉnh sửa",REJECTED:"Đã từ chối"};

export function CustomerSignalsSection({customerId,subjectType="CUSTOMER"}:{customerId:string;subjectType?:"CUSTOMER"|"LEAD"}) {
  const {user}=useSession(); const cache=useQueryClient(); const [editing,setEditing]=useState<string|null>(null); const [draft,setDraft]=useState(""); const [adding,setAdding]=useState(false); const [messageId,setMessageId]=useState(""); const [signalType,setSignalType]=useState("INTENT"); const [value,setValue]=useState("ASK_PRICE"); const [evidence,setEvidence]=useState(""); const heading=useRef<HTMLHeadingElement>(null);
  useEffect(()=>{if(editing)document.getElementById(`signal-${editing}`)?.focus();},[editing]);
  const permitted=user.permissions.includes("conversation.read") && !user.roles.includes("DATA_STAFF") && (subjectType==="LEAD" || (user.permissions.includes("ai.assist.use") && user.permissions.includes("customer_360.read")));
  const canExtract=permitted && user.permissions.includes("ai.assist.use");
  const canReview=permitted && user.permissions.includes("ai.signal.review");
  const key=["customer-signals",user.organization.id,user.id,subjectType,customerId];
  const status=useQuery({queryKey:aiStatusKey(user),queryFn:getAiStatus,enabled:canExtract});
  const signals=useQuery({queryKey:key,queryFn:()=>api.request<Signal[]>(`/api/backend/ai/signals?subjectType=${subjectType}&subjectId=${encodeURIComponent(customerId)}`),enabled:permitted});
  const extract=useMutation({mutationFn:()=>api.request<ExtractionResponse>("/api/backend/ai/signals/extract",{method:"POST",signal:AbortSignal.timeout(75000),headers:{"Content-Type":"application/json"},body:JSON.stringify({subjectType,subjectId:customerId})}),onSuccess:()=>void cache.invalidateQueries({queryKey:key})});
  const messages=useQuery({queryKey:[...key,"messages"],queryFn:()=>api.request<{id:string;occurredAt:string;preview:string}[]>(`/api/backend/ai/signals/messages?subjectType=${subjectType}&subjectId=${encodeURIComponent(customerId)}`),enabled:canReview && adding});
  useEffect(()=>{if(adding && messages.isSuccess)document.getElementById("human-signal-message")?.focus();},[adding,messages.isSuccess]);
  const create=useMutation({mutationFn:()=>api.request<Signal>("/api/backend/ai/signals",{method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify({subjectType,subjectId:customerId,messageId,signalType,value,...(evidence?{evidenceText:evidence}:{})})}),onSuccess:()=>{setAdding(false);setEvidence("");setMessageId("");heading.current?.focus();void cache.invalidateQueries({queryKey:key});}});
  const fieldErrors=crmFieldErrors(create.error);
  const review=useMutation({mutationFn:({id,action,value}:{id:string;action:"accept"|"reject"|"edit";value?:string})=>api.request<Signal>(`/api/backend/ai/signals/${encodeURIComponent(id)}/${action}`,{method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify(action==="edit"?{value}:{})}),onSuccess:()=>{setEditing(null);heading.current?.focus();void cache.invalidateQueries({queryKey:key});}});
  if(!permitted)return null;
  return <section className="offering-section" aria-labelledby="signals-heading"><h2 id="signals-heading" ref={heading} tabIndex={-1}>Tín hiệu khách hàng</h2>
    {canExtract && status.isPending && <p role="status">Đang kiểm tra AI…</p>}
    {canExtract && status.isError && <Alert>{copilotErrorMessage(status.error)}</Alert>}
    {canExtract && status.data && !status.data.enabled && <p>AI chưa được bật; bạn vẫn có thể thêm tín hiệu thủ công.</p>}
    {canExtract && status.data?.enabled && <Button onClick={()=>extract.mutate()} busy={extract.isPending}>Trích tín hiệu</Button>}
    {canReview && !adding && <Button onClick={()=>setAdding(true)}>Thêm tín hiệu</Button>}
    {adding && <form className="offering-form" onSubmit={event=>{event.preventDefault();if(!create.isPending)create.mutate();}}>
      {messages.isPending?<p role="status">Đang tải tin nhắn…</p>:messages.isError?<Alert>Không thể tải tin nhắn.</Alert>:messages.data.length===0?<p role="status">Chưa có tin nhắn đến để thêm tín hiệu.</p>:<>
        <label htmlFor="human-signal-message">Tin nhắn đến</label><select className="input" id="human-signal-message" value={messageId} onChange={event=>setMessageId(event.target.value)} required aria-invalid={!!fieldErrors.messageId} aria-describedby={fieldErrors.messageId?"human-message-error":undefined}><option value="">Chọn tin nhắn</option>{messages.data.map(message=><option key={message.id} value={message.id}>{new Date(message.occurredAt).toLocaleString("vi-VN")} — {message.preview}</option>)}</select>{fieldErrors.messageId && <p id="human-message-error" role="alert">{fieldErrors.messageId}</p>}
        <label htmlFor="human-signal-type">Loại tín hiệu</label><select className="input" id="human-signal-type" value={signalType} onChange={event=>{const next=event.target.value;setSignalType(next);setValue(values[next][0]);}} aria-invalid={!!fieldErrors.signalType}><option value="INTENT">Ý định</option><option value="SENTIMENT">Cảm xúc</option></select>{fieldErrors.signalType && <p role="alert">{fieldErrors.signalType}</p>}
        <label htmlFor="human-signal-value">Giá trị tín hiệu</label><select className="input" id="human-signal-value" value={value} onChange={event=>setValue(event.target.value)} aria-invalid={!!fieldErrors.value}>{values[signalType].map(item=><option key={item} value={item}>{labels[item]}</option>)}</select>{fieldErrors.value && <p role="alert">{fieldErrors.value}</p>}
        <label htmlFor="human-signal-evidence">Đoạn trích (không bắt buộc)</label><textarea className="input offering-textarea" id="human-signal-evidence" maxLength={500} value={evidence} onChange={event=>setEvidence(event.target.value)} aria-invalid={!!fieldErrors.evidenceText} aria-describedby={fieldErrors.evidenceText?"human-evidence-error":undefined}/>{fieldErrors.evidenceText && <p id="human-evidence-error" role="alert">{fieldErrors.evidenceText}</p>}
        <Button type="submit" busy={create.isPending}>Lưu tín hiệu</Button>
      </>}
      <Button type="button" variant="secondary" onClick={()=>{setAdding(false);heading.current?.focus();}} disabled={create.isPending}>Hủy</Button>
      {create.isPending && <p role="status">Đang lưu tín hiệu…</p>}{create.isError && <Alert>{create.error instanceof ApiError && create.error.code==="AI_SIGNAL_DUPLICATE"?"Tin nhắn này đã có tín hiệu cùng loại do nhân viên thêm.":copilotErrorMessage(create.error)}</Alert>}
    </form>}
    {create.isSuccess && !adding && <p role="status">Đã thêm tín hiệu.</p>}
    {extract.isPending && <p role="status">Đang trích tín hiệu…</p>}
    {extract.isSuccess && extract.data.results.length===0 && <p role="status">Không có tin nhắn mới để trích.</p>}
    {extract.isSuccess && extract.data.results.some(item=>item.status==="SUCCEEDED") && <p role="status">Đã trích tín hiệu từ tin nhắn.</p>}
    {extract.isSuccess && extract.data.remaining>0 && <p role="status">Còn {extract.data.remaining} tin chưa trích</p>}
    {extract.isError && <Alert>{copilotErrorMessage(extract.error)}</Alert>}
    {extract.data?.results.map((item,index)=>{const message=copilotInvokeFailureMessage(item.status,item.errorCode);return message?<Alert key={index}>{message}</Alert>:item.status==="DISABLED"?<p key={index}>AI chưa được bật.</p>:null;})}
    {signals.isPending?<p role="status">Đang tải tín hiệu…</p>:signals.isError?<Alert>{copilotErrorMessage(signals.error)}</Alert>:signals.data.length===0?<p>Chưa có tín hiệu khách hàng.</p>:<ul>{signals.data.map(signal=><li key={signal.id}>
      <h3>{signal.signalType==="INTENT"?"Ý định":"Cảm xúc"}: {labels[signal.value]??signal.value}</h3>
      <p>{signal.source==="AI"?<>Do AI tạo — cần kiểm tra. </>:<>Nhân viên thêm. </>}{statuses[signal.status]??signal.status}{signal.confidence!==null?` · Độ tin cậy ${Math.round(signal.confidence*100)}%`:""}</p>
      {signal.evidenceText && <blockquote className="copilot-point">{signal.evidenceText}</blockquote>}
      {canReview && signal.source==="AI" && signal.status==="PROPOSED" && <div>
        {editing===signal.id?<><label htmlFor={`signal-${signal.id}`}>Giá trị tín hiệu</label><select id={`signal-${signal.id}`} value={draft} onChange={event=>setDraft(event.target.value)}>{(values[signal.signalType]??[]).map(value=><option key={value} value={value}>{labels[value]}</option>)}</select><Button onClick={()=>review.mutate({id:signal.id,action:"edit",value:draft})} busy={review.isPending}>Lưu chỉnh sửa</Button><Button variant="secondary" onClick={()=>{setEditing(null);heading.current?.focus();}}>Hủy</Button></>:<><Button onClick={()=>review.mutate({id:signal.id,action:"accept"})} busy={review.isPending}>Chấp nhận</Button><Button variant="secondary" onClick={()=>{setDraft(signal.value);setEditing(signal.id);}}>Sửa</Button><Button variant="secondary" onClick={()=>review.mutate({id:signal.id,action:"reject"})} busy={review.isPending}>Từ chối</Button></>}
      </div>}
    </li>)}</ul>}
    {review.isError && <Alert>{copilotErrorMessage(review.error)}</Alert>}
  </section>;
}
