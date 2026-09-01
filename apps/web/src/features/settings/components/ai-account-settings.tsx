"use client";

import { Building2, KeyRound, Loader2 } from "lucide-react";
import { useTranslations } from "next-intl";
import { useState } from "react";
import { toast } from "sonner";

import { EmptyState } from "@/components/common/empty-state";
import { ErrorState } from "@/components/common/error-state";
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/components/ui/alert-dialog";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Skeleton } from "@/components/ui/skeleton";
import { useProviders } from "@/features/chat/api/ai";
import {
  useAiAccount,
  useDeleteAiAccount,
  useSaveAiAccount,
} from "@/features/settings/api/ai-account";
import { useActiveWorkspace } from "@/features/workspaces/api/workspaces";
import { Link } from "@/i18n/navigation";
import { ApiError } from "@/lib/api/client";
import type { AiAccount } from "@/lib/api/types";

/**
 * Bring-your-own-key: the workspace's provider, key and budget. The key field is write-only by
 * design — the API only ever says whether one is stored, so the form starts empty and an untouched
 * field means "keep what is there".
 */
export function AiAccountSettings() {
  const t = useTranslations("settings.ai");
  const tErrors = useTranslations("errors");

  const active = useActiveWorkspace();
  const account = useAiAccount(active.workspace?.id);

  if (active.isPending || (active.workspace && account.isPending)) {
    return (
      <div className="grid max-w-xl gap-4">
        <Skeleton className="h-6 w-48" />
        <Skeleton className="h-64 w-full" />
      </div>
    );
  }

  if (active.isError) {
    return <ErrorState error={active.error} onRetry={() => void active.refetch()} />;
  }

  if (!active.workspace) {
    return (
      <EmptyState
        icon={Building2}
        title={t("noWorkspace.title")}
        description={t("noWorkspace.description")}
        action={
          <Button asChild size="sm">
            <Link href="/projects">{t("noWorkspace.cta")}</Link>
          </Button>
        }
      />
    );
  }

  if (account.isError) {
    return <ErrorState error={account.error} onRetry={() => void account.refetch()} />;
  }

  return (
    <AiAccountForm
      workspaceId={active.workspace.id}
      account={account.data ?? null}
      describe={(error) => (error instanceof ApiError ? error.message : tErrors("generic"))}
    />
  );
}

