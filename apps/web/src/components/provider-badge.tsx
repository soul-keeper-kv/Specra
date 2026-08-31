"use client";

import { CircleAlert, Cpu } from "lucide-react";

import { Badge } from "@/components/ui/badge";
import { Skeleton } from "@/components/ui/skeleton";
import { Tooltip, TooltipContent, TooltipTrigger } from "@/components/ui/tooltip";
import { useProviders } from "@/lib/api/ai";

/** Surfaces which models are live, so "why did the answer change?" is one glance away. */
export function ProviderBadge() {
  const { data, isPending, isError } = useProviders();

  if (isPending) return <Skeleton className="h-6 w-24 rounded-full" />;

  if (isError || !data) {
    return (
      <Tooltip>
        <TooltipTrigger asChild>
          <Badge variant="outline" className="gap-1.5 text-muted-foreground">
            <CircleAlert className="size-3" />
            API offline
          </Badge>
        </TooltipTrigger>
        <TooltipContent>
          Start it with <code>cd apps/api &amp;&amp; ./mvnw spring-boot:run</code>
        </TooltipContent>
      </Tooltip>
    );
  }

  return (
    <Tooltip>
      <TooltipTrigger asChild>
        <Badge variant="secondary" className="gap-1.5 font-normal">
          <Cpu className="size-3" />
          {data.chatProvider}
        </Badge>
      </TooltipTrigger>
      <TooltipContent className="text-xs">
        <div className="grid gap-0.5">
          <div>
            chat: <b>{data.chatProvider}</b> ({data.chatModelType})
          </div>
          <div>
            embedding: <b>{data.embeddingProvider}</b> ({data.embeddingDimensions}d)
          </div>
          <div className="pt-1 text-muted-foreground">
            switch with AI_CHAT_PROVIDER={data.availableProviders.join(" | ")}
          </div>
        </div>
      </TooltipContent>
    </Tooltip>
  );
}
