import { expect, test } from "@playwright/test";
import { execFileSync } from "node:child_process";
import { randomUUID } from "node:crypto";
import path from "node:path";
import { authPath, roleApi } from "./helpers";

test.use({ storageState: authPath("mgr") });

test("manager extracts and reviews a customer signal from a linked inbound message", async ({ page }) => {
  const database=process.env.POSE_E2E_RUN_ID ?? "";
  if (!/^pose_e2e_[0-9]+_[0-9]+$/.test(database)) throw new Error("Isolated E2E database is required");
  const psql="C:\\Program Files\\PostgreSQL\\18\\bin\\psql.exe";
  const args=["-w","-h","127.0.0.1","-p","54329","-U","pose_auth_test","-d",database,"-v","ON_ERROR_STOP=1"];
  const directory=execFileSync(psql,[...args,"-tAc","SHOW data_directory"],{encoding:"utf8"}).trim();
  if(path.resolve(directory).toLowerCase()!==path.resolve("../backend/target/auth-postgres-test").toLowerCase()) throw new Error("Unexpected PostgreSQL cluster");
  const manager=await roleApi("mgr");
  try {
    const me=await (await manager.get("/api/session/me")).json();
    const customers=await (await manager.get("/api/backend/customers?page=0&size=1")).json();
    const organization=String(me.user.organization.id),customer=String(customers.items[0].id);
    for(const id of [organization,customer]) if(!/^[0-9a-f-]{36}$/i.test(id))throw new Error("Invalid fixture UUID");
    const account=randomUUID(),conversation=randomUUID(),message=randomUUID();
    const messageText="Xin hỏi giá sản phẩm", messageHex=Buffer.from(messageText,"utf8").toString("hex");
    const sql=`INSERT INTO channel_accounts(id,organization_id,channel_type,display_name,external_account_id,status,secret_ref,created_at,updated_at) VALUES ('${account}','${organization}','TELEGRAM','E2E signals','${account}','ACTIVE','E2E_SECRET',now(),now());
      INSERT INTO conversations(id,organization_id,channel_account_id,external_thread_id,customer_id,status,created_at,updated_at) VALUES ('${conversation}','${organization}','${account}','${conversation}','${customer}','OPEN',now(),now());
      INSERT INTO conversation_messages(id,organization_id,conversation_id,direction,external_message_id,content_type,content,delivery_status,occurred_at,created_at) VALUES ('${message}','${organization}','${conversation}','INBOUND','${message}','text',convert_from(decode('${messageHex}','hex'),'UTF8'),'RECEIVED',now(),now());`;
    execFileSync(psql,[...args,"-q","-c",sql],{stdio:"pipe"});
    await page.goto(`/app/khach-hang-360/${customer}`);
    const section=page.getByRole("region",{name:"Tín hiệu khách hàng"});
    await expect(section).toBeVisible();await section.getByRole("button",{name:"Trích tín hiệu"}).click();
    await expect(section.getByText("Ý định: Khác")).toBeVisible();await expect(section.getByText(messageText)).toBeVisible();
    await section.getByRole("button",{name:"Chấp nhận"}).click();await expect(section.getByText("Đã chấp nhận")).toBeVisible();
  } finally {await manager.dispose();}
});
