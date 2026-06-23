import { useCallback, useEffect, useRef, useState, type CSSProperties, type FormEvent, type ReactNode } from 'react'
import {
  Activity, ArrowLeft, BarChart3, BookOpen, Bot, BriefcaseBusiness, Check, ChevronRight, CircleAlert, Clock3,
  Download, FileText, LayoutDashboard, ListChecks, LogOut, Menu, MessageSquare, RefreshCw, Send,
  Sparkles, Square, Trash2, Upload, X
} from 'lucide-react'
import { api, session, type CareerTask, type InterviewMemory, type InterviewQuestionAnswer, type InterviewReview, type InterviewSession, type InterviewSessionSummary, type Job, type LearningPlan, type LearningPlanInterviewQuestion, type ReportDetail, type ReportSummary, type Resume, type TaskEventSnapshot, type TechnicalDetail, type TutorSession, type TutorSessionSummary, type User, type UserTaskStep } from './api'
import './App.css'

type View = 'overview' | 'prepare' | 'tasks' | 'reports' | 'interviews' | 'tutor'
type InterviewItem = { questionId: number; question: string; questionType: string; difficulty: string; expectedPoints?: string[]; citations?: string[]; noCitationReason?: string }
type AggregatedReport = {
  status: string; citations?: string[]; resume?: { title: string }; job?: { company?: string; position: string };
  jobMatch?: { status: string; reason?: string; data?: { matchScore?: number; summary?: string; strengths?: string[]; suggestedResumeChanges?: string[] } };
  resumeAnalysis?: { status: string; reason?: string; data?: { highlights?: string[]; suggestions?: string[] } };
  interviewQuestions?: { status: string; reason?: string; items?: InterviewItem[] }
}

const statusLabel: Record<string, string> = {
  PENDING: '等待中', MATCHING_JOB: '岗位匹配',
  ANALYZING_RESUME: '简历分析', GENERATING_QUESTIONS: '生成面试题', GENERATING_FINAL_REPORT: '汇总最终报告', SUCCESS: '已完成', FAILED: '失败',
  COMPLETE: '完整', PARTIAL: '部分结果', RUNNING: '执行中', IN_PROGRESS: '进行中', FINISHED: '已结束'
  , INTERVIEW_ANSWER_RECEIVED: '已收到回答', INTERVIEW_EVALUATING: '正在分析回答',
  INTERVIEW_SCORING: '正在生成评分与建议', INTERVIEW_SCORE_COMPLETED: '评分完成',
  INTERVIEW_SCORE_FAILED: '本次评分暂不可用',
  INTERVIEW_FOLLOWUP_STREAMING: '正在生成追问或反馈', INTERVIEW_FEEDBACK_COMPLETED: '反馈完成',
  INTERVIEW_NEXT_QUESTION: '进入下一题', INTERVIEW_COMPLETED: '面试完成', INTERVIEW_FAILED: '处理失败',
  TUTOR_MESSAGE_RECEIVED: '问题已接收', TUTOR_RETRIEVING: '正在检索资料',
  TUTOR_GENERATING: '正在组织回答', TUTOR_COMPLETED: '回答完成', TUTOR_FAILED: '生成失败'
}

function App() {
  const [user, setUser] = useState<User | null>(null)
  const [loading, setLoading] = useState(Boolean(session.get()))

  useEffect(() => {
    if (!session.get()) return
    api.me().then(setUser).catch(() => session.clear()).finally(() => setLoading(false))
  }, [])

  if (loading) return <div className="boot"><Sparkles size={24} /><span>SkillPilot</span></div>
  if (!user) return <AuthPage onAuthenticated={setUser} />
  return <Workspace user={user} onLogout={() => { session.clear(); setUser(null) }} />
}

function AuthPage({ onAuthenticated }: { onAuthenticated: (user: User) => void }) {
  const [mode, setMode] = useState<'login' | 'register'>('login')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [nickname, setNickname] = useState('')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  async function submit(event: FormEvent) {
    event.preventDefault(); setBusy(true); setError('')
    try {
      if (mode === 'register') await api.register({ email, password, nickname })
      const result = await api.login(email, password)
      session.set(result.accessToken); onAuthenticated(result.user)
    } catch (reason) { setError(reason instanceof Error ? reason.message : '登录失败') }
    finally { setBusy(false) }
  }

  return <main className="auth-page">
    <section className="auth-brand">
      <div className="brand-mark"><Sparkles size={22} /></div>
      <div><strong>SkillPilot</strong><span>Career Intelligence Workspace</span></div>
    </section>
    <form className="auth-form" onSubmit={submit}>
      <div className="auth-heading"><span className="eyebrow">WORKSPACE ACCESS</span><h1>{mode === 'login' ? '欢迎回来' : '创建工作区'}</h1></div>
      {mode === 'register' && <Field label="昵称"><input value={nickname} onChange={e => setNickname(e.target.value)} required /></Field>}
      <Field label="邮箱"><input type="email" value={email} onChange={e => setEmail(e.target.value)} required /></Field>
      <Field label="密码"><input type="password" value={password} onChange={e => setPassword(e.target.value)} minLength={8} required /></Field>
      {error && <div className="alert"><CircleAlert size={16} />{error}</div>}
      <button className="primary wide" disabled={busy}>{busy ? '正在连接...' : mode === 'login' ? '登录' : '注册并登录'}<ChevronRight size={17} /></button>
      <button type="button" className="text-button" onClick={() => setMode(mode === 'login' ? 'register' : 'login')}>
        {mode === 'login' ? '还没有账号？立即注册' : '已有账号？返回登录'}
      </button>
    </form>
    <div className="auth-meta"><span>岗位匹配</span><span>简历洞察</span><span>面试准备</span></div>
  </main>
}

