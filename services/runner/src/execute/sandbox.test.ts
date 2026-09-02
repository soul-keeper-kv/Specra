/**
 * The restrictions a sandboxed run is actually given.
 *
 * Asserted as flags rather than by starting a container, because these are the difference
 * between "runs in Docker" and "isolated": a container with the working copy mounted writable,
 * or with capabilities left on, would pass a smoke test and fail the requirement.
 */

import { describe, expect, it } from "vitest";

import { containerCommand, CONTAINER_OUTPUT_DIR, CONTAINER_OUTPUT_MOUNT } from "./sandbox.js";

function command(overrides: Partial<Parameters<typeof containerCommand>[0]> = {}) {
  return containerCommand({
    projectDir: "/copies/p1",
    outputDir: "/tmp/out",
    variables: {},
    engineArgs: ["test", "--reporter=json"],
    image: "engine:1",
    entrypoint: ["npx", "engine"],
    ...overrides,
  });
}

describe("the sandbox command", () => {
  it("mounts the working copy read-only, so a test cannot rewrite its own repository", () => {
    const { args } = command();

    expect(args).toContain("--volume=/copies/p1:/work:ro");
    // …and the output directory writable, because that is where evidence has to land.
    expect(args).toContain(`--volume=/tmp/out:${CONTAINER_OUTPUT_MOUNT}`);
  });

  /**
   * The engine is pointed *below* the mount, never at it.
   *
   * It clears its output directory before each run, and removing a mount point fails with
   * EROFS however writable the mount is — found by running it, not by reading the docs.
   */
  it("writes artifacts inside the mount rather than over it", () => {
    expect(CONTAINER_OUTPUT_DIR.startsWith(`${CONTAINER_OUTPUT_MOUNT}/`)).toBe(true);
  });

  it("drops every capability and forbids gaining new ones", () => {
    const { args } = command();

    expect(args).toContain("--cap-drop=ALL");
    expect(args).toContain("--security-opt=no-new-privileges");
    expect(args).toContain("--read-only");
  });

  it("does not put the container on the host network", () => {
    // The suite reaches the application under test over the bridge. `--network=host` would let
    // generated code reach the runner's own loopback services, which is the escape this
    // sandbox exists to prevent.
    expect(command().args).not.toContain("--network=host");
  });

  it("bounds memory and processes, so a runaway suite is not a dead machine", () => {
    const { args } = command();

    expect(args.some((arg) => arg.startsWith("--memory="))).toBe(true);
    expect(args.some((arg) => arg.startsWith("--pids-limit="))).toBe(true);
  });

  it("does not run as root", () => {
    expect(command().args).toContain("--user=pwuser");
  });

  it("removes the container afterwards", () => {
    expect(command().args).toContain("--rm");
  });

  /**
   * Secrets travel as arguments to docker, never through a shell. A value with a space or a
   * quote in it has to stay one value — the alternative is a credential splitting into two
   * arguments and one of them landing in a log.
   */
  it("passes variables as separate arguments", () => {
    const { args } = command({
      variables: { QA_PASSWORD: "two words 'quoted'" },
    });

    const index = args.indexOf("--env");
    expect(index).toBeGreaterThan(-1);
    expect(args[index + 1]).toBe("QA_PASSWORD=two words 'quoted'");
  });

  it("puts the image and its entrypoint after every flag", () => {
    const { command: binary, args } = command();

    expect(binary).toBe("docker");
    const image = args.indexOf("engine:1");
    expect(image).toBeGreaterThan(0);
    // Everything after the image is the command inside it, in order.
    expect(args.slice(image)).toEqual(["engine:1", "npx", "engine", "test", "--reporter=json"]);
  });
});
