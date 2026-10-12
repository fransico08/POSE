import { defineConfig, globalIgnores } from "eslint/config";
import nextVitals from "eslint-config-next/core-web-vitals";
import nextTypescript from "eslint-config-next/typescript";
import a11y from "eslint-plugin-jsx-a11y";

export default defineConfig([
  ...nextVitals,
  ...nextTypescript,
  { files: ["**/*.{ts,tsx}"], rules: a11y.flatConfigs.strict.rules },
  globalIgnores([".next/**", "coverage/**", "next-env.d.ts"]),
]);
