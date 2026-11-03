import {
  defineConfig,
  minimal2023Preset as preset,
} from "@vite-pwa/assets-generator/config";

const BRAND_BACKGROUND = "#0a0e27";

export default defineConfig({
  headLinkOptions: { preset: "2023" },
  preset: {
    ...preset,
    maskable: {
      ...preset.maskable,
      resizeOptions: { background: BRAND_BACKGROUND },
    },
    apple: {
      ...preset.apple,
      resizeOptions: { background: BRAND_BACKGROUND },
    },
  },
  images: ["public/favicon.svg"],
});
