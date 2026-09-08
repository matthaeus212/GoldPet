import React, { useState } from 'react';
import clsx from 'clsx';
import {
  useReactTable,
  getCoreRowModel,
  getSortedRowModel,
  flexRender,
  type SortingState,
  type ColumnDef as TanStackColumnDef,
} from '@tanstack/react-table';
import { ArrowUp, ArrowDown, ArrowUpDown } from 'lucide-react';

export interface ColumnDef<T> {
  key: string;
  header: string;
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  render?: (value: any, row: T) => React.ReactNode;
  align?: 'left' | 'center' | 'right';
  width?: string;
  className?: string;
  sortable?: boolean;
}

interface DataTableProps<T> {
  columns: ColumnDef<T>[];
  data: T[];
  loading?: boolean;
  emptyMessage?: string;
  onRowClick?: (row: T) => void;
  rowClassName?: (row: T) => string;
  rowDataTestId?: (row: T) => string;
}

function getNestedValue(obj: Record<string, unknown>, path: string): unknown {
  return path.split('.').reduce<unknown>((acc, part) => {
    if (acc !== null && typeof acc === 'object') {
      return (acc as Record<string, unknown>)[part];
    }
    return undefined;
  }, obj);
}

const alignClass = (align?: string) => {
  if (align === 'center') return 'text-center';
  if (align === 'right') return 'text-right';
  return 'text-left';
};

function HeaderRow<T>({ columns }: { columns: ColumnDef<T>[] }) {
  return (
    <tr className="border-b border-gray-200">
      {columns.map((col) => (
        <th
          key={col.key}
          className={clsx('px-4 py-3 text-xs font-medium text-gray-500 uppercase tracking-wider', alignClass(col.align))}
          style={col.width ? { width: col.width } : undefined}
        >
          {col.header}
        </th>
      ))}
    </tr>
  );
}

export function DataTable<T>({
  columns,
  data,
  loading = false,
  emptyMessage = '데이터가 없습니다.',
  onRowClick,
  rowClassName,
  rowDataTestId,
}: DataTableProps<T>) {
  const [sorting, setSorting] = useState<SortingState>([]);

  const allNonSortable = columns.every((col) => col.sortable === false);
  const colByKey = Object.fromEntries(columns.map((col) => [col.key, col]));

  const tanstackColumns: TanStackColumnDef<T>[] = columns.map((col) => ({
    id: col.key,
    accessorFn: (row: T) => getNestedValue(row as Record<string, unknown>, col.key),
    header: col.header,
    enableSorting: col.sortable !== false && !allNonSortable,
    cell: ({ row }) => {
      const value = getNestedValue(row.original as Record<string, unknown>, col.key);
      return col.render ? col.render(value, row.original) : String(value ?? '-');
    },
  }));

  // eslint-disable-next-line react-hooks/incompatible-library -- TanStack Table returns stable fn refs by design
  const table = useReactTable({
    data,
    columns: tanstackColumns,
    state: { sorting },
    onSortingChange: setSorting,
    getCoreRowModel: getCoreRowModel(),
    getSortedRowModel: getSortedRowModel(),
  });

  if (loading) {
    return (
      <div className="overflow-x-auto">
        <table className="w-full">
          <thead>
            <HeaderRow columns={columns} />
          </thead>
          <tbody>
            {Array.from({ length: 5 }).map((_, i) => (
              <tr key={`skeleton-${i}`} className="border-b border-gray-100">
                {columns.map((col) => (
                  <td key={col.key} className="px-4 py-3">
                    <div className="h-4 bg-gray-200 rounded animate-pulse" />
                  </td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    );
  }

  if (data.length === 0) {
    return (
      <div className="overflow-x-auto">
        <table className="w-full">
          <thead>
            <HeaderRow columns={columns} />
          </thead>
        </table>
        <div className="py-12 text-center text-gray-500">{emptyMessage}</div>
      </div>
    );
  }

  return (
    <div className="overflow-x-auto">
      <table className="w-full">
        <thead>
          <tr className="border-b border-gray-200">
            {table.getHeaderGroups()[0].headers.map((header) => {
              const col = colByKey[header.id];
              const isSortable = col.sortable !== false && !allNonSortable;
              const sortDir = header.column.getIsSorted();
              return (
                <th
                  key={header.id}
                  className={clsx('px-4 py-3 text-xs font-medium text-gray-500 uppercase tracking-wider', alignClass(col.align))}
                  style={col.width ? { width: col.width } : undefined}
                >
                  {isSortable ? (
                    <button
                      onClick={header.column.getToggleSortingHandler()}
                      className="inline-flex items-center gap-1 hover:text-gray-700"
                    >
                      {flexRender(header.column.columnDef.header, header.getContext())}
                      {sortDir === 'asc' ? (
                        <ArrowUp className="w-3 h-3" />
                      ) : sortDir === 'desc' ? (
                        <ArrowDown className="w-3 h-3" />
                      ) : (
                        <ArrowUpDown className="w-3 h-3 opacity-40" />
                      )}
                    </button>
                  ) : (
                    flexRender(header.column.columnDef.header, header.getContext())
                  )}
                </th>
              );
            })}
          </tr>
        </thead>
        <tbody>
          {table.getRowModel().rows.map((row) => (
            <tr
              key={row.id}
              onClick={onRowClick ? () => onRowClick(row.original) : undefined}
              data-testid={rowDataTestId?.(row.original)}
              className={clsx(
                'border-b border-gray-100 transition-colors',
                onRowClick && 'cursor-pointer',
                'hover:bg-gray-50',
                rowClassName?.(row.original),
              )}
            >
              {row.getVisibleCells().map((cell) => {
                const col = colByKey[cell.column.id];
                return (
                  <td
                    key={cell.id}
                    className={clsx('px-4 py-3 text-sm text-gray-900', alignClass(col.align), col.className)}
                    style={col.width ? { width: col.width } : undefined}
                  >
                    {flexRender(cell.column.columnDef.cell, cell.getContext())}
                  </td>
                );
              })}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
