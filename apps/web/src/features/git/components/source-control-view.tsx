"use client";

import {
  ArrowDownToLine,
  ArrowUpFromLine,
  GitBranch,
  GitCommitHorizontal,
  Loader2,
  Plug,
  Plus,
  RefreshCw,
  Unplug,
} from "lucide-react";
import { useFormatter, useTranslations } from "next-intl";
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
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
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
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { Textarea } from "@/components/ui/textarea";
import { useGitCredentials } from "@/features/git/api/credentials";
import {
  useCheckout,
  useCommit,
  useConnectRepository,
  useCreateBranch,
  useDisconnectRepository,
  useGitBranches,
  useGitDiff,
  useGitHistory,
  useGitStatus,
  usePull,
  usePush,
  useRepository,
  useVerifyRepository,
} from "@/features/git/api/git";
import { ChangeBadge } from "@/features/git/components/change-badge";
import {
  ConnectRepositoryForm,
  toRepositoryInput,
} from "@/features/git/components/connect-repository-form";
import { DiffView } from "@/features/git/components/diff-view";
import { useActiveWorkspace } from "@/features/workspaces/api/workspaces";
import { ApiError } from "@/lib/api/client";
import type { GitRepository } from "@/lib/api/types";

export function SourceControlView({ projectId }: { projectId: string }) {
  const tErrors = useTranslations("errors");

  const repository = useRepository(projectId);

  const describe = (error: unknown) =>
    error instanceof ApiError ? error.message : tErrors("generic");

  if (repository.isPending) {
    return (
      <div className="grid gap-4">
        <Skeleton className="h-8 w-48" />
        <Skeleton className="h-64 w-full" />
      </div>
    );
  }

  if (repository.isError) {
    return <ErrorState error={repository.error} onRetry={() => void repository.refetch()} />;
  }

  return repository.data === null ? (
    <ConnectPanel projectId={projectId} describe={describe} />
  ) : (
    <ConnectedPanel projectId={projectId} repository={repository.data} describe={describe} />
  );
}

/** Step one of the golden path after a project exists: point it at a repository. */
function ConnectPanel({
  projectId,
  describe,
}: {
  projectId: string;
  describe: (error: unknown) => string;
}) {
  const t = useTranslations("git");
  const active = useActiveWorkspace();
  const credentials = useGitCredentials(active.workspace?.id);
  const connect = useConnectRepository(projectId);
  const [open, setOpen] = useState(false);

  return (
    <>
      <EmptyState
        icon={Plug}
        title={t("empty.title")}
        description={t("empty.description")}
        action={
          <Button size="sm" onClick={() => setOpen(true)}>
            <Plug className="size-4" />
            {t("connect.trigger")}
          </Button>
        }
      />

      <Dialog open={open} onOpenChange={setOpen}>
        <DialogContent className="sm:max-w-xl">
          <DialogHeader>
            <DialogTitle>{t("connect.title")}</DialogTitle>
            <DialogDescription>{t("connect.description")}</DialogDescription>
          </DialogHeader>
          <ConnectRepositoryForm
            credentials={credentials.data ?? []}
            submitLabel={t("connect.submit")}
            pending={connect.isPending}
            serverErrors={
              connect.error instanceof ApiError ? connect.error.fieldErrors : undefined
            }
            onSubmit={(values) =>
              connect.mutate(toRepositoryInput(values), {
                onSuccess: () => {
                  toast.success(t("toast.connected"));
                  setOpen(false);
                },
                onError: (error) => toast.error(describe(error)),
              })
            }
          />
        </DialogContent>
      </Dialog>
    </>
  );
}

