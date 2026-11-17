import { createHash } from "node:crypto";
import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { describe, expect, it } from "vitest";

const fontDirectory = resolve(process.cwd(), "src/fonts/be-vietnam-pro");
const expectedFiles = [
  "BeVietnamPro-Regular.ttf",
  "BeVietnamPro-Medium.ttf",
  "BeVietnamPro-SemiBold.ttf",
  "BeVietnamPro-Bold.ttf",
];

describe("self-hosted Be Vietnam Pro", () => {
  it("keeps the four selected weights and their checked-in checksums", () => {
    const manifest = Object.fromEntries(readFileSync(resolve(fontDirectory, "SHA256SUMS.txt"), "utf8").trim().split(/\r?\n/).map(line => {
      const [, hash, filename] = line.match(/^([a-f0-9]{64})\s+\*(.+)$/)!;
      return [filename, hash];
    }));
    expect(Object.keys(manifest).sort()).toEqual([...expectedFiles, "OFL.txt"].sort());
    for (const filename of expectedFiles) {
      const file = readFileSync(resolve(fontDirectory, filename));
      expect(file.length).toBeGreaterThan(100_000);
      expect(createHash("sha256").update(file).digest("hex")).toBe(manifest[filename]);
    }
    const license = readFileSync(resolve(fontDirectory, "OFL.txt"));
    expect(license.toString("utf8")).toContain("SIL OPEN FONT LICENSE Version 1.1");
    expect(createHash("sha256").update(license).digest("hex")).toBe(manifest["OFL.txt"]);
  });

  it("loads the local family through next/font/local without a Google Fonts import", () => {
    const layout = readFileSync(resolve(process.cwd(), "src/app/layout.tsx"), "utf8");
    expect(layout).toContain('from "next/font/local"');
    expect(layout).not.toContain("next/font/google");
    for (const filename of expectedFiles) expect(layout).toContain(filename);
  });
});
