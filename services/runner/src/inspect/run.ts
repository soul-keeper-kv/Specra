import { readPage } from "../adapters/playwright/inspect.js";
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
  };
}
