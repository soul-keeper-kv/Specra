import { readPage } from "../adapters/playwright/inspect.js";
import { installDependencies } from "../execute/install.js";
import { planElements } from "./plan.js";
import type { InspectRequest, InspectResult } from "./types.js";

/**
 * The `inspect` job: open a page, and report how its elements can be addressed.
 *
 * Two steps, deliberately separate. The adapter reads the DOM — that is the only part that needs
 * a browser. The planner scores what it read — that is the part carrying the product's judgement,
 * and it is pure, so `plan.test.ts` covers it without launching anything.
 *
 * Nothing here decides which candidate wins. The job reports a ranked list per element; storing
 * the top one as the locator and the runner-up as the fallback is `apps/api`'s decision, and a
 * person can override it. Inspection's job is to make sure nobody has to guess.
 */
export async function inspect(request: InspectRequest): Promise<InspectResult> {
  // Before the browser, because inspection borrows the engine from the user's working copy and
  // a freshly cloned one has no node_modules. Telling a QA user to "run npm install in the
  // repository" names a shell they do not have, in a directory they cannot see — and execution
  // already installs for itself, so the two jobs would otherwise disagree about which
  // repositories are usable.
  const install = await installDependencies(request.projectDir);
  if (install) {
    throw new Error(install);
  }

  const page = await readPage(request);
  const { elements, ambiguous } = planElements(page.elements);

  return {
    pageName: request.pageName,
    // Where the browser actually ended up: a redirect to a login page is the most common reason
    // an inspection comes back with elements nobody expected, and the URL is what says so.
    url: page.url,
    title: page.title,
    elements,
    ambiguous,
    ...redirect(request.url, page.url),
  };
}

/**
 * Whether the browser ended up somewhere other than the page that was asked for.
 *
 * Compared on origin and path only. A query string or a fragment the application added is still
 * the same page — `?tab=recent` is a view of the dashboard, not a different screen — and treating
 * either as a redirect would refuse inspections that worked. A trailing slash is likewise the same
 * path: servers add and drop it freely, and no user has ever meant the two differently.
 *
 * An unparseable URL reports nothing rather than guessing. The request's URL is built by the API
 * from an environment's base URL, so a malformed one is a different bug and inventing a redirect
 * out of it would hide it.
 */
export function redirect(
  requested: string,
  reached: string,
): Pick<InspectResult, "redirectedTo"> | Record<string, never> {
  let from: URL;
  let to: URL;
  try {
    from = new URL(requested);
    to = new URL(reached);
  } catch {
    return {};
  }

  if (from.origin === to.origin && path(from) === path(to)) {
    return {};
  }
  return { redirectedTo: { requested, reached } };
}

/** The path, with a trailing slash normalised away — `/login/` and `/login` are one page. */
function path(url: URL): string {
  return url.pathname.length > 1 ? url.pathname.replace(/\/+$/, "") : url.pathname;
}
