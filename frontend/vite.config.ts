import { svelte } from "@sveltejs/vite-plugin-svelte"
// @ts-ignore
import tailwindcss from "@tailwindcss/vite"
// @ts-ignore
import { svelteTesting } from "@testing-library/svelte/vite"
import * as path from "path"
import { defineConfig } from "vite"
//@ts-ignore
import pkg from "./package.json"

// NOTE(Julian, 04-25-25): In order to get independent bundle files for each custom element we will need to
// write a custom build script, see: https://github.com/rollup/rollup/issues/5601#issuecomment-2474350383
export default defineConfig(({ mode }) => ({
  root: "./src",
  build: {
    outDir: "../build/",
    emptyOutDir: true,
    target: "es2015",
    lib: {
      entry: "main/index.ts",
      formats: ["es"],
      name: pkg.name,
    },
  },
  resolve: {
    alias: {
      $lib: path.resolve("src/main/lib"),
      $components: path.resolve("src/main/components"),
      $style: path.resolve("src/main/style"),
    },
  },
  plugins: [
    tailwindcss(),
    svelte({
      compilerOptions: {
        customElement: true,
      },
    }),
    svelteTesting(),
  ],
  test: {
    // Runs all our tests in a browser environment (as opposed to node). Reasons include but are not limited to:
    // - Component tests need to run in the browser in order to render
    // - API tests need the browser version of `fetch`
    environment: "happy-dom",
  },
}))
