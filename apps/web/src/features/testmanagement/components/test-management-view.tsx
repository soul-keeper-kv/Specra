"use client";

import { ArrowRight, ExternalLink, Loader2, Plug, Search, Unplug } from "lucide-react";
import { useTranslations } from "next-intl";
import { FormEvent, useState } from "react";

import { ErrorState } from "@/components/common/error-state";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Skeleton } from "@/components/ui/skeleton";
import {
  useBindTestManagement,
  useCreateTestManagementConnection,
  useExternalTests,
  useTestManagementBinding,
  useUnbindTestManagement,
  useVerifyTestManagement,
} from "@/features/testmanagement/api/test-management";
import { useActiveWorkspace } from "@/features/workspaces/api/workspaces";
import { useDebouncedValue } from "@/hooks/use-debounced-value";
import { Link } from "@/i18n/navigation";

const PAGE_SIZE = 20;

export function TestManagementView({ projectId }: { projectId: string }) {
  const t = useTranslations("testManagement");
  const tActions = useTranslations("actions");
  const active = useActiveWorkspace();
  const binding = useTestManagementBinding(projectId);
  const createConnection = useCreateTestManagementConnection(active.workspace?.id);
  const bind = useBindTestManagement(projectId);
  const unbind = useUnbindTestManagement(projectId);
  const verify = useVerifyTestManagement(projectId);
  const [search, setSearch] = useState("");
  const [page, setPage] = useState(0);
  const debouncedSearch = useDebouncedValue(search);
  const tests = useExternalTests(
    projectId,
    { q: debouncedSearch || undefined, page, size: PAGE_SIZE },
    Boolean(binding.data),
  );

  const [name, setName] = useState("Company Xray");
  const [baseUrl, setBaseUrl] = useState("");
  const [username, setUsername] = useState("");
  const [token, setToken] = useState("");
  const [remoteProjectId, setRemoteProjectId] = useState("");

  if (active.isPending || binding.isPending) {
    return <Skeleton className="h-72 w-full" />;
  }
  if (active.isError) return <ErrorState error={active.error} />;
  if (binding.isError)
    return <ErrorState error={binding.error} onRetry={() => void binding.refetch()} />;

  async function connect(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const connection = await createConnection.mutateAsync({
      name,
      provider: "xray",
      configuration: { baseUrl, username },
      credentials: { token },
    });
    await bind.mutateAsync({ connectionId: connection.id, remoteProjectId });
    setToken("");
  }

  if (!binding.data) {
    const error = createConnection.error ?? bind.error;
    return (
      <Card className="max-w-2xl">
        <CardHeader>
          <CardTitle>{t("connect.title")}</CardTitle>
          <CardDescription>{t("connect.description")}</CardDescription>
        </CardHeader>
        <CardContent>
          <form className="grid gap-4" onSubmit={(event) => void connect(event)}>
            <Field label={t("connect.name")} value={name} onChange={setName} required />
            <Field
              label={t("connect.baseUrl")}
              value={baseUrl}
              onChange={setBaseUrl}
              placeholder="https://jira.example.com"
              type="url"
              required
            />
            <Field
              label={t("connect.username")}
              value={username}
              onChange={setUsername}
              autoComplete="username"
            />
            <Field
              label={t("connect.token")}
              value={token}
              onChange={setToken}
              type="password"
              autoComplete="new-password"
              required
            />
            <Field
              label={t("connect.remoteProject")}
              value={remoteProjectId}
              onChange={setRemoteProjectId}
              placeholder="XRAY"
              required
            />
            {error ? <ErrorState error={error} /> : null}
            <Button
              type="submit"
              className="w-fit"
              disabled={createConnection.isPending || bind.isPending}
            >
              {createConnection.isPending || bind.isPending ? (
                <Loader2 className="size-4 animate-spin" />
              ) : (
                <Plug className="size-4" />
              )}
              {t("connect.submit")}
            </Button>
          </form>
        </CardContent>
      </Card>
    );
  }

  return (
    <div className="grid gap-6">
      <Card>
        <CardHeader className="flex-row items-start justify-between gap-4">
          <div className="grid gap-1.5">
            <CardTitle>{binding.data.connectionName}</CardTitle>
            <CardDescription>{binding.data.configuration.baseUrl}</CardDescription>
          </div>
          <Badge variant="outline">{binding.data.provider}</Badge>
        </CardHeader>
        <CardContent className="grid gap-4">
          <p className="text-sm text-muted-foreground">
            {t("connected.project", { project: binding.data.remoteProjectId })}
          </p>
          <div className="flex flex-wrap gap-2">
            <Button
              variant="outline"
              onClick={() => verify.mutate()}
              disabled={verify.isPending}
            >
              {verify.isPending ? (
                <Loader2 className="size-4 animate-spin" />
              ) : (
                <Plug className="size-4" />
              )}
              {t("connected.verify")}
            </Button>
            <Button
              variant="outline"
              onClick={() => unbind.mutate()}
              disabled={unbind.isPending}
            >
              <Unplug className="size-4" />
              {t("connected.disconnect")}
            </Button>
          </div>
          {verify.data ? (
            <p className="text-sm">
              {t("connected.verified", {
                account: verify.data.accountName,
                project: verify.data.remoteProjectName,
                count: verify.data.testCount,
              })}
            </p>
          ) : null}
          {verify.error || unbind.error ? (
            <ErrorState error={verify.error ?? unbind.error} />
          ) : null}
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>{t("tests.title")}</CardTitle>
          <CardDescription>{t("tests.description")}</CardDescription>
        </CardHeader>
        <CardContent className="grid gap-4">
          <div className="relative max-w-sm">
            <Search className="pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2 text-muted-foreground" />
            <Input
              value={search}
              onChange={(event) => {
                setSearch(event.target.value);
                setPage(0);
              }}
              placeholder={t("tests.searchPlaceholder")}
              aria-label={tActions("search")}
              className="pl-9"
            />
          </div>
          {tests.isPending ? <Skeleton className="h-28 w-full" /> : null}
          {tests.isError ? (
            <ErrorState error={tests.error} onRetry={() => void tests.refetch()} />
          ) : null}
          {tests.data?.content.length === 0 ? (
            <p className="text-sm text-muted-foreground">
              {debouncedSearch
                ? t("tests.noMatch", { query: debouncedSearch })
                : t("tests.empty")}
            </p>
          ) : null}
          <div className="grid divide-y">
            {tests.data?.content.map((test) => (
              <div
                key={test.externalId}
                className="flex items-center justify-between gap-4 py-3"
              >
                <div className="min-w-0">
                  <p className="font-medium">{test.title}</p>
                  <p className="text-sm text-muted-foreground">
                    {test.externalId} · {test.status}
                  </p>
                </div>
                <div className="flex shrink-0 gap-1">
                  <Button asChild variant="ghost" size="icon-sm">
                    <a
                      href={test.url}
                      target="_blank"
                      rel="noreferrer"
                      aria-label={t("tests.open", { id: test.externalId })}
                    >
                      <ExternalLink className="size-4" />
                    </a>
                  </Button>
                  <Button asChild variant="outline" size="sm">
                    <Link href={`/projects/${projectId}/automation/${test.externalId}`}>
                      {t("tests.automate")}
                      <ArrowRight className="size-4" />
                    </Link>
                  </Button>
                </div>
              </div>
            ))}
          </div>
          {tests.data && tests.data.totalElements > 0 ? (
            <div className="flex flex-wrap items-center justify-between gap-2 text-sm">
              <span className="text-muted-foreground">
                {t("tests.count", { count: tests.data.totalElements })}
                {tests.isFetching ? (
                  <Loader2 className="ml-2 inline size-3 animate-spin" />
                ) : null}
              </span>
              <div className="flex items-center gap-2">
                <Button
                  variant="outline"
                  size="sm"
                  disabled={tests.data.first}
                  onClick={() => setPage((current) => Math.max(0, current - 1))}
                >
                  {tActions("previous")}
                </Button>
                <span className="text-muted-foreground tabular-nums">
                  {t("tests.pagination", {
                    page: tests.data.page + 1,
                    total: Math.max(1, tests.data.totalPages),
                  })}
                </span>
                <Button
                  variant="outline"
                  size="sm"
                  disabled={tests.data.last}
                  onClick={() => setPage((current) => current + 1)}
                >
                  {tActions("next")}
                </Button>
              </div>
            </div>
          ) : null}
        </CardContent>
      </Card>
    </div>
  );
}

function Field({
  label,
  value,
  onChange,
  ...props
}: {
  label: string;
  value: string;
  onChange: (value: string) => void;
} & Omit<React.ComponentProps<typeof Input>, "value" | "onChange">) {
  const id = `test-management-${label.toLowerCase().replaceAll(" ", "-")}`;
  return (
    <div className="grid gap-2">
      <Label htmlFor={id}>{label}</Label>
      <Input
        id={id}
        value={value}
        onChange={(event) => onChange(event.target.value)}
        {...props}
      />
    </div>
  );
}
