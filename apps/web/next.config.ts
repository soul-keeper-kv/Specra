import path from "node:path";
import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  reactCompiler: true,
  turbopack: {
    // pnpm workspace: dependencies are symlinked out of the store at the repo
    // root, so the root has to cover both packages or Turbopack refuses to
    // compile anything it resolves outside apps/web.
    root: path.resolve(__dirname, "../.."),
  },
};

export default nextConfig;
