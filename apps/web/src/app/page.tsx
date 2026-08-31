"use client";

import { ArrowRight, Cpu, Database, MessagesSquare, NotebookPen } from "lucide-react";
import Link from "next/link";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { useProviders } from "@/lib/api/ai";
import { useNotes } from "@/lib/api/notes";

export default function HomePage() {
  const notes = useNotes({ size: 5 });
  const providers = useProviders();

  const indexed = notes.data?.content.filter((n) => n.indexedAt).length ?? 0;

  return (
    <div className="grid gap-8">
      <div>
        <h1 className="font-heading text-3xl font-semibold tracking-tight">Specra</h1>
        <p className="mt-1 text-muted-foreground">
          Next.js talking to Spring Boot 3 — notes on PostgreSQL, chat and RAG on whichever
          model provider you point it at.
        </p>
      </div>

      <div className="grid gap-4 sm:grid-cols-3">
        <Stat
          icon={NotebookPen}
          label="Notes"
          value={notes.isPending ? undefined : String(notes.data?.totalElements ?? 0)}
          hint="stored in PostgreSQL"
        />
        <Stat
          icon={Database}
          label="Indexed on this page"
          value={notes.isPending ? undefined : String(indexed)}
          hint="chunks live in pgvector"
        />
        <Stat
          icon={Cpu}
          label="Chat provider"
          value={providers.isPending ? undefined : (providers.data?.chatProvider ?? "offline")}
          hint={
            providers.data
              ? `embeddings: ${providers.data.embeddingProvider} · ${providers.data.embeddingDimensions}d`
              : "start the API to see this"
          }
        />
      </div>

      <div className="grid gap-4 sm:grid-cols-2">
        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2 text-base">
              <NotebookPen className="size-4" />
              Notes
            </CardTitle>
            <CardDescription>
              CRUD through Spring Data JPA, paged and sorted on the server, rendered with
              TanStack Table.
            </CardDescription>
          </CardHeader>
          <CardContent>
            <Button asChild variant="outline" size="sm">
              <Link href="/notes">
                Open notes
                <ArrowRight className="size-4" />
              </Link>
            </Button>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2 text-base">
              <MessagesSquare className="size-4" />
              Chat &amp; RAG
            </CardTitle>
            <CardDescription>
              Streaming answers over SSE, with history in Postgres. Flip on RAG to answer
              strictly from indexed notes.
            </CardDescription>
          </CardHeader>
          <CardContent>
            <Button asChild variant="outline" size="sm">
              <Link href="/chat">
                Open chat
                <ArrowRight className="size-4" />
              </Link>
            </Button>
          </CardContent>
        </Card>
      </div>

      {providers.data ? (
        <div className="flex flex-wrap items-center gap-2 text-xs text-muted-foreground">
          <span>Swap providers without touching code:</span>
          {providers.data.availableProviders.map((provider) => (
            <Badge key={provider} variant="outline" className="font-mono font-normal">
              AI_CHAT_PROVIDER={provider}
            </Badge>
          ))}
        </div>
      ) : null}
    </div>
  );
}

function Stat({
  icon: Icon,
  label,
  value,
  hint,
}: {
  icon: typeof NotebookPen;
  label: string;
  value?: string;
  hint: string;
}) {
  return (
    <Card>
      <CardContent className="grid gap-1">
        <div className="flex items-center gap-2 text-sm text-muted-foreground">
          <Icon className="size-4" />
          {label}
        </div>
        {value === undefined ? (
          <Skeleton className="h-8 w-20" />
        ) : (
          <div className="font-heading text-2xl font-semibold tabular-nums">{value}</div>
        )}
        <p className="text-xs text-muted-foreground">{hint}</p>
      </CardContent>
    </Card>
  );
}