function ConnectedPanel({
  projectId,
  repository,
  describe,
}: {
  projectId: string;
  repository: GitRepository;
  describe: (error: unknown) => string;
}) {
  const t = useTranslations("git");
  const tActions = useTranslations("actions");
  const format = useFormatter();

  const status = useGitStatus(projectId, true);
  const branches = useGitBranches(projectId, true);
  const [historyPage, setHistoryPage] = useState(0);
  const history = useGitHistory(projectId, historyPage, true);
  const [diffPath, setDiffPath] = useState<string | undefined>(undefined);
  const diff = useGitDiff(projectId, diffPath, true);

  const commit = useCommit(projectId);
  const push = usePush(projectId);
  const pull = usePull(projectId);
  const checkout = useCheckout(projectId);
  const createBranch = useCreateBranch(projectId);
  const disconnect = useDisconnectRepository(projectId);
  const verify = useVerifyRepository(projectId);

  const [selected, setSelected] = useState<string[]>([]);
  const [message, setMessage] = useState("");
  const [newBranch, setNewBranch] = useState("");
  const [branchOpen, setBranchOpen] = useState(false);
  const [confirmDisconnect, setConfirmDisconnect] = useState(false);

  const changes = status.data?.changes ?? [];
  const branch = status.data?.branch ?? repository.activeBranch ?? repository.defaultBranch;

  function toggle(path: string) {
    setSelected((current) =>
      current.includes(path) ? current.filter((p) => p !== path) : [...current, path],
    );
  }

  function submitCommit() {
    const trimmed = message.trim();
    if (!trimmed || selected.length === 0) return;
    commit.mutate(
      { message: trimmed, paths: selected },
      {
        onSuccess: (created) => {
          toast.success(t("toast.committed", { sha: created.sha.slice(0, 7) }));
          setMessage("");
          setSelected([]);
        },
        onError: (error) => toast.error(describe(error)),
      },
    );
  }

  return (
    <div className="grid gap-4">
      <Card>
        <CardHeader className="gap-3">
          <div className="flex flex-wrap items-center justify-between gap-3">
            <CardTitle className="flex items-center gap-2 text-base">
              <GitBranch className="size-4" />
              <span className="font-mono">{branch}</span>
              {status.data && !status.data.clean ? (
                <Badge variant="outline" className="font-normal">
                  {t("status.dirty", { count: changes.length })}
                </Badge>
              ) : null}
            </CardTitle>
            <div className="flex flex-wrap items-center gap-2">
              <Button
                variant="outline"
                size="sm"
                disabled={status.isFetching}
                onClick={() => void status.refetch()}
              >
                <RefreshCw className={status.isFetching ? "size-4 animate-spin" : "size-4"} />
                {tActions("retry")}
              </Button>
              <Button
                variant="outline"
                size="sm"
                disabled={pull.isPending}
                onClick={() =>
                  pull.mutate(undefined, {
                    onSuccess: () => toast.success(t("toast.pulled")),
                    onError: (error) => toast.error(describe(error)),
                  })
                }
              >
                <ArrowDownToLine className="size-4" />
                {t("actions.pull")}
                {status.data && status.data.behind > 0 ? ` (${status.data.behind})` : ""}
              </Button>
              <Button
                size="sm"
                disabled={push.isPending || (status.data?.ahead ?? 0) === 0}
                onClick={() =>
                  push.mutate(undefined, {
                    onSuccess: () => toast.success(t("toast.pushed")),
                    onError: (error) => toast.error(describe(error)),
                  })
                }
              >
                <ArrowUpFromLine className="size-4" />
                {t("actions.push")}
                {status.data && status.data.ahead > 0 ? ` (${status.data.ahead})` : ""}
              </Button>
            </div>
          </div>

          <div className="flex flex-wrap items-center gap-2 text-xs text-muted-foreground">
            <span className="truncate font-mono">{repository.remoteUrl}</span>
            <Select
              value={branch}
              onValueChange={(value) =>
                checkout.mutate(value, {
                  onSuccess: () => toast.success(t("toast.switched", { branch: value })),
                  onError: (error) => toast.error(describe(error)),
                })
              }
            >
              <SelectTrigger className="h-7 w-56" aria-label={t("actions.switchBranch")}>
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {(branches.data ?? [branch]).map((name) => (
                  <SelectItem key={name} value={name}>
                    {name}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
            <Button variant="ghost" size="sm" onClick={() => setBranchOpen(true)}>
              <Plus className="size-4" />
              {t("actions.newBranch")}
            </Button>
            <Button
              variant="ghost"
              size="sm"
              disabled={verify.isPending}
              onClick={() =>
                verify.mutate(undefined, {
                  onSuccess: (result) =>
                    toast.success(t("toast.verified", { count: result.branches.length })),
                  onError: (error) => toast.error(describe(error)),
                })
              }
            >
              {verify.isPending ? <Loader2 className="size-4 animate-spin" /> : null}
              {t("actions.verify")}
            </Button>
            <Button
              variant="ghost"
              size="sm"
              className="text-destructive"
              onClick={() => setConfirmDisconnect(true)}
            >
              <Unplug className="size-4" />
              {t("actions.disconnect")}
            </Button>
          </div>
        </CardHeader>
      </Card>

      <Tabs defaultValue="changes">
        <TabsList>
          <TabsTrigger value="changes">{t("tabs.changes")}</TabsTrigger>
          <TabsTrigger value="history">{t("tabs.history")}</TabsTrigger>
        </TabsList>

        <TabsContent value="changes" className="grid gap-4 lg:grid-cols-[20rem_1fr]">
          <Card>
            <CardHeader>
              <CardTitle className="text-sm">{t("changes.title")}</CardTitle>
            </CardHeader>
            <CardContent className="grid gap-4">
              {status.isPending ? (
                <Skeleton className="h-24 w-full" />
              ) : status.isError ? (
                <ErrorState error={status.error} onRetry={() => void status.refetch()} />
              ) : changes.length === 0 ? (
                <p className="text-sm text-muted-foreground">{t("changes.clean")}</p>
              ) : (
                <ul className="grid gap-1">
                  {changes.map((change) => (
                    <li key={change.path}>
                      <label className="flex cursor-pointer items-center gap-2 rounded px-1 py-1 text-sm hover:bg-muted">
                        <input
                          type="checkbox"
                          className="size-4 accent-primary"
                          checked={selected.includes(change.path)}
                          onChange={() => toggle(change.path)}
                          aria-label={change.path}
                        />
                        <ChangeBadge kind={change.kind} />
                        <button
                          type="button"
                          className="truncate text-left font-mono text-xs hover:underline"
                          onClick={() => setDiffPath(change.path)}
                          title={change.path}
                        >
                          {change.path}
                        </button>
                      </label>
                    </li>
                  ))}
                </ul>
              )}

              <div className="grid gap-2">
                <Label htmlFor="commit-message">{t("commit.message")}</Label>
                <Textarea
                  id="commit-message"
                  rows={3}
                  value={message}
                  onChange={(event) => setMessage(event.target.value)}
                  placeholder={t("commit.placeholder")}
                  maxLength={500}
                />
                <p className="text-xs text-muted-foreground">{t("commit.hint")}</p>
                <Button
                  size="sm"
                  disabled={!message.trim() || selected.length === 0 || commit.isPending}
                  onClick={submitCommit}
                >
                  {commit.isPending ? (
                    <Loader2 className="size-4 animate-spin" />
                  ) : (
                    <GitCommitHorizontal className="size-4" />
                  )}
                  {t("commit.submit", { count: selected.length })}
                </Button>
              </div>
            </CardContent>
          </Card>

          <Card className="overflow-hidden">
            <CardHeader>
              <CardTitle className="truncate text-sm">
                {diffPath ?? t("diff.allChanges")}
              </CardTitle>
            </CardHeader>
            <CardContent className="p-0">
              {diff.isPending ? (
                <Skeleton className="m-4 h-40" />
              ) : diff.isError ? (
                <div className="p-4">
                  <ErrorState error={diff.error} onRetry={() => void diff.refetch()} />
                </div>
              ) : (
                <DiffView diff={diff.data ?? ""} />
              )}
            </CardContent>
          </Card>
        </TabsContent>

        <TabsContent value="history">
          <Card>
            <CardContent className="grid gap-2">
              {history.isPending ? (
                <Skeleton className="h-40 w-full" />
              ) : history.isError ? (
                <ErrorState error={history.error} onRetry={() => void history.refetch()} />
              ) : history.data.content.length === 0 ? (
                <p className="text-sm text-muted-foreground">{t("history.empty")}</p>
              ) : (
                <>
                  <ul className="grid divide-y">
                    {history.data.content.map((entry) => (
                      <li key={entry.sha} className="grid gap-1 py-2">
                        <div className="flex items-baseline gap-2">
                          <code className="text-xs text-muted-foreground">
                            {entry.sha.slice(0, 7)}
                          </code>
                          <span className="truncate text-sm font-medium">{entry.message}</span>
                        </div>
                        <span className="text-xs text-muted-foreground">
                          {t("history.by", {
                            author: entry.authorName,
                            when: format.dateTime(new Date(entry.committedAt), {
                              dateStyle: "medium",
                              timeStyle: "short",
                            }),
                          })}
                        </span>
                      </li>
                    ))}
                  </ul>
                  <div className="flex items-center justify-end gap-2 pt-2">
                    <Button
                      variant="outline"
                      size="sm"
                      disabled={history.data.first}
                      onClick={() => setHistoryPage((page) => Math.max(0, page - 1))}
                    >
                      {tActions("previous")}
                    </Button>
                    <Button
                      variant="outline"
                      size="sm"
                      disabled={history.data.last}
                      onClick={() => setHistoryPage((page) => page + 1)}
                    >
                      {tActions("next")}
                    </Button>
                  </div>
                </>
              )}
            </CardContent>
          </Card>
        </TabsContent>
      </Tabs>

      <Dialog
        open={branchOpen}
        onOpenChange={(open) => {
          setBranchOpen(open);
          if (open) setNewBranch("");
        }}
      >
        <DialogContent className="sm:max-w-md">
          <DialogHeader>
            <DialogTitle>{t("branch.title")}</DialogTitle>
            <DialogDescription>{t("branch.description")}</DialogDescription>
          </DialogHeader>
          <form
            className="grid gap-4"
            onSubmit={(event) => {
              event.preventDefault();
              const name = newBranch.trim();
              if (!name) return;
              createBranch.mutate(
                { name },
                {
                  onSuccess: () => {
                    toast.success(t("toast.branchCreated", { branch: name }));
                    setBranchOpen(false);
                  },
                  onError: (error) => toast.error(describe(error)),
                },
              );
            }}
          >
            <div className="grid gap-2">
              <Label htmlFor="branch-name">{t("branch.name")}</Label>
              <Input
                id="branch-name"
                value={newBranch}
                onChange={(event) => setNewBranch(event.target.value)}
                placeholder="specra/tc-104-login"
                maxLength={200}
              />
            </div>
            <Button type="submit" disabled={!newBranch.trim() || createBranch.isPending}>
              {createBranch.isPending ? <Loader2 className="size-4 animate-spin" /> : null}
              {t("branch.submit")}
            </Button>
          </form>
        </DialogContent>
      </Dialog>

      <AlertDialog open={confirmDisconnect} onOpenChange={setConfirmDisconnect}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>{t("confirmDisconnect.title")}</AlertDialogTitle>
            <AlertDialogDescription>
              {t("confirmDisconnect.description")}
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>{tActions("cancel")}</AlertDialogCancel>
            <AlertDialogAction
              disabled={disconnect.isPending}
              onClick={() =>
                disconnect.mutate(undefined, {
                  onSuccess: () => toast.success(t("toast.disconnected")),
                  onError: (error) => toast.error(describe(error)),
                  onSettled: () => setConfirmDisconnect(false),
                })
              }
            >
              {t("confirmDisconnect.confirm")}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </div>
  );
}
