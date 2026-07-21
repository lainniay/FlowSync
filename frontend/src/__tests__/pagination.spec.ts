import { describe, expect, it, vi } from 'vitest'

import type { Page } from '@/shared/api/types'
import { fetchAllPages } from '@/shared/api/pagination'

function createPage<T>(
  items: readonly T[],
  page: number,
  size: number,
  totalElements: number,
): Page<T> {
  return {
    items,
    page,
    size,
    totalElements,
    totalPages: Math.ceil(totalElements / size),
  }
}

describe('fetchAllPages', () => {
  it('loads all pages when results exceed one page', async () => {
    const fetchPage = vi.fn<
      (query: { page?: number; size?: number }) => Promise<Page<{ id: string }>>
    >()
      .mockResolvedValueOnce(createPage(
        Array.from({ length: 100 }, (_, index) => ({
          id: String(index + 1),
        })),
        0,
        100,
        150,
      ))
      .mockResolvedValueOnce(createPage(
        Array.from({ length: 50 }, (_, index) => ({
          id: String(index + 101),
        })),
        1,
        100,
        150,
      ))

    const items = await fetchAllPages(fetchPage, {})

    expect(fetchPage).toHaveBeenCalledTimes(2)
    expect(fetchPage).toHaveBeenNthCalledWith(1, {
      page: 0,
      size: 100,
    })
    expect(fetchPage).toHaveBeenNthCalledWith(2, {
      page: 1,
      size: 100,
    })
    expect(items).toHaveLength(150)
    expect(items[0]?.id).toBe('1')
    expect(items[149]?.id).toBe('150')
  })
})
