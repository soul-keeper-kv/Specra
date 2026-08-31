import { getTranslations } from "next-intl/server";
import { FileQuestion } from "lucide-react";

import { EmptyState } from "@/components/common/empty-state";
import { Button } from "@/components/ui/button";
import { Link } from "@/i18n/navigation";

export default async function NotFound() {
  const t = await getTranslations("errors.notFound");

  return (
    <main className="grid flex-1 place-items-center p-6">
      <EmptyState
        icon={FileQuestion}
        title={t("title")}
        description={t("description")}
        action={
          <Button asChild variant="outline" size="sm">
            <Link href="/dashboard">{t("cta")}</Link>
          </Button>
        }
      />
    </main>
  );
}
