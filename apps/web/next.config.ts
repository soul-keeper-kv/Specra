import path from "node:path";
import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  reactCompiler: true,
  turbopack: {
    // The repo root also has a package-lock.json (husky/commitlint live there),
    // so pin the workspace root instead of letting Next infer the wrong one.
    root: path.resolve(__dirname),
  },
};

export default nextConfig;
