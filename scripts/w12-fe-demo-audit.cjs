// Local W12 demo inspection. Password stays in the process; no storageState or trace is written.
const { chromium } = require('../frontend/node_modules/playwright');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');

const baseURL = 'http://localhost:3000';
const password = process.env.POSE_DEMO_PASSWORD;
if (!password) throw new Error('POSE_DEMO_PASSWORD is required');
const tenants = ['EDU', 'REA', 'TECH', 'RET', 'LOG', 'CLI'];
const roles = ['admin', 'mgr', 's01', 'care', 'data'];
const screens = [
  ['overview', '/app'], ['offerings', '/app/san-pham'], ['leads', '/app/lead'],
  ['customers', '/app/khach-hang'], ['opportunities', '/app/co-hoi'],
  ['interactions', '/app/tuong-tac'], ['tasks', '/app/nhiem-vu'],
  ['customer360', '/app/khach-hang-360'], ['dashboard', '/app/bao-cao'],
];
const enumPattern = /^(ACTIVE|INACTIVE|DRAFT|CURRENT|UPCOMING|EXPIRED|REVOKED|NEW|QUALIFIED|CONVERTED|DISQUALIFIED|PROPOSAL|WON|LOST|OPEN|IN_PROGRESS|DONE|CANCELLED|PRODUCT|SERVICE|INDIVIDUAL|BUSINESS|WEBSITE|REFERRAL|EVENT|SOCIAL_MEDIA|PHONE|WALK_IN|OTHER|LOW|MEDIUM|HIGH|URGENT|CALL|MEETING|NOTE|CHAT|CARE_FOLLOW_UP|IN_PERSON|SALES_ONLY|CARE_VISIBLE)$/;
const reportPath = path.join(os.tmpdir(), 'pose-w12-fe-demo-audit.json');
const screenshotDir = path.resolve(__dirname, '../docs/testing/w12-fe-demo-2026-11-12');
const output = { database: 'pose_w12_v12_canonical_test', runs: [], startedAt: new Date().toISOString() };
const orgCustomer = new Map();
const orgNames = { EDU: 'POSE Academy', REA: 'POSE Realty', TECH: 'POSE Tech', RET: 'POSE Retail', LOG: 'POSE Logistics', CLI: 'POSE Clinic' };
const csvLines = fs.readFileSync(path.resolve(__dirname, '../database/data/seed/multisector_v2/expected_kpis.csv'), 'utf8').replace(/^\uFEFF/, '').trim().split(/\r?\n/);
const columns = csvLines.shift().split(',').map(JSON.parse);
const expectations = csvLines.map(line => {
  const values = line.split(',').map(JSON.parse);
  if (values.length !== columns.length) throw new Error('Unsupported expected_kpis.csv format');
  return Object.fromEntries(columns.map((column, index) => [column, values[index]]));
});

