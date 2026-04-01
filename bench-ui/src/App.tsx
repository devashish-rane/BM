import { useEffect, useMemo, useRef, useState } from 'react'
import './App.css'

type BenchmarkResults = Record<string, Record<string, Record<string, string>>>

type BenchmarkApiResponse = {
  results: BenchmarkResults
}

type ToolEvaluation = {
  tool: string
  expectedFindings: number
  detectedFindings: number
  matchedFindings: number
  precision: number
  recall: number
  f1Score: number
  sourceRepoUrl: string
  expectedResultVersion: string
  weekOverWeekChartUrl: string
}

type DatasetEvaluation = {
  sourceRepoUrl: string
  defaultBranch: string
  expectedFindings: number
  expectedResultVersion: string
  tools: Record<string, ToolEvaluation>
}

type BenchmarkEvaluationResponse = {
  evaluations: Record<string, Record<string, DatasetEvaluation>>
}

type JobStatus =
  | 'QUEUED'
  | 'RUNNING'
  | 'COMPLETED'
  | 'PARTIALLY_COMPLETED'
  | 'FAILED'
  | 'CANCELLED'

type TaskStatus =
  | 'PENDING'
  | 'RUNNING'
  | 'SUCCESS'
  | 'FAILED'
  | 'TIMEOUT'
  | 'SKIPPED_CIRCUIT_OPEN'
  | 'CANCELLED'

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
  archived: boolean
  totalTasks: number
  pendingTasks: number
  runningTasks: number
  successTasks: number
  failedTasks: number
  timeoutTasks: number
  skippedTasks: number
  cancelledTasks: number
  submittedAt: string
  startedAt: string | null
  completedAt: string | null
  archivedAt: string | null
  tasks: TaskProgress[]
}

type BenchmarkRunSummary = {
  runId: string
  requestedLanguage: string
  status: JobStatus
  archived: boolean
  totalTasks: number
  pendingTasks: number
  runningTasks: number
  successTasks: number
  failedTasks: number
  timeoutTasks: number
  skippedTasks: number
  cancelledTasks: number
  submittedAt: string
  startedAt: string | null
  completedAt: string | null
  archivedAt: string | null
}

