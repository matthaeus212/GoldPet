import React from 'react';

interface PaginationProps {
  page: number;          // 0-indexed
  totalPages: number;
  totalElements: number;
  size: number;
  onPageChange: (page: number) => void;
  onSizeChange: (size: number) => void;
}

export const Pagination: React.FC<PaginationProps> = ({
  page,
  totalPages,
  totalElements,
  size,
  onPageChange,
  onSizeChange,
}) => {
  // Generate page numbers to show (max 5 with ellipsis)
  const getPageNumbers = (): (number | 'ellipsis')[] => {
    if (totalPages <= 5) {
      return Array.from({ length: totalPages }, (_, i) => i);
    }

    const pages: (number | 'ellipsis')[] = [];

    if (page <= 2) {
      pages.push(0, 1, 2, 3, 'ellipsis', totalPages - 1);
    } else if (page >= totalPages - 3) {
      pages.push(0, 'ellipsis', totalPages - 4, totalPages - 3, totalPages - 2, totalPages - 1);
    } else {
      pages.push(0, 'ellipsis', page - 1, page, page + 1, 'ellipsis', totalPages - 1);
    }

    return pages;
  };

  return (
    <div className="flex items-center justify-between px-2 py-3">
      <div className="text-sm text-gray-600">
        총 <span className="font-medium text-gray-900">{totalElements.toLocaleString()}</span>건
      </div>

      {totalElements > 0 && (
        <div className="flex items-center gap-2">
          <button
            onClick={() => onPageChange(page - 1)}
            disabled={page === 0}
            className="px-3 py-1.5 text-sm border border-gray-300 rounded-lg hover:bg-gray-50 disabled:opacity-50 disabled:cursor-not-allowed"
          >
            이전
          </button>

          <div className="flex items-center gap-1">
            {getPageNumbers().map((pageNum, idx) =>
              pageNum === 'ellipsis' ? (
                <span key={`ellipsis-${idx}`} className="px-2 text-gray-400">…</span>
              ) : (
                <button
                  key={pageNum}
                  onClick={() => onPageChange(pageNum)}
                  className={`min-w-[2rem] px-2 py-1.5 text-sm rounded-lg ${
                    pageNum === page
                      ? 'bg-indigo-600 text-white font-medium'
                      : 'text-gray-700 hover:bg-gray-100'
                  }`}
                >
                  {pageNum + 1}
                </button>
              )
            )}
          </div>

          <button
            onClick={() => onPageChange(page + 1)}
            disabled={page >= totalPages - 1}
            className="px-3 py-1.5 text-sm border border-gray-300 rounded-lg hover:bg-gray-50 disabled:opacity-50 disabled:cursor-not-allowed"
          >
            다음
          </button>

          <select
            value={size}
            onChange={(e) => onSizeChange(Number(e.target.value))}
            className="ml-2 px-2 py-1.5 text-sm border border-gray-300 rounded-lg focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500"
          >
            <option value={10}>10건</option>
            <option value={20}>20건</option>
            <option value={50}>50건</option>
          </select>
        </div>
      )}
    </div>
  );
};