function Workspace({ user, onLogout }: { user: User; onLogout: () => void }) {
  const [view, setView] = useState<View>('overview')
  const [tasks, setTasks] = useState<CareerTask[]>([])
  const [reports, setReports] = useState<ReportSummary[]>([])
  const [resumes, setResumes] = useState<Resume[]>([])
  const [jobs, setJobs] = useState<Job[]>([])
  const [selectedTask, setSelectedTask] = useState<number | null>(null)
  const [selectedReport, setSelectedReport] = useState<number | null>(null)
  const [menuOpen, setMenuOpen] = useState(false)
  const [error, setError] = useState('')

  const load = useCallback(async () => {
    try {
      const [taskPage, reportItems, resumePage, jobPage] = await Promise.all([api.tasks(), api.reports(), api.resumes(), api.jobs()])
      setTasks(taskPage.items); setReports(reportItems); setResumes(resumePage.items); setJobs(jobPage.items); setError('')
    } catch (reason) { setError(reason instanceof Error ? reason.message : '数据加载失败') }
  }, [])
  useEffect(() => { const timer = window.setTimeout(() => void load(), 0); return () => window.clearTimeout(timer) }, [load])
  useEffect(() => {
    if (!tasks.some(task => !['SUCCESS', 'FAILED'].includes(task.status))) return
    const timer = window.setInterval(() => void load(), 2000)
    return () => window.clearInterval(timer)
  }, [tasks, load])

  const navigate = (next: View) => { setView(next); setSelectedTask(null); setSelectedReport(null); setMenuOpen(false) }
  const title = selectedTask ? '任务详情' : selectedReport ? '报告详情' : ({ overview: '工作台', prepare: '新建分析', tasks: '任务', reports: '报告', interviews: '模拟面试', tutor: 'AI 答疑' } as const)[view]

  return <div className="workspace">
    <aside className={menuOpen ? 'sidebar open' : 'sidebar'}>
      <div className="sidebar-brand"><div className="brand-mark"><Sparkles size={19} /></div><strong>SkillPilot</strong><button className="icon mobile-close" onClick={() => setMenuOpen(false)} aria-label="关闭菜单"><X /></button></div>
      <nav>
        <NavButton active={view === 'overview'} icon={<LayoutDashboard />} label="工作台" onClick={() => navigate('overview')} />
        <NavButton active={view === 'prepare'} icon={<Upload />} label="新建分析" onClick={() => navigate('prepare')} />
        <NavButton active={view === 'tasks'} icon={<ListChecks />} label="任务" badge={tasks.filter(t => !['SUCCESS', 'FAILED'].includes(t.status)).length} onClick={() => navigate('tasks')} />
        <NavButton active={view === 'reports'} icon={<BarChart3 />} label="报告" onClick={() => navigate('reports')} />
        <NavButton active={view === 'interviews'} icon={<MessageSquare />} label="模拟面试" onClick={() => navigate('interviews')} />
        <NavButton active={view === 'tutor'} icon={<Bot />} label="AI 答疑" onClick={() => navigate('tutor')} />
      </nav>
      <div className="account"><div className="avatar">{(user.nickname || user.email).slice(0, 1).toUpperCase()}</div><div><strong>{user.nickname || '用户'}</strong><span>{user.email}</span></div><button className="icon" onClick={onLogout} title="退出登录"><LogOut /></button></div>
    </aside>
    <main className="main">
      <header className="topbar"><button className="icon menu-button" onClick={() => setMenuOpen(true)} aria-label="打开菜单"><Menu /></button><div><span className="eyebrow">CAREER WORKSPACE</span><h1>{title}</h1></div><button className="icon" onClick={() => void load()} title="刷新"><RefreshCw /></button></header>
      {error && <div className="global-alert"><CircleAlert size={17} />{error}</div>}
      <div className="content">
        {selectedTask ? <TaskDetail id={selectedTask} onBack={() => setSelectedTask(null)} onChanged={load} onRetry={setSelectedTask} onReport={reportId => { setView('reports'); setSelectedTask(null); setSelectedReport(reportId) }} /> :
         selectedReport ? <ReportDetailView id={selectedReport} onBack={() => setSelectedReport(null)} /> :
         view === 'overview' ? <Overview tasks={tasks} reports={reports} resumes={resumes} jobs={jobs} onNew={() => navigate('prepare')} onTask={id => { setView('tasks'); setSelectedTask(id) }} onReport={id => { setView('reports'); setSelectedReport(id) }} /> :
         view === 'prepare' ? <Prepare resumes={resumes} jobs={jobs} onCreated={async id => { await load(); setView('tasks'); setSelectedTask(id) }} onResourcesChanged={load} /> :
         view === 'tasks' ? <TaskList tasks={tasks} onSelect={setSelectedTask} /> :
         view === 'reports' ? <ReportList reports={reports} onSelect={setSelectedReport} /> :
         view === 'interviews' ? <InterviewWorkspace resumes={resumes} jobs={jobs} /> :
         <TutorWorkspace resumes={resumes} jobs={jobs} />}
      </div>
    </main>
    {menuOpen && <button className="backdrop" onClick={() => setMenuOpen(false)} aria-label="关闭菜单" />}
  </div>
}

function TutorWorkspace({ resumes, jobs }: { resumes: Resume[]; jobs: Job[] }) {
  const [sessions, setSessions] = useState<TutorSessionSummary[]>([])
  const [active, setActive] = useState<TutorSession | null>(null)
  const [resumeId, setResumeId] = useState(0); const [jobId, setJobId] = useState(0)
  const [question, setQuestion] = useState(''); const [streamText, setStreamText] = useState('')
  const [state, setState] = useState(''); const [busy, setBusy] = useState(false); const [error, setError] = useState('')
  const transcriptRef = useRef<HTMLDivElement>(null)

  const loadSessions = useCallback(async () => {
    try { setSessions(await api.tutorSessions()) }
    catch (reason) { setError(reason instanceof Error ? reason.message : '答疑会话加载失败') }
  }, [])
  useEffect(() => { const timer = window.setTimeout(() => void loadSessions(), 0); return () => window.clearTimeout(timer) }, [loadSessions])
  useEffect(() => { transcriptRef.current?.scrollTo({ top: transcriptRef.current.scrollHeight, behavior: 'smooth' }) }, [active?.messages, streamText])

  async function createSession() {
    setBusy(true); setError('')
    try {
      const next = await api.createTutorSession({
        title: 'AI 学习答疑', resumeId: resumeId || undefined, jobId: jobId || undefined
      }); setActive(next); await loadSessions()
    } catch (reason) { setError(reason instanceof Error ? reason.message : '答疑会话创建失败') }
    finally { setBusy(false) }
  }
  async function openSession(id: number) {
    setBusy(true); setError('')
    try { setActive(await api.tutorSession(id)) }
    catch (reason) { setError(reason instanceof Error ? reason.message : '答疑会话加载失败') }
    finally { setBusy(false) }
  }
  async function removeSession(id: number) {
    if (!window.confirm('确认删除这条答疑会话？')) return
    setBusy(true); setError('')
    try { await api.deleteTutorSession(id); if (active?.sessionId === id) setActive(null); await loadSessions() }
    catch (reason) { setError(reason instanceof Error ? reason.message : '答疑会话删除失败') }
    finally { setBusy(false) }
  }
  async function submit(event: FormEvent) {
    event.preventDefault(); if (busy || !active || !question.trim()) return
    const content = question.trim(); setQuestion(''); setBusy(true); setError(''); setStreamText(''); setState('TUTOR_MESSAGE_RECEIVED')
    setActive(current => current ? { ...current, messages: [...current.messages, {
      messageId: -Date.now(), role: 'USER', content, citations: [], sequenceNo: current.messages.length + 1,
      createdAt: new Date().toISOString()
    }] } : current)
    try {
      await api.streamTutorMessage(active.sessionId, content, (eventName, data) => {
        setState(eventName)
        if (eventName === 'TUTOR_DELTA') setStreamText(value => value + String(data.delta || ''))
        const next = data.session as TutorSession | undefined
        if (next) { setActive(next); setStreamText('') }
      })
      setActive(await api.tutorSession(active.sessionId)); await loadSessions()
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : '答疑生成失败')
      try { setActive(await api.tutorSession(active.sessionId)) } catch { /* Preserve stream error. */ }
    } finally { setBusy(false); setState('') }
  }

  return <div className="tutor-layout">
    <aside className="tutor-panel"><div className="tutor-create"><SectionTitle title="新答疑" />
      <Field label="关联简历（可选）"><select value={resumeId} onChange={event => setResumeId(Number(event.target.value))}><option value={0}>不关联</option>{resumes.map(item => <option key={item.resumeId} value={item.resumeId}>{item.title}</option>)}</select></Field>
      <Field label="关联岗位（可选）"><select value={jobId} onChange={event => setJobId(Number(event.target.value))}><option value={0}>不关联</option>{jobs.map(item => <option key={item.jobId} value={item.jobId}>{item.company ? `${item.company} · ` : ''}{item.position}</option>)}</select></Field>
      <button className="primary wide" disabled={busy} onClick={() => void createSession()}><Bot size={16} />开始答疑</button>
    </div><div className="tutor-session-list"><SectionTitle title="历史答疑" />{sessions.map(item => <div className={active?.sessionId === item.sessionId ? 'tutor-session active' : 'tutor-session'} key={item.sessionId}><button onClick={() => void openSession(item.sessionId)}><strong>{item.title}</strong><span>{item.processing ? '回答生成中' : formatDate(item.updatedAt)}</span></button><button className="icon" title="删除" onClick={() => void removeSession(item.sessionId)}><Trash2 size={14} /></button></div>)}{!sessions.length && <Empty icon={<Bot />} text="暂无答疑会话" />}</div></aside>
    <section className="tutor-room">{active ? <><header className="tutor-head"><div><span className="eyebrow">GROUNDED TUTOR</span><h2>{active.title}</h2></div><div className="tutor-context"><span>长期记忆 · 保留最近 {Math.min(12, active.messages.length)} 条原文</span>{active.resumeId && <span>简历：{resumes.find(item => item.resumeId === active.resumeId)?.title || `#${active.resumeId}`}</span>}{active.jobId && <span>岗位：{(() => { const job = jobs.find(item => item.jobId === active.jobId); return job ? `${job.company ? `${job.company} · ` : ''}${job.position}` : `#${active.jobId}` })()}</span>}</div></header>
      <div className="tutor-transcript" ref={transcriptRef}>{active.messages.map(message => <div className={`tutor-message ${message.role.toLowerCase()}`} key={message.messageId}><div className="message-role">{message.role === 'ASSISTANT' ? 'AI 导师' : '我'}</div><p>{message.content}</p>{message.citations.length > 0 && <div className="tutor-citations">{message.citations.map(citation => <article key={citation.citationId}><strong>{citation.title}</strong><span>{citation.sourceType}</span><p>{citation.snippet}</p><code>{citation.citationId}</code></article>)}</div>}<time>{formatDate(message.createdAt)}</time></div>)}
      {streamText && <div className="tutor-message assistant streaming"><div className="message-role">AI 导师</div><p>{streamText}</p></div>}{busy && <div className="thinking"><span /><span /><span /><small>{statusLabel[state] || state}</small></div>}</div>
      <form className="answer-box" onSubmit={submit}><textarea value={question} onChange={event => setQuestion(event.target.value)} placeholder={busy ? '可以先输入下一条，当前回答完成后即可发送' : '可以追问、改写上一轮，或切换到新话题'} maxLength={10000} /><button className="primary" disabled={busy || !question.trim()} title={busy ? '当前回答完成后即可发送' : '发送问题'}><Send size={17} /></button></form>
    </> : <Empty icon={<Bot />} text="创建一场答疑，直接询问你不理解的概念" />}{error && <div className="alert interview-error"><CircleAlert size={16} />{error}</div>}</section>
  </div>
}

