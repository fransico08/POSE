import { createHash } from "node:crypto";
import { readFileSync, readdirSync } from "node:fs";
import { resolve } from "node:path";
import { describe, expect, it } from "vitest";

const directory = resolve(process.cwd(), "public/fonts/fraunces");
const expected = {
  "fraunces-vietnamese.woff2": { bytes: 11536, sha256: "250cc2966c658fb6d336731de9d82a8129025e9839c20c253bbc477852f6cf4f" },
  "fraunces-latin-ext.woff2": { bytes: 33640, sha256: "f1451edd6434085c4f9f3a8b4a674182dd7d6acccf53bfced19fd167f0705a06" },
  "fraunces-latin.woff2": { bytes: 36560, sha256: "88e17be075f1be50ab67b057b99e3701b828f44ed28f9452df6c02645bb0cba9" },
};
const stylesheet = readFileSync(resolve(process.cwd(), "src/styles/landing-fraunces.css"), "utf8");
const original = readFileSync(resolve(process.cwd(), "tests/fixtures/fraunces-font-face-next-16.4.0.css"), "utf8");
const faces = (css: string) => [...css.matchAll(/@font-face\s*\{([^}]+)\}/g)].map(match => Object.fromEntries(match[1].split(";").filter(Boolean).map(declaration => {
  const colon = declaration.indexOf(":");
  return [declaration.slice(0, colon).trim(), declaration.slice(colon + 1).trim()];
})));

describe("pinned self-hosted Fraunces", () => {
  it("matches the known-good hashes and the checked-in checksum manifest", () => {
    const manifest = Object.fromEntries(readFileSync(`${directory}/SHA256SUMS.txt`, "utf8").trim().split(/\r?\n/).map(line => {
      const [, hash, filename] = line.match(/^([a-f0-9]{64})\s+\*(.+)$/)!;
      return [filename, hash];
    }));
    expect(Object.keys(manifest).sort()).toEqual(Object.keys(expected).sort());
    for (const [file, spec] of Object.entries(expected)) {
      const font = readFileSync(`${directory}/${file}`);
      expect(createHash("sha256").update(font).digest("hex")).toBe(spec.sha256);
      expect(manifest[file]).toBe(spec.sha256);
      expect(font.length).toBe(spec.bytes);
    }
  });

  it("keeps exactly three files within the 81920-byte display-font budget", () => {
    const fonts = readdirSync(directory).filter(file => file.endsWith(".woff2"));
    expect(fonts.sort()).toEqual(Object.keys(expected).sort());
    const bytes = fonts.reduce((sum, file) => sum + readFileSync(`${directory}/${file}`).length, 0);
    expect(bytes).toBe(81736);
    expect(bytes).toBeLessThanOrEqual(81920);
  });

  it("preserves all six original weight/subset unicode ranges and swap behavior", () => {
    const pinned = faces(stylesheet).filter(face => face["font-family"] === "Fraunces");
    const baseline = faces(original).filter(face => face["font-family"] === "Fraunces");
    expect(pinned).toHaveLength(6);
    expect(baseline).toHaveLength(6);
    pinned.forEach((face, index) => {
      expect(face["font-weight"]).toBe(baseline[index]["font-weight"]);
      expect(face["unicode-range"]).toBe(baseline[index]["unicode-range"]);
      expect(face["font-style"]).toBe("normal");
      expect(face["font-display"]).toBe("swap");
      expect(face.src).toMatch(/^url\(\/fonts\/fraunces\/fraunces-(vietnamese|latin-ext|latin)\.woff2\)format\("woff2"\)$/);
    });
  });

  it("preserves the known-good Times New Roman fallback metrics", () => {
    const fallback = faces(stylesheet).find(face => face["font-family"] === "Fraunces Fallback");
    expect(fallback).toEqual(faces(original).find(face => face["font-family"] === "Fraunces Fallback"));
    expect(fallback).toMatchObject({ src: "local(Times New Roman)", "ascent-override": "84.71%", "descent-override": "22.09%", "line-gap-override": "0.0%", "size-adjust": "115.45%" });
  });
});
