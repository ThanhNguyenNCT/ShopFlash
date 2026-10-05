import { color, font, text, space, radius, shadow } from './tokens';

// Theme cho <ConfigProvider theme={antdTheme}> của Ant Design v5.
export const antdTheme = {
  token: {
    colorPrimary: color.brand,
    colorInfo: color.info,
    colorSuccess: color.success,
    colorWarning: color.warning,
    colorError: color.danger,
    colorLink: color.brand,
    colorText: color.ink,
    colorTextSecondary: color.inkMuted,
    colorBorder: color.lineStrong,
    colorBorderSecondary: color.line,
    colorBgLayout: color.surfaceSunken,
    colorBgContainer: color.surface,
    fontFamily: font.sans,
    fontSize: text.body[0],
    borderRadius: radius.md,
    borderRadiusSM: radius.sm,
    borderRadiusLG: radius.lg,
    boxShadow: shadow.popup,
    controlHeight: 38,
  },
  components: {
    Button: { primaryShadow: 'none', fontWeight: 600 },
    Card: { borderRadiusLG: radius.lg },
    Tag: { borderRadiusSM: radius.pill },
  },
};

const kebab = (s) => s.replace(/[A-Z]/g, (c) => '-' + c.toLowerCase());

// Biến CSS dùng trong file .css: var(--sf-color-brand), var(--sf-space-4), var(--sf-radius-md)...
export const cssVariables = {
  ...Object.fromEntries(Object.entries(color).map(([k, v]) => [`--sf-color-${kebab(k)}`, v])),
  ...Object.fromEntries(Object.entries(font).map(([k, v]) => [`--sf-font-${k}`, v])),
  ...Object.fromEntries(Object.entries(space).map(([k, v]) => [`--sf-space-${k}`, `${v}px`])),
  ...Object.fromEntries(Object.entries(radius).map(([k, v]) => [`--sf-radius-${k}`, `${v}px`])),
  ...Object.fromEntries(Object.entries(shadow).map(([k, v]) => [`--sf-shadow-${k}`, v])),
  ...Object.fromEntries(Object.entries(text).flatMap(([k, [size, lh, weight]]) => [
    [`--sf-text-${k}-size`, `${size}px`],
    [`--sf-text-${k}-line`, `${lh}px`],
    [`--sf-text-${k}-weight`, String(weight)],
  ])),
};

export function applyCssVariables(root = document.documentElement) {
  for (const [name, value] of Object.entries(cssVariables)) root.style.setProperty(name, value);
}
