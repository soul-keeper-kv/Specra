import { setRequestLocale } from "next-intl/server";
import { Suspense } from "react";

import { SignInForm } from "@/features/auth/components/sign-in-form";

/**
 * The form reads `?next=` with `useSearchParams`, which opts its subtree out of prerendering —
 * without this boundary the whole page bails out and the production build fails. The layout's
 * card frame still renders statically; only the form waits for the client.
 */
export default async function SignInPage({ params }: PageProps<"/[locale]/sign-in">) {
  const { locale } = await params;
  setRequestLocale(locale);

  return (
    <Suspense>
      <SignInForm />
    </Suspense>
  );
}
