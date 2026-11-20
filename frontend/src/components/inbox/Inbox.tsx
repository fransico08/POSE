"use client";
import Link from "next/link";
import { useEffect, useRef, useState } from "react";
import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useSession } from "@/components/session/SessionGate";
import { CustomerSignalsSection } from "@/components/customer360/CustomerSignalsSection";
import { InboxCopilot } from "@/components/inbox/InboxCopilot";
import { CreateLeadDialog } from "@/components/inbox/CreateLeadDialog";
import { Alert } from "@/components/ui/Alert";
import { Button } from "@/components/ui/Button";
import { api } from "@/lib/api";
import { ApiError } from "@/lib/api/client";
import { useDebouncedValue } from "@/lib/use-debounced-value";

type Conversation = { id:string; channelType:string; channelName:string; status:string; assignedUserId:string|null; leadId:string|null; customerId:string|null; lastMessageAt:string|null; lastMessagePreview:string|null; lastMessageDirection:string|null };
type Message = { id:string; direction:string; content:string; deliveryStatus:string; occurredAt:string };
type Page<T> = { items:T[]; page:number; totalPages:number; totalItems:number };
type Profile = { id:string; displayName:string };
type Assignee = { id:string; fullName:string };
export const inboxChannelLabel=(value:string)=>({TELEGRAM:"Telegram",MESSENGER:"Messenger",ZALO_SIM:"Zalo mô phỏng"}[value]??value);
const channel=inboxChannelLabel;
const statusLabel=(value:string)=>({OPEN:"Đang mở",PENDING:"Đang chờ",CLOSED:"Đã đóng"}[value]??value);
const date=(value:string|null)=>value?new Intl.DateTimeFormat("vi-VN",{dateStyle:"short",timeStyle:"short",timeZone:"Asia/Ho_Chi_Minh"}).format(new Date(value)):"Chưa có tin nhắn";
export const inboxPollingInterval=()=>typeof document!=="undefined"&&document.visibilityState==="hidden"?false:15000;
function errorMessage(error:unknown) {
  if(error instanceof ApiError) {
    const messages:Record<string,string>={FORBIDDEN_PERMISSION:"Bạn không có quyền thực hiện thao tác này.",FORBIDDEN_SCOPE:"Bạn không có quyền truy cập hội thoại này.",RESOURCE_NOT_FOUND:"Không tìm thấy hội thoại hoặc bạn không có quyền xem.",CONVERSATION_ALREADY_LINKED:"Hội thoại đã được gắn với hồ sơ khác.",CONVERSATION_CLOSED:"Hội thoại đã đóng, không thể gửi tin.",CHANNEL_ACCOUNT_INACTIVE:"Kênh không hoạt động, không thể gửi tin.",VALIDATION_ERROR:"Vui lòng kiểm tra thông tin đã nhập."};
    return messages[error.code]??"Không thể kết nối hoặc xử lý yêu cầu. Vui lòng thử lại.";
  }
  return "Không thể kết nối. Vui lòng thử lại.";
}
const request=<T,>(url:string,init?:RequestInit)=>api.request<T>(url,init,{suppressForbiddenNavigation:true});
const post=(body:object):RequestInit=>({method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify(body)});