function InterviewWorkspace({ resumes, jobs }: { resumes: Resume[]; jobs: Job[] }) {
  const [sessions, setSessions] = useState<InterviewSessionSummary[]>([])
  const [active, setActive] = useState<InterviewSession | null>(null)
  const [resumeId, setResumeId] = useState(resumes[0]?.resumeId || 0)
  const [jobId, setJobId] = useState(jobs[0]?.jobId || 0)
  const [answer, setAnswer] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [interviewState, setInterviewState] = useState('')
  const [streamText, setStreamText] = useState('')
  const [review, setReview] = useState<InterviewReview | null>(null)
  const [reviewBusy, setReviewBusy] = useState(false)
  const [memory, setMemory] = useState<InterviewMemory | null>(null)
  const transcriptRef = useRef<HTMLDivElement>(null)

  const loadSessions = useCallback(async () => {
    try { setSessions(await api.interviewSessions()) }
    catch (reason) { setError(reason instanceof Error ? reason.message : '会话加载失败') }
  }, [])

  useEffect(() => { const timer = window.setTimeout(() => void loadSessions(), 0); return () => window.clearTimeout(timer) }, [loadSessions])
  useEffect(() => { transcriptRef.current?.scrollTo({ top: transcriptRef.current.scrollHeight, behavior: 'smooth' }) }, [active?.messages, streamText])

  async function openSession(id: number) {
    setBusy(true); setError(''); setReview(null); setMemory(null)
    try {
      const next = await api.interviewSession(id); setActive(next)
      setMemory(await api.interviewMemory(next.resumeId, next.jobId))
      if (next.status === 'FINISHED') {
        const state = await api.interviewReview(id); setReview(state.review || null)
      }
    }
    catch (reason) { setError(reason instanceof Error ? reason.message : '会话加载失败') }
    finally { setBusy(false) }
  }

  async function createSession() {
    if (!resumeId || !jobId) return
    setBusy(true); setError('')
    try { const next = await api.createInterviewSession(resumeId, jobId); setActive(next); setReview(null); setMemory(await api.interviewMemory(resumeId, jobId)); await loadSessions() }
    catch (reason) { setError(reason instanceof Error ? reason.message : '面试创建失败') }
    finally { setBusy(false) }
  }

  async function submitAnswer(event: FormEvent) {
    event.preventDefault()
    if (!active || !answer.trim()) return
    setBusy(true); setError('')
    try {
      const submitted = answer.trim(); setAnswer(''); setStreamText(''); setInterviewState('INTERVIEW_ANSWER_RECEIVED')
      const currentQuestionId = [...active.messages].reverse().find(message => message.role === 'INTERVIEWER')?.questionId
      setActive(current => current ? {
        ...current,
        messages: [...current.messages, {
          messageId: -Date.now(), questionId: currentQuestionId, role: 'CANDIDATE', content: submitted,
          sequenceNo: current.messages.length + 1, createdAt: new Date().toISOString()
        }]
      } : current)
      await api.streamInterviewAnswer(active.sessionId, submitted, (event, data) => {
        setInterviewState(event)
        if (event === 'INTERVIEW_FOLLOWUP_STREAMING') setStreamText(value => value + String(data.delta || ''))
        const next = data.session as InterviewSession | undefined
        if (next) { setActive(next); setStreamText('') }
      })
      const completed = await api.interviewSession(active.sessionId)
      setActive(completed)
      setMemory(await api.interviewMemory(completed.resumeId, completed.jobId))
      if (completed.status === 'FINISHED') await generateReview(completed.sessionId)
      await loadSessions()
    }
    catch (reason) {
      setError(reason instanceof Error ? reason.message : '回答发送失败')
      try { setActive(await api.interviewSession(active.sessionId)) } catch { /* Keep the actionable stream error. */ }
    }
    finally { setBusy(false); setInterviewState('') }
  }

  async function finish() {
    if (!active) return
    setBusy(true); setError('')
    try {
      const finished = await api.finishInterview(active.sessionId); setActive(finished)
      if (finished.evaluations.length) await generateReview(finished.sessionId)
      await loadSessions()
    }
    catch (reason) { setError(reason instanceof Error ? reason.message : '结束面试失败') }
    finally { setBusy(false) }
  }

  async function generateReview(sessionId: number) {
    setReviewBusy(true)
    try { setReview(await api.generateInterviewReview(sessionId)) }
    catch (reason) { setError(reason instanceof Error ? reason.message : '复盘报告生成失败') }
    finally { setReviewBusy(false) }
  }

  async function clearMemory() {
    if (!active) return
    setBusy(true); setError('')
    try { await api.clearInterviewMemory(active.resumeId, active.jobId); setMemory(await api.interviewMemory(active.resumeId, active.jobId)) }
    catch (reason) { setError(reason instanceof Error ? reason.message : '记忆清除失败') }
    finally { setBusy(false) }
  }

  const resumeName = (id: number) => resumes.find(item => item.resumeId === id)?.title || `简历 #${id}`
  const jobName = (id: number) => jobs.find(item => item.jobId === id)?.position || `岗位 #${id}`

  return <div className="interview-layout">
    <aside className="interview-panel">
      <div className="interview-create">
        <SectionTitle title="新面试" />
        <Field label="简历"><select value={resumeId} onChange={event => setResumeId(Number(event.target.value))}><option value={0}>选择简历</option>{resumes.map(item => <option key={item.resumeId} value={item.resumeId}>{item.title}</option>)}</select></Field>
        <Field label="岗位"><select value={jobId} onChange={event => setJobId(Number(event.target.value))}><option value={0}>选择岗位</option>{jobs.map(item => <option key={item.jobId} value={item.jobId}>{item.company ? `${item.company} · ` : ''}{item.position}</option>)}</select></Field>
        <button className="primary wide" onClick={() => void createSession()} disabled={!resumeId || !jobId || busy}><MessageSquare size={16} />开始面试</button>
      </div>
      <div className="session-list">
        <SectionTitle title="历史会话" />
        {sessions.map(item => <button key={item.sessionId} className={active?.sessionId === item.sessionId ? 'session-row active' : 'session-row'} onClick={() => void openSession(item.sessionId)}>
          <div><strong>{jobName(item.jobId)}</strong><span>{resumeName(item.resumeId)}</span></div>
          <Status status={item.status} />
          <small>{item.currentQuestion}/{item.totalQuestions} · {formatDate(item.updatedAt)}</small>
        </button>)}
        {!sessions.length && <Empty icon={<MessageSquare />} text="暂无面试会话" />}
      </div>
    </aside>
    <section className="interview-room">
      {active ? <>
        <header className="interview-room-head">
          <div><Status status={active.status} /><h2>{jobName(active.jobId)}</h2><span>{resumeName(active.resumeId)}</span></div>
          <div className="interview-progress"><strong>{active.currentQuestion}/{active.totalQuestions}</strong><span>当前题目</span></div>
          {active.status === 'IN_PROGRESS' && <button className="secondary" onClick={() => void finish()} disabled={busy}><Square size={14} />结束</button>}
        </header>
        {memory?.available && <div className="memory-card"><div><strong>长期面试记忆</strong><span>{memory.sessionCount} 场 · {memory.answerCount} 次回答 · 平均 {memory.averageScore}</span><p>{memory.summary}</p></div><button className="secondary small" disabled={busy} onClick={() => void clearMemory()}>清除记忆</button></div>}
        <div className="transcript" ref={transcriptRef}>
          {active.messages.map(message => {
            const evaluation = active.evaluations.find(item => item.answerMessageId === message.messageId)
            return <div key={message.messageId} className="interview-turn">
              <div className={`message ${message.role.toLowerCase()}`}>
                <div className="message-role">{message.role === 'INTERVIEWER' ? 'AI 面试官' : '我'}</div>
                <p>{message.content}</p>
                <time>{formatDate(message.createdAt)}</time>
              </div>
              {evaluation && <AnswerScore evaluation={evaluation} />}
            </div>
          })}
          {streamText && <div className="message interviewer"><div className="message-role">AI 面试官</div><p>{streamText}</p></div>}
          {busy && <div className="thinking"><span /><span /><span />{interviewState && <small>{statusLabel[interviewState] || interviewState}</small>}</div>}
          {active.status === 'FINISHED' && (review
            ? <InterviewReviewCard review={review} />
            : <div className="review-empty"><strong>本轮面试已结束</strong><span>{reviewBusy ? '正在生成复盘报告…' : '生成会话级总结、能力缺口与改进计划'}</span><button className="secondary" disabled={reviewBusy || !active.evaluations.length} onClick={() => void generateReview(active.sessionId)}>{reviewBusy ? '生成中' : '生成复盘报告'}</button></div>)}
        </div>
        <form className="answer-box" onSubmit={submitAnswer}>
          <textarea value={answer} onChange={event => setAnswer(event.target.value)} placeholder={active.status === 'FINISHED' ? '本轮面试已结束' : '输入你的回答'} disabled={active.status === 'FINISHED' || busy} maxLength={10000} />
          <button className="primary" disabled={active.status === 'FINISHED' || !answer.trim() || busy} title="发送回答"><Send size={17} /></button>
        </form>
      </> : <Empty icon={<MessageSquare />} text="选择或创建一场模拟面试" />}
      {error && <div className="alert interview-error"><CircleAlert size={16} />{error}</div>}
    </section>
  </div>
}

