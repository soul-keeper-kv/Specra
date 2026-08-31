"use client";

import { useTranslations } from "next-intl";

import { Alert, AlertDescription } from "@/components/ui/alert";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { useAuthStore, useSessionReady, useSessionUser } from "@/features/auth/store";
import { Link } from "@/i18n/navigation";

/** Edits the local stand-in session. Every keystroke is written straight to the store. */
export function ProfileSettings() {
  const t = useTranslations("settings.profile");
  const tNav = useTranslations("nav");
  const user = useSessionUser();
  const ready = useSessionReady();
  const update = useAuthStore((state) => state.update);

  return (
    <Card>
      <CardHeader>
        <CardTitle>{t("title")}</CardTitle>
        <CardDescription>{t("description")}</CardDescription>
      </CardHeader>
      <CardContent className="grid gap-4">
        {ready && !user ? (
          <Alert>
            <AlertDescription>
              <Link href="/sign-in" className="underline underline-offset-4">
                {tNav("signIn")}
              </Link>
            </AlertDescription>
          </Alert>
        ) : (
          <>
            <div className="grid gap-2">
              <Label htmlFor="profile-name">{t("name")}</Label>
              <Input
                id="profile-name"
                value={user?.name ?? ""}
                disabled={!user}
                onChange={(event) => update({ name: event.target.value })}
              />
            </div>
            <div className="grid gap-2">
              <Label htmlFor="profile-email">{t("email")}</Label>
              <Input
                id="profile-email"
                type="email"
                value={user?.email ?? ""}
                disabled={!user}
                onChange={(event) => update({ email: event.target.value })}
              />
            </div>
          </>
        )}

        <p className="text-xs text-muted-foreground">{t("demoNotice")}</p>
      </CardContent>
    </Card>
  );
}
