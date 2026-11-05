import { readdir, readFile, rm, unlink, writeFile } from "node:fs/promises";
import { resolve, join } from "node:path";
import { deflateRawSync, inflateRawSync } from "node:zlib";
import { authPath } from "./helpers";

async function filesUnder(path: string): Promise<string[]> {
  const entries = await readdir(path, { withFileTypes: true }).catch(() => []);
  return (await Promise.all(entries.map(entry => entry.isDirectory() ? filesUnder(join(path, entry.name)) : [join(path, entry.name)]))).flat();
}

// Inspect trace ZIP entries, including compressed network records, without printing contents.
function traceContainsSecrets(zip: Buffer, secrets: string[]): boolean {
  let end = -1;
  for (let i = zip.length - 22; i >= Math.max(0, zip.length - 65557); i--) {
    if (zip.readUInt32LE(i) === 0x06054b50) { end = i; break; }
  }
  if (end < 0) throw new Error("Cannot inspect trace ZIP.");
  let pos = zip.readUInt32LE(end + 16);
  const count = zip.readUInt16LE(end + 10);
  for (let i = 0; i < count; i++) {
    if (zip.readUInt32LE(pos) !== 0x02014b50) throw new Error("Invalid trace ZIP directory.");
    const method = zip.readUInt16LE(pos + 10), size = zip.readUInt32LE(pos + 20), offset = zip.readUInt32LE(pos + 42);
    const localName = zip.readUInt16LE(offset + 26), localExtra = zip.readUInt16LE(offset + 28);
    const compressed = zip.subarray(offset + 30 + localName + localExtra, offset + 30 + localName + localExtra + size);
    const body = method === 0 ? compressed : method === 8 ? inflateRawSync(compressed, { maxOutputLength: 100_000_000 }) : null;
    if (!body) throw new Error("Unsupported trace ZIP compression.");
    if (secrets.some(secret => body.includes(Buffer.from(secret)))) return true;
    const text = body.toString("utf8");
    if (/eyJ[A-Za-z0-9_-]{8,}\.[A-Za-z0-9_-]{8,}\.[A-Za-z0-9_-]{8,}/.test(text) || /(?:pose_at|pose_rt)=[^;\s"\\]{12,}/i.test(text)) return true;
    pos += 46 + zip.readUInt16LE(pos + 28) + zip.readUInt16LE(pos + 30) + zip.readUInt16LE(pos + 32);
  }
  return false;
}

function crc32(data: Buffer): number {
  let crc = 0xffffffff;
  for (const byte of data) {
    crc ^= byte;
    for (let i = 0; i < 8; i++) crc = (crc >>> 1) ^ (0xedb88320 & -(crc & 1));
  }
  return (crc ^ 0xffffffff) >>> 0;
}

// Keep the action/screenshot trace, discard network captures, and redact auth state.
function sanitizeTrace(zip: Buffer, secrets: string[]): Buffer {
  let end = -1;
  for (let i = zip.length - 22; i >= Math.max(0, zip.length - 65557); i--) {
    if (zip.readUInt32LE(i) === 0x06054b50) { end = i; break; }
  }
  if (end < 0) throw new Error("Cannot inspect trace ZIP.");
  let pos = zip.readUInt32LE(end + 16), offsetOut = 0;
  const count = zip.readUInt16LE(end + 10), locals: Buffer[] = [], directories: Buffer[] = [];
  for (let i = 0; i < count; i++) {
    if (zip.readUInt32LE(pos) !== 0x02014b50) throw new Error("Invalid trace ZIP directory.");
    const method = zip.readUInt16LE(pos + 10), size = zip.readUInt32LE(pos + 20), offset = zip.readUInt32LE(pos + 42);
    const nameLen = zip.readUInt16LE(pos + 28), extraLen = zip.readUInt16LE(pos + 30), commentLen = zip.readUInt16LE(pos + 32);
    const name = zip.subarray(pos + 46, pos + 46 + nameLen), filename = name.toString("utf8");
    const localName = zip.readUInt16LE(offset + 26), localExtra = zip.readUInt16LE(offset + 28);
    const compressed = zip.subarray(offset + 30 + localName + localExtra, offset + 30 + localName + localExtra + size);
    let body = method === 0 ? compressed : method === 8 ? inflateRawSync(compressed, { maxOutputLength: 100_000_000 }) : null;
    if (!body) throw new Error("Unsupported trace ZIP compression.");
    if (filename.endsWith(".network")) body = Buffer.alloc(0);
    else {
      for (const secret of secrets) body = Buffer.from(body.toString("latin1").split(secret).join("[REDACTED]"), "latin1");
      if (/\.(trace|json|txt|html|js|stacks)$/.test(filename)) {
        body = Buffer.from(body.toString("utf8")
          .replace(/eyJ[A-Za-z0-9_-]{8,}\.[A-Za-z0-9_-]{8,}\.[A-Za-z0-9_-]{8,}/g, "[REDACTED]")
          .replace(/((?:pose_at|pose_rt)=)[^;\s"\\]{12,}/gi, "$1[REDACTED]"), "utf8");
      }
    }
    const output = deflateRawSync(body), crc = crc32(body);
    const local = Buffer.alloc(30); local.writeUInt32LE(0x04034b50, 0); local.writeUInt16LE(20, 4); local.writeUInt16LE(0x800, 6); local.writeUInt16LE(8, 8);
    local.writeUInt32LE(crc, 14); local.writeUInt32LE(output.length, 18); local.writeUInt32LE(body.length, 22); local.writeUInt16LE(name.length, 26);
    locals.push(local, name, output);
    const central = Buffer.alloc(46); central.writeUInt32LE(0x02014b50, 0); central.writeUInt16LE(20, 4); central.writeUInt16LE(20, 6); central.writeUInt16LE(0x800, 8); central.writeUInt16LE(8, 10);
    central.writeUInt32LE(crc, 16); central.writeUInt32LE(output.length, 20); central.writeUInt32LE(body.length, 24); central.writeUInt16LE(name.length, 28); central.writeUInt32LE(offsetOut, 42);
    directories.push(central, name); offsetOut += local.length + name.length + output.length;
    pos += 46 + nameLen + extraLen + commentLen;
  }
  const directory = Buffer.concat(directories), ending = Buffer.alloc(22);
  ending.writeUInt32LE(0x06054b50, 0); ending.writeUInt16LE(count, 8); ending.writeUInt16LE(count, 10);
  ending.writeUInt32LE(directory.length, 12); ending.writeUInt32LE(offsetOut, 16);
  return Buffer.concat([...locals, directory, ending]);
}

export default async function teardown() {
  const authDir = resolve(authPath("admin"), "..");
  const secrets = [process.env.POSE_E2E_PASSWORD ?? ""].filter(Boolean);
  for (const file of await filesUnder(authDir)) {
    const state = JSON.parse(await readFile(file, "utf8")) as { cookies?: { name: string; value: string }[] };
    for (const cookie of state.cookies ?? []) if (["pose_at", "pose_rt"].includes(cookie.name) && cookie.value) secrets.push(cookie.value);
  }
  let unsafe = false;
  try {
    for (const file of await filesUnder(resolve("test-results"))) {
      try {
        let data: Buffer = await readFile(file);
        if (file.endsWith(".zip")) { data = sanitizeTrace(data, secrets); await writeFile(file, data); }
        const containsSecrets = file.endsWith(".zip") ? traceContainsSecrets(data, secrets) : secrets.some(secret => data.includes(Buffer.from(secret)));
        if (containsSecrets) { unsafe = true; await unlink(file); }
      } catch { unsafe = true; await unlink(file); }
    }
  } finally { await rm(authDir, { recursive: true, force: true }); }
  if (unsafe) throw new Error("A trace could not be verified as secret-free and was removed.");
}
