import prettierConfig from "eslint-config-prettier"
import storybook from "eslint-plugin-storybook"
import tseslint from "typescript-eslint"

export default tseslint.config(
  tseslint.configs.recommended,
  prettierConfig, // Disables formatting rules that might conflict with prettier
  {
    rules: { "no-fallthrough": "off" }, // Allows multiple cases to fall through to a single body
  },
  {
    ignores: ["./src/main/components/atoms/intlTelInput/utils.js"],
  },
  ...storybook.configs["flat/recommended"],
)
