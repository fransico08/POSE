import { expect, request, test, type APIRequestContext } from "@playwright/test";
import { randomUUID } from "node:crypto";
import { authPath, roleApi } from "./helpers";

test.use({storageState:authPath("s01")});
const origin={Origin:"http://127.0.0.1:3011"};

test("rehearses the CRM AI demo on one synthetic customer",async({page,browser})=>{
  test.setTimeout(120_000);
  const secret=process.env.POSE_E2E_ZALO_SECRET;if(!secret)throw new Error("Missing isolated webhook secret");
  const admin=await roleApi("admin"),manager=await roleApi("mgr"),sales=await roleApi("s01"),sales2=await roleApi("s02");
  const direct=await request.newContext({baseURL:"http://127.0.0.1:8081"});
  let managerContext:Awaited<ReturnType<typeof browser.newContext>>|undefined;
  const get=async(api:APIRequestContext,path:string)=>{const response=await api.get(path);expect(response.status(),path).toBe(200);return response.json();};
  try {
    const me=(await get(sales,"/api/session/me")).user, next=(await get(sales2,"/api/session/me")).user, mgr=(await get(manager,"/api/session/me")).user;
    const teams=(await get(admin,"/api/backend/teams?size=100")).items as {id:string;managerUserId:string;members:{id:string}[]}[];
    const team=teams.find(item=>item.managerUserId===mgr.id&&item.members.some(member=>member.id===me.id));expect(team).toBeTruthy();
    const offerings=(await get(sales,"/api/backend/offerings?page=0&size=100&active=true")).items as {id:string;name:string;teamId:string}[];
    const offering=offerings.find(item=>item.teamId===team!.id);expect(offering).toBeTruthy();
    const candidates=await get(manager,`/api/backend/customers/owner-candidates?offeringId=${offering!.id}`) as {id:string}[];
    if(!candidates.some(item=>item.id===next.id)){
      const assignment=await manager.post("/api/backend/sales-assignments",{headers:origin,data:{offeringId:offering!.id,salesUserId:next.id,effectiveTo:"2098-12-31T16:59:00Z"}});expect(assignment.status()).toBe(201);
    }
    const key=`E2E-${randomUUID()}`,phone=`09${String(Date.now()).slice(-8)}`,name=`Khách demo ${Date.now()}`;
    const account=await admin.post("/api/backend/channels/accounts",{headers:origin,data:{channelType:"ZALO_SIM",displayName:key,externalAccountId:key,teamId:team!.id,defaultAssigneeUserId:me.id,secretRef:"POSE_CHANNEL_ZALO_SIM_WEBHOOK_SECRET"}});expect(account.status()).toBe(201);
    const webhook=async(messageId:string,text:string)=>direct.post("/api/v1/channels/zalo-sim/webhook",{headers:{"X-POSE-Webhook-Secret":secret},data:{event_name:"user_send_text",timestamp:new Date().toISOString(),app_id:key,sender:{id:`sender-${key}`},recipient:{id:key},message:{msg_id:messageId,text}}});
    const inbound=await webhook(`first-${key}`,`Tôi hỏi học phí, liên hệ ${phone} về ${key}`);expect(inbound.status(),await inbound.text()).toBe(200);const conversation=(await inbound.json()).conversationId as string;
    await page.setViewportSize({width:375,height:812});await page.goto("/app/hoi-thoai");
    await page.getByRole("button",{name:new RegExp(`Tôi hỏi học phí, liên hệ ${phone}`)}).click();
    await page.getByRole("button",{name:"Tạo Lead từ hội thoại"}).click();const dialog=page.getByRole("dialog",{name:"Tạo Lead từ hội thoại"});
    await dialog.getByRole("button",{name:`Dùng số ${phone}`}).click();await dialog.getByLabel("Tên khách *").fill(name);await dialog.getByLabel("Sản phẩm/dịch vụ quan tâm *").selectOption(offering!.id);await dialog.getByRole("button",{name:"Tạo Lead",exact:true}).click();
    await expect(page.getByRole("link",{name:"Mở Lead"})).toBeVisible();const signals=page.getByRole("region",{name:"Tín hiệu khách hàng"});
    await signals.getByRole("button",{name:"Trích tín hiệu"}).click();await expect(signals.getByRole("heading",{name:"Ý định: Hỏi giá"})).toBeVisible();
    await signals.getByRole("button",{name:"Chấp nhận"}).click();await expect(signals.getByText(/Đã chấp nhận/)).toBeVisible();
    const metrics=async()=>{
      const result=await get(sales,"/api/backend/dashboard/kpis?metricIds=E04_FIRST_MESSAGE_RESPONSE_TIME,E05_AI_SUGGESTION_ACCEPTANCE_RATE");
      const rows=result.metrics as {metricId:string;dimensions:{channelType?:string;kind?:string};numerator:number;denominator:number}[];
      const e04=rows.find(row=>row.metricId.startsWith("E04")&&row.dimensions.channelType==="ZALO_SIM");
      const e05=rows.find(row=>row.metricId.startsWith("E05")&&row.dimensions.kind==="REPLY_DRAFT");
      expect(e04).toBeTruthy();return {e04:{n:e04!.numerator,d:e04!.denominator},e05:{n:e05?.numerator??0,d:e05?.denominator??0}};
    };
    const before=await metrics();const copilot=page.getByRole("region",{name:"Copilot"});await copilot.getByRole("button",{name:"Soạn bản nháp"}).click();await expect(copilot.getByText("Do AI tạo — cần kiểm tra")).toBeVisible();await copilot.getByRole("button",{name:"Dùng bản nháp"}).click();
    const reply=page.getByLabel("Trả lời");await expect(reply).not.toHaveValue("");const draft=await reply.inputValue();await page.getByRole("button",{name:"Gửi trả lời"}).click();await expect(page.locator(".inbox-message-text").getByText(draft)).toBeVisible();
    const after=await metrics();expect(after.e04.n).toBe(before.e04.n+1);expect(after.e04.d).toBe(before.e04.d);expect(after.e05.n).toBe(before.e05.n+1);expect(after.e05.d).toBe(before.e05.d+1);
    await page.getByRole("link",{name:"Mở Lead"}).click();await page.getByRole("button",{name:"Xác nhận đủ điều kiện"}).click();await page.getByRole("dialog",{name:"Xác nhận đủ điều kiện"}).getByRole("button",{name:"Xác nhận"}).click();
    await page.getByRole("button",{name:"Chuyển thành khách hàng"}).click();await page.getByLabel("Loại khách hàng *").selectOption("INDIVIDUAL");await page.getByRole("button",{name:"Xác nhận chuyển"}).click();await expect(page).toHaveURL(/\/app\/khach-hang\/[0-9a-f-]{36}$/i);const customer=page.url().split("/").pop()!;
    const followup=await webhook(`next-${key}`,"Cảm ơn, tôi cần tư vấn thêm.");expect(followup.status(),await followup.text()).toBe(200);
    await page.goto(`/app/khach-hang-360/${customer}`);await expect(page.getByRole("link",{name:"Hội thoại Zalo"}).first()).toBeVisible();await expect(page.getByRole("region",{name:"Tín hiệu khách hàng"}).getByRole("heading",{name:"Ý định: Hỏi giá"})).toBeVisible();
    expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);
    await page.goto(`/app/khach-hang/${customer}`);await page.getByRole("link",{name:"Thêm cơ hội"}).click();await page.locator("#opp-title").fill(`Cơ hội ${key}`);await page.locator("#opp-expectedCloseDate").fill("31/12/2099");const line=page.locator("fieldset").first();await line.getByLabel("Sản phẩm/dịch vụ *").selectOption(offering!.id);await line.getByLabel("Đơn giá *").fill("12000000");await page.getByRole("button",{name:"Lưu cơ hội"}).click();await expect(page.getByRole("heading",{name:`Cơ hội ${key}`})).toBeVisible();
    const opportunity=page.url().split("/").pop()!;const previous=await get(manager,`/api/backend/opportunities/${opportunity}`);expect(previous.owner.id).toBe(me.id);
    managerContext=await browser.newContext({storageState:authPath("mgr"),viewport:{width:375,height:812}});const mp=await managerContext.newPage();await mp.goto(`/app/khach-hang-360/${customer}`);await expect(mp.getByRole("heading",{name,level:1})).toBeVisible();await mp.getByRole("button",{name:"Đổi người phụ trách"}).click();
    const handover=mp.getByRole("dialog",{name:"Đổi người phụ trách"});await handover.getByLabel("Người phụ trách mới *").selectOption(next.id);await handover.getByLabel("Lý do bàn giao *").fill("Điều phối hồ sơ demo");await handover.getByRole("button",{name:"Xác nhận đổi người phụ trách"}).click();
    const banner=mp.getByRole("status").filter({hasText:`Đã đổi người phụ trách sang ${next.fullName}.`});await expect(banner).toBeVisible();expect((await get(manager,`/api/backend/opportunities/${opportunity}`)).owner.id).toBe(next.id);
    await banner.getByRole("button",{name:"Soạn hồ sơ bàn giao"}).click();const brief=mp.getByRole("article",{name:"Gợi ý AI Copilot"});await expect(brief).toBeVisible();await expect(brief).toContainText("Cơ hội cần tiếp nhận");expect(await mp.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);
    await mp.goto("/app/bao-cao");await expect(mp.getByRole("heading",{name:"Giá trị và rò rỉ"})).toBeVisible();expect(await mp.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);
    const list=await get(manager,"/api/backend/conversations?size=100");expect(list.items.some((item:{id:string;customerId:string})=>item.id===conversation&&item.customerId===customer)).toBe(true);
  } finally {await Promise.all([admin.dispose(),manager.dispose(),sales.dispose(),sales2.dispose(),direct.dispose(),...(managerContext?[managerContext.close()]:[])]);}
});
