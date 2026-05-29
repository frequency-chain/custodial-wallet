// @ts-check

import eslint from "@eslint/js"
import prettierConfig from "eslint-config-prettier"
import tseslint from "typescript-eslint"

export default tseslint.config(
  eslint.configs.recommended,
  tseslint.configs.recommended,
  // Disables formatting rules that might conflict with prettier
  prettierConfig,
  {
    rules: { "no-fallthrough": "off" }, // Allows multiple cases to fall through to a single body
  },
)