function recordEnums(pairs) {
  return pairs.flatMap(({ field, value }) => value.split(/\s*·\s*/).filter(v => enumPattern.test(v.trim())).map(v => ({ field, value: v.trim() })));
}
async function inspect(page, route, mobile) {
  const started = Date.now(); const errors = [];
  const onResponse = response => { if (response.status() >= 500) errors.push({ status: response.status(), path: new URL(response.url()).pathname }); };
  page.on('response', onResponse);
  try {
    await page.goto(route, { waitUntil: 'domcontentloaded', timeout: 30000 });
    await page.locator('main.app-main').waitFor({ state: 'visible', timeout: 30000 });
    const loading = page.locator('main.app-main [role="status"]').filter({ hasText: /^Đang tải/ });
    await loading.first().waitFor({ state: 'hidden', timeout: 45000 });
    const main = page.locator('main.app-main');
    const text = await main.innerText();
    // A profile can contain restricted subsections while the profile itself is accessible.
    const firstHeading = ((await main.locator('h1,h2,h3').first().allTextContents())[0] || '').trim();
    const restricted = /^(không có quyền|không được phép|bạn (?:không|chưa) có quyền)/i.test(firstHeading || text.trim()) || page.url().includes('/khong-co-quyen');
    const errorMessage = /Không thể tải|Không thể kết nối|Không thể xử lý yêu cầu/.test(text);
    const pairs = await main.evaluate(root => {
      const labels = new Set(['Trạng thái', 'Loại', 'Kênh', 'Loại khách hàng', 'Nguồn', 'Giai đoạn', 'Ưu tiên', 'Loại / Kênh']);
      const result = [];
      for (const dt of root.querySelectorAll('dt')) {
        const field = (dt.textContent || '').trim(), dd = dt.nextElementSibling;
        if (labels.has(field) && dd?.tagName === 'DD') result.push({ field, value: (dd.textContent || '').trim() });
      }
      for (const table of root.querySelectorAll('table')) {
        const headers = [...table.querySelectorAll('thead th')].map(th => (th.textContent || '').trim());
        for (const row of table.querySelectorAll('tbody tr')) for (const [i, field] of headers.entries()) {
          if (labels.has(field) && row.cells[i]) result.push({ field, value: (row.cells[i].textContent || '').trim() });
        }
      }
      for (const badge of root.querySelectorAll('.badge,.sales-assignment-state')) result.push({ field: 'badge', value: (badge.textContent || '').trim() });
      return result;
    });
    let overflow = false;
    if (mobile) {
      await page.setViewportSize({ width: 375, height: 812 });
      overflow = await page.evaluate(() => document.documentElement.scrollWidth > document.documentElement.clientWidth);
      await page.setViewportSize({ width: 1280, height: 800 });
    }
    return { status: restricted ? 'RESTRICTED' : errorMessage || errors.length ? 'ERROR' : 'OK',
      elapsedMs: Date.now() - started, rows: await main.locator('table tbody tr, .opportunity-card').count(),
      rawEnums: recordEnums(pairs), rawIsoDate: /\b20\d{2}-\d{2}-\d{2}(?:T\d{2}:\d{2}|\b)/.test(text), overflow, errors };
  } catch (error) {
    return { status: 'ERROR', elapsedMs: Date.now() - started, error: error.name, errors };
  } finally { page.off('response', onResponse); }
}

