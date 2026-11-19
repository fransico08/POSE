"use client";
import { useEffect, useState, type FormEvent } from "react";
import { useMutation, useQuery } from "@tanstack/react-query";
import { useSession } from "@/components/session/SessionGate";
import { useModal } from "@/components/ui/use-modal";
import { Button } from "@/components/ui/Button";
import { Alert } from "@/components/ui/Alert";
import { api } from "@/lib/api";
import { ApiError } from "@/lib/api/client";
import { crmErrorMessage, crmFieldErrors, getScopedOfferings, mayReadOfferings, sources } from "@/lib/crm";
import { getLeadOwnerCandidates } from "@/lib/leads";

type Message = { direction: string; content: string };
type Created<T> = { lead: { id: string }; conversation: T };
export function CreateLeadDialog<T>({ open, onClose, conversationId, assignedUserId, messages, onCreated }: {
  open: boolean; onClose: () => void; conversationId: string; assignedUserId: string | null; messages: Message[]; onCreated: (result: Created<T>) => void;
}) {
  const { user }=useSession(); const manager=user.roles.includes("MANAGER");
  const [name,setName]=useState(""); const [email,setEmail]=useState(""); const [phone,setPhone]=useState("");
  const [source,setSource]=useState("SOCIAL_MEDIA"); const [offering,setOffering]=useState(""); const [owner,setOwner]=useState("");
  const [errors,setErrors]=useState<Record<string,string>>({}); const modal=useModal(open,()=>{if(!mutation.isPending)onClose();});
  const offerings=useQuery({queryKey:["crm-offering-options",user.organization.id,user.id],queryFn:()=>getScopedOfferings(),enabled:open&&mayReadOfferings(user),retry:false});
  const owners=useQuery({queryKey:["lead",user.organization.id,user.id,"owners",offering],queryFn:()=>getLeadOwnerCandidates(offering),enabled:open&&manager&&!!offering,retry:false});
  useEffect(()=>{if(!manager||!assignedUserId||!owners.data?.some(item=>item.id===assignedUserId))return;const timer=window.setTimeout(()=>setOwner(current=>current||assignedUserId),0);return()=>window.clearTimeout(timer);},[manager,assignedUserId,owners.data]);
  const mutation=useMutation({mutationFn:()=>api.request<Created<T>>(`/api/backend/conversations/${encodeURIComponent(conversationId)}/lead`,{
    method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify({displayName:name.trim(),email:email.trim()||null,phone:phone.trim()||null,source,offeringId:offering,...(manager?{ownerUserId:owner}:{})})
  },{suppressForbiddenNavigation:true}),onSuccess:onCreated,onError:error=>setErrors(crmFieldErrors(error))});
  const inbound=messages.filter(item=>item.direction==="INBOUND").map(item=>item.content).join("\n");
  const emails=[...new Set(inbound.match(/[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/gi)??[])];
  const phones=[...new Set(inbound.match(/(?:\+84|0)[\d .()\-]{8,18}\d/g)??[])];
  function submit(event:FormEvent) {event.preventDefault();if(mutation.isPending)return;const next:Record<string,string>={};
    if(!name.trim())next.displayName="Vui lòng nhập tên khách.";if(!email.trim()&&!phone.trim())next.phone="Nhập số điện thoại hoặc email.";
    if(!offering)next.offeringId="Chọn sản phẩm/dịch vụ.";if(manager&&!owner)next.ownerUserId="Chọn người phụ trách.";
    setErrors(next);if(Object.keys(next).length)return;mutation.mutate();}
  const error=(field:string)=>errors[field]?<p className="offering-field-error" id={`inbox-create-${field}-error`}>{errors[field]}</p>:null;
  const attrs=(field:string)=>({"aria-invalid":Boolean(errors[field]),"aria-describedby":errors[field]?`inbox-create-${field}-error`:undefined});
  return <dialog {...modal} className="dialog inbox-create-dialog" aria-modal="true" aria-labelledby="inbox-create-lead-title">
    <h2 id="inbox-create-lead-title">Tạo Lead từ hội thoại</h2><p>Nhập tên và ít nhất một thông tin liên hệ của khách.</p>
    {mutation.isError&&<Alert>{mutation.error instanceof ApiError&&mutation.error.code==="LEAD_DUPLICATE"?"Thông tin liên hệ có thể trùng. Hãy dùng Gắn hồ sơ để chọn Lead đã có.":crmErrorMessage(mutation.error)}</Alert>}
    <form onSubmit={submit} noValidate>
      <div className="field"><label htmlFor="inbox-create-name">Tên khách *</label><input id="inbox-create-name" className="input" data-autofocus value={name} onChange={e=>setName(e.target.value)} placeholder="Nhập tên khách (hỏi khách nếu chưa biết)" maxLength={180} {...attrs("displayName")}/>{error("displayName")}</div>
      <div className="field"><label htmlFor="inbox-create-email">Email</label><input id="inbox-create-email" className="input" type="email" value={email} onChange={e=>setEmail(e.target.value)} {...attrs("email")}/>{error("email")}</div>
      <div className="field"><label htmlFor="inbox-create-phone">Số điện thoại</label><input id="inbox-create-phone" className="input" type="tel" value={phone} onChange={e=>setPhone(e.target.value)} {...attrs("phone")}/>{error("phone")}</div>
      {emails.length+phones.length===0?<p>Hỏi khách số điện thoại hoặc email trước khi tạo Lead.</p>:<div><p>Thông tin tìm thấy trong tin nhắn — chọn nếu khách xác nhận:</p>{emails.map(value=><Button key={value} type="button" variant="secondary" onClick={()=>setEmail(value)}>Dùng email {value}</Button>)}{phones.map(value=><Button key={value} type="button" variant="secondary" onClick={()=>setPhone(value)}>Dùng số {value}</Button>)}</div>}
      <div className="field"><label htmlFor="inbox-create-source">Nguồn *</label><select id="inbox-create-source" className="input" value={source} onChange={e=>setSource(e.target.value)} {...attrs("source")}>{Object.entries(sources).map(([value,label])=><option key={value} value={value}>{label}</option>)}</select>{error("source")}</div>
      <div className="field"><label htmlFor="inbox-create-offering">Sản phẩm/dịch vụ quan tâm *</label><select id="inbox-create-offering" className="input" value={offering} onChange={e=>{setOffering(e.target.value);setOwner("");}} {...attrs("offeringId")}><option value="">Chọn sản phẩm/dịch vụ</option>{offerings.data?.map(item=><option key={item.id} value={item.id}>{item.name}</option>)}</select>{error("offeringId")}{offerings.isError&&<Alert>Không tải được sản phẩm/dịch vụ.</Alert>}</div>
      {manager&&<div className="field"><label htmlFor="inbox-create-owner">Người phụ trách *</label><select id="inbox-create-owner" className="input" value={owner} onChange={e=>setOwner(e.target.value)} {...attrs("ownerUserId")}><option value="">Chọn người phụ trách</option>{owners.data?.map(item=><option key={item.id} value={item.id}>{item.fullName}</option>)}</select>{error("ownerUserId")}{owners.isError&&<Alert>Không tải được người phụ trách.</Alert>}</div>}
      <footer className="dialog-actions"><Button type="button" variant="secondary" disabled={mutation.isPending} onClick={onClose}>Hủy</Button><Button type="submit" busy={mutation.isPending}>Tạo Lead</Button></footer>
      {mutation.isPending&&<p role="status">Đang tạo và gắn Lead…</p>}
    </form>
  </dialog>;
}
