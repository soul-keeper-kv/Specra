"use client";

import { Loader2, Trash2, UserPlus, Users } from "lucide-react";
import { useFormatter, useTranslations } from "next-intl";
import { useState } from "react";

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
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
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
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { AuthFormError } from "@/features/auth/components/auth-form-error";
import { useSessionUser } from "@/features/auth/store";
import {
  useAddMember,
  useChangeMemberRole,
  useMembers,
  useRemoveMember,
  useRoles,
} from "@/features/workspaces/api/members";
import { useActiveWorkspace } from "@/features/workspaces/api/workspaces";
import type { Member, Permission, WorkspaceRole } from "@/lib/api/types";

/**
 * Who is in this workspace, and as what.
 *
 * <p>Every control here is gated on the caller's own role, which arrives on the workspace itself —
 * so the screen offers exactly what the API would accept. The rules it mirrors are the API's: you
 * cannot change your own role, cannot act on somebody at or above your rank, and cannot grant a
 * role above your own. It mirrors them to keep the UI honest, and the API still enforces them,
 * because a disabled button is a courtesy and not a control.
 */
export function MembersView() {
  const t = useTranslations("members");
  const {
    workspace,
    isPending: workspacePending,
    error: workspaceError,
  } = useActiveWorkspace();

  if (workspacePending) return <MembersSkeleton />;
  if (workspaceError) return <ErrorState error={workspaceError} />;
  if (!workspace) {
    return (
      <EmptyState icon={Users} title={t("noWorkspace")} description={t("noWorkspaceHint")} />
    );
  }

  return (
    <Members
      workspaceId={workspace.id}
      myRole={workspace.role}
      workspaceName={workspace.name}
    />
  );
}

