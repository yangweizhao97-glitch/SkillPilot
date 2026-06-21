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
export type ToolCall = {
  toolCallId: string; agentName: string; toolName: string; inputSummary?: Record<string, unknown>;
  resultSummary?: Record<string, unknown>; status: 'TOOL_STARTED' | 'TOOL_COMPLETED' | 'TOOL_FAILED';
  durationMs?: number; errorMessage?: string; updatedAt: string
}
export type ReportSummary = {
  reportId: number; taskId?: number; resumeId: number; jobId: number; version: number; status: string;
  resumeTitle: string; company?: string; position: string; createdAt: string
}
export type ReportDetail = ReportSummary & { report: Record<string, unknown>; exportStatus: string }
export type InterviewMessage = {
  messageId: number; questionId?: number; role: 'INTERVIEWER' | 'CANDIDATE';
  content: string; sequenceNo: number; createdAt: string
}
export type InterviewScoreDimension = { key: string; label: string; score: number; weight: number; rationale: string }
export type InterviewEvaluation = {
  evaluationId: number; questionId: number; answerMessageId: number; overallScore: number;
  result: { dimensions: InterviewScoreDimension[]; strengths: string[]; improvements: string[]; improvedAnswer: string };
  schemaVersion: string; createdAt: string
}
export type InterviewSessionSummary = {
  sessionId: number; resumeId: number; jobId: number; status: 'IN_PROGRESS' | 'FINISHED';
  currentQuestion: number; totalQuestions: number; createdAt: string; updatedAt: string; finishedAt?: string
}
export type InterviewSession = InterviewSessionSummary & { messages: InterviewMessage[]; evaluations: InterviewEvaluation[] }
export type InterviewReview = {
  reviewId: number; sessionId: number; overallScore: number; evaluatedAnswers: number; schemaVersion: string;
  generationSource: 'LLM' | 'FALLBACK'; createdAt: string;
  result: {
    summary: string; strengths: string[]; gaps: string[]; recommendedPracticeQuestions: string[];
    dimensions: { key: string; label: string; score: number; assessment: string }[];
    actionPlan: { priority: number; action: string; reason: string }[]
  }
}
export type InterviewReviewState = { available: boolean; review?: InterviewReview }
export type InterviewMemory = {
  available: boolean; sessionCount: number; answerCount: number; averageScore: number;
  strengths: string[]; improvementAreas: string[]; topics: string[]; summary: string; revision: number
}
export type TaskEventSnapshot = {
  task: CareerTask; logs: TaskLog[]; toolCalls: ToolCall[];
  resumedAfterEventId?: string; synchronizedAt: string
}

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
  logs: (id: number) => request<{ taskId: number; traceId: string; items: TaskLog[]; toolCalls: ToolCall[] }>(`/api/career-tasks/${id}/logs`),
  streamTaskEvents: async (id: number, lastEventId: string | undefined,
    onEvent: (event: string, data: Record<string, unknown>, eventId?: string) => void,
    signal: AbortSignal) => {
    const headers: Record<string, string> = { Authorization: `Bearer ${session.get() || ''}` }
    if (lastEventId) headers['Last-Event-ID'] = lastEventId
    const response = await fetch(`/api/career-tasks/${id}/events`, { headers, signal })
    if (!response.ok || !response.body) {
      if (response.status === 401) session.clear()
      throw new Error('任务事件流连接失败')
    }
    const reader = response.body.getReader(); const decoder = new TextDecoder(); let buffer = ''
    while (true) {
      const { done, value } = await reader.read(); if (done) break
      buffer += decoder.decode(value, { stream: true })
      const frames = buffer.split('\n\n'); buffer = frames.pop() || ''
      for (const frame of frames) {
        let event = 'message'; let data = ''; let eventId: string | undefined
        for (const line of frame.split('\n')) {
          if (line.startsWith('id:')) eventId = line.slice(3).trim()
          if (line.startsWith('event:')) event = line.slice(6).trim()
          if (line.startsWith('data:')) data += line.slice(5).trim()
        }
        if (data) onEvent(event, JSON.parse(data) as Record<string, unknown>, eventId)
      }
    }
  },
  retryTask: (id: number) => request<CareerTask>(`/api/career-tasks/${id}/retry`, { method: 'POST' }),
  reports: () => request<ReportSummary[]>('/api/reports'),
  report: (id: number) => request<ReportDetail>(`/api/reports/${id}`),
  interviewSessions: () => request<InterviewSessionSummary[]>('/api/interview/sessions'),
  interviewSession: (id: number) => request<InterviewSession>(`/api/interview/sessions/${id}`),
  createInterviewSession: (resumeId: number, jobId: number) => request<InterviewSession>('/api/interview/sessions', {
    method: 'POST', body: JSON.stringify({ resumeId, jobId })
  }),
  answerInterview: (id: number, answer: string) => request<InterviewSession>(`/api/interview/sessions/${id}/answers`, {
    method: 'POST', body: JSON.stringify({ answer })
  }),
  streamInterviewAnswer: async (id: number, answer: string,
    onEvent: (event: string, data: Record<string, unknown>) => void) => {
    const response = await fetch(`/api/interview/sessions/${id}/answers/stream`, {
      method: 'POST', headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${session.get() || ''}` },
      body: JSON.stringify({ answer })
    })
    if (!response.ok || !response.body) throw new Error('面试流连接失败')
    const reader = response.body.getReader(); const decoder = new TextDecoder(); let buffer = ''
    while (true) {
      const { done, value } = await reader.read(); if (done) break
      buffer += decoder.decode(value, { stream: true })
      const frames = buffer.split('\n\n'); buffer = frames.pop() || ''
      for (const frame of frames) {
        let event = 'message'; let data = ''
        for (const line of frame.split('\n')) {
          if (line.startsWith('event:')) event = line.slice(6).trim()
          if (line.startsWith('data:')) data += line.slice(5).trim()
        }
        if (data) onEvent(event, JSON.parse(data) as Record<string, unknown>)
      }
    }
  },
  finishInterview: (id: number) => request<InterviewSession>(`/api/interview/sessions/${id}/finish`, { method: 'POST' }),
  interviewReview: (id: number) => request<InterviewReviewState>(`/api/interview/sessions/${id}/review`),
  generateInterviewReview: (id: number) => request<InterviewReview>(`/api/interview/sessions/${id}/review`, { method: 'POST' }),
  interviewMemory: (resumeId: number, jobId: number) =>
    request<InterviewMemory>(`/api/interview/memory?resumeId=${resumeId}&jobId=${jobId}`),
  clearInterviewMemory: (resumeId: number, jobId: number) =>
    request<void>(`/api/interview/memory?resumeId=${resumeId}&jobId=${jobId}`, { method: 'DELETE' }),
  createTask: (resumeId: number, jobId: number) => request<CareerTask>('/api/career-tasks', {
    method: 'POST', body: JSON.stringify({ resumeId, jobId })
  }),
  upload: async (file: File, fileType: 'RESUME' | 'JD' | 'NOTE' | 'PROJECT_DOC') => {
    const form = new FormData(); form.append('file', file); form.append('fileType', fileType)
    return request<{ fileId: number }>('/api/files/upload', { method: 'POST', body: form })
  },
  processFile: (fileId: number) => request<{ documentId: number; status: 'READY' }>(`/api/files/${fileId}/process`, { method: 'POST' }),
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
