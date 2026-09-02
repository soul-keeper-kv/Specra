/**
 * The entry point `pnpm --filter specra-runner start` runs.
 *
 * The port is configuration, not a constant: the API is told where the runner is by the same
 * kind of environment variable, so the two agree without either hard-coding the other.
 */

import { createRunnerServer } from "./server.js";

const port = Number(process.env.RUNNER_PORT ?? 8090);
const host = process.env.RUNNER_HOST ?? "127.0.0.1";

const server = createRunnerServer();
server.listen(port, host, () => {
  process.stdout.write(`specra-runner listening on http://${host}:${port}\n`);
  // Said out loud at startup because the failure mode is silent: without the engine's types a
  // generation is proposed having only been formatted, and nothing downstream would say so.
  process.stdout.write(
    process.env.SPECRA_ENGINE_TYPES
      ? `codegen verification on, engine types at ${process.env.SPECRA_ENGINE_TYPES}\n`
      : "codegen verification OFF: set SPECRA_ENGINE_TYPES to typecheck and lint generated code\n",
  );
});

for (const signal of ["SIGINT", "SIGTERM"] as const) {
  process.on(signal, () => {
    server.close(() => process.exit(0));
  });
}
