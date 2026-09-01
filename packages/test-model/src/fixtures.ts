import { readdirSync, readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

export type FixtureKind = "valid" | "invalid/schema" | "invalid/semantic";

export interface Fixture {
  /** The file name without its extension. It states the rule the fixture is about. */
  name: string;
  path: string;
  document: unknown;
}

const root = join(dirname(fileURLToPath(import.meta.url)), "..", "fixtures");

/**
 * The fixtures both runtimes check themselves against.
 *
 * `valid` must be accepted everywhere. `invalid/schema` must be rejected everywhere.
 * `invalid/semantic` is schema-valid on purpose: it is rejected by the semantic layer in
 * `apps/api`, and this package's job is only to prove that the schema alone lets it through.
 */
export function loadFixtures(kind: FixtureKind): Fixture[] {
  const directory = join(root, ...kind.split("/"));
  return readdirSync(directory)
    .filter((file) => file.endsWith(".json"))
    .sort()
    .map((file) => {
      const path = join(directory, file);
      return {
        name: file.replace(/\.json$/, ""),
        path,
        document: JSON.parse(readFileSync(path, "utf8")) as unknown,
      };
    });
}
