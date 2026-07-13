import { expect, test } from 'playwright/test'

test('routes only backend API requests through strict mock handling', async ({ page }) => {
  await page.goto('http://127.0.0.1:8081')
  await page.evaluate(() => navigator.serviceWorker.ready)

  const result = await page.evaluate(async () => {
    const matched = await fetch('/api/auth/csrf')
    const outside = await fetch('/apiary')
    let unhandled = 'resolved'

    try {
      await fetch('/api/not-configured')
    } catch (error) {
      unhandled = error instanceof Error ? error.name : 'unknown error'
    }

    return {
      matchedStatus: matched.status,
      outsideStatus: outside.status,
      outsideContentType: outside.headers.get('content-type'),
      unhandled,
    }
  })

  expect(result).toEqual({
    matchedStatus: 200,
    outsideStatus: 200,
    outsideContentType: 'text/html',
    unhandled: 'TypeError',
  })
})
