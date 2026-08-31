export default {
  extends: ["@commitlint/config-conventional"],
  rules: {
    // Warn-only, and the values match the folders in this repo so a scope says
    // where the change landed.
    "scope-enum": [1, "always", ["web", "api", "ci", "deps", "docs", "tools"]],
  },
};
