import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { api, session } from './api'

describe('API client', () => {
  beforeEach(() => localStorage.clear())
  afterEach(() => vi.restoreAllMocks())

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

  it('parses task SSE event ids and sends the reconnect cursor', async () => {
    session.set('stream-token')
    const body = [
      'id:snapshot-1', 'event:TASK_SNAPSHOT', 'data:{"task":{"taskId":1}}', '',
      'id:step-9', 'event:STEP_EVENT', 'data:{"logId":9}', '', '',
    ].join('\n')
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(body, {
      status: 200, headers: { 'Content-Type': 'text/event-stream' },
    }))
    const events: Array<{ event: string; id?: string }> = []

    await api.streamTaskEvents(1, 'step-previous', (event, _data, id) => events.push({ event, id }),
      new AbortController().signal)

    expect(events).toEqual([
      { event: 'TASK_SNAPSHOT', id: 'snapshot-1' },
      { event: 'STEP_EVENT', id: 'step-9' },
    ])
    const headers = fetchMock.mock.calls[0][1]?.headers as Record<string, string>
    expect(headers.Authorization).toBe('Bearer stream-token')
    expect(headers['Last-Event-ID']).toBe('step-previous')
  })
})