function AiAccountForm({
  workspaceId,
  account,
  describe,
}: {
  workspaceId: string;
  account: AiAccount | null;
  describe: (error: unknown) => string;
}) {
  const t = useTranslations("settings.ai");
  const tActions = useTranslations("actions");

  const providers = useProviders();
  const save = useSaveAiAccount(workspaceId);
  const remove = useDeleteAiAccount(workspaceId);

  const [provider, setProvider] = useState(account?.provider ?? "");
  const [apiKey, setApiKey] = useState("");
  const [chatModel, setChatModel] = useState(account?.chatModel ?? "");
  const [embeddingModel, setEmbeddingModel] = useState(account?.embeddingModel ?? "");
  const [budget, setBudget] = useState(
    account?.monthlyBudgetUsd == null ? "" : String(account.monthlyBudgetUsd),
  );
  const [confirmingDefault, setConfirmingDefault] = useState(false);

  const serverErrors = save.error instanceof ApiError ? save.error.fieldErrors : {};
  const providerOptions = providers.data?.availableProviders ?? [];
  const keySet = account?.keySet ?? false;

  function submit() {
    save.mutate(
      {
        provider,
        // Untouched means "keep"; the clear path is its own button below.
        apiKey: apiKey ? apiKey : undefined,
        chatModel: chatModel.trim() || undefined,
        embeddingModel: embeddingModel.trim() || undefined,
        monthlyBudgetUsd: budget.trim() === "" ? undefined : Number(budget),
      },
      {
        onSuccess: () => {
          setApiKey("");
          toast.success(t("toast.saved"));
        },
        onError: (error) => toast.error(describe(error)),
      },
    );
  }

  function removeKey() {
    save.mutate(
      { provider, apiKey: "" },
      {
        onSuccess: () => toast.success(t("toast.keyRemoved")),
        onError: (error) => toast.error(describe(error)),
      },
    );
  }

  function useDefault() {
    remove.mutate(undefined, {
      onSuccess: () => toast.success(t("toast.cleared")),
      onError: (error) => toast.error(describe(error)),
      onSettled: () => setConfirmingDefault(false),
    });
  }

  return (
    <div className="grid max-w-xl gap-6">
      <div className="grid gap-1">
        <h2 className="font-heading text-lg font-semibold">{t("title")}</h2>
        <p className="text-sm text-muted-foreground">{t("description")}</p>
        {account === null ? (
          <p className="text-sm text-muted-foreground">{t("platformDefault")}</p>
        ) : null}
      </div>

      <form
        className="grid gap-5"
        onSubmit={(event) => {
          event.preventDefault();
          submit();
        }}
      >
        <div className="grid gap-2">
          <Label htmlFor="ai-provider">{t("provider")}</Label>
          <Select value={provider} onValueChange={setProvider}>
            <SelectTrigger id="ai-provider" className="w-56">
              <SelectValue placeholder={t("providerPlaceholder")} />
            </SelectTrigger>
            <SelectContent>
              {providerOptions.map((option) => (
                <SelectItem key={option} value={option}>
                  {option}
                </SelectItem>
              ))}
              {/* A stored provider the running API no longer lists must stay selectable. */}
              {provider && !providerOptions.includes(provider) ? (
                <SelectItem value={provider}>{provider}</SelectItem>
              ) : null}
            </SelectContent>
          </Select>
          <FieldError message={serverErrors.provider} />
        </div>

        <div className="grid gap-2">
          <div className="flex items-center gap-2">
            <Label htmlFor="ai-key">{t("apiKey")}</Label>
            {keySet ? (
              <Badge variant="secondary" className="font-normal">
                <KeyRound className="size-3" />
                {t("apiKeyStored")}
              </Badge>
            ) : null}
          </div>
          <Input
            id="ai-key"
            type="password"
            autoComplete="off"
            value={apiKey}
            onChange={(event) => setApiKey(event.target.value)}
            placeholder={keySet ? t("apiKeyKeepPlaceholder") : t("apiKeyPlaceholder")}
          />
          <p className="text-xs text-muted-foreground">
            {keySet ? t("apiKeySetHint") : t("apiKeyUnsetHint")}
          </p>
          <FieldError message={serverErrors.apiKey} />
        </div>

        <div className="grid gap-5 sm:grid-cols-2">
          <div className="grid gap-2">
            <Label htmlFor="ai-chat-model">{t("chatModel")}</Label>
            <Input
              id="ai-chat-model"
              value={chatModel}
              onChange={(event) => setChatModel(event.target.value)}
              placeholder={t("chatModelPlaceholder")}
            />
            <FieldError message={serverErrors.chatModel} />
          </div>
          <div className="grid gap-2">
            <Label htmlFor="ai-embedding-model">{t("embeddingModel")}</Label>
            <Input
              id="ai-embedding-model"
              value={embeddingModel}
              onChange={(event) => setEmbeddingModel(event.target.value)}
              placeholder={t("embeddingModelPlaceholder")}
            />
            <FieldError message={serverErrors.embeddingModel} />
          </div>
        </div>

        <div className="grid gap-2">
          <Label htmlFor="ai-budget">{t("budget")}</Label>
          <Input
            id="ai-budget"
            type="number"
            min="0"
            step="0.01"
            value={budget}
            onChange={(event) => setBudget(event.target.value)}
            placeholder="50.00"
            className="w-40"
          />
          <p className="text-xs text-muted-foreground">{t("budgetHint")}</p>
          <FieldError message={serverErrors.monthlyBudgetUsd} />
        </div>

        <div className="flex flex-wrap gap-2">
          <Button type="submit" disabled={!provider || save.isPending}>
            {save.isPending ? <Loader2 className="size-4 animate-spin" /> : null}
            {tActions("save")}
          </Button>
          {keySet ? (
            <Button
              type="button"
              variant="outline"
              disabled={save.isPending}
              onClick={removeKey}
            >
              {t("removeKey")}
            </Button>
          ) : null}
          {account !== null ? (
            <Button
              type="button"
              variant="ghost"
              className="text-destructive"
              onClick={() => setConfirmingDefault(true)}
            >
              {t("useDefault")}
            </Button>
          ) : null}
        </div>
      </form>

      <AlertDialog open={confirmingDefault} onOpenChange={setConfirmingDefault}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>{t("confirmUseDefault.title")}</AlertDialogTitle>
            <AlertDialogDescription>
              {t("confirmUseDefault.description")}
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>{tActions("cancel")}</AlertDialogCancel>
            <AlertDialogAction disabled={remove.isPending} onClick={useDefault}>
              {t("confirmUseDefault.confirm")}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </div>
  );
}

function FieldError({ message }: { message?: string }) {
  if (!message) return null;
  return <p className="text-sm text-destructive">{message}</p>;
}
