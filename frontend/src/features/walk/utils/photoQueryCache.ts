import type { InfiniteData } from '@tanstack/react-query';
import type { PageResponse, WalkPhotoItem } from '../../../services/walkService';

export function mapPagesUpdateItemById(
  old: InfiniteData<PageResponse<WalkPhotoItem>> | undefined,
  id: number,
  patch: Partial<WalkPhotoItem>
): InfiniteData<PageResponse<WalkPhotoItem>> | undefined {
  if (!old) return old;
  return {
    ...old,
    pages: old.pages.map(page => ({
      ...page,
      content: page.content.map(item => (item.id === id ? { ...item, ...patch } : item)),
    })),
  };
}

export function mapPagesRemoveItemById(
  old: InfiniteData<PageResponse<WalkPhotoItem>> | undefined,
  id: number
): InfiniteData<PageResponse<WalkPhotoItem>> | undefined {
  if (!old) return old;
  return {
    ...old,
    pages: old.pages.map(page => ({
      ...page,
      content: page.content.filter(item => item.id !== id),
    })),
  };
}

export function mapArrayUpdateItemById(
  old: PageResponse<WalkPhotoItem> | undefined,
  id: number,
  patch: Partial<WalkPhotoItem>
): PageResponse<WalkPhotoItem> | undefined {
  if (!old) return old;
  return {
    ...old,
    content: old.content.map(item => (item.id === id ? { ...item, ...patch } : item)),
  };
}

export function mapArrayRemoveItemById(
  old: PageResponse<WalkPhotoItem> | undefined,
  id: number
): PageResponse<WalkPhotoItem> | undefined {
  if (!old) return old;
  return {
    ...old,
    content: old.content.filter(item => item.id !== id),
  };
}
