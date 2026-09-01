"use client";

import { ExternalLink, Loader2, Plug, Unplug } from "lucide-react";
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

export function TestManagementView({ projectId }: { projectId: string }) {
  const t = useTranslations("testManagement");
  const active = useActiveWorkspace();
  const binding = useTestManagementBinding(projectId);
  const createConnection = useCreateTestManagementConnection(active.workspace?.id);
  const bind = useBindTestManagement(projectId);
  const unbind = useUnbindTestManagement(projectId);
  const verify = useVerifyTestManagement(projectId);
  const tests = useExternalTests(projectId, Boolean(binding.data));

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
        <CardContent>
          {tests.isPending ? <Skeleton className="h-28 w-full" /> : null}
          {tests.isError ? (
            <ErrorState error={tests.error} onRetry={() => void tests.refetch()} />
          ) : null}
          {tests.data?.length === 0 ? (
            <p className="text-sm text-muted-foreground">{t("tests.empty")}</p>
          ) : null}
          <div className="grid divide-y">
            {tests.data?.map((test) => (
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
              </div>
            ))}
          </div>
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
