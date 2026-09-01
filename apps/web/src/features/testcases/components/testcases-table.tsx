"use client";

import {
  createColumnHelper,
  createSortedRowModel,
  rowSortingFeature,
  sortFn_datetime,
  sortFn_text,
  tableFeatures,
  useTable,
  type OnChangeFn,
  type SortingState,
} from "@tanstack/react-table";
import { ArrowDown, ArrowUp, ArrowUpDown, Trash2 } from "lucide-react";
import { useFormatter, useTranslations } from "next-intl";
import { useMemo } from "react";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import {
  OutOfDateBadge,
  PriorityBadge,
  StatusBadge,
} from "@/features/testcases/components/testcase-badges";
import { Link } from "@/i18n/navigation";
import type { TestCaseSummary } from "@/lib/api/types";

// Declared statically, outside the component, as the TanStack docs require.
const features = tableFeatures({
  rowSortingFeature,
  sortedRowModel: createSortedRowModel(),
  sortFns: { text: sortFn_text, datetime: sortFn_datetime },
});

const helper = createColumnHelper<typeof features, TestCaseSummary>();

type Props = {
  testCases: TestCaseSummary[];
  sorting: SortingState;
  /** Matches React's setState signature, so a useState setter can be passed directly. */
  onSortingChange: OnChangeFn<SortingState>;
  onDelete: (testCase: TestCaseSummary) => void;
};

export function TestCasesTable({ testCases, sorting, onSortingChange, onDelete }: Props) {
  const t = useTranslations("testcases.table");
  const format = useFormatter();

  // helper.columns() preserves each column's value type; a bare array widens them.
  const columns = useMemo(
    () =>
      helper.columns([
        helper.accessor("reference", {
          header: t("reference"),
          // "TC-10" sorts before "TC-9" as text; the list is already newest-first anyway.
          enableSorting: false,
          cell: ({ row }) => (
            <Link
              href={`/projects/${row.original.projectId}/test-cases/${row.original.id}`}
              className="font-mono text-xs font-medium whitespace-nowrap hover:underline"
            >
              {row.original.reference}
            </Link>
          ),
        }),
        helper.accessor("title", {
          header: t("title"),
          sortFn: "text",
          cell: ({ row }) => (
            <Link
              href={`/projects/${row.original.projectId}/test-cases/${row.original.id}`}
              className="font-medium hover:underline"
            >
              {row.original.title}
            </Link>
          ),
        }),
        helper.accessor("priority", {
          header: t("priority"),
          enableSorting: false,
          cell: (info) => <PriorityBadge priority={info.getValue()} />,
        }),
        helper.accessor("automationStatus", {
          header: t("status"),
          enableSorting: false,
          cell: ({ row }) => (
            <div className="flex flex-wrap items-center gap-1">
              <StatusBadge status={row.original.automationStatus} />
              {row.original.outOfDate ? <OutOfDateBadge /> : null}
            </div>
          ),
        }),
        helper.accessor("tags", {
          header: t("tags"),
          enableSorting: false,
          cell: (info) => (
            <div className="flex flex-wrap gap-1">
              {info.getValue().length === 0 ? (
                <span className="text-muted-foreground">{t("noTags")}</span>
              ) : (
                info.getValue().map((tag) => (
                  <Badge key={tag} variant="outline" className="font-normal">
                    {tag}
                  </Badge>
                ))
              )}
            </div>
          ),
        }),
        helper.accessor("updatedAt", {
          header: t("updated"),
          sortFn: "datetime",
          cell: (info) => (
            <span className="whitespace-nowrap text-muted-foreground tabular-nums">
              {format.dateTime(new Date(info.getValue()), {
                dateStyle: "medium",
                timeStyle: "short",
              })}
            </span>
          ),
        }),
        helper.display({
          id: "actions",
          header: () => <span className="sr-only">{t("actions")}</span>,
          cell: ({ row }) => (
            <div className="flex justify-end">
              <Button
                variant="ghost"
                size="icon"
                aria-label={t("deleteRow", { title: row.original.title })}
                onClick={() => onDelete(row.original)}
              >
                <Trash2 className="size-4 text-destructive" />
              </Button>
            </div>
          ),
        }),
      ]),
    [format, onDelete, t],
  );

  const table = useTable({
    features,
    columns,
    data: testCases,
    state: { sorting },
    onSortingChange,
    // Rows arrive already paged and ordered from Spring Data, so the header
    // controls only translate into the API's `sort` parameter.
    manualSorting: true,
  });

  return (
    <div className="overflow-x-auto rounded-lg border">
      <Table>
        <TableHeader>
          {table.getHeaderGroups().map((headerGroup) => (
            <TableRow key={headerGroup.id}>
              {headerGroup.headers.map((header) => {
                const sortable = header.column.getCanSort();
                const direction = header.column.getIsSorted();
                return (
                  <TableHead
                    key={header.id}
                    className={header.column.id === "actions" ? "text-right" : undefined}
                    aria-sort={
                      direction === "asc"
                        ? "ascending"
                        : direction === "desc"
                          ? "descending"
                          : undefined
                    }
                  >
                    {sortable ? (
                      <button
                        type="button"
                        onClick={header.column.getToggleSortingHandler()}
                        className="-mx-1 flex items-center gap-1 rounded px-1 py-0.5 hover:text-foreground"
                      >
                        <table.FlexRender header={header} />
                        {direction === "asc" ? (
                          <ArrowUp className="size-3.5" />
                        ) : direction === "desc" ? (
                          <ArrowDown className="size-3.5" />
                        ) : (
                          <ArrowUpDown className="size-3.5 opacity-40" />
                        )}
                      </button>
                    ) : (
                      <table.FlexRender header={header} />
                    )}
                  </TableHead>
                );
              })}
            </TableRow>
          ))}
        </TableHeader>

        <TableBody>
          {table.getRowModel().rows.map((row) => (
            <TableRow key={row.id}>
              {row.getAllCells().map((cell) => (
                <TableCell key={cell.id}>
                  <table.FlexRender cell={cell} />
                </TableCell>
              ))}
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </div>
  );
}
