import { getTranslations, setRequestLocale } from "next-intl/server";

import { SignUpForm } from "@/features/auth/components/sign-up-form";

export async function generateMetadata({ params }: PageProps<"/[locale]/sign-up">) {
  const { locale } = await params;
  const t = await getTranslations({ locale, namespace: "auth.signUp" });
  return { title: t("title") };
}

export default async function SignUpPage({ params }: PageProps<"/[locale]/sign-up">) {
  const { locale } = await params;
  setRequestLocale(locale);

  return <SignUpForm />;
}
