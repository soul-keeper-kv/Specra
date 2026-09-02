/**
 * The page objects and options the golden tests project the shared IR fixtures with.
 *
 * `packages/test-model/fixtures/valid/` owns the IR; this file owns the other half of the
 * adapter's input, so a golden file is a function of two things that both live in the
 * repository and neither of which a test invents inline.
 */

import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import path from "node:path";
import type { TestModel } from "@specra/test-model";

import type { PageObject, ProjectOptions } from "./types.js";

const HERE = path.dirname(fileURLToPath(import.meta.url));

/** Read straight from the shared package's fixtures — the same bytes Java validates against. */
export function irFixture(name: string): TestModel {
  const file = path.resolve(
    HERE,
    "..",
    "..",
    "..",
    "..",
    "packages",
    "test-model",
    "fixtures",
    "valid",
    `${name}.json`,
  );
  return JSON.parse(readFileSync(file, "utf8")) as TestModel;
}

export const LOGIN_PAGES: PageObject[] = [
  {
    name: "LoginPage",
    route: "/login",
    elements: [
      { name: "usernameInput", strategy: "label", value: "Email", confidence: 0.94 },
      { name: "passwordInput", strategy: "label", value: "Password", confidence: 0.94 },
      {
        name: "submitButton",
        strategy: "role",
        value: "button",
        name_: "Log in",
        fallback: { strategy: "testId", value: "login-submit" },
        confidence: 0.88,
      },
    ],
  },
  {
    name: "DashboardPage",
    route: "/dashboard",
    elements: [{ name: "heading", strategy: "role", value: "heading", name_: "Dashboard" }],
  },
];

export const HOME_PAGES: PageObject[] = [
  {
    name: "HomePage",
    route: "/",
    elements: [{ name: "heading", strategy: "role", value: "heading", name_: "Welcome" }],
  },
];

export const CHECKOUT_PAGES: PageObject[] = [
  {
    name: "CartPage",
    route: "/cart",
    elements: [
      { name: "lineItems", strategy: "testId", value: "cart-line-item" },
      { name: "shippingMethod", strategy: "label", value: "Shipping method" },
      { name: "giftWrap", strategy: "label", value: "Gift wrap" },
      { name: "couponInput", strategy: "placeholder", value: "Coupon code" },
      { name: "promoDropZone", strategy: "testId", value: "promo-drop-zone" },
      {
        name: "checkoutButton",
        strategy: "role",
        value: "button",
        name_: "Checkout",
        fallback: { strategy: "testId", value: "checkout" },
      },
    ],
  },
  {
    name: "PaymentPage",
    route: "/checkout/payment",
    elements: [
      { name: "cardNumberInput", strategy: "label", value: "Card number" },
      { name: "payButton", strategy: "role", value: "button", name_: "Pay now" },
    ],
  },
  {
    name: "ConfirmationPage",
    elements: [
      { name: "status", strategy: "testId", value: "order-status" },
      { name: "downloadInvoice", strategy: "role", value: "link", name_: "Download invoice" },
    ],
  },
];

/** The reusable flow the checkout fixture's `useFlow` steps refer to. */
export const LOGIN_FLOW: TestModel = {
  irVersion: 1,
  name: "Sign in as the standard user",
  description: "Signs the standard user in; shared by every case that starts logged in.",
  parameters: [
    { name: "username", type: "string", required: true },
    { name: "password", type: "string", required: true, secret: true },
  ],
  steps: [
    { id: "f1", derived: true, action: "navigate", target: { page: "LoginPage" } },
    {
      id: "f2",
      derived: true,
      action: "fill",
      target: { page: "LoginPage", element: "usernameInput" },
      value: { kind: "param", name: "username" },
    },
    {
      id: "f3",
      derived: true,
      action: "fill",
      target: { page: "LoginPage", element: "passwordInput" },
      value: { kind: "secret", name: "password" },
    },
    {
      id: "f4",
      derived: true,
      action: "click",
      target: { page: "LoginPage", element: "submitButton" },
    },
    {
      id: "f5",
      derived: true,
      action: "assert",
      target: { page: "DashboardPage", element: "heading" },
      assertion: { condition: "visible" },
    },
  ],
};

export const CLEAR_CART_FLOW: TestModel = {
  irVersion: 1,
  name: "Empty the cart",
  parameters: [],
  steps: [
    { id: "c1", derived: true, action: "navigate", target: { page: "CartPage" } },
    {
      id: "c2",
      derived: true,
      action: "assert",
      target: { page: "CartPage", element: "lineItems" },
      assertion: { condition: "countEquals", expected: 0 },
    },
  ],
};

export function options(overrides: Partial<ProjectOptions> = {}): ProjectOptions {
  return {
    reference: "TC-1",
    adapterVersion: "0.1.0",
    ...overrides,
  };
}
