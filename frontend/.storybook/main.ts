import type { StorybookConfig } from "@storybook/svelte-vite"
import * as path from "path"

const config: StorybookConfig = {
  stories: ["../src/**/*.mdx", "../src/**/*.stories.@(js|ts|svelte)"],
  addons: [
    "@storybook/addon-essentials",
    "@storybook/addon-svelte-csf",
    "@chromatic-com/storybook",
    "@storybook/experimental-addon-test",
  ],
  framework: {
    name: "@storybook/svelte-vite",
    options: {},
  },
  staticDirs: ["./static/"],
  // Fix for using aliases in storybooks: https://github.com/storybookjs/storybook/issues/14087#issuecomment-1329304212
  viteFinal: async (config) => {
    config.resolve.alias = {
      ...config.resolve.alias,
      "$components": path.resolve(__dirname, "../src/main/components"),
      "$lib": path.resolve(__dirname, "../src/main/lib"),
      "style": path.resolve(__dirname, "../src/main/style"),
    }
    return config
  },
}

export default config
