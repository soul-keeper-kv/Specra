import path from "node:path";
import type { NextConfig } from "next";
import createNextIntlPlugin from "next-intl/plugin";

const nextConfig: NextConfig = {
  reactCompiler: true,
  turbopack: {
    // pnpm workspace: dependencies are symlinked out of the store at the repo
    // root, so the root has to cover both packages or Turbopack refuses to
    // compile anything it resolves outside apps/web.
    root: path.resolve(__dirname, "../.."),
  },
};

// Wires src/i18n/request.ts into the server runtime, which is what lets a Server Component call
// getTranslations() without being handed a locale explicitly.
const withNextIntl = createNextIntlPlugin();

export default withNextIntl(nextConfig);
