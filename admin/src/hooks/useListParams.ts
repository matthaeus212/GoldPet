import { useSearchParams } from 'react-router-dom';
import { useCallback, useMemo } from 'react';

interface ListParamsOptions {
  defaultSize?: number;
}

export function useListParams({ defaultSize = 20 }: ListParamsOptions = {}) {
  const [searchParams, setSearchParams] = useSearchParams();

  const page = useMemo(() => {
    const p = searchParams.get('page');
    return p ? Math.max(0, parseInt(p, 10)) : 0;
  }, [searchParams]);

  const size = useMemo(() => {
    const s = searchParams.get('size');
    return s ? parseInt(s, 10) : defaultSize;
  }, [searchParams, defaultSize]);

  const search = useMemo(() => searchParams.get('search') || '', [searchParams]);

  const filters = useMemo(() => {
    const result: Record<string, string> = {};
    searchParams.forEach((value, key) => {
      if (!['page', 'size', 'search'].includes(key)) {
        result[key] = value;
      }
    });
    return result;
  }, [searchParams]);

  const setPage = useCallback((newPage: number) => {
    setSearchParams(prev => {
      const next = new URLSearchParams(prev);
      next.set('page', String(newPage));
      return next;
    });
  }, [setSearchParams]);

  const setSize = useCallback((newSize: number) => {
    setSearchParams(prev => {
      const next = new URLSearchParams(prev);
      next.set('size', String(newSize));
      next.set('page', '0');
      return next;
    });
  }, [setSearchParams]);

  const setSearch = useCallback((newSearch: string) => {
    setSearchParams(prev => {
      const next = new URLSearchParams(prev);
      if (newSearch) {
        next.set('search', newSearch);
      } else {
        next.delete('search');
      }
      next.set('page', '0');
      return next;
    });
  }, [setSearchParams]);

  const setFilter = useCallback((key: string, value: string | null) => {
    setSearchParams(prev => {
      const next = new URLSearchParams(prev);
      if (value) {
        next.set(key, value);
      } else {
        next.delete(key);
      }
      next.set('page', '0');
      return next;
    });
  }, [setSearchParams]);

  return { page, size, search, filters, setPage, setSize, setSearch, setFilter };
}
