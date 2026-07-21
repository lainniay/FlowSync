import type { Page, PaginationQuery } from './types'

export const MAX_PAGE_SIZE = 100

export async function fetchAllPages<T, Q extends PaginationQuery>(
  fetchPage: (query: Q) => Promise<Page<T>>,
  baseQuery: Omit<Q, 'page' | 'size'>,
): Promise<readonly T[]> {
  const items: T[] = []
  let page = 0
  let totalPages = 1

  while (page < totalPages) {
    const result = await fetchPage({
      ...baseQuery,
      page,
      size: MAX_PAGE_SIZE,
    } as Q)

    items.push(...result.items)
    totalPages = result.totalPages
    page += 1
  }

  return items
}
