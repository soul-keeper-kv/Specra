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
import { ArrowDown, ArrowUp, ArrowUpDown, Database, Pencil, Trash2 } from "lucide-react";
import Link from "next/link";
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
import type { Note } from "@/lib/api/types";

// Declared statically, outside the component, as the TanStack docs require.
const features = tableFeatures({
  rowSortingFeature,
  sortedRowModel: createSortedRowModel(),
  sortFns: { text: sortFn_text, datetime: sortFn_datetime },
});

const helper = createColumnHelper<typeof features, Note>();

type Props = {
  notes: Note[];
  sorting: SortingState;
  /** Matches React's setState signature, so a useState setter can be passed directly. */
  onSortingChange: OnChangeFn<SortingState>;
  onDelete: (note: Note) => void;
  onIndex: (note: Note) => void;
  indexingId?: string;
};

export function NotesTable({
  notes,
  sorting,
  onSortingChange,
  onDelete,
  onIndex,
  indexingId,
}: Props) {
  // helper.columns() preserves each column's value type; a bare array widens them.
  const columns = useMemo(
    () =>
      helper.columns([
        helper.accessor("title", {
          header: "Title",
          sortFn: "text",
          cell: (info) => (
            <Link
              href={`/notes/${info.row.original.id}`}
              className="font-medium hover:underline"
            >
              {info.getValue()}
            </Link>
          ),
        }),
        helper.accessor("tags", {
          header: "Tags",
          enableSorting: false,
          cell: (info) => (
            <div className="flex flex-wrap gap-1">
              {info.getValue().length === 0 ? (
                <span className="text-muted-foreground">—</span>
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
        helper.accessor("indexedAt", {
          header: "Indexed",
          sortFn: "datetime",
          cell: (info) =>
            info.getValue() ? (
              <Badge variant="secondary" className="font-normal">
                in pgvector
              </Badge>
            ) : (
              <span className="text-xs text-muted-foreground">not indexed</span>
            ),
        }),
        helper.accessor("updatedAt", {
          header: "Updated",
          sortFn: "datetime",
          cell: (info) => (
            <span className="text-muted-foreground tabular-nums">
              {new Date(info.getValue()).toLocaleString()}
            </span>
          ),
        }),
        helper.display({
          id: "actions",
          header: () => <span className="sr-only">Actions</span>,
          cell: ({ row }) => (
            <div className="flex justify-end gap-1">
              <Button
                variant="ghost"
                size="icon"
                aria-label={`Index ${row.original.title}`}
                disabled={indexingId === row.original.id}
                onClick={() => onIndex(row.original)}
              >
                <Database className="size-4" />
              </Button>
              <Button
                variant="ghost"
                size="icon"
                asChild
                aria-label={`Edit ${row.original.title}`}
              >
                <Link href={`/notes/${row.original.id}`}>
                  <Pencil className="size-4" />
                </Link>
              </Button>
              <Button
                variant="ghost"
                size="icon"
                aria-label={`Delete ${row.original.title}`}
                onClick={() => onDelete(row.original)}
              >
                <Trash2 className="size-4 text-destructive" />
              </Button>
            </div>
          ),
        }),
      ]),
    [indexingId, onDelete, onIndex],
  );

  const table = useTable({
    features,
    columns,
    data: notes,
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
          {table.getRowModel().rows.length === 0 ? (
            <TableRow>
              <TableCell
                colSpan={columns.length}
                className="h-24 text-center text-muted-foreground"
              >
                No notes yet.
              </TableCell>
            </TableRow>
          ) : (
            table.getRowModel().rows.map((row) => (
              <TableRow key={row.id}>
                {row.getAllCells().map((cell) => (
                  <TableCell key={cell.id}>
                    <table.FlexRender cell={cell} />
                  </TableCell>
                ))}
              </TableRow>
            ))
          )}
        </TableBody>
      </Table>
    </div>
  );
}
