import { expect, request, test } from "@playwright/test";
import { randomUUID } from "node:crypto";
import { authPath, roleApi } from "./helpers";

test.use({storageState:authPath("s01")});
test("receives a simulated message, replies and links a customer within scope",async({page},testInfo)=>{
  const consoleErrors:string[]=[];page.on("pageerror",error=>consoleErrors.push(error.message));page.on("console",message=>{if(message.type()==="error")consoleErrors.push(message.text());});
  const secret=process.env.POSE_E2E_ZALO_SECRET;if(!secret)throw new Error("Missing isolated webhook secret");
  const admin=await roleApi("admin"), sales=await roleApi("s01"), other=await roleApi("s02"), manager=await roleApi("mgr");
  const direct=await request.newContext({baseURL:"http://127.0.0.1:8081"});
  try {
    const salesUser=(await (await sales.get("/api/session/me")).json()).user;
    const managerUser=(await (await manager.get("/api/session/me")).json()).user;
    const teamsResponse=await admin.get("/api/backend/teams?size=100");expect(teamsResponse.ok()).toBeTruthy();
    const teams=(await teamsResponse.json()).items as {id:string;managerUserId:string;members:{id:string}[]}[];
    const team=teams.find(item=>item.managerUserId===managerUser.id&&item.members.some(member=>member.id===salesUser.id));expect(team).toBeTruthy();
    const customersResponse=await sales.get("/api/backend/customers?size=20");expect(customersResponse.ok()).toBeTruthy();
    const customer=(await customersResponse.json()).items[0] as {id:string;displayName:string};expect(customer).toBeTruthy();
    const key=`E2E-${randomUUID()}`;
    const account=await admin.post("/api/backend/channels/accounts",{headers:{Origin:"http://127.0.0.1:3011"},data:{channelType:"ZALO_SIM",displayName:key,externalAccountId:key,teamId:team!.id,defaultAssigneeUserId:salesUser.id,secretRef:"POSE_CHANNEL_ZALO_SIM_WEBHOOK_SECRET"}});expect(account.status()).toBe(201);
    const incoming=await direct.post("/api/v1/channels/zalo-sim/webhook",{headers:{"X-POSE-Webhook-Secret":secret},data:{event_name:"user_send_text",timestamp:new Date().toISOString(),app_id:key,sender:{id:`sender-${key}`},recipient:{id:key},message:{msg_id:`message-${key}`,text:`Xin hỏi ${key}`}}});expect(incoming.ok()).toBeTruthy();
    const conversation=(await incoming.json()).conversationId as string;
    const otherList=await other.get("/api/backend/conversations?size=100");expect(otherList.ok()).toBeTruthy();expect(JSON.stringify(await otherList.json())).not.toContain(conversation);
    await page.setViewportSize({width:375,height:812});await page.goto("/app/hoi-thoai");
    const conversationButton=page.getByRole("button",{name:new RegExp(`Xin hỏi ${key}.*Chờ phản hồi`)});await expect(conversationButton).toBeVisible();await page.screenshot({path:testInfo.outputPath("inbox-list-mobile.png")});
    await conversationButton.click();await expect(page.getByRole("list",{name:"Tin nhắn"}).getByText(`Xin hỏi ${key}`)).toBeVisible();await expect(page.getByRole("heading",{name:key})).toBeFocused();await page.keyboard.press("Tab");expect(await page.evaluate(()=>document.activeElement!==document.body)).toBe(true);
    let sentRequests=0;page.on("request",req=>{if(req.method()==="POST"&&req.url().includes(`/conversations/${conversation}/messages`))sentRequests++;});
    const copilot=page.getByRole("region",{name:"Copilot"});await expect(copilot).toBeVisible();await copilot.getByRole("button",{name:"Soạn bản nháp"}).click();
    await expect(copilot.getByText("Do AI tạo — cần kiểm tra")).toBeVisible();await copilot.getByRole("button",{name:"Dùng bản nháp"}).click();
    const reply=page.getByLabel("Trả lời");await expect(reply).not.toHaveValue("");expect(sentRequests).toBe(0);await expect(reply).toBeFocused();
    const today=new Intl.DateTimeFormat("en-CA",{timeZone:"Asia/Ho_Chi_Minh",year:"numeric",month:"2-digit",day:"2-digit"}).format(new Date());
    const midnight=Date.parse(`${today}T00:00:00+07:00`);
    const period=new URLSearchParams({start:new Date(midnight-29*86400000).toISOString(),end:new Date(midnight+86400000).toISOString(),timezone:"Asia/Ho_Chi_Minh",metricIds:"E04_FIRST_MESSAGE_RESPONSE_TIME"});
    const readE04=async()=>{const response=await sales.get(`/api/backend/dashboard/kpis?${period}`);expect(response.status()).toBe(200);const data=await response.json();const metric=data.metrics.find((item:{dimensions:{channelType?:string}})=>item.dimensions.channelType==="ZALO_SIM");expect(metric).toBeTruthy();return {numerator:metric.numerator as number,denominator:metric.denominator as number};};
    const before=await readE04();
    const drafted=await reply.inputValue();await page.getByRole("button",{name:"Gửi trả lời"}).click();await expect(page.getByText(drafted)).toBeVisible();expect(sentRequests).toBe(1);await expect(reply).toBeFocused();
    const after=await readE04();expect(after.numerator).toBe(before.numerator+1);expect(after.denominator).toBe(before.denominator);
    await page.goto("/app/bao-cao");const e04=page.locator('[data-metric="E04"]');const zalo=e04.getByRole("row").filter({hasText:"Zalo mô phỏng"});await expect(zalo).toContainText(`Đã phản hồi ${after.numerator}/${after.denominator} hội thoại`);
    await page.goto("/app/hoi-thoai");await page.getByRole("button",{name:new RegExp(key)}).click();
    await page.getByLabel("Tìm hồ sơ").fill(customer.displayName);await expect(page.getByRole("option",{name:customer.displayName})).toHaveCount(1);
    await page.getByLabel("Chọn hồ sơ").selectOption(customer.id);await page.getByRole("button",{name:"Gắn hồ sơ"}).click();await expect(page.getByRole("link",{name:"Mở hồ sơ khách hàng 360"})).toBeVisible();
    const signals=page.getByRole("region",{name:"Tín hiệu khách hàng"});await expect(signals).toBeVisible();await signals.getByRole("button",{name:"Trích tín hiệu"}).click();await expect(signals.getByText("Đã trích tín hiệu từ tin nhắn.")).toBeVisible();await expect(signals.getByText(/Do AI tạo — cần kiểm tra/).first()).toBeVisible();
    const dashboard=await manager.get("/api/backend/dashboard/kpis");expect(dashboard.status()).toBe(200);
    expect(await page.evaluate(()=>document.documentElement.scrollWidth<=window.innerWidth)).toBe(true);await page.screenshot({path:testInfo.outputPath("inbox-mobile.png")});
    await page.getByRole("link",{name:"Mở hồ sơ khách hàng 360"}).click();await expect(page.getByRole("heading",{name:"Tương tác",exact:true})).toBeVisible();
    await expect(page.getByRole("link",{name:"Hội thoại Zalo"}).first()).toBeVisible();await expect(page.getByText("Hệ thống",{exact:true}).first()).toBeVisible();
    await page.goto("/app/bao-cao");await expect(page.getByRole("heading",{name:"Giá trị và rò rỉ"})).toBeVisible();expect(consoleErrors).toEqual([]);
  } finally {await Promise.all([admin.dispose(),sales.dispose(),other.dispose(),manager.dispose(),direct.dispose()]);}
});

