export type User = { userId: number; email: string; nickname: string; role: string }
export type PageData<T> = { items: T[]; page: number; pageSize: number; totalItems: number; totalPages: number }
export type Resume = { resumeId: number; documentId: number; title: string; latestAnalysisVersion: number; createdAt: string }
export type Job = { jobId: number; documentId?: number; company?: string; position: string; jdText: string; createdAt: string }
export type CareerTask = {
  taskId: number; traceId: string; status: string; progress: number; resumeId: number; jobId: number;
  enabledSteps: string[]; errorMessage?: string; createdAt: string; updatedAt: string; finishedAt?: string
}
export type TaskLog = {
  logId: number; agentName: string; stepName: string; workflowStatus: string; progress: number; status: string;
  outputSummary?: string; errorMessage?: string; durationMs?: number; totalTokens?: number; updatedAt: string
}
export type ReportSummary = {
  reportId: number; taskId?: number; resumeId: number; jobId: number; version: number; status: string;
  resumeTitle: string; company?: string; position: string; createdAt: string
}
export type ReportDetail = ReportSummary & { report: Record<string, unknown>; exportStatus: string }

type Envelope<T> = { success: boolean; data: T; error?: { code: string; message: string }; traceId?: string }

const TOKEN_KEY = 'skillpilot_token'
export const session = {
  get: () => localStorage.getItem(TOKEN_KEY),
  set: (token: string) => localStorage.setItem(TOKEN_KEY, token),
  clear: () => localStorage.removeItem(TOKEN_KEY),
}

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers)
  const token = session.get()
  if (token) headers.set('Authorization', `Bearer ${token}`)
  if (init.body && !(init.body instanceof FormData)) headers.set('Content-Type', 'application/json')
  const response = await fetch(path, { ...init, headers })
  const payload = await response.json() as Envelope<T>
  if (!response.ok || !payload.success) {
    if (response.status === 401) session.clear()
    throw new Error(payload.error?.message || '请求失败，请稍后再试')
  }
  return payload.data
}

export const api = {
  register: (body: { email: string; password: string; nickname: string }) =>
    request<User>('/api/auth/register', { method: 'POST', body: JSON.stringify(body) }),
  login: (email: string, password: string) =>
    request<{ accessToken: string; user: User }>('/api/auth/login', { method: 'POST', body: JSON.stringify({ email, password }) }),
  me: () => request<User>('/api/auth/me'),
  resumes: () => request<PageData<Resume>>('/api/resumes?pageSize=100'),
  jobs: () => request<PageData<Job>>('/api/jobs?pageSize=100'),
  tasks: () => request<PageData<CareerTask>>('/api/career-tasks?pageSize=50'),
  task: (id: number) => request<CareerTask>(`/api/career-tasks/${id}`),
  logs: (id: number) => request<{ taskId: number; traceId: string; items: TaskLog[] }>(`/api/career-tasks/${id}/logs`),
  retryTask: (id: number) => request<CareerTask>(`/api/career-tasks/${id}/retry`, { method: 'POST' }),
  reports: () => request<ReportSummary[]>('/api/reports'),
  report: (id: number) => request<ReportDetail>(`/api/reports/${id}`),
  createTask: (resumeId: number, jobId: number) => request<CareerTask>('/api/career-tasks', {
    method: 'POST', body: JSON.stringify({ resumeId, jobId })
  }),
  upload: async (file: File, fileType: 'RESUME' | 'JD') => {
    const form = new FormData(); form.append('file', file); form.append('fileType', fileType)
    return request<{ fileId: number }>('/api/files/upload', { method: 'POST', body: form })
  },
  parse: (fileId: number) => request<{ documentId: number }>(`/api/files/${fileId}/parse`, { method: 'POST' }),
  chunk: (documentId: number) => request<unknown>(`/api/documents/${documentId}/chunks`, { method: 'POST' }),
  embed: (documentId: number) => request<unknown>(`/api/documents/${documentId}/embeddings`, { method: 'POST' }),
  createResume: (documentId: number, title: string) => request<Resume>('/api/resumes', {
    method: 'POST', body: JSON.stringify({ documentId, title })
  }),
  createJob: (documentId: number, company: string, position: string) => request<Job>('/api/jobs', {
    method: 'POST', body: JSON.stringify({ documentId, company, position })
  }),
}

