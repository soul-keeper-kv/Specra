import type { Metadata } from "next";
import { getTranslations, setRequestLocale } from "next-intl/server";

import { ChatView } from "@/features/chat/components/chat-view";

export async function generateMetadata(props: PageProps<"/[locale]/chat">): Promise<Metadata> {
  const { locale } = await props.params;
  const t = await getTranslations({ locale, namespace: "chat" });
  return { title: t("title") };
}

export default async function ChatPage({ params }: PageProps<"/[locale]/chat">) {
  const { locale } = await params;
  setRequestLocale(locale);

  return <ChatView />;
}
