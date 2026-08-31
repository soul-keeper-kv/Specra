"use client";

import { CircleAlert, Cpu } from "lucide-react";
import { useTranslations } from "next-intl";

import { Badge } from "@/components/ui/badge";
import { Skeleton } from "@/components/ui/skeleton";
import { Tooltip, TooltipContent, TooltipTrigger } from "@/components/ui/tooltip";
import { useProviders } from "@/features/chat/api/ai";

/** Surfaces which models are live, so "why did the answer change?" is one glance away. */
export function ProviderBadge() {
  const t = useTranslations("states");
  const { data, isPending, isError } = useProviders();

  if (isPending) return <Skeleton className="h-6 w-24 rounded-full" />;

  if (isError || !data) {
    return (
      <Tooltip>
        <TooltipTrigger asChild>
          <Badge variant="outline" className="gap-1.5 text-muted-foreground">
            <CircleAlert className="size-3" />
            {t("apiOffline")}
          </Badge>
        </TooltipTrigger>
        <TooltipContent>{t("apiOfflineHint")}</TooltipContent>
      </Tooltip>
    );
  }

  return (
    <Tooltip>
      <TooltipTrigger asChild>
        <Badge variant="secondary" className="hidden gap-1.5 font-normal sm:inline-flex">
          <Cpu className="size-3" />
          {data.chatProvider}
        </Badge>
      </TooltipTrigger>
      {/* Model names and env-var syntax are identifiers, not prose: deliberately not translated. */}
      <TooltipContent className="text-xs">
        <div className="grid gap-0.5">
          <div>
            chat: <b>{data.chatProvider}</b> ({data.chatModelType})
          </div>
          <div>
            embedding: <b>{data.embeddingProvider}</b> ({data.embeddingDimensions}d)
          </div>
          <div className="pt-1 text-muted-foreground">
            AI_CHAT_PROVIDER={data.availableProviders.join(" | ")}
          </div>
        </div>
      </TooltipContent>
    </Tooltip>
  );
}
