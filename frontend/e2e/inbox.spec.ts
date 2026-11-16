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
    const reply=page.getByLabel("Trả lời");await reply.fill(`Phản hồi ${key}`);await page.getByRole("button",{name:"Gửi trả lời"}).click();await expect(page.getByText(`Phản hồi ${key}`)).toBeVisible();await expect(reply).toBeFocused();
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