test("creates and links a lead from an inbound conversation",async({page})=>{
  const secret=process.env.POSE_E2E_ZALO_SECRET;if(!secret)throw new Error("Missing isolated webhook secret");
  const admin=await roleApi("admin"),sales=await roleApi("s01"),manager=await roleApi("mgr");
  const direct=await request.newContext({baseURL:"http://127.0.0.1:8081"});
  try {
    const salesUser=(await (await sales.get("/api/session/me")).json()).user;
    const managerUser=(await (await manager.get("/api/session/me")).json()).user;
    const teams=(await (await admin.get("/api/backend/teams?size=100")).json()).items as {id:string;managerUserId:string;members:{id:string}[]}[];
    const team=teams.find(item=>item.managerUserId===managerUser.id&&item.members.some(member=>member.id===salesUser.id));expect(team).toBeTruthy();
    const offers=await sales.get("/api/backend/offerings?page=0&size=100&active=true");expect(offers.ok()).toBeTruthy();const offering=(await offers.json()).items[0] as {id:string;name:string};expect(offering).toBeTruthy();
    const key=`E2E-${randomUUID()}`,phone=`09${String(Date.now()).slice(-8)}`;
    const account=await admin.post("/api/backend/channels/accounts",{headers:{Origin:"http://127.0.0.1:3011"},data:{channelType:"ZALO_SIM",displayName:key,externalAccountId:key,teamId:team!.id,defaultAssigneeUserId:salesUser.id,secretRef:"POSE_CHANNEL_ZALO_SIM_WEBHOOK_SECRET"}});expect(account.status()).toBe(201);
    const incoming=await direct.post("/api/v1/channels/zalo-sim/webhook",{headers:{"X-POSE-Webhook-Secret":secret},data:{event_name:"user_send_text",timestamp:new Date().toISOString(),app_id:key,sender:{id:`sender-${key}`},recipient:{id:key},message:{msg_id:`message-${key}`,text:`Tôi muốn tư vấn ${phone} về ${key}`}}});expect(incoming.ok()).toBeTruthy();const conversation=(await incoming.json()).conversationId as string;
    await page.setViewportSize({width:375,height:812});await page.goto("/app/hoi-thoai");await page.getByRole("button",{name:new RegExp(`Tôi muốn tư vấn ${phone}`)}).click();
    await page.getByRole("button",{name:"Tạo Lead từ hội thoại"}).click();const dialog=page.getByRole("dialog",{name:"Tạo Lead từ hội thoại"});
    expect(await page.evaluate(()=>document.documentElement.scrollWidth<=window.innerWidth)).toBe(true);
    await expect(dialog.getByLabel("Số điện thoại")).toHaveValue("");await dialog.getByRole("button",{name:`Dùng số ${phone}`}).click();
    await dialog.getByLabel("Tên khách *").fill(`Khách ${key}`);await dialog.getByLabel("Sản phẩm/dịch vụ quan tâm *").selectOption(offering.id);
    await dialog.getByRole("button",{name:"Tạo Lead",exact:true}).click();await expect(page.getByRole("link",{name:"Mở Lead"})).toBeVisible();
    const signals=page.getByRole("region",{name:"Tín hiệu khách hàng"});await expect(signals).toBeVisible();await signals.getByRole("button",{name:"Trích tín hiệu"}).click();await expect(signals.getByText("Đã trích tín hiệu từ tin nhắn.")).toBeVisible();
    expect(await page.evaluate(()=>document.documentElement.scrollWidth<=window.innerWidth)).toBe(true);
    await page.getByLabel("Trả lời").fill("Tôi đã tiếp nhận yêu cầu.");await page.getByRole("button",{name:"Gửi trả lời"}).click();await expect(page.getByText("Tôi đã tiếp nhận yêu cầu.")).toBeVisible();
    await page.getByRole("link",{name:"Mở Lead"}).click();await page.getByRole("button",{name:"Xác nhận đủ điều kiện"}).click();await page.getByRole("dialog",{name:"Xác nhận đủ điều kiện"}).getByRole("button",{name:"Xác nhận"}).click();
    await page.getByRole("button",{name:"Chuyển thành khách hàng"}).click();await page.getByLabel("Loại khách hàng *").selectOption("INDIVIDUAL");await page.getByRole("button",{name:"Xác nhận chuyển"}).click();await expect(page).toHaveURL(/\/app\/khach-hang\/[0-9a-f-]{36}$/i);const customer=page.url().split("/").pop()!;
    await page.goto("/app/hoi-thoai");await page.getByRole("button",{name:new RegExp(key)}).click();await expect(page.getByRole("link",{name:"Mở hồ sơ khách hàng 360"})).toHaveAttribute("href",`/app/khach-hang-360/${customer}`);
    await expect(page.getByRole("region",{name:"Tín hiệu khách hàng"}).getByText(/Do AI tạo — cần kiểm tra/).first()).toBeVisible();
    await page.getByLabel("Trả lời").fill("Tôi sẽ tiếp tục hỗ trợ bạn.");await page.getByRole("button",{name:"Gửi trả lời"}).click();await expect(page.getByText("Tôi sẽ tiếp tục hỗ trợ bạn.")).toBeVisible();
    const followup=await direct.post("/api/v1/channels/zalo-sim/webhook",{headers:{"X-POSE-Webhook-Secret":secret},data:{event_name:"user_send_text",timestamp:new Date().toISOString(),app_id:key,sender:{id:`sender-${key}`},recipient:{id:key},message:{msg_id:`followup-${key}`,text:"Tôi cần tư vấn thêm."}}});expect(followup.ok()).toBeTruthy();
    const list=await sales.get("/api/backend/conversations?size=100");const row=((await list.json()).items as {id:string;customerId:string;leadId:string|null}[]).find(item=>item.id===conversation);expect(row?.customerId).toBe(customer);expect(row?.leadId).toBeNull();
    await page.getByRole("link",{name:"Mở hồ sơ khách hàng 360"}).click();await expect(page.getByRole("link",{name:"Hội thoại Zalo"}).first()).toBeVisible();
    await expect(page.getByRole("region",{name:"Tín hiệu khách hàng"}).getByText(/Do AI tạo — cần kiểm tra/).first()).toBeVisible();
  } finally {await Promise.all([admin.dispose(),sales.dispose(),manager.dispose(),direct.dispose()]);}
});
