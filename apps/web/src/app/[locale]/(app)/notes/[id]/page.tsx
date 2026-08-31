import { setRequestLocale } from "next-intl/server";

import { NoteDetailView } from "@/features/notes/components/note-detail-view";

export default async function NoteDetailPage({ params }: PageProps<"/[locale]/notes/[id]">) {
  const { locale, id } = await params;
  setRequestLocale(locale);

  return <NoteDetailView noteId={id} />;
}
