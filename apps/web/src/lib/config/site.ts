/**
 * Facts about the product that appear in more than one place — metadata, the sidebar header, the
 * marketing page. Kept out of components so renaming the app is one edit.
 */
export const site = {
  name: "Specra",
  /** Used for <title> templates and the OpenGraph site name. */
  url: process.env.NEXT_PUBLIC_SITE_URL ?? "http://localhost:3000",
  docsUrl: `${process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080"}/swagger-ui.html`,
  repoUrl: "https://github.com/specra/specra",
} as const;
