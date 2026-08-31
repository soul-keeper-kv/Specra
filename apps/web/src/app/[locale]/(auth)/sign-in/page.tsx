import { setRequestLocale } from "next-intl/server";

import { SignInForm } from "@/features/auth/components/sign-in-form";

export default async function SignInPage({ params }: PageProps<"/[locale]/sign-in">) {
  const { locale } = await params;
  setRequestLocale(locale);

  return <SignInForm />;
}