function InterviewReviewCard({ review }: { review: InterviewReview }) {
  return <section className="interview-review">
    <header><div><span>面试复盘</span><strong>{review.overallScore}</strong><small>/ 100</small></div><p>{review.result.summary}</p></header>
    <div className="review-dimensions">{review.result.dimensions.map(item => <div key={item.key}><strong>{item.label}</strong><b>{item.score}</b><p>{item.assessment}</p></div>)}</div>
    <div className="review-columns">
      <div><strong>表现优势</strong><ul>{review.result.strengths.map(item => <li key={item}>{item}</li>)}</ul></div>
      <div><strong>主要缺口</strong><ul>{review.result.gaps.map(item => <li key={item}>{item}</li>)}</ul></div>
    </div>
    <div className="review-actions"><strong>下一步行动</strong>{[...review.result.actionPlan].sort((a, b) => a.priority - b.priority).map(item => <div key={`${item.priority}-${item.action}`}><b>{item.priority}</b><p><strong>{item.action}</strong><span>{item.reason}</span></p></div>)}</div>
    <div className="review-practice"><strong>建议练习题</strong><ol>{review.result.recommendedPracticeQuestions.map(item => <li key={item}>{item}</li>)}</ol></div>
    {review.generationSource === 'FALLBACK' && <small className="review-source">模型暂不可用，本报告由已校验的逐题评分自动汇总。</small>}
  </section>
}

function AnswerScore({ evaluation }: { evaluation: import('./api').InterviewEvaluation }) {
  const result = evaluation.result
  return <details className="answer-score">
    <summary><span>回答评分</span><strong>{evaluation.overallScore}</strong><small>/ 100</small></summary>
    <div className="score-body">
      <div className="score-dimensions">{result.dimensions.map(item => <div key={item.key}>
        <header><strong>{item.label} · {item.weight}%</strong><span>{item.score}</span></header><p>{item.rationale}</p>
      </div>)}</div>
      {!!result.strengths.length && <div className="score-notes positive"><strong>做得不错</strong><ul>{result.strengths.map(item => <li key={item}>{item}</li>)}</ul></div>}
      <div className="score-notes"><strong>可以改进</strong><ul>{result.improvements.map(item => <li key={item}>{item}</li>)}</ul></div>
      <div className="improved-answer"><strong>参考表达</strong><p>{result.improvedAnswer}</p></div>
    </div>
  </details>
}