async function inspectPagination(page) {
  const next = page.getByRole('button', { name: 'Sau', exact: true }).first();
  if (!await next.isVisible().catch(() => false) || !await next.isEnabled()) return 'NO_NEXT_PAGE';
  await next.click();
  try { await page.getByText(/^Trang 2\s*\//).first().waitFor({ state: 'visible', timeout: 15000 }); return 'OK'; }
  catch { return 'ERROR'; }
}

async function customerProfile(context, page, org, role) {
  let id;
  const customers = await context.request.get('/api/backend/customers?page=0&size=1');
  if (customers.ok()) {
    const body = await customers.json(); id = body.items?.[0]?.id;
    if (id && (role === 'admin' || role === 'mgr')) orgCustomer.set(org, id);
  }
  if (!id && role === 'data') id = orgCustomer.get(org);
  if (!id) return { status: 'NO_ACCESSIBLE_CUSTOMER' };
  const profile = await inspect(page, '/app/khach-hang-360/' + encodeURIComponent(id), role === 'mgr');
  if (profile.status !== 'OK') return profile;
  const copilot = page.getByRole('heading', { name: 'AI Copilot', exact: true });
  if (role === 'mgr' && await copilot.isVisible().catch(() => false)) {
    try {
      const button = page.getByRole('button', { name: 'Tóm tắt hồ sơ', exact: true });
      await button.waitFor({ state: 'visible', timeout: 15000 });
      const responsePromise = page.waitForResponse(response => response.url().includes('/ai/copilot/customers/') && response.url().endsWith('/summary') && response.request().method() === 'POST', { timeout: 30000 });
      await button.click();
      const response = await responsePromise;
      const result = await response.json();
      if (!response.ok() || result.status !== 'SUCCEEDED') throw new Error('Copilot did not succeed');
      await page.getByRole('article', { name: 'Gợi ý AI Copilot' }).waitFor({ state: 'visible', timeout: 30000 });
      profile.copilot = 'MOCK_OK';
    } catch (error) { profile.copilot = 'ERROR'; profile.copilotError = error.name; }
  } else profile.copilot = await copilot.isVisible().catch(() => false) ? 'VISIBLE' : 'NOT_VISIBLE';
  if (org === 'EDU' && role === 'mgr') {
    fs.mkdirSync(screenshotDir, { recursive: true });
    await page.screenshot({ path: path.join(screenshotDir, 'edu-customer-360.png'), fullPage: false });
  }
  return profile;
}

async function inspectKpiUi(page, org) {
  const expected = expectations.find(row => row.organization_name === orgNames[org]);
  if (!expected) throw new Error('Missing KPI row');
  const query = new URLSearchParams({ start: '2026-09-10T17:00:00Z', end: '2026-10-10T17:00:00Z', asOf: '2026-10-10T17:00:00Z' });
  await page.goto('/app/bao-cao?' + query, { waitUntil: 'domcontentloaded' });
  await page.locator('[data-metric="A01"] .dashboard-value').waitFor({ state: 'visible', timeout: 45000 });
  const results = {};
  for (const [code, field] of [['A01', 'leads_total'], ['B01', 'tasks_open_or_in_progress'], ['B02', 'tasks_overdue_as_of_demo_date']]) {
    const actual = (await page.locator(`[data-metric="${code}"] .dashboard-value`).innerText()).trim();
    const wanted = Number(expected[field]).toLocaleString('vi-VN');
    results[code] = { actual, expected: wanted, match: actual === wanted };
  }
  return results;
}

(async () => {
  const browser = await chromium.launch({ headless: true });
  try {
    for (const org of tenants) for (const role of roles) {
      const row = { org, role, screens: {}, pagination: {} }; output.runs.push(row);
      const context = await browser.newContext({ baseURL, viewport: { width: 1280, height: 800 } });
      try {
        const login = await context.request.post('/api/session/login', {
          headers: { Origin: baseURL }, data: { organizationCode: org, email: org.toLowerCase() + '.' + role + '@example.com', password },
        });
        row.login = login.status();
        if (!login.ok()) { console.log(org, role, 'LOGIN', row.login); continue; }
        const session = await context.request.get('/api/session/me');
        if (!session.ok()) { row.session = session.status(); console.log(org, role, 'SESSION', row.session); continue; }
        const page = await context.newPage();
        for (const [name, route] of screens) {
          row.screens[name] = await inspect(page, route, role === 'mgr');
          if (name === 'overview' && row.screens[name].status === 'OK')
            row.today = await page.getByRole('heading', { name: 'Việc hôm nay', exact: true }).isVisible().catch(() => false) ? 'VISIBLE' : 'NOT_VISIBLE';
          if (name === 'dashboard' && row.screens[name].status === 'OK')
            row.kpiGroups = await page.locator('.dashboard-groups > section > h2').allTextContents();
          if (role === 'mgr' && ['leads', 'customers', 'opportunities', 'interactions', 'tasks'].includes(name) && row.screens[name].status === 'OK')
            row.pagination[name] = await inspectPagination(page);
          if (org === 'EDU' && role === 'mgr' && name === 'dashboard' && row.screens[name].status === 'OK') {
            fs.mkdirSync(screenshotDir, { recursive: true });
            await page.screenshot({ path: path.join(screenshotDir, 'edu-dashboard.png'), fullPage: false });
          }
        }
        row.profile = await customerProfile(context, page, org, role);
        if (role === 'admin' || (org === 'CLI' && role === 'data')) {
          try { row.kpiUi = await inspectKpiUi(page, org); }
          catch (error) { row.kpiUiError = error.name; }
        }
        const issues = Object.values(row.screens).filter(v => v.status === 'ERROR' || v.rawEnums?.length || v.rawIsoDate || v.overflow).length;
        console.log(org, role, 'screens=', Object.keys(row.screens).length, 'issues=', issues, 'profile=', row.profile.status, 'copilot=', row.profile.copilot || '-');
      } catch (error) { row.error = error.name; console.log(org, role, 'ERROR', error.name); }
      finally { await context.close(); fs.writeFileSync(reportPath, JSON.stringify(output, null, 2)); }
    }
  } finally { await browser.close(); output.finishedAt = new Date().toISOString(); fs.writeFileSync(reportPath, JSON.stringify(output, null, 2)); }
  console.log('Sanitized report:', reportPath);
})().catch(error => { console.error('Audit failed:', error.name); process.exitCode = 1; });