function Members({
  workspaceId,
  myRole,
  workspaceName,
}: {
  workspaceId: string;
  myRole: WorkspaceRole;
  workspaceName: string;
}) {
  const t = useTranslations("members");
  const format = useFormatter();
  const me = useSessionUser();

  const members = useMembers(workspaceId);
  const roles = useRoles();
  const addMember = useAddMember(workspaceId);
  const changeRole = useChangeMemberRole(workspaceId);
  const removeMember = useRemoveMember(workspaceId);

  const [email, setEmail] = useState("");
  const [newRole, setNewRole] = useState<WorkspaceRole>("MEMBER");
  const [pendingRemoval, setPendingRemoval] = useState<Member | null>(null);

  const permissions = roles.data?.find((role) => role.role === myRole)?.permissions ?? [];
  const can = (permission: Permission) => permissions.includes(permission);
  /** Only roles at or below your own: the API refuses a grant above it, so it is not offered. */
  const grantable = (roles.data ?? []).filter((role) => rank(role.role) >= rank(myRole));

  return (
    <div className="grid gap-6">
      {can("member-add") ? (
        <Card>
          <CardHeader>
            <CardTitle>{t("add.title")}</CardTitle>
            <CardDescription>{t("add.description")}</CardDescription>
          </CardHeader>
          <CardContent>
            <form
              className="grid gap-4 sm:grid-cols-[1fr_auto_auto] sm:items-end"
              onSubmit={(event) => {
                event.preventDefault();
                addMember.mutate({ email, role: newRole }, { onSuccess: () => setEmail("") });
              }}
            >
              <div className="grid gap-2">
                <Label htmlFor="member-email">{t("add.email")}</Label>
                <Input
                  id="member-email"
                  type="email"
                  required
                  value={email}
                  onChange={(event) => setEmail(event.target.value)}
                  placeholder={t("add.emailPlaceholder")}
                />
              </div>
              <div className="grid gap-2">
                <Label htmlFor="member-role">{t("add.role")}</Label>
                <Select
                  value={newRole}
                  onValueChange={(value) => setNewRole(value as WorkspaceRole)}
                >
                  <SelectTrigger id="member-role" className="w-40">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    {grantable.map((role) => (
                      <SelectItem key={role.role} value={role.role}>
                        {t(`roles.${role.role}`)}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
              <Button type="submit" disabled={addMember.isPending || email.trim().length === 0}>
                {addMember.isPending ? (
                  <Loader2 className="size-4 animate-spin" />
                ) : (
                  <UserPlus className="size-4" />
                )}
                {t("add.submit")}
              </Button>
              <div className="sm:col-span-3">
                <AuthFormError error={addMember.error} />
              </div>
            </form>
          </CardContent>
        </Card>
      ) : null}

      <Card>
        <CardHeader>
          <CardTitle>{t("title")}</CardTitle>
          <CardDescription>{t("subtitle", { workspace: workspaceName })}</CardDescription>
        </CardHeader>
        <CardContent className="grid gap-4">
          <AuthFormError error={changeRole.error ?? removeMember.error} />

          {members.isPending ? (
            <MembersSkeleton />
          ) : members.error ? (
            <ErrorState error={members.error} onRetry={() => void members.refetch()} />
          ) : members.data && members.data.content.length > 0 ? (
            <div className="overflow-x-auto">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>{t("columns.person")}</TableHead>
                    <TableHead>{t("columns.role")}</TableHead>
                    <TableHead>{t("columns.joined")}</TableHead>
                    <TableHead className="w-10" />
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {members.data.content.map((member) => {
                    const isMe = member.userId === me?.id;
                    // The same three rules the API applies, so the screen cannot offer a
                    // control that would come back as a 403.
                    const editable =
                      can("member-update-role") && !isMe && rank(myRole) < rank(member.role);
                    const removable =
                      can("member-remove") && !isMe && rank(myRole) < rank(member.role);

                    return (
                      <TableRow key={member.id}>
                        <TableCell>
                          <div className="grid">
                            <span className="font-medium">
                              {member.displayName}
                              {isMe ? (
                                <span className="ml-2 text-xs text-muted-foreground">
                                  {t("you")}
                                </span>
                              ) : null}
                            </span>
                            <span className="text-xs text-muted-foreground">
                              {member.email}
                            </span>
                          </div>
                        </TableCell>
                        <TableCell>
                          {editable ? (
                            <Select
                              value={member.role}
                              disabled={changeRole.isPending}
                              onValueChange={(value) =>
                                changeRole.mutate({
                                  userId: member.userId,
                                  role: value as WorkspaceRole,
                                })
                              }
                            >
                              <SelectTrigger className="w-40" aria-label={t("columns.role")}>
                                <SelectValue />
                              </SelectTrigger>
                              <SelectContent>
                                {grantable.map((role) => (
                                  <SelectItem key={role.role} value={role.role}>
                                    {t(`roles.${role.role}`)}
                                  </SelectItem>
                                ))}
                              </SelectContent>
                            </Select>
                          ) : (
                            <Badge variant="secondary">{t(`roles.${member.role}`)}</Badge>
                          )}
                        </TableCell>
                        <TableCell className="text-sm text-muted-foreground">
                          {format.dateTime(new Date(member.createdAt), {
                            dateStyle: "medium",
                          })}
                        </TableCell>
                        <TableCell>
                          {removable ? (
                            <Button
                              variant="ghost"
                              size="icon"
                              aria-label={t("remove.action")}
                              onClick={() => setPendingRemoval(member)}
                            >
                              <Trash2 className="size-4" />
                            </Button>
                          ) : null}
                        </TableCell>
                      </TableRow>
                    );
                  })}
                </TableBody>
              </Table>
            </div>
          ) : (
            <EmptyState icon={Users} title={t("empty")} description={t("emptyHint")} />
          )}
        </CardContent>
      </Card>

      <AlertDialog
        open={pendingRemoval !== null}
        onOpenChange={(open) => {
          if (!open) setPendingRemoval(null);
        }}
      >
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>{t("remove.title")}</AlertDialogTitle>
            <AlertDialogDescription>
              {t("remove.description", { name: pendingRemoval?.displayName ?? "" })}
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>{t("remove.cancel")}</AlertDialogCancel>
            <AlertDialogAction
              onClick={() => {
                if (pendingRemoval) removeMember.mutate(pendingRemoval.userId);
                setPendingRemoval(null);
              }}
            >
              {t("remove.confirm")}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </div>
  );
}

/** Seniority, matching the API's declaration order: 0 is the most senior. */
const ORDER: WorkspaceRole[] = ["OWNER", "ADMIN", "MEMBER"];

function rank(role: WorkspaceRole): number {
  return ORDER.indexOf(role);
}

function MembersSkeleton() {
  return (
    <div className="grid gap-2">
      <Skeleton className="h-10 w-full" />
      <Skeleton className="h-10 w-full" />
      <Skeleton className="h-10 w-2/3" />
    </div>
  );
}