function Overview({ tasks, reports, resumes, jobs, onNew, onTask, onReport }: { tasks: CareerTask[]; reports: ReportSummary[]; resumes: Resume[]; jobs: Job[]; onNew: () => void; onTask: (id: number) => void; onReport: (id: number) => void }) {
  const running = tasks.filter(t => !['SUCCESS', 'FAILED'].includes(t.status))
  return <>
    <section className="summary-band">
      <div><span className="eyebrow">TODAY</span><h2>职业准备进度</h2></div>
      <button className="primary" onClick={onNew}><Sparkles size={17} />开始新分析</button>
    </section>
    <section className="metric-grid">
      <Metric icon={<FileText />} label="简历" value={resumes.length} tone="green" />
      <Metric icon={<BriefcaseBusiness />} label="岗位" value={jobs.length} tone="coral" />
      <Metric icon={<Activity />} label="进行中" value={running.length} tone="amber" />
      <Metric icon={<BarChart3 />} label="报告" value={reports.length} tone="blue" />
    </section>
    <section className="two-column">
      <div className="plain-section"><SectionTitle title="最近任务" action="查看全部" />
        <div className="list-stack">{tasks.slice(0, 5).map(task => <TaskRow key={task.taskId} task={task} onClick={() => onTask(task.taskId)} />)}{!tasks.length && <Empty icon={<ListChecks />} text="暂无分析任务" />}</div>
      </div>
      <div className="plain-section"><SectionTitle title="最新报告" />
        <div className="list-stack">{reports.slice(0, 4).map(report => <ReportRow key={report.reportId} report={report} onClick={() => onReport(report.reportId)} />)}{!reports.length && <Empty icon={<BarChart3 />} text="报告会在任务完成后生成" />}</div>
      </div>
    </section>
  </>
}

function Prepare({ resumes, jobs, onCreated, onResourcesChanged }: { resumes: Resume[]; jobs: Job[]; onCreated: (id: number) => void; onResourcesChanged: () => Promise<void> }) {
  const [resumeId, setResumeId] = useState(resumes[0]?.resumeId || 0)
  const [jobId, setJobId] = useState(jobs[0]?.jobId || 0)
  const [resumeFile, setResumeFile] = useState<File | null>(null)
  const [jdFile, setJdFile] = useState<File | null>(null)
  const [resumeTitle, setResumeTitle] = useState('')
  const [company, setCompany] = useState('')
  const [position, setPosition] = useState('')
  const [busy, setBusy] = useState('')
  const [error, setError] = useState('')

  async function importResume() {
    if (!resumeFile || !resumeTitle.trim()) return
    setBusy('resume'); setError('')
    try {
      const upload = await api.upload(resumeFile, 'RESUME'); const processed = await api.processFile(upload.fileId)
      const resume = await api.createResume(processed.documentId, resumeTitle); setResumeId(resume.resumeId); await onResourcesChanged()
    } catch (reason) { setError(reason instanceof Error ? reason.message : '简历导入失败') } finally { setBusy('') }
  }
  async function importJob() {
    if (!jdFile || !position.trim()) return
    setBusy('job'); setError('')
    try {
      const upload = await api.upload(jdFile, 'JD'); const processed = await api.processFile(upload.fileId)
      const job = await api.createJob(processed.documentId, company, position); setJobId(job.jobId); await onResourcesChanged()
    } catch (reason) { setError(reason instanceof Error ? reason.message : '岗位导入失败') } finally { setBusy('') }
  }
  async function start() {
    setBusy('task'); setError('')
    try { const task = await api.createTask(resumeId, jobId); onCreated(task.taskId) }
    catch (reason) { setError(reason instanceof Error ? reason.message : '任务创建失败') } finally { setBusy('') }
  }

  return <div className="prepare-layout">
    <section className="plain-section"><div className="step-title"><span>01</span><div><h2>选择简历</h2><p>{resumes.length} 份可用</p></div></div>
      <select value={resumeId} onChange={e => setResumeId(Number(e.target.value))}><option value={0}>选择已有简历</option>{resumes.map(r => <option key={r.resumeId} value={r.resumeId}>{r.title}</option>)}</select>
      <div className="separator"><span>或导入新简历</span></div>
      <Field label="简历名称"><input value={resumeTitle} onChange={e => setResumeTitle(e.target.value)} placeholder="例如：后端工程师简历" /></Field>
      <FilePicker file={resumeFile} onChange={setResumeFile} accept=".pdf,.doc,.docx,.txt,.md" />
      <button className="secondary" onClick={() => void importResume()} disabled={!resumeFile || !resumeTitle || Boolean(busy)}>{busy === 'resume' ? '处理中...' : '导入简历'}<Upload size={16} /></button>
    </section>
    <section className="plain-section"><div className="step-title"><span>02</span><div><h2>选择岗位</h2><p>{jobs.length} 个可用</p></div></div>
      <select value={jobId} onChange={e => setJobId(Number(e.target.value))}><option value={0}>选择已有岗位</option>{jobs.map(j => <option key={j.jobId} value={j.jobId}>{j.company ? `${j.company} · ` : ''}{j.position}</option>)}</select>
      <div className="separator"><span>或导入新 JD</span></div>
      <div className="form-row"><Field label="公司"><input value={company} onChange={e => setCompany(e.target.value)} /></Field><Field label="职位"><input value={position} onChange={e => setPosition(e.target.value)} /></Field></div>
      <FilePicker file={jdFile} onChange={setJdFile} accept=".pdf,.doc,.docx,.txt,.md" />
      <button className="secondary" onClick={() => void importJob()} disabled={!jdFile || !position || Boolean(busy)}>{busy === 'job' ? '处理中...' : '导入岗位'}<Upload size={16} /></button>
    </section>
    <section className="launch-band"><div><span className="eyebrow">03 · FULL WORKFLOW</span><h2>生成职业准备报告</h2><p>岗位匹配、简历分析、个性化面试题</p></div><button className="primary" onClick={() => void start()} disabled={!resumeId || !jobId || Boolean(busy)}>{busy === 'task' ? '正在创建...' : '开始分析'}<ChevronRight size={17} /></button></section>
    {error && <div className="alert span-full"><CircleAlert size={16} />{error}</div>}
  </div>
}

function TaskList({ tasks, onSelect }: { tasks: CareerTask[]; onSelect: (id: number) => void }) {
  return <section className="plain-section"><SectionTitle title={`${tasks.length} 个任务`} />
    <div className="table-list">{tasks.map(task => <TaskRow key={task.taskId} task={task} onClick={() => onSelect(task.taskId)} />)}{!tasks.length && <Empty icon={<ListChecks />} text="暂无任务" />}</div>
  </section>
}

