import { useEffect, useMemo, useRef, useState } from 'react'
import './App.css'

type BenchmarkResults = Record<string, Record<string, Record<string, string>>>

type BenchmarkApiResponse = {
  results: BenchmarkResults
}

type JobStatus =
  | 'QUEUED'
  | 'RUNNING'
  | 'COMPLETED'
  | 'PARTIALLY_COMPLETED'
  | 'FAILED'

type TaskStatus =
  | 'PENDING'
  | 'RUNNING'
  | 'SUCCESS'
  | 'FAILED'
  | 'TIMEOUT'
  | 'SKIPPED_CIRCUIT_OPEN'

type TaskProgress = {
  taskId: string
  language: string
  dataset: string
  tool: string
  status: TaskStatus
  attempts: number
  startedAt: string | null
  completedAt: string | null
  error: string | null
}

type JobStatusResponse = {
  runId: string
  requestedLanguage: string
  status: JobStatus
  totalTasks: number
  pendingTasks: number
  runningTasks: number
  successTasks: number
  failedTasks: number
  timeoutTasks: number
  skippedTasks: number
  submittedAt: string
  startedAt: string | null
  completedAt: string | null
  tasks: TaskProgress[]
}

type JobSubmitResponse = {
  runId: string
  status: JobStatus
  totalTasks: number
  submittedAt: string
}

type ExpandedTool = {
  language: string
  dataset: string
  tool: string
  status: TaskStatus
  attempts: number
  result: string | null
  error: string | null
}

type ToolView = {
  tool: string
  status: TaskStatus
  attempts: number
  result: string | null
  error: string | null
}

type GroupedTaskView = Record<string, Record<string, ToolView[]>>

const TERMINAL_JOB_STATES: JobStatus[] = ['COMPLETED', 'PARTIALLY_COMPLETED', 'FAILED']

