/**
 * Running the suite inside a container, which is what 06-execution.md asks for.
 *
 * "The runner executes code that a model wrote against a repository we cloned. Treat it as
 * untrusted from day one, because retrofitting a sandbox around a service that assumed trust is
 * a rewrite." Spawning the engine directly gives that code the runner's filesystem, its network
 * and its environment; a container gives it a copy of one directory and nothing else.
 *
 * **It is a strategy, not a hard requirement, and that is deliberate.** A contributor without
 * Docker should still be able to run the suite, and `pnpm test:runner` must not depend on a
 * daemon. So the sandbox is chosen when it is available and configured, the direct path remains
 * for development, and the result says which was used — because "did this run sandboxed" is a
 * question an operator has to be able to answer without reading configuration.
 */

import { execFile } from "node:child_process";
import { promisify } from "node:util";

const run = promisify(execFile);

export type Isolation = "container" | "process";

/**
 * Whether a container can actually be started here.
 *
 * Asked once and cached: `docker info` takes a moment, and a run is not the place to discover
 * the daemon is down for the fiftieth time. A false answer is not an error — it selects the
 * direct path and the result says so.
 */
let available: Promise<boolean> | null = null;

export function isolationAvailable(): Promise<boolean> {
  available ??= run("docker", ["info", "--format", "{{.ServerVersion}}"], { timeout: 5000 })
    .then(() => true)
    .catch(() => false);
  return available;
}

/** Only for tests, which must not inherit a cached answer from another case. */
export function resetIsolationCache(): void {
  available = null;
}

export interface ContainerCommand {
  command: string;
  args: string[];
}

/**
 * The `docker run` that executes one suite.
 *
 * Every flag here is a restriction rather than a convenience:
 *
 * - `--rm` so a failed run leaves nothing behind to accumulate or be reused.
 * - `--network` is *not* host: the suite reaches the application under test over the bridge,
 *   and cannot reach the runner's own loopback services.
 * - `--cap-drop=ALL` and `--security-opt=no-new-privileges`: a browser needs neither.
 * - `--read-only` with explicit writable mounts, so generated code cannot modify the image or
 *   anything outside the two directories it was given.
 * - `--memory` and `--pids-limit`: a runaway suite is a bounded failure rather than a machine
 *   that stops answering.
 * - The working copy is mounted **read-only**. The suite reads the code and writes only into the
 *   output directory, so a test cannot rewrite the repository it was generated from.
 */
export function containerCommand(options: {
  projectDir: string;
  outputDir: string;
  variables: Record<string, string>;
  engineArgs: string[];
  /** The image and the command inside it, from the adapter: this file names no engine. */
  image: string;
  entrypoint: string[];
}): ContainerCommand {
  const args = [
    "run",
    "--rm",
    "--cap-drop=ALL",
    "--security-opt=no-new-privileges",
    "--read-only",
    "--memory=2g",
    "--pids-limit=512",
    // The suite is a browser driving a page; it never needs to be root.
    "--user=pwuser",
    "--workdir=/work",
    `--volume=${options.projectDir}:/work:ro`,
    `--volume=${options.outputDir}:${CONTAINER_OUTPUT_MOUNT}`,
    // Chromium's shared memory default is too small for real pages; the usual remedy.
    "--shm-size=1g",
    // A writable temp that is not the image: --read-only leaves none.
    "--tmpfs=/tmp:rw,size=512m",
  ];

  // Passed as arguments to docker, not interpolated into a shell, so a value with a space or a
  // quote in it stays one value. Secrets reach the container this way and never touch its disk.
  for (const [name, value] of Object.entries(options.variables)) {
    args.push("--env", `${name}=${value}`);
  }

  args.push(options.image, ...options.entrypoint, ...options.engineArgs);
  return { command: "docker", args };
}

/** Where the host's output directory is mounted inside the container. */
export const CONTAINER_OUTPUT_MOUNT = "/output";

/**
 * Where the engine is told to put artifacts — a directory *inside* the mount, never the mount
 * itself.
 *
 * The engine clears its output directory before a run, and removing a mount point fails with
 * `EROFS` however writable the mount is. One level down is an ordinary directory it may delete
 * and recreate, and the host still sees everything through the same mount.
 */
export const CONTAINER_OUTPUT_DIR = `${CONTAINER_OUTPUT_MOUNT}/artifacts`;