function TaskDetail({ id, onBack, onChanged, onRetry, onReport }: { id: number; onBack: () => void; onChanged: () => Promise<void>; onRetry: (id: number) => void; onReport: (id: number) => void }) {
  const [task, setTask] = useState<CareerTask | null>(null); const [steps, setSteps] = useState<UserTaskStep[]>([]); const [technicalDetails, setTechnicalDetails] = useState<TechnicalDetail[]>([]); const [reportId, setReportId] = useState<number | null>(null); const [error, setError] = useState('')
  const refreshReport = useCallback(async () => { const reports = await api.reports(); setReportId(reports.find(report => report.taskId === id)?.reportId || null) }, [id])
  const load = useCallback(async () => {
    try {
      const next = await api.task(id)
      setTask(next)
      setError('')
      if (next.status === 'SUCCESS') void refreshReport()
      try {
        const progress = await api.taskProgress(id)
        setSteps(progress.steps || [])
        setTechnicalDetails(progress.technicalDetails || [])
      } catch (reason) {
        setError(reason instanceof Error ? `执行过程加载失败：${reason.message}` : '执行过程加载失败')
      }
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : '加载失败')
    }
  }, [id, refreshReport])
  useEffect(() => {
    const controller = new AbortController(); let disposed = false; let terminal = false; let lastEventId: string | undefined; let retryTimer = 0; let retryDelay = 500
    const upsertStep = (item: UserTaskStep) => setSteps(current => current.some(step => step.step === item.step)
      ? current.map(step => step.step === item.step ? item : step) : [...current, item])
    const connect = async () => {
      try {
        await api.streamTaskEvents(id, lastEventId, (event, data, eventId) => {
          if (eventId) lastEventId = eventId
          if (event === 'TASK_SNAPSHOT') {
            const snapshot = data as unknown as TaskEventSnapshot
            setTask(snapshot.task); setSteps(snapshot.steps || []); setTechnicalDetails(snapshot.technicalDetails || [])
            terminal = ['SUCCESS', 'FAILED'].includes(snapshot.task.status)
            if (snapshot.task.status === 'SUCCESS') void refreshReport()
          } else if (event === 'TASK_UPDATED' || event === 'TASK_STREAM_COMPLETED') {
            const next = data as unknown as CareerTask; setTask(next)
            terminal = ['SUCCESS', 'FAILED'].includes(next.status)
            if (next.status === 'SUCCESS') void refreshReport()
          } else if (event === 'USER_STEP_EVENT') upsertStep(data as unknown as UserTaskStep)
          else if (event === 'TECHNICAL_DETAILS_UPDATED') setTechnicalDetails(data as unknown as TechnicalDetail[])
          setError(''); retryDelay = 500
        }, controller.signal)
      } catch (reason) {
        if (!disposed && !(reason instanceof DOMException && reason.name === 'AbortError')) {
          setError('实时连接中断，正在恢复…'); await load()
        }
      }
      if (!disposed && !terminal) {
        retryTimer = window.setTimeout(() => void connect(), retryDelay)
        retryDelay = Math.min(retryDelay * 2, 8000)
      }
    }
    retryTimer = window.setTimeout(() => { void load(); void connect() }, 0)
    return () => { disposed = true; controller.abort(); window.clearTimeout(retryTimer) }
  }, [id, load, refreshReport])
  if (!task) return error ? <div className="alert"><CircleAlert size={16} />{error}</div> : <div className="loading-line">正在加载任务...</div>
  return <><button className="back-link" onClick={onBack}><ArrowLeft size={16} />返回任务列表</button>
    <section className="task-head"><div><Status status={task.status} /><h2>{task.status === 'SUCCESS' ? '分析已完成' : task.status === 'FAILED' ? '分析未完成' : 'Agent 正在分析'}</h2><p>任务 #{task.taskId} · 简历与岗位智能分析</p></div><div className="progress-ring" style={{ '--progress': `${task.progress * 3.6}deg` } as CSSProperties}><span>{task.progress}%</span></div></section>
    {task.status === 'FAILED' && <div className="alert"><CircleAlert size={16} />任务在“{steps.find(step => step.status === 'FAILED')?.title || '当前步骤'}”未能完成<button className="secondary small" onClick={async () => { const next = await api.retryTask(id); await onChanged(); onRetry(next.taskId) }}>重试该任务</button></div>}
    <section className="plain-section agent-process"><SectionTitle title="Agent 执行过程" /><div className="step-flow">{steps.map((step, index) => <AgentStep key={step.step} step={step} last={index === steps.length - 1} />)}{!steps.length && <Empty icon={<Activity />} text="正在准备本次分析" />}</div>
      {task.status === 'SUCCESS' && <div className="report-ready"><div><Check size={18} /><span><strong>报告已生成</strong><small>岗位匹配、简历建议和面试题已整理完成</small></span></div><button className="primary" disabled={!reportId} onClick={() => reportId && onReport(reportId)}><FileText size={16} />{reportId ? '查看报告' : '正在准备报告…'}</button></div>}
    </section>
    <details className="technical-details"><summary>技术详情 <span>已脱敏，默认隐藏</span></summary><div>{technicalDetails.map((detail, index) => <div className="technical-row" key={`${detail.category}-${detail.occurredAt}-${index}`}><span>{detail.category}</span><strong>{detail.label}</strong><small>{friendlyTechnicalStatus(detail.status)}{detail.durationMs != null ? ` · ${formatDuration(detail.durationMs)}` : ''}</small><p>{detail.safeSummary}</p></div>)}{!technicalDetails.length && <p className="muted-copy">暂无技术事件</p>}</div></details>
    {error && <div className="alert"><CircleAlert size={16} />{error}</div>}
  </>
}

function AgentStep({ step, last }: { step: UserTaskStep; last: boolean }) {
  const running = step.status === 'RUNNING'; const failed = step.status === 'FAILED'
  return <article className={`agent-step ${step.status.toLowerCase()}`}><div className="step-rail"><span className="step-icon">{running ? <Clock3 /> : failed ? <X /> : <Check />}</span>{!last && <i />}</div><div className="step-content"><header><div><strong>{running ? `正在${step.title}` : step.title}</strong><p>{step.summary}</p></div><span className="step-status">{running ? '进行中' : failed ? '失败' : step.durationMs != null ? formatDuration(step.durationMs) : '已完成'}</span></header>{running && <ul className="step-progress">{step.progress.slice(0, 3).map(item => <li key={item}>{item}</li>)}</ul>}{step.details.length > 0 && <details className="step-details" open={running || undefined}><summary>查看详情</summary><div>{step.details.map((detail, index) => <div key={`${detail.label}-${index}`}><span>{detail.label}</span><small>{detail.source}{detail.durationMs != null ? ` · ${formatDuration(detail.durationMs)}` : ''}</small></div>)}</div></details>}</div></article>
}

function formatDuration(value: number) { return value < 1000 ? `${value} 毫秒` : value < 60000 ? `${(value / 1000).toFixed(value < 10000 ? 1 : 0)} 秒` : `${Math.floor(value / 60000)} 分 ${Math.round(value % 60000 / 1000)} 秒` }
function friendlyTechnicalStatus(status: string) { return status.includes('FAILED') ? '失败' : status.includes('STARTED') ? '进行中' : '已完成' }

function ReportList({ reports, onSelect }: { reports: ReportSummary[]; onSelect: (id: number) => void }) {
  return <section className="plain-section"><SectionTitle title={`${reports.length} 份报告`} /><div className="report-grid">{reports.map(report => <ReportRow key={report.reportId} report={report} onClick={() => onSelect(report.reportId)} />)}{!reports.length && <Empty icon={<BarChart3 />} text="暂无报告" />}</div></section>
}

