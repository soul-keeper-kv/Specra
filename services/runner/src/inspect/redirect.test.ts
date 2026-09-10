import { describe, expect, it } from "vitest";

import { redirect } from "./run.js";

/**
 * Telling a redirect apart from the same page reached slightly differently.
 *
 * Inspection used to report whatever the browser ended up on, whatever page that was. An
 * application that bounces an anonymous visitor to its sign-in screen therefore produced a
 * `DashboardPage` holding `emailInput` and `passwordInput`, with no error and no warning — the
 * worst kind of wrong, because everything downstream believed it.
 *
 * The judgement here is where the line sits. Refusing every difference would reject inspections
 * that worked, so a query string, a fragment and a trailing slash all have to pass.
 */
describe("deciding whether a page redirected", () => {
  it("reports the redirect an unauthenticated visit produces", () => {
    expect(redirect("https://app.acme.dev/dashboard", "https://app.acme.dev/login")).toEqual({
      redirectedTo: {
        requested: "https://app.acme.dev/dashboard",
        reached: "https://app.acme.dev/login",
      },
    });
  });

  it("reports a redirect that leaves the origin, as an identity provider does", () => {
    expect(
      redirect("https://app.acme.dev/dashboard", "https://id.acme.dev/authorize?client=app"),
    ).toHaveProperty("redirectedTo");
  });

  it("says nothing when the page was reached", () => {
    expect(redirect("https://app.acme.dev/login", "https://app.acme.dev/login")).toEqual({});
  });

  /**
   * The three ways the same page arrives looking different. Each of these was a working
   * inspection before this check existed, and refusing any of them would be a regression.
   */
  it("treats a trailing slash as the same page", () => {
    expect(redirect("https://app.acme.dev/login", "https://app.acme.dev/login/")).toEqual({});
    expect(redirect("https://app.acme.dev/login/", "https://app.acme.dev/login")).toEqual({});
  });

  it("treats a query the application added as the same page", () => {
    expect(redirect("https://app.acme.dev/orders", "https://app.acme.dev/orders?tab=recent")).toEqual(
      {},
    );
  });

  it("treats a fragment as the same page", () => {
    expect(redirect("https://app.acme.dev/docs", "https://app.acme.dev/docs#install")).toEqual({});
  });

  /** The root is one character and must not be stripped to an empty path. */
  it("compares the root path correctly", () => {
    expect(redirect("https://app.acme.dev/", "https://app.acme.dev/")).toEqual({});
    expect(redirect("https://app.acme.dev/", "https://app.acme.dev/login")).toHaveProperty(
      "redirectedTo",
    );
  });

  /**
   * A URL the API built out of an environment's base URL cannot be malformed without something
   * else being wrong. Inventing a redirect out of it would hide that bug behind this message.
   */
  it("reports nothing rather than guessing when a URL will not parse", () => {
    expect(redirect("not a url", "https://app.acme.dev/login")).toEqual({});
  });

  /** Inspection is used against local work in progress as often as against a deployment. */
  it("compares a port as part of the origin", () => {
    expect(redirect("http://localhost:3000/app", "http://localhost:3001/app")).toHaveProperty(
      "redirectedTo",
    );
  });
});