type PagedRunsResponse = {
  items: BenchmarkRunSummary[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  hasNext: boolean
}

type ExpandedTool = {
  runId: string
  language: string
  dataset: string
  tool: string
  status: TaskStatus
  attempts: number
  result: string | null
  error: string | null
  sourceRepoUrl: string | null
  expectedFindings: number | null
  detectedFindings: number | null
  matchedFindings: number | null
  precision: number | null
  recall: number | null
  f1Score: number | null
  expectedResultVersion: string | null
  weekOverWeekChartUrl: string | null
}

type ToolView = {
  tool: string
  status: TaskStatus
  attempts: number
  result: string | null
  error: string | null
  sourceRepoUrl: string | null
  expectedFindings: number | null
  detectedFindings: number | null
  matchedFindings: number | null
  precision: number | null
  recall: number | null
  f1Score: number | null
  expectedResultVersion: string | null
  weekOverWeekChartUrl: string | null
}

type ResumeRunRequest = {
  languages?: string[]
  datasets?: string[]
  tools?: string[]
  failedOnly?: boolean
}

type DatasetView = {
  sourceRepoUrl: string | null
  defaultBranch: string | null
  expectedFindings: number | null
  expectedResultVersion: string | null
  tools: ToolView[]
}

type GroupedTaskView = Record<string, Record<string, DatasetView>>

const TERMINAL_JOB_STATES: JobStatus[] = ['COMPLETED', 'PARTIALLY_COMPLETED', 'FAILED', 'CANCELLED']
const LANGUAGE_OPTIONS = ['all', 'java', 'python', 'javascript', 'go', 'csharp']

function App() {
  const analyticsRunId = getAnalyticsRunId()

  if (analyticsRunId) {
    return <RunAnalyticsPage runId={analyticsRunId} />
  }

  return <DashboardPage />
}

function DashboardPage() {
  const [selectedLanguage, setSelectedLanguage] = useState('all')
  const [runsPage, setRunsPage] = useState<PagedRunsResponse | null>(null)
  const [selectedRunId, setSelectedRunId] = useState<string | null>(null)
  const [jobStatus, setJobStatus] = useState<JobStatusResponse | null>(null)
  const [results, setResults] = useState<BenchmarkApiResponse | null>(null)
  const [evaluations, setEvaluations] = useState<BenchmarkEvaluationResponse | null>(null)
  const [historyError, setHistoryError] = useState<string | null>(null)
  const [detailError, setDetailError] = useState<string | null>(null)
  const [createError, setCreateError] = useState<string | null>(null)
  const [isLoadingRuns, setIsLoadingRuns] = useState(true)
  const [isRefreshingRun, setIsRefreshingRun] = useState(false)
  const [isCancelling, setIsCancelling] = useState(false)
  const [isCreatingRun, setIsCreatingRun] = useState(false)
  const [isArchiving, setIsArchiving] = useState(false)
  const [isResuming, setIsResuming] = useState(false)
  const [openLanguages, setOpenLanguages] = useState<Record<string, boolean>>({})
  const [expandedTool, setExpandedTool] = useState<ExpandedTool | null>(null)
  const pollerRef = useRef<number | null>(null)

  const groupedTasks = useMemo(
    () => buildGroupedTasks(jobStatus?.tasks ?? [], results, evaluations),
    [jobStatus, results, evaluations]
  )

  useEffect(() => {
    void loadRuns()
    return () => {
      stopPolling()
    }
  }, [])

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
    if (!selectedRunId) {
      setJobStatus(null)
      setResults(null)
      setEvaluations(null)
      stopPolling()
      return
    }

    void loadSelectedRun(selectedRunId, true)
  }, [selectedRunId])

  const loadRuns = async (preserveSelection = true) => {
    setIsLoadingRuns(true)
    try {
      const response = await fetch('bench/runs?page=0&size=20', { cache: 'no-store' })
      if (!response.ok) {
        throw new Error(await response.text())
      }

      const payload = (await response.json()) as PagedRunsResponse
      setRunsPage(payload)
      setHistoryError(null)

      setSelectedRunId((currentSelectedRunId) => {
        if (payload.items.length === 0) {
          return null
        }
        if (preserveSelection && currentSelectedRunId && payload.items.some((item) => item.runId === currentSelectedRunId)) {
          return currentSelectedRunId
        }
        return payload.items[0].runId
      })
    } catch (loadError) {
      setHistoryError(loadError instanceof Error ? loadError.message : 'Failed to load run history')
    } finally {
      setIsLoadingRuns(false)
    }
  }

  const createRun = async () => {
    setIsCreatingRun(true)
    setCreateError(null)
    setDetailError(null)

    const generatedRunId = globalThis.crypto?.randomUUID?.() ?? `run-${Date.now()}`

    try {
      const response = await fetch('bench/runs', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          runid: generatedRunId,
          language: selectedLanguage,
        }),
      })

      if (!response.ok) {
        throw new Error(await response.text())
      }

      const payload = (await response.json()) as { runId: string }
      stopPolling()
      await loadRuns(false)
      setSelectedRunId(payload.runId)
    } catch (createRunError) {
      setCreateError(createRunError instanceof Error ? createRunError.message : 'Failed to create run')
    } finally {
      setIsCreatingRun(false)
    }
  }

  const loadSelectedRun = async (runId: string, startPollingIfNeeded = false) => {
    setIsRefreshingRun(true)
    try {
      const [statusResponse, resultsResponse, evaluationsResponse] = await Promise.all([
        fetch(`bench/runs/${runId}?t=${Date.now()}`, { cache: 'no-store' }),
        fetch(`bench/runs/${runId}/results?t=${Date.now()}`, { cache: 'no-store' }),
        fetch(`bench/runs/${runId}/evaluations?t=${Date.now()}`, { cache: 'no-store' }),
      ])

      if (!statusResponse.ok) {
        throw new Error(await statusResponse.text())
      }
      if (!resultsResponse.ok) {
        throw new Error(await resultsResponse.text())
      }
      if (!evaluationsResponse.ok) {
        throw new Error(await evaluationsResponse.text())
      }

      const statusPayload = (await statusResponse.json()) as JobStatusResponse
      const resultsPayload = (await resultsResponse.json()) as BenchmarkApiResponse
      const evaluationsPayload = (await evaluationsResponse.json()) as BenchmarkEvaluationResponse

      setJobStatus(statusPayload)
      setResults(resultsPayload)
      setEvaluations(evaluationsPayload)
      setDetailError(null)

      if (startPollingIfNeeded) {
        if (TERMINAL_JOB_STATES.includes(statusPayload.status)) {
          stopPolling()
        } else {
          startPolling(runId)
        }
      }
    } catch (loadError) {
      setDetailError(loadError instanceof Error ? loadError.message : 'Failed to load run details')
      stopPolling()
    } finally {
      setIsRefreshingRun(false)
    }
  }

  const startPolling = (runId: string) => {
    stopPolling()

    const tick = async () => {
      try {
        const [statusResponse, resultsResponse, runsResponse, evaluationsResponse] = await Promise.all([
          fetch(`bench/runs/${runId}?t=${Date.now()}`, { cache: 'no-store' }),
          fetch(`bench/runs/${runId}/results?t=${Date.now()}`, { cache: 'no-store' }),
          fetch(`bench/runs?page=0&size=20&t=${Date.now()}`, { cache: 'no-store' }),
          fetch(`bench/runs/${runId}/evaluations?t=${Date.now()}`, { cache: 'no-store' }),
        ])

        if (!statusResponse.ok) {
          throw new Error(await statusResponse.text())
        }
        if (!resultsResponse.ok) {
          throw new Error(await resultsResponse.text())
        }
        if (!runsResponse.ok) {
          throw new Error(await runsResponse.text())
        }
        if (!evaluationsResponse.ok) {
          throw new Error(await evaluationsResponse.text())
        }

        const statusPayload = (await statusResponse.json()) as JobStatusResponse
        const resultsPayload = (await resultsResponse.json()) as BenchmarkApiResponse
        const runsPayload = (await runsResponse.json()) as PagedRunsResponse
        const evaluationsPayload = (await evaluationsResponse.json()) as BenchmarkEvaluationResponse

        setJobStatus(statusPayload)
        setResults(resultsPayload)
        setRunsPage(runsPayload)
        setEvaluations(evaluationsPayload)
        setHistoryError(null)
        setDetailError(null)

        if (TERMINAL_JOB_STATES.includes(statusPayload.status)) {
          stopPolling()
        }
      } catch (pollError) {
        setDetailError(pollError instanceof Error ? pollError.message : 'Failed to refresh run details')
        stopPolling()
      }
    }

    void tick()
    pollerRef.current = window.setInterval(() => void tick(), 2000)
  }

  const stopPolling = () => {
    if (pollerRef.current !== null) {
      window.clearInterval(pollerRef.current)
      pollerRef.current = null
    }
  }

  const cancelSelectedRun = async () => {
    if (!selectedRunId) {
      return
    }

    setIsCancelling(true)
    try {
      const response = await fetch(`bench/runs/${selectedRunId}/cancel`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
      })
      if (!response.ok) {
        throw new Error(await response.text())
      }

      const payload = (await response.json()) as JobStatusResponse
      setJobStatus(payload)
      setDetailError(null)
      stopPolling()
      await loadRuns(true)
      await loadSelectedRun(selectedRunId, false)
    } catch (cancelError) {
      setDetailError(cancelError instanceof Error ? cancelError.message : 'Failed to cancel run')
    } finally {
      setIsCancelling(false)
    }
  }

  const refreshCurrentView = async () => {
    await loadRuns(true)
    if (selectedRunId) {
      await loadSelectedRun(selectedRunId, true)
    }
  }

  const archiveSelectedRun = async () => {
    if (!selectedRunId) {
      return
    }

    setIsArchiving(true)
    try {
      const response = await fetch(`bench/runs/${selectedRunId}/archive`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
      })
      if (!response.ok) {
        throw new Error(await response.text())
      }

      const payload = (await response.json()) as JobStatusResponse
      setJobStatus(payload)
      setDetailError(null)
      stopPolling()
      await loadRuns(true)
    } catch (archiveError) {
      setDetailError(archiveError instanceof Error ? archiveError.message : 'Failed to archive run')
    } finally {
      setIsArchiving(false)
    }
  }

  const resumeRunSelection = async (selection: ResumeRunRequest) => {
    if (!selectedRunId) {
      return
    }

    setIsResuming(true)
    try {
      const response = await fetch(`bench/runs/${selectedRunId}/resume`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(selection),
      })
      if (!response.ok) {
        throw new Error(await response.text())
      }

      const payload = (await response.json()) as JobStatusResponse
      setJobStatus(payload)
      setExpandedTool(null)
      setDetailError(null)
      await loadRuns(true)
      await loadSelectedRun(selectedRunId, true)
    } catch (resumeError) {
      setDetailError(resumeError instanceof Error ? resumeError.message : 'Failed to resume selection')
    } finally {
      setIsResuming(false)
    }
  }

  const toggleLanguage = (language: string) => {
    setOpenLanguages((prev) => ({ ...prev, [language]: !prev[language] }))
  }

  const canCancel = jobStatus !== null && !jobStatus.archived && !TERMINAL_JOB_STATES.includes(jobStatus.status)
  const canArchive = jobStatus !== null && !jobStatus.archived && TERMINAL_JOB_STATES.includes(jobStatus.status)
  const canResume = jobStatus !== null && !jobStatus.archived && TERMINAL_JOB_STATES.includes(jobStatus.status)
  const failureCount = jobStatus ? jobStatus.failedTasks + jobStatus.timeoutTasks + jobStatus.skippedTasks + jobStatus.cancelledTasks : 0

  return (
    <main className="app-shell">
      <header className="page-header">
        <div>
          <h1>Benchmarking Dashboard</h1>
          <p>Browse recent runs, inspect task history, and cancel active executions.</p>
        </div>
        <button className="secondary-btn" onClick={() => void refreshCurrentView()} disabled={isLoadingRuns || isRefreshingRun}>
          Refresh History
        </button>
      </header>

      <section className="create-run-panel">
        <div className="create-run-copy">
          <h2>Create Run</h2>
          <p>Submit a new benchmark run, then inspect or cancel it from history.</p>
        </div>
        <div className="create-run-controls">
          <label htmlFor="language-select">Language</label>
          <select
            id="language-select"
            value={selectedLanguage}
            onChange={(event) => setSelectedLanguage(event.target.value)}
            disabled={isCreatingRun}
          >
            {LANGUAGE_OPTIONS.map((language) => (
              <option key={language} value={language}>
                {language}
              </option>
            ))}
          </select>
          <button className="primary-btn" onClick={() => void createRun()} disabled={isCreatingRun}>
            {isCreatingRun ? 'Creating...' : 'Create Run'}
          </button>
        </div>
      </section>

      {createError && <div className="inline-error">{createError}</div>}

      <section className="workspace-layout">
        <aside className="history-panel">
          <div className="panel-header">
            <div>
              <h2>Recent Runs</h2>
              <p>{runsPage?.totalElements ?? 0} runs available</p>
            </div>
          </div>

          {historyError && <div className="inline-error">{historyError}</div>}

          {isLoadingRuns && !runsPage && (
            <div className="history-empty">
              <div className="loader small" aria-label="Loading runs" />
              <span>Loading run history...</span>
            </div>
          )}

          {!isLoadingRuns && runsPage?.items.length === 0 && <div className="history-empty">No benchmark runs found yet.</div>}

          <div className="run-list">
            {runsPage?.items.map((run) => (
              <button
                key={run.runId}
                className={`run-list-item ${selectedRunId === run.runId ? 'selected' : ''}`}
                onClick={() => setSelectedRunId(run.runId)}
              >
                <div className="run-list-head">
                  <span className="run-language">{run.requestedLanguage}</span>
                  <div className="run-list-tags">
                    <span className={`status-tag-inline status-${run.status.toLowerCase()}`}>{run.status}</span>
                    {run.archived && <span className="status-tag-inline status-archived">ARCHIVED</span>}
                  </div>
                </div>
                <strong>{shortRunId(run.runId)}</strong>
                <span>{formatDate(run.submittedAt)}</span>
                <span>{run.totalTasks} tasks</span>
              </button>
            ))}
          </div>
        </aside>

        <section className="detail-panel">
          {detailError && <div className="inline-error">{detailError}</div>}

          {!selectedRunId && <div className="status empty">Select a run from history to inspect details.</div>}

          {selectedRunId && !jobStatus && !detailError && (
            <div className="status loading">
              <div className="loader" aria-label="Loading details" />
              <span>Loading run details...</span>
            </div>
          )}

          {jobStatus && (
            <>
              <section className="detail-header-card">
                <div className="detail-header-copy">
                  <div className="detail-title-row">
                    <h2>{jobStatus.requestedLanguage} run</h2>
                    <span className={`status-tag-inline status-${jobStatus.status.toLowerCase()}`}>{jobStatus.status}</span>
                    {jobStatus.archived && <span className="status-tag-inline status-archived">ARCHIVED</span>}
                  </div>
                  <p>Run ID: {jobStatus.runId}</p>
                  <p>Submitted: {formatDate(jobStatus.submittedAt)}</p>
                  {jobStatus.archivedAt && <p>Archived: {formatDate(jobStatus.archivedAt)}</p>}
                </div>
                <div className="detail-actions">
                  <a className="secondary-btn link-btn" href={`/analytics/${jobStatus.runId}`}>
                    Analytics
                  </a>
                  <button className="secondary-btn" onClick={() => void refreshCurrentView()} disabled={isRefreshingRun}>
                    Refresh
                  </button>
                  <button className="secondary-btn" onClick={() => void archiveSelectedRun()} disabled={!canArchive || isArchiving}>
                    {isArchiving ? 'Archiving...' : 'Archive'}
                  </button>
                  <button className="danger-btn" onClick={() => void cancelSelectedRun()} disabled={!canCancel || isCancelling}>
                    {isCancelling ? 'Cancelling...' : 'Cancel Run'}
                  </button>
                </div>
              </section>

              <section className="summary-row compact">
                <div className="metric success">Success: {jobStatus.successTasks}</div>
                <div className="metric running">Running: {jobStatus.runningTasks}</div>
                <div className="metric pending">Pending: {jobStatus.pendingTasks}</div>
                <div className="metric failed">Non-success: {failureCount}</div>
                <div className="metric total">Total: {jobStatus.totalTasks}</div>
              </section>

              {Object.keys(groupedTasks).length === 0 && (
                <div className="status empty">No task detail is available for this run yet.</div>
              )}

              <div className="results">
                {Object.entries(groupedTasks).map(([language, datasets]) => (
                  <section key={language} className="language-panel">
                    <div className="language-header">
                      <h2>{language}</h2>
                      <button className="language-toggle" onClick={() => toggleLanguage(language)}>
                        {openLanguages[language] ? 'Collapse' : 'Expand'}
                      </button>
                    </div>

                    {openLanguages[language] && (
                      <div className="dataset-grid">
                        {Object.entries(datasets).map(([dataset, datasetView]) => (
                          <article key={dataset} className="dataset-card">
                            <div className="dataset-card-head">
                              <div>
                                <h3>{dataset}</h3>
                                <p>{datasetView.tools.length} tools tracked</p>
                                {datasetView.sourceRepoUrl && (
                                  <a className="dataset-source-link" href={datasetView.sourceRepoUrl} target="_blank" rel="noreferrer">
                                    Source repo
                                  </a>
                                )}
                                {datasetView.expectedFindings !== null && (
                                  <p className="dataset-meta">
                                    Expected detections: {datasetView.expectedFindings}
                                    {datasetView.expectedResultVersion ? ` • ${datasetView.expectedResultVersion}` : ''}
                                  </p>
                                )}
                              </div>
                            </div>
                            <div className="tool-actions">
                              {datasetView.tools.map((toolView) => (
                                <button
                                  key={`${dataset}-${toolView.tool}`}
                                  className="tool-pill"
                                  onClick={() =>
                                    setExpandedTool({
                                      runId: jobStatus.runId,
                                      language,
                                      dataset,
                                      tool: toolView.tool,
                                      status: toolView.status,
                                      attempts: toolView.attempts,
                                      result: toolView.result,
                                      error: toolView.error,
                                      sourceRepoUrl: toolView.sourceRepoUrl,
                                      expectedFindings: toolView.expectedFindings,
                                      detectedFindings: toolView.detectedFindings,
                                      matchedFindings: toolView.matchedFindings,
                                      precision: toolView.precision,
                                      recall: toolView.recall,
                                      f1Score: toolView.f1Score,
                                      expectedResultVersion: toolView.expectedResultVersion,
                                      weekOverWeekChartUrl: toolView.weekOverWeekChartUrl,
                                    })
                                  }
                                >
                                  <span className="tool-pill-copy">
                                    <span className="tool-pill-name">{toolView.tool}</span>
                                    {toolView.precision !== null && toolView.recall !== null && (
                                      <span className="tool-pill-metrics">
                                        P {formatMetricPercent(toolView.precision)} • R {formatMetricPercent(toolView.recall)}
                                      </span>
                                    )}
                                  </span>
                                  <span className={`status-tag-inline status-${toolView.status.toLowerCase()}`}>{toolView.status}</span>
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
            </>
          )}
        </section>
      </section>

      {expandedTool && (
        <div className="modal-backdrop" onClick={() => setExpandedTool(null)}>
          <section className="modal-card" onClick={(event) => event.stopPropagation()}>
            <h2>
              {expandedTool.language} / {expandedTool.dataset}
            </h2>
            <p className="tool-name">{expandedTool.tool}</p>
            <p className={`status-tag status-${expandedTool.status.toLowerCase()}`}>{expandedTool.status}</p>
            <p className="attempts">Attempts: {expandedTool.attempts}</p>
            {expandedTool.sourceRepoUrl && (
              <p className="tool-meta">
                Source repo:{' '}
                <a href={expandedTool.sourceRepoUrl} target="_blank" rel="noreferrer">
                  {expandedTool.sourceRepoUrl}
                </a>
              </p>
            )}
            {expandedTool.expectedFindings !== null && (
              <div className="tool-metrics-grid">
                <div className="tool-metric-card">
                  <span>Expected</span>
                  <strong>{expandedTool.expectedFindings}</strong>
                </div>
                <div className="tool-metric-card">
                  <span>Detected</span>
                  <strong>{expandedTool.detectedFindings ?? 0}</strong>
                </div>
                <div className="tool-metric-card">
                  <span>Matched</span>
                  <strong>{expandedTool.matchedFindings ?? 0}</strong>
                </div>
                <div className="tool-metric-card">
                  <span>Precision</span>
                  <strong>{formatMetricPercent(expandedTool.precision)}</strong>
                </div>
                <div className="tool-metric-card">
                  <span>Recall</span>
                  <strong>{formatMetricPercent(expandedTool.recall)}</strong>
                </div>
                <div className="tool-metric-card">
                  <span>F1</span>
                  <strong>{formatMetricPercent(expandedTool.f1Score)}</strong>
                </div>
              </div>
            )}
            {expandedTool.expectedResultVersion && <p className="tool-meta">Expected baseline: {expandedTool.expectedResultVersion}</p>}
            {expandedTool.error && <p className="tool-error">Error: {expandedTool.error}</p>}
            <pre className="tool-result">{expandedTool.result ?? 'Result not available yet.'}</pre>
            <div className="modal-actions">
              <button
                className="secondary-btn"
                onClick={() => downloadToolResult(expandedTool)}
                disabled={!expandedTool.result}
              >
                Download JSON
              </button>
              <button
                className="secondary-btn"
                onClick={() =>
                  void resumeRunSelection({
                    languages: [expandedTool.language],
                    datasets: [expandedTool.dataset],
                    tools: [expandedTool.tool],
                    failedOnly: true,
                  })
                }
                disabled={!canResume || isResuming || !isFailureLike(expandedTool.status)}
              >
                {isResuming ? 'Resuming...' : 'Resume Tool'}
              </button>
              <button className="close-btn" onClick={() => setExpandedTool(null)}>
                Close
              </button>
            </div>
          </section>
        </div>
      )}
    </main>
  )
}

function RunAnalyticsPage({ runId }: { runId: string }) {
  const [jobStatus, setJobStatus] = useState<JobStatusResponse | null>(null)
  const [evaluations, setEvaluations] = useState<BenchmarkEvaluationResponse | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    const loadAnalytics = async () => {
      setLoading(true)
      try {
        const [statusResponse, evaluationsResponse] = await Promise.all([
          fetch(`/bench/runs/${runId}?t=${Date.now()}`, { cache: 'no-store' }),
          fetch(`/bench/runs/${runId}/evaluations?t=${Date.now()}`, { cache: 'no-store' }),
        ])

        if (!statusResponse.ok) {
          throw new Error(await statusResponse.text())
        }
        if (!evaluationsResponse.ok) {
          throw new Error(await evaluationsResponse.text())
        }

        setJobStatus((await statusResponse.json()) as JobStatusResponse)
        setEvaluations((await evaluationsResponse.json()) as BenchmarkEvaluationResponse)
        setError(null)
      } catch (loadError) {
        setError(loadError instanceof Error ? loadError.message : 'Failed to load analytics')
      } finally {
        setLoading(false)
      }
    }

    void loadAnalytics()
  }, [runId])

  const taskStatusIndex = useMemo(() => {
    const statusIndex = new Map<string, TaskProgress>()
    for (const task of jobStatus?.tasks ?? []) {
      statusIndex.set(buildTaskKey(task.language, task.dataset, task.tool), task)
    }
    return statusIndex
  }, [jobStatus])

  return (
    <main className="app-shell analytics-shell">
      <header className="page-header analytics-header">
        <div>
          <a className="back-link" href="/">
            Back to dashboard
          </a>
          <h1>Run Analytics</h1>
          <p>Dataset and tool quality metrics for one benchmark run.</p>
          {jobStatus && (
            <p className="analytics-subtitle">
              {jobStatus.requestedLanguage} / {shortRunId(jobStatus.runId)} / {jobStatus.status}
            </p>
          )}
        </div>
        <a className="secondary-btn link-btn" href={`/`}>
          Open Dashboard
        </a>
      </header>

      {error && <div className="inline-error">{error}</div>}

      {loading && (
        <div className="status loading">
          <div className="loader" aria-label="Loading analytics" />
          <span>Loading analytics...</span>
        </div>
      )}

      {!loading && evaluations && (
        <div className="analytics-grid">
          {Object.entries(evaluations.evaluations).map(([language, datasets]) => (
            <section key={language} className="language-panel analytics-language-panel">
              <div className="language-header">
                <h2>{language}</h2>
              </div>
              <div className="dataset-grid analytics-dataset-grid">
                {Object.entries(datasets).map(([dataset, datasetEvaluation]) => (
                  <article key={dataset} className="dataset-card analytics-dataset-card">
                    <div className="dataset-card-head">
                      <div>
                        <h3>{dataset}</h3>
                        <a className="dataset-source-link" href={datasetEvaluation.sourceRepoUrl} target="_blank" rel="noreferrer">
                          {datasetEvaluation.sourceRepoUrl}
                        </a>
                        <p className="dataset-meta">
                          Expected detections: {datasetEvaluation.expectedFindings} • {datasetEvaluation.expectedResultVersion}
                        </p>
                      </div>
                    </div>

                    <div className="analytics-tool-grid">
                      {Object.entries(datasetEvaluation.tools).map(([tool, toolEvaluation]) => {
                        const task = taskStatusIndex.get(buildTaskKey(language, dataset, tool))
                        return (
                          <section key={tool} className="analytics-tool-card">
                            <div className="analytics-tool-head">
                              <div>
                                <h4>{tool}</h4>
                                {task && <span className={`status-tag-inline status-${task.status.toLowerCase()}`}>{task.status}</span>}
                              </div>
                              <a className="secondary-btn link-btn compact-link" href={toolEvaluation.weekOverWeekChartUrl} target="_blank" rel="noreferrer">
                                Week over week
                              </a>
                            </div>
                            <div className="tool-metrics-grid">
                              <div className="tool-metric-card">
                                <span>Expected</span>
                                <strong>{toolEvaluation.expectedFindings}</strong>
                              </div>
                              <div className="tool-metric-card">
                                <span>Detected</span>
                                <strong>{toolEvaluation.detectedFindings}</strong>
                              </div>
                              <div className="tool-metric-card">
                                <span>Matched</span>
                                <strong>{toolEvaluation.matchedFindings}</strong>
                              </div>
                              <div className="tool-metric-card">
                                <span>Precision</span>
                                <strong>{formatMetricPercent(toolEvaluation.precision)}</strong>
                              </div>
                              <div className="tool-metric-card">
                                <span>Recall</span>
                                <strong>{formatMetricPercent(toolEvaluation.recall)}</strong>
                              </div>
                              <div className="tool-metric-card">
                                <span>F1</span>
                                <strong>{formatMetricPercent(toolEvaluation.f1Score)}</strong>
                              </div>
                            </div>
                            {task && (
                              <p className="dataset-meta">
                                Attempts: {task.attempts}
                                {task.completedAt ? ` • Completed ${formatDate(task.completedAt)}` : ''}
                              </p>
                            )}
                          </section>
                        )
                      })}
                    </div>
                  </article>
                ))}
              </div>
            </section>
          ))}
        </div>
      )}
    </main>
  )
}

function buildGroupedTasks(
  tasks: TaskProgress[],
  results: BenchmarkApiResponse | null,
  evaluations: BenchmarkEvaluationResponse | null
): GroupedTaskView {
  const grouped: GroupedTaskView = {}

  for (const task of tasks) {
    const result = results?.results?.[task.language]?.[task.dataset]?.[task.tool] ?? null
    const datasetEvaluation = evaluations?.evaluations?.[task.language]?.[task.dataset]
    const toolEvaluation = datasetEvaluation?.tools?.[task.tool]
    if (!grouped[task.language]) {
      grouped[task.language] = {}
    }
    if (!grouped[task.language][task.dataset]) {
      grouped[task.language][task.dataset] = {
        sourceRepoUrl: datasetEvaluation?.sourceRepoUrl ?? null,
        defaultBranch: datasetEvaluation?.defaultBranch ?? null,
        expectedFindings: datasetEvaluation?.expectedFindings ?? null,
        expectedResultVersion: datasetEvaluation?.expectedResultVersion ?? null,
        tools: [],
      }
    }
    grouped[task.language][task.dataset].tools.push({
      tool: task.tool,
      status: task.status,
      attempts: task.attempts,
      result,
      error: task.error,
      sourceRepoUrl: toolEvaluation?.sourceRepoUrl ?? datasetEvaluation?.sourceRepoUrl ?? null,
      expectedFindings: toolEvaluation?.expectedFindings ?? datasetEvaluation?.expectedFindings ?? null,
      detectedFindings: toolEvaluation?.detectedFindings ?? null,
      matchedFindings: toolEvaluation?.matchedFindings ?? null,
      precision: toolEvaluation?.precision ?? null,
      recall: toolEvaluation?.recall ?? null,
      f1Score: toolEvaluation?.f1Score ?? null,
      expectedResultVersion: toolEvaluation?.expectedResultVersion ?? datasetEvaluation?.expectedResultVersion ?? null,
      weekOverWeekChartUrl: toolEvaluation?.weekOverWeekChartUrl ?? null,
    })
  }

  for (const language of Object.keys(grouped)) {
    for (const dataset of Object.keys(grouped[language])) {
      grouped[language][dataset].tools.sort((a, b) => a.tool.localeCompare(b.tool))
    }
  }

  return grouped
}

function shortRunId(runId: string) {
  return `${runId.slice(0, 8)}...${runId.slice(-6)}`
}

function formatDate(value: string | null) {
  if (!value) {
    return '-'
  }
  return new Date(value).toLocaleString()
}

function isFailureLike(status: TaskStatus) {
  return status === 'FAILED' || status === 'TIMEOUT' || status === 'SKIPPED_CIRCUIT_OPEN' || status === 'CANCELLED'
}

function getAnalyticsRunId() {
  const match = window.location.pathname.match(/^\/analytics\/([0-9a-fA-F-]+)$/)
  return match ? match[1] : null
}

function buildTaskKey(language: string, dataset: string, tool: string) {
  return `${language}::${dataset}::${tool}`
}

function formatMetricPercent(value: number | null) {
  if (value === null) {
    return '-'
  }
  return `${(value * 100).toFixed(1)}%`
}

function downloadToolResult(expandedTool: ExpandedTool) {
  if (!expandedTool.result) {
    return
  }

  const normalizedRunId = expandedTool.runId.replaceAll(/[^a-zA-Z0-9-]/g, '_')
  const normalizedLanguage = expandedTool.language.replaceAll(/[^a-zA-Z0-9-]/g, '_')
  const normalizedDataset = expandedTool.dataset.replaceAll(/[^a-zA-Z0-9-]/g, '_')
  const normalizedTool = expandedTool.tool.replaceAll(/[^a-zA-Z0-9-]/g, '_')
  const fileName = `${normalizedRunId}-${normalizedLanguage}-${normalizedDataset}-${normalizedTool}.json`

  let fileContent = expandedTool.result
  try {
    fileContent = JSON.stringify(JSON.parse(expandedTool.result), null, 2)
  } catch {
    // Keep original content when the result is not valid JSON.
  }

  const blob = new Blob([fileContent], { type: 'application/json;charset=utf-8' })
  const downloadUrl = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = downloadUrl
  link.download = fileName
  document.body.appendChild(link)
  link.click()
  link.remove()
  URL.revokeObjectURL(downloadUrl)
}

export default App