function ReportDetailView({ id, onBack }: { id: number; onBack: () => void }) {
  const [detail, setDetail] = useState<ReportDetail | null>(null); const [plan, setPlan] = useState<LearningPlan | null>(null)
  const [error, setError] = useState(''); const [planBusy, setPlanBusy] = useState(false); const [pdfBusy, setPdfBusy] = useState(false)
  const [showPlanForm, setShowPlanForm] = useState(false); const [planMode, setPlanMode] = useState<'AUTO' | 'SPRINT' | 'LONG_TERM'>('AUTO')
  const [interviewDate, setInterviewDate] = useState(''); const [hoursPerDay, setHoursPerDay] = useState(2); const [durationWeeks, setDurationWeeks] = useState(8)
  const [focusAreas, setFocusAreas] = useState('')
  useEffect(() => { api.report(id).then(value => { setDetail(value); if (value.taskId) api.learningPlan(value.taskId).then(setPlan).catch(() => undefined) }).catch(reason => setError(reason instanceof Error ? reason.message : '报告加载失败')) }, [id])
  async function generatePlan() { if (!detail?.taskId) return; setPlanBusy(true); setError(''); try { const report = detail.report as AggregatedReport; setPlan(await api.generateLearningPlan({ taskId: detail.taskId, planMode, interviewDate: interviewDate || undefined, availableHoursPerDay: hoursPerDay, durationWeeks, targetCompany: report.job?.company, targetPosition: report.job?.position, focusAreas: focusAreas.split(/[，,]/).map(item => item.trim()).filter(Boolean) })); setShowPlanForm(false) } catch (reason) { setError(reason instanceof Error ? reason.message : '学习计划生成失败') } finally { setPlanBusy(false) } }
  async function exportPdf() { if (!detail) return; setPdfBusy(true); setError(''); try { const exported = await api.exportReportPdf(detail.reportId); await api.downloadReportPdf(detail.reportId, exported.fileName); setDetail({ ...detail, exportStatus: 'EXPORTED' }) } catch (reason) { setError(reason instanceof Error ? reason.message : 'PDF 导出失败') } finally { setPdfBusy(false) } }
  if (!detail) return <>{error ? <div className="alert"><CircleAlert size={16} />{error}</div> : <div className="loading-line">正在加载报告...</div>}</>
  const report = detail.report as AggregatedReport; const match = report.jobMatch; const analysis = report.resumeAnalysis; const questions = report.interviewQuestions
  return <><button className="back-link" onClick={onBack}><ArrowLeft size={16} />返回报告列表</button>
    <section className="report-head"><div><Status status={report.status} /><h2>{report.job?.position}</h2><p>{report.job?.company || '未填写公司'} · {report.resume?.title}</p></div><div className="report-actions"><div className="report-version">V{detail.version}<span>{formatDate(detail.createdAt)}</span></div><button className="secondary" onClick={() => setShowPlanForm(value => !value)} disabled={planBusy}><BookOpen size={15} />{planBusy ? '生成中...' : plan ? '调整学习计划' : '生成学习计划'}</button><button className="primary" onClick={exportPdf} disabled={pdfBusy}><Download size={15} />{pdfBusy ? '导出中...' : '导出 PDF'}</button></div></section>
    {error && <div className="alert"><CircleAlert size={16} />{error}</div>}
    {showPlanForm && <section className="plan-config"><div><h3>学习计划设置</h3><p>AUTO 会根据面试日期自动选择短期冲刺或长期成长。</p></div><div className="plan-config-grid"><Field label="计划模式"><select value={planMode} onChange={event => setPlanMode(event.target.value as typeof planMode)}><option value="AUTO">自动判断</option><option value="SPRINT">短期冲刺</option><option value="LONG_TERM">长期成长</option></select></Field><Field label="面试日期"><input type="date" value={interviewDate} min={new Date().toISOString().slice(0, 10)} onChange={event => setInterviewDate(event.target.value)} /></Field><Field label="每天投入"><input type="number" min={1} max={12} value={hoursPerDay} onChange={event => setHoursPerDay(Number(event.target.value))} /></Field><Field label="长期周数"><input type="number" min={2} max={24} value={durationWeeks} onChange={event => setDurationWeeks(Number(event.target.value))} /></Field><Field label="重点方向"><input value={focusAreas} onChange={event => setFocusAreas(event.target.value)} placeholder="事务、消息队列、系统设计" /></Field></div><button className="primary" disabled={planBusy || (planMode === 'SPRINT' && !interviewDate)} onClick={() => void generatePlan()}>{planBusy ? '正在生成…' : '生成计划'}</button></section>}
    <section className="score-band"><div className="score"><strong>{match?.data?.matchScore ?? '--'}</strong><span>岗位匹配分</span></div><div><h3>{match?.data?.summary || match?.reason}</h3><div className="tag-list">{(match?.data?.strengths || []).map((item: string) => <span key={item}>{item}</span>)}</div></div></section>
    <section className="report-columns"><ReportSection title="简历亮点" items={analysis?.data?.highlights} missing={analysis?.reason} tone="positive" /><ReportSection title="优先改进" items={[...(analysis?.data?.suggestions || []), ...(match?.data?.suggestedResumeChanges || [])]} missing={analysis?.reason} tone="attention" /></section>
    <section className="plain-section"><SectionTitle title="面试题" /><div className="question-list">{(questions?.items || []).map((item, index) => <InterviewQuestionCard key={item.questionId} item={item} index={index} />)}{questions?.status === 'MISSING' && <Empty icon={<CircleAlert />} text={questions.reason || '面试题暂不可用'} />}</div></section>
    {plan && <LearningPlanPanel value={plan} />}
    <section className="citation-band"><div><FileText size={18} /><strong>引用来源</strong></div><div className="tag-list">{(report.citations || []).map((citation: string) => <code key={citation}>{citation}</code>)}{!report.citations?.length && <span>本报告未使用知识库引用</span>}</div></section>
  </>
}

function InterviewQuestionCard({ item, index }: { item: InterviewItem; index: number }) {
  const [answer, setAnswer] = useState<InterviewQuestionAnswer | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  async function revealAnswer() {
    setLoading(true); setError('')
    try { setAnswer(await api.interviewQuestionAnswer(item.questionId)) }
    catch (reason) { setError(reason instanceof Error ? reason.message : '参考答案加载失败') }
    finally { setLoading(false) }
  }
  return <article className="question"><div className="question-index">{String(index + 1).padStart(2, '0')}</div><div>
    <div className="question-meta"><span>{item.questionType}</span><span>{item.difficulty}</span></div><h3>{item.question}</h3>
    <ul>{item.expectedPoints?.map(point => <li key={point}>{point}</li>)}</ul>
    {item.citations?.length ? <div className="citations">来源：{item.citations.join('、')}</div> : item.noCitationReason && <div className="citations muted">{item.noCitationReason}</div>}
    {!answer && <button className="secondary small answer-toggle" disabled={loading} onClick={() => void revealAnswer()}>{loading ? '加载中…' : '查看参考答案'}</button>}
    {error && <div className="answer-error">{error}</div>}
    {answer && <details className="question-answer" open><summary>参考答案与评分要点</summary><ol>{answer.answerOutline.map(point => <li key={point}>{point}</li>)}</ol><p>{answer.referenceAnswer}</p><div>{answer.scoringRubric.map(rule => <span key={rule.criterion}>{rule.criterion} · {rule.weight}%</span>)}</div>{answer.commonMistakes.length > 0 && <small>常见误区：{answer.commonMistakes.join('；')}</small>}</details>}
  </div></article>
}