export function Inbox() {
  const {user}=useSession();const cache=useQueryClient();
  const allowed=user.permissions.includes("conversation.read")&&!user.roles.includes("DATA_STAFF");
  const canAssign=allowed&&user.permissions.includes("conversation.assign")&&user.roles.includes("MANAGER");
  const canReply=allowed&&user.permissions.includes("conversation.reply");
  const [filter,setFilter]=useState("");const [mine,setMine]=useState(false);const [page,setPage]=useState(0);
  const [selectedId,setSelectedId]=useState<string|null>(null);const [lastSelected,setLastSelected]=useState<Conversation|null>(null);const [content,setContent]=useState("");
  const [kind,setKind]=useState<"customers"|"leads">("customers");const [search,setSearch]=useState("");const term=useDebouncedValue(search);const [profileId,setProfileId]=useState("");const [assignee,setAssignee]=useState("");
  const [createLeadOpen,setCreateLeadOpen]=useState(false);const [focusCreatedProfile,setFocusCreatedProfile]=useState(false);
  const heading=useRef<HTMLHeadingElement>(null);const listHeading=useRef<HTMLHeadingElement>(null);const replyField=useRef<HTMLTextAreaElement>(null);const profileHeading=useRef<HTMLHeadingElement>(null);
  const prefix=["inbox",user.organization.id,user.id];
  const params=new URLSearchParams({page:String(page),size:"20",assignedToMe:String(mine)});if(filter)params.set("status",filter);
  const list=useQuery({queryKey:[...prefix,"list",params.toString()],queryFn:()=>request<Page<Conversation>>(`/api/backend/conversations?${params}`),enabled:allowed,refetchInterval:inboxPollingInterval,refetchIntervalInBackground:false,retry:false});
  const selected=list.data?.items.find(item=>item.id===selectedId) ?? (lastSelected?.id===selectedId?lastSelected:null);
  const messages=useInfiniteQuery({queryKey:[...prefix,"messages",selected?.id],queryFn:({pageParam})=>request<Page<Message>>(`/api/backend/conversations/${selected!.id}/messages?page=${pageParam}&size=30`),initialPageParam:0,getNextPageParam:last=>last.page+1<last.totalPages?last.page+1:undefined,enabled:allowed&&!!selected,refetchInterval:inboxPollingInterval,refetchIntervalInBackground:false,retry:false});
  const linkedId=selected?.customerId??selected?.leadId;const linkedKind=selected?.customerId?"customers":"leads";
  const profile=useQuery({queryKey:[...prefix,"profile",linkedKind,linkedId],queryFn:()=>request<Profile>(`/api/backend/${linkedKind}/${linkedId}`),enabled:allowed&&!!linkedId,retry:false});
  const options=useQuery({queryKey:[...prefix,"profiles",kind,term],queryFn:()=>request<Page<Profile>>(`/api/backend/${kind}?${new URLSearchParams({search:term,page:"0",size:"20"})}`),enabled:allowed&&!!selected&&!linkedId&&user.permissions.includes(kind==="customers"?"customer.read":"lead.read"),retry:false});
  const people=useQuery({queryKey:[...prefix,"assignees",selected?.id],queryFn:()=>request<Assignee[]>(`/api/backend/conversations/${selected!.id}/assignees`),enabled:canAssign&&!!selected,retry:false});
  const refresh=()=>{void cache.invalidateQueries({queryKey:prefix});};
  const reply=useMutation({mutationFn:()=>request(`/api/backend/conversations/${selected!.id}/messages`,post({content:content.trim()})),onSuccess:()=>{setContent("");refresh();replyField.current?.focus();}});
  const updateSelected=(value:Conversation)=>{const updated={...value,lastMessagePreview:selected?.lastMessagePreview??null,lastMessageDirection:selected?.lastMessageDirection??null};setLastSelected(updated);cache.setQueryData<Page<Conversation>>([...prefix,"list",params.toString()],old=>old?{...old,items:old.items.map(item=>item.id===value.id?updated:item)}:old);};
  const link=useMutation({mutationFn:()=>request<Conversation>(`/api/backend/conversations/${selected!.id}/link`,post(kind==="customers"?{customerId:profileId}:{leadId:profileId})),onSuccess:value=>{updateSelected(value);setProfileId("");refresh();}});
  const assign=useMutation({mutationFn:()=>request<Conversation>(`/api/backend/conversations/${selected!.id}/assign`,post({assignedUserId:assignee})),onSuccess:value=>{updateSelected(value);setAssignee("");refresh();}});
  const listKey=JSON.stringify([...prefix,"list",params.toString()]);
  useEffect(()=>cache.getQueryCache().subscribe(event=>{
    if(event.type!=="updated"||event.action.type!=="success"||JSON.stringify(event.query.queryKey)!==listKey)return;
    const latest=(event.query.state.data as Page<Conversation>|undefined)?.items.find(item=>item.id===selectedId);
    if(latest)setLastSelected(latest);
  }),[cache,listKey,selectedId]);
  useEffect(()=>{if(selectedId)heading.current?.focus();},[selectedId]);
  useEffect(()=>{if(!focusCreatedProfile||createLeadOpen||!linkedId)return;const frame=requestAnimationFrame(()=>{profileHeading.current?.focus();setFocusCreatedProfile(false);});return()=>cancelAnimationFrame(frame);},[focusCreatedProfile,createLeadOpen,linkedId]);
  if(!allowed)return <div className="offering-page"><h1>Hội thoại</h1><Alert>Bạn không có quyền xem hội thoại.</Alert></div>;
  const allMessages=Array.from(new Map(messages.data?.pages.flatMap(item=>item.items).map(item=>[item.id,item])??[]).values()).sort((a,b)=>Date.parse(a.occurredAt)-Date.parse(b.occurredAt)||(a.id<b.id?-1:a.id>b.id?1:0));
  return <div className="inbox-page"><header><h1>Hội thoại</h1><p>Tiếp nhận và theo dõi trao đổi với khách hàng.</p></header>
    <div className={`inbox-layout ${selected?"inbox-has-selection":""}`}>
      <section className="inbox-list offering-section" aria-labelledby="inbox-list-heading"><h2 id="inbox-list-heading" ref={listHeading} tabIndex={-1}>Danh sách hội thoại</h2>
        <div className="field"><label htmlFor="inbox-status">Trạng thái</label><select className="input" id="inbox-status" value={filter} onChange={e=>{setFilter(e.target.value);setPage(0);}}><option value="">Tất cả</option><option value="OPEN">Đang mở</option><option value="PENDING">Đang chờ</option><option value="CLOSED">Đã đóng</option></select></div>
        <label className="inbox-mine"><input type="checkbox" checked={mine} onChange={e=>{setMine(e.target.checked);setPage(0);}}/> Được giao cho tôi</label>
        {list.isPending?<p role="status">Đang tải hội thoại…</p>:list.isError?<Alert>{errorMessage(list.error)}</Alert>:list.data.items.length===0?<p>Chưa có hội thoại phù hợp.</p>:<ul className="inbox-conversations">{list.data.items.map(item=><li key={item.id}><button type="button" className="inbox-conversation" aria-pressed={selectedId===item.id} onClick={()=>{setSelectedId(item.id);setLastSelected(item);setContent("");setProfileId("");}}><strong>{item.lastMessagePreview??"Chưa có tin nhắn"}</strong><span>{channel(item.channelType)} · {item.channelName} · {statusLabel(item.status)}</span>{item.lastMessageDirection==="INBOUND"&&<span>Chờ phản hồi</span>}<span>{!item.customerId&&!item.leadId?"Chưa gắn hồ sơ":"Đã gắn hồ sơ"}</span><time>{date(item.lastMessageAt)}</time></button></li>)}</ul>}
        {list.data&&<div className="inbox-pagination"><Button variant="secondary" disabled={page===0} onClick={()=>setPage(page-1)}>Trước</Button><span>Trang {page+1} / {Math.max(1,list.data.totalPages)}</span><Button variant="secondary" disabled={page+1>=list.data.totalPages} onClick={()=>setPage(page+1)}>Sau</Button></div>}
      </section>
      <section className="inbox-thread offering-section" aria-labelledby="inbox-thread-heading"><Button className="inbox-back" variant="secondary" onClick={()=>{setSelectedId(null);setLastSelected(null);requestAnimationFrame(()=>listHeading.current?.focus());}}>Quay lại danh sách</Button><h2 id="inbox-thread-heading" ref={heading} tabIndex={-1}>{selected?selected.channelName:"Nội dung hội thoại"}</h2>
        {!selected?<p>Chọn hội thoại để xem tin nhắn.</p>:messages.isPending?<p role="status">Đang tải tin nhắn…</p>:messages.isError?<Alert>{errorMessage(messages.error)}</Alert>:<><p>{channel(selected.channelType)} · {statusLabel(selected.status)}</p>{messages.hasNextPage&&<Button variant="secondary" busy={messages.isFetchingNextPage} onClick={()=>void messages.fetchNextPage()}>Tải tin cũ hơn</Button>}{allMessages.length===0?<p>Chưa có tin nhắn.</p>:<ol className="inbox-messages" aria-label="Tin nhắn">{allMessages.map(message=><li key={message.id} id={`inbox-message-${message.id}`} tabIndex={-1} className={message.direction==="INBOUND"?"inbox-message-in":"inbox-message-out"}><p><strong>{message.direction==="INBOUND"?"Khách hàng":"Nhân viên"}</strong> · <time>{date(message.occurredAt)}</time> · {message.deliveryStatus==="SENT"?"Đã gửi":message.deliveryStatus==="RECEIVED"?"Đã nhận":message.deliveryStatus}</p><p className="inbox-message-text">{message.content}</p></li>)}</ol>}
        {canReply&&selected.status!=="CLOSED"&&<InboxCopilot key={selected.id} conversationId={selected.id} currentReply={content} onUse={text=>{setContent(text);requestAnimationFrame(()=>replyField.current?.focus());}}/>}
        {canReply&&(selected.status==="CLOSED"?<p>Hội thoại đã đóng, không thể gửi tin.</p>:<form onSubmit={event=>{event.preventDefault();if(content.trim()&&!reply.isPending)reply.mutate();}}><div className="field"><label htmlFor="inbox-reply">Trả lời</label><textarea className="input" id="inbox-reply" ref={replyField} maxLength={4000} value={content} onChange={e=>setContent(e.target.value)} aria-describedby="inbox-reply-count"/><p id="inbox-reply-count" className="helper">{content.length}/4000 ký tự</p></div><Button type="submit" busy={reply.isPending} disabled={!content.trim()}>Gửi trả lời</Button>{reply.isPending&&<p role="status">Đang gửi tin…</p>}{reply.isError&&<Alert>{errorMessage(reply.error)}</Alert>}</form>)}</>}
      </section>
      {selected&&<aside className="inbox-profile offering-section" aria-labelledby="inbox-profile-heading"><h2 id="inbox-profile-heading" ref={profileHeading} tabIndex={-1}>Hồ sơ liên quan</h2>{linkedId?<>{profile.isPending?<p role="status">Đang tải hồ sơ…</p>:profile.isError?<p>Không thể xem hồ sơ trong phạm vi hiện tại.</p>:<><p>{profile.data.displayName}</p><Link href={selected.customerId?`/app/khach-hang-360/${linkedId}`:`/app/lead/${linkedId}`}>Mở {selected.customerId?"hồ sơ khách hàng 360":"Lead"}</Link>{(selected.customerId?(user.permissions.includes("ai.assist.use")&&user.permissions.includes("customer_360.read")):user.permissions.includes("lead.read"))&&<CustomerSignalsSection key={`${linkedKind}:${linkedId}`} customerId={linkedId} subjectType={selected.customerId?"CUSTOMER":"LEAD"}/>}</>}</>:<><p>Chưa gắn hồ sơ</p><p>Gắn hồ sơ để xem tín hiệu.</p><div className="field"><label htmlFor="inbox-kind">Loại hồ sơ</label><select className="input" id="inbox-kind" value={kind} onChange={e=>{setKind(e.target.value as "customers"|"leads");setProfileId("");}}><option value="customers">Khách hàng</option><option value="leads">Lead</option></select></div><div className="field"><label htmlFor="inbox-search">Tìm hồ sơ</label><input className="input" id="inbox-search" value={search} onChange={e=>setSearch(e.target.value)}/></div><div className="field"><label htmlFor="inbox-profile">Chọn hồ sơ</label><select className="input" id="inbox-profile" value={profileId} onChange={e=>setProfileId(e.target.value)}><option value="">Chọn hồ sơ</option>{options.data?.items.map(item=><option key={item.id} value={item.id}>{item.displayName}</option>)}</select></div>{options.isError&&<Alert>{errorMessage(options.error)}</Alert>}<Button disabled={!profileId} busy={link.isPending} onClick={()=>link.mutate()}>Gắn hồ sơ</Button>{link.isError&&<Alert>{errorMessage(link.error)}</Alert>}{user.permissions.includes("lead.write")&&<p><Button onClick={()=>setCreateLeadOpen(true)}>Tạo Lead từ hội thoại</Button></p>}</>}
        {canAssign&&<div className="inbox-assign"><h3>Giao lại hội thoại</h3>{people.isPending?<p role="status">Đang tải người nhận…</p>:people.isError?<Alert>{errorMessage(people.error)}</Alert>:<><label htmlFor="inbox-assignee">Người nhận</label><select className="input" id="inbox-assignee" value={assignee} onChange={e=>setAssignee(e.target.value)}><option value="">Chọn người nhận</option>{people.data.map(person=><option key={person.id} value={person.id}>{person.fullName}</option>)}</select><Button disabled={!assignee} busy={assign.isPending} onClick={()=>assign.mutate()}>Giao lại</Button>{assign.isError&&<Alert>{errorMessage(assign.error)}</Alert>}</>}</div>}
      </aside>}
    </div>{selected&&createLeadOpen&&<CreateLeadDialog<Conversation> key={selected.id} open onClose={()=>setCreateLeadOpen(false)} conversationId={selected.id} assignedUserId={selected.assignedUserId} messages={allMessages} onCreated={result=>{updateSelected(result.conversation);setFocusCreatedProfile(true);setCreateLeadOpen(false);refresh();void cache.invalidateQueries({queryKey:["lead",user.organization.id,user.id]});}}/>}
  </div>;
}