function App() {
  const [selectedLanguage, setSelectedLanguage] = useState('all')
  const [jobStatus, setJobStatus] = useState<JobStatusResponse | null>(null)
  const [results, setResults] = useState<BenchmarkApiResponse | null>(null)
  const [runId, setRunId] = useState<string | null>(null)
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [openLanguages, setOpenLanguages] = useState<Record<string, boolean>>({})
  const [expandedTool, setExpandedTool] = useState<ExpandedTool | null>(null)
  const pollerRef = useRef<number | null>(null)

  const groupedTasks = useMemo(() => buildGroupedTasks(jobStatus?.tasks ?? [], results), [jobStatus, results])

  useEffect(() => {
    const languages = Object.keys(groupedTasks)
    if (languages.length === 0) {
      return
    }
    setOpenLanguages((prev) => {
      const next = { ...prev }
      for (const language of languages) {
        if (!(language in next)) {
          next[language] = true
        }
      }
      return next
    })
  }, [groupedTasks])

  useEffect(() => {
    return () => {
      stopPolling()
    }
  }, [])

  const triggerRun = async () => {
    stopPolling()
    setIsSubmitting(true)
    setError(null)
    setExpandedTool(null)
    setResults(null)
    setJobStatus(null)

    const generatedRunId = globalThis.crypto?.randomUUID?.() ?? `run-${Date.now()}`

    try {
      const submitResponse = await fetch('bench/runs', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          runid: generatedRunId,
          language: selectedLanguage,
        }),
      })
      if (!submitResponse.ok) {
        throw new Error(await submitResponse.text())
      }
      const submitted = (await submitResponse.json()) as JobSubmitResponse
      setRunId(submitted.runId)
      startPolling(submitted.runId)
    } catch (submitError) {
      const message = submitError instanceof Error ? submitError.message : 'Failed to submit run'
      setError(message)
      setIsSubmitting(false)
    }
  }

  const startPolling = (nextRunId: string) => {
    stopPolling()

    const tick = async () => {
      try {
        const [statusResponse, resultsResponse] = await Promise.all([
          fetch(`bench/runs/${nextRunId}?t=${Date.now()}`, { cache: 'no-store' }),
          fetch(`bench/runs/${nextRunId}/results?t=${Date.now()}`, { cache: 'no-store' }),
        ])

        if (!statusResponse.ok) {
          throw new Error(await statusResponse.text())
        }
        if (!resultsResponse.ok) {
          throw new Error(await resultsResponse.text())
        }

        const statusPayload = (await statusResponse.json()) as JobStatusResponse
        const resultsPayload = (await resultsResponse.json()) as BenchmarkApiResponse

        setJobStatus(statusPayload)
        setResults(resultsPayload)
        setError(null)

        if (TERMINAL_JOB_STATES.includes(statusPayload.status)) {
          setIsSubmitting(false)
          stopPolling()
        }
      } catch (pollError) {
        const message = pollError instanceof Error ? pollError.message : 'Polling failed'
        setError(message)
        setIsSubmitting(false)
        stopPolling()
      }
    }

    void tick()
    pollerRef.current = window.setInterval(() => void tick(), 1500)
  }

  const stopPolling = () => {
    if (pollerRef.current !== null) {
      window.clearInterval(pollerRef.current)
      pollerRef.current = null
    }
  }

  const toggleLanguage = (language: string) => {
    setOpenLanguages((prev) => ({ ...prev, [language]: !prev[language] }))
  }

  return (
    <main className="app-shell">
      <header className="page-header">
        <h1>Benchmarking Dashboard</h1>
        <p>Trigger runs and watch each tool complete in real time.</p>
      </header>

      <section className="control-panel">
        <div className="controls">
          <label htmlFor="language-select">Language</label>
          <select
            id="language-select"
            value={selectedLanguage}
            onChange={(event) => setSelectedLanguage(event.target.value)}
            disabled={isSubmitting}
          >
            <option value="all">all</option>
            <option value="java">java</option>
            <option value="python">python</option>
            <option value="javascript">javascript</option>
            <option value="go">go</option>
            <option value="csharp">csharp</option>
          </select>
          <button className="trigger-btn" onClick={triggerRun} disabled={isSubmitting}>
            {isSubmitting ? 'Running...' : 'Trigger Run'}
          </button>
        </div>

        <div className="run-meta">
          <span>Run ID: {runId ?? '-'}</span>
          <span>Status: {jobStatus?.status ?? 'IDLE'}</span>
        </div>
      </section>

      {jobStatus && (
        <section className="summary-row">
          <div className="metric success">Success: {jobStatus.successTasks}</div>
          <div className="metric running">Running: {jobStatus.runningTasks}</div>
          <div className="metric pending">Pending: {jobStatus.pendingTasks}</div>
          <div className="metric failed">Failed: {jobStatus.failedTasks + jobStatus.timeoutTasks + jobStatus.skippedTasks}</div>
          <div className="metric total">Total: {jobStatus.totalTasks}</div>
        </section>
      )}

      <div className="results">
        {error && <div className="status error">{error}</div>}

        {!error && !jobStatus && (
          <div className="status empty">Trigger a run to see live task updates.</div>
        )}

        {!error && isSubmitting && (
          <div className="status loading">
            <div className="loader" aria-label="Loading" />
            <span>Streaming updates...</span>
          </div>
        )}

        {!error &&
          Object.entries(groupedTasks).map(([language, datasets]) => (
            <section key={language} className="language-panel">
              <div className="language-header">
                <h2>{language}</h2>
                <button className="language-toggle" onClick={() => toggleLanguage(language)}>
                  {openLanguages[language] ? 'Collapse' : 'Expand'}
                </button>
              </div>

              {openLanguages[language] && (
                <div className="dataset-grid">
                  {Object.entries(datasets).map(([dataset, tools]) => (
                    <article key={dataset} className="dataset-card">
                      <h3>{dataset}</h3>
                      <p>{tools.length} tools tracked</p>
                      <div className="tool-actions">
                        {tools.map((toolView) => (
                          <button
                            key={`${dataset}-${toolView.tool}`}
                            className="tool-pill"
                            onClick={() =>
                              setExpandedTool({
                                language,
                                dataset,
                                tool: toolView.tool,
                                status: toolView.status,
                                attempts: toolView.attempts,
                                result: toolView.result,
                                error: toolView.error,
                              })
                            }
                          >
                            <span className="tool-pill-name">{toolView.tool}</span>
                            <span className={`status-tag-inline status-${toolView.status.toLowerCase()}`}>
                              {toolView.status}
                            </span>
                          </button>
                        ))}
                      </div>
                    </article>
                  ))}
                </div>
              )}
            </section>
          ))}
      </div>

      {expandedTool && (
        <div className="modal-backdrop" onClick={() => setExpandedTool(null)}>
          <section className="modal-card" onClick={(event) => event.stopPropagation()}>
            <h2>
              {expandedTool.language} / {expandedTool.dataset}
            </h2>
            <p className="tool-name">{expandedTool.tool}</p>
            <p className={`status-tag status-${expandedTool.status.toLowerCase()}`}>{expandedTool.status}</p>
            <p className="attempts">Attempts: {expandedTool.attempts}</p>
            {expandedTool.error && <p className="tool-error">Error: {expandedTool.error}</p>}
            <pre className="tool-result">{expandedTool.result ?? 'Result not available yet.'}</pre>
            <button className="close-btn" onClick={() => setExpandedTool(null)}>
              Close
            </button>
          </section>
        </div>
      )}
    </main>
  )
}

function buildGroupedTasks(tasks: TaskProgress[], results: BenchmarkApiResponse | null): GroupedTaskView {
  const grouped: GroupedTaskView = {}

  for (const task of tasks) {
    const result = results?.results?.[task.language]?.[task.dataset]?.[task.tool] ?? null
    if (!grouped[task.language]) {
      grouped[task.language] = {}
    }
    if (!grouped[task.language][task.dataset]) {
      grouped[task.language][task.dataset] = []
    }
    grouped[task.language][task.dataset].push({
      tool: task.tool,
      status: task.status,
      attempts: task.attempts,
      result,
      error: task.error,
    })
  }

  for (const language of Object.keys(grouped)) {
    for (const dataset of Object.keys(grouped[language])) {
      grouped[language][dataset].sort((a, b) => a.tool.localeCompare(b.tool))
    }
  }

  return grouped
}

export default App