function LearningPlanPanel({ value }: { value: LearningPlan }) {
  const plan = value.plan
  if ('dailyPlans' in plan) return <section className="learning-plan sprint-plan"><div className="learning-plan-head"><div><span className="eyebrow">INTERVIEW SPRINT</span><h2>短期面试冲刺</h2><p>{plan.summary}</p></div><div><strong>{plan.daysRemaining === 0 ? '今天' : `${plan.daysRemaining} 天`}</strong><span>每天 {plan.availableHoursPerDay} 小时</span></div></div><p className="adjustment-reason">{plan.adjustmentReason}</p><div className="priority-grid">{plan.priorities.map(item => <article key={`${item.priority}-${item.skill}`}><span>优先级 {item.priority}</span><h3>{item.skill}</h3><p>{item.gap}</p><small>{item.evidence}</small></article>)}</div><div className="plan-phases daily-phases">{plan.dailyPlans.map(day => <article key={`${day.day}-${day.date}`}><div className="phase-week">D{day.day}</div><div><h3>{day.focus}</h3><p>{day.date}</p><ul>{day.actions.map(action => <li key={action}>{action}</li>)}</ul><small>必练题：{day.questions.join('、')}</small><small>交付物：{day.deliverables.join('、')}</small></div></article>)}</div><div className="review-practice"><strong>冲刺题单</strong><ol>{plan.practiceQuestions.map(item => <li key={item}>{item}</li>)}</ol></div><LikelyInterviewQuestions questions={plan.likelyInterviewQuestions} /><div className="success-metrics"><strong>验收标准</strong><ul>{plan.successMetrics.map(metric => <li key={metric}>{metric}</li>)}</ul></div></section>
  return <section className="learning-plan"><div className="learning-plan-head"><div><span className="eyebrow">PERSONAL ROADMAP</span><h2>个性化学习计划</h2><p>{plan.summary}</p></div><div><strong>{plan.durationWeeks} 周</strong><span>每周 {plan.weeklyHours} 小时</span></div></div>{plan.adjustmentReason && <p className="adjustment-reason">{plan.adjustmentReason}</p>}<div className="priority-grid">{plan.priorities.map(item => <article key={`${item.priority}-${item.skill}`}><span>优先级 {item.priority}</span><h3>{item.skill}</h3><p>{item.gap}</p><small>{item.evidence}</small></article>)}</div><div className="plan-phases">{plan.phases.map(phase => <article key={`${phase.weekStart}-${phase.title}`}><div className="phase-week">W{phase.weekStart}–{phase.weekEnd}</div><div><h3>{phase.title}</h3><p>{phase.goals.join(' · ')}</p><ul>{phase.actions.map(action => <li key={action}>{action}</li>)}</ul><small>交付物：{phase.deliverables.join('、')}</small></div></article>)}</div>{plan.practiceQuestions && <div className="review-practice"><strong>专项练习题</strong><ol>{plan.practiceQuestions.map(item => <li key={item}>{item}</li>)}</ol></div>}<LikelyInterviewQuestions questions={plan.likelyInterviewQuestions} /><div className="success-metrics"><strong>验收标准</strong><ul>{plan.successMetrics.map(metric => <li key={metric}>{metric}</li>)}</ul></div></section>
}

function LikelyInterviewQuestions({ questions = [] }: { questions?: LearningPlanInterviewQuestion[] }) {
  if (!questions.length) return null
  return <div className="likely-questions"><strong>岗位可能追问与参考答案</strong>{questions.map((item, index) => <article key={`${index}-${item.question}`}><h3>{item.question}</h3><p>{item.whyAsked}</p><ul>{item.answerStrategy.map(point => <li key={point}>{point}</li>)}</ul><blockquote>{item.referenceAnswer}</blockquote><small>关联知识点：{item.knowledgePoints.join('、')}</small><small>练习动作：{item.practiceTasks.join('、')}</small><small>依据材料：{item.sourceMaterials.join('、')}</small></article>)}</div>
}

function ReportSection({ title, items = [], missing, tone }: { title: string; items?: string[]; missing?: string; tone: string }) { return <div className={`report-section ${tone}`}><h3>{title}</h3>{missing ? <p>{missing}</p> : <ul>{items.map(item => <li key={item}>{item}</li>)}</ul>}</div> }
function TaskRow({ task, onClick }: { task: CareerTask; onClick: () => void }) { return <button className="row-item" onClick={onClick}><div className="row-icon"><Activity /></div><div className="row-main"><strong>职业分析 #{task.taskId}</strong><span>{statusLabel[task.status] || task.status} · {formatDate(task.updatedAt)}</span></div><div className="row-progress"><span>{task.progress}%</span><i><b style={{ width: `${task.progress}%` }} /></i></div><ChevronRight className="chevron" /></button> }
function ReportRow({ report, onClick }: { report: ReportSummary; onClick: () => void }) { return <button className="report-item" onClick={onClick}><div className="report-item-top"><Status status={report.status} /><span>V{report.version}</span></div><h3>{report.position}</h3><p>{report.company || '未填写公司'}</p><div><FileText size={15} /><span>{report.resumeTitle}</span><time>{formatDate(report.createdAt)}</time></div></button> }
function Status({ status }: { status: string }) { return <span className={`status status-${status.toLowerCase()}`}><i />{statusLabel[status] || status}</span> }
function Metric({ icon, label, value, tone }: { icon: ReactNode; label: string; value: number; tone: string }) { return <div className="metric"><span className={`metric-icon ${tone}`}>{icon}</span><div><strong>{value}</strong><span>{label}</span></div></div> }
function SectionTitle({ title, action }: { title: string; action?: string }) { return <div className="section-title"><h2>{title}</h2>{action && <span>{action}</span>}</div> }
function Empty({ icon, text }: { icon: ReactNode; text: string }) { return <div className="empty">{icon}<span>{text}</span></div> }
function Field({ label, children }: { label: string; children: ReactNode }) { return <label className="field"><span>{label}</span>{children}</label> }
function FilePicker({ file, onChange, accept }: { file: File | null; onChange: (file: File | null) => void; accept: string }) { return <label className="file-picker"><input type="file" accept={accept} onChange={e => onChange(e.target.files?.[0] || null)} /><Upload size={20} /><div><strong>{file?.name || '选择文件'}</strong><span>{file ? `${(file.size / 1024).toFixed(0)} KB` : 'PDF、Word、TXT 或 Markdown'}</span></div></label> }
function NavButton({ active, icon, label, badge, onClick }: { active: boolean; icon: ReactNode; label: string; badge?: number; onClick: () => void }) { return <button className={active ? 'nav active' : 'nav'} onClick={onClick}>{icon}<span>{label}</span>{Boolean(badge) && <b>{badge}</b>}</button> }
function formatDate(value?: string) { if (!value) return ''; return new Intl.DateTimeFormat('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' }).format(new Date(value)) }

export default App
