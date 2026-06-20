import { beforeEach, describe, expect, it, vi } from 'vitest'
import { api, session } from './api'

describe('API client', () => {
  beforeEach(() => localStorage.clear())

  it('adds the stored bearer token and unwraps successful responses', async () => {
    session.set('test-token')
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(JSON.stringify({
      success: true,
      data: { userId: 7, email: 'user@example.com', nickname: 'User', role: 'USER' },
    }), { status: 200, headers: { 'Content-Type': 'application/json' } }))

    const user = await api.me()

    expect(user.userId).toBe(7)
    const headers = fetchMock.mock.calls[0][1]?.headers as Headers
    expect(headers.get('Authorization')).toBe('Bearer test-token')
  })

  it('clears an expired session and exposes the API error message', async () => {
    session.set('expired-token')
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(JSON.stringify({
      success: false,
      error: { code: 'UNAUTHORIZED', message: 'Unauthorized' },
    }), { status: 401, headers: { 'Content-Type': 'application/json' } }))

    await expect(api.me()).rejects.toThrow('Unauthorized')
    expect(session.get()).toBeNull()
  })
})

