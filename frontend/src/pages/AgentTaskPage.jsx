import { useEffect, useState, useRef, useCallback } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { useAuthStore } from '../stores/authStore'
import { taskApi, agentApi } from '../api/agenthub'

export default function AgentTaskPage() {
  const navigate = useNavigate()
  const params = useParams()
  const { token, logout, user } = useAuthStore()

  const [agents, setAgents] = useState([])
  const [selectedAgent, setSelectedAgent] = useState(null)
  const [task, setTask] = useState('')
  const [sessionId, setSessionId] = useState(params.sessionId || null)
  const [status, setStatus] = useState(null)
  const [messages, setMessages] = useState([])
  const [toolCalls, setToolCalls] = useState([])
  const [isRunning, setIsRunning] = useState(false)
  const [events, setEvents] = useState([])
  const [showHistory, setShowHistory] = useState(false)
  const [taskHistory, setTaskHistory] = useState([])

  const eventSourceRef = useRef(null)
  const messagesEndRef = useRef(null)

  useEffect(() => {
    if (!token) {
      navigate('/login')
      return
    }
    loadAgents()
    if (sessionId) {
      loadSession()
      connectSSE()
    }
  }, [token, navigate, sessionId])

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [events])

  const loadAgents = async () => {
    try {
      const response = await agentApi.getAgents()
      setAgents(response.data || [])
      if (response.data && response.data.length > 0) {
        setSelectedAgent(response.data[0])
      }
    } catch (error) {
      console.error('Failed to load agents:', error)
    }
  }

  const loadSession = async () => {
    try {
      const response = await taskApi.getTask(sessionId)
      setStatus(response.data)
      setTask(response.data.task)

      // Load history if available
      const messagesResponse = await taskApi.getTaskMessages(sessionId)
      if (messagesResponse.data) {
        setMessages(messagesResponse.data.messages || [])
        setToolCalls(messagesResponse.data.toolResults || [])
      }

      // Load task history
      const historyResponse = await taskApi.getTasks()
      setTaskHistory(historyResponse.data || [])
    } catch (error) {
      console.error('Failed to load session:', error)
    }
  }

  const loadHistory = async () => {
    try {
      const response = await taskApi.getTasks()
      setTaskHistory(response.data || [])
    } catch (error) {
      console.error('Failed to load history:', error)
    }
  }

  const connectSSE = useCallback(() => {
    if (eventSourceRef.current) {
      eventSourceRef.current.close()
    }

    const url = taskApi.getTaskStreamUrl(sessionId)
    const eventSource = new EventSource(url)
    eventSourceRef.current = eventSource

    eventSource.addEventListener('connected', (e) => {
      console.log('SSE Connected:', e.data)
      setIsRunning(true)
    })

    eventSource.addEventListener('status', (e) => {
      const data = JSON.parse(e.data)
      setStatus(prev => ({ ...prev, status: data.status, message: data.message }))
      addEvent({ type: 'status', data, time: new Date().toISOString() })
    })

    eventSource.addEventListener('tool_call', (e) => {
      const data = JSON.parse(e.data)
      addEvent({ type: 'tool_call', data, time: new Date().toISOString() })
      setToolCalls(prev => [...prev, data])
    })

    eventSource.addEventListener('tool_result', (e) => {
      const data = JSON.parse(e.data)
      addEvent({ type: 'tool_result', data, time: new Date().toISOString() })
      setToolCalls(prev => prev.map(tc =>
        tc.toolCallId === data.toolCallId ? { ...tc, result: data.result, success: data.success } : tc
      ))
    })

    eventSource.addEventListener('complete', (e) => {
      const data = JSON.parse(e.data)
      setStatus({ status: 'COMPLETED', content: data.content, iterations: data.iterations })
      addEvent({ type: 'complete', data, time: new Date().toISOString() })
      setIsRunning(false)
    })

    eventSource.addEventListener('error', (e) => {
      const data = JSON.parse(e.data)
      setStatus({ status: 'FAILED', errorMessage: data.message })
      addEvent({ type: 'error', data, time: new Date().toISOString() })
      setIsRunning(false)
      eventSource.close()
    })

    eventSource.onerror = (error) => {
      console.error('SSE Error:', error)
      setIsRunning(false)
    }
  }, [sessionId])

  const addEvent = (event) => {
    setEvents(prev => [...prev, event])
  }

  const startTask = async () => {
    if (!task.trim() || !selectedAgent) return

    try {
      const response = await taskApi.createTask(selectedAgent.id, task)
      const newSessionId = response.data.sessionId
      setSessionId(newSessionId)
      setEvents([])
      setToolCalls([])
      setStatus({ status: 'RUNNING' })

      // Update URL without reload
      window.history.pushState({}, '', `/tasks/${newSessionId}`)

      // Connect to SSE after a short delay to ensure session is registered
      setTimeout(() => {
        connectSSE()
      }, 500)
    } catch (error) {
      console.error('Failed to start task:', error)
      alert('Failed to start task: ' + error.message)
    }
  }

  const cancelTask = async () => {
    if (!sessionId) return

    try {
      await taskApi.cancelTask(sessionId)
      setStatus({ status: 'CANCELLED' })
      setIsRunning(false)

      if (eventSourceRef.current) {
        eventSourceRef.current.close()
      }
    } catch (error) {
      console.error('Failed to cancel task:', error)
    }
  }

  const viewSession = (id) => {
    setSessionId(id)
    window.history.pushState({}, '', `/tasks/${id}`)
  }

  const startNewTask = () => {
    if (eventSourceRef.current) {
      eventSourceRef.current.close()
    }
    setSessionId(null)
    setStatus(null)
    setEvents([])
    setToolCalls([])
    setMessages([])
    setTask('')
    window.history.pushState({}, '', '/tasks')
  }

  useEffect(() => {
    return () => {
      if (eventSourceRef.current) {
        eventSourceRef.current.close()
      }
    }
  }, [])

  const getStatusColor = (status) => {
    switch (status) {
      case 'RUNNING': return 'text-blue-400'
      case 'WAITING_TOOL': return 'text-yellow-400'
      case 'COMPLETED': return 'text-green-400'
      case 'FAILED': return 'text-red-400'
      case 'CANCELLED': return 'text-gray-400'
      default: return 'text-gray-400'
    }
  }

  const getStatusBadge = (status) => {
    const colors = {
      RUNNING: 'bg-blue-500/20 text-blue-400',
      WAITING_TOOL: 'bg-yellow-500/20 text-yellow-400',
      COMPLETED: 'bg-green-500/20 text-green-400',
      FAILED: 'bg-red-500/20 text-red-400',
      CANCELLED: 'bg-gray-500/20 text-gray-400'
    }
    return colors[status] || 'bg-gray-500/20 text-gray-400'
  }

  return (
    <div className="h-screen flex bg-gray-900">
      {/* Sidebar - Task History */}
      <div className={`${showHistory ? 'w-72' : 'w-0'} flex-shrink-0 transition-all duration-300 overflow-hidden bg-gray-800 border-r border-gray-700`}>
        <div className="w-72 p-4">
          <div className="flex items-center justify-between mb-4">
            <h2 className="text-white font-semibold">Task History</h2>
            <button
              onClick={() => setShowHistory(false)}
              className="text-gray-400 hover:text-white"
            >
              <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
              </svg>
            </button>
          </div>

          <button
            onClick={startNewTask}
            className="w-full mb-4 px-4 py-2 bg-primary-600 text-white rounded-lg hover:bg-primary-700 flex items-center justify-center gap-2"
          >
            <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 4v16m8-8H4" />
            </svg>
            New Task
          </button>

          <div className="space-y-2">
            {taskHistory.map((t) => (
              <button
                key={t.sessionId}
                onClick={() => viewSession(t.sessionId)}
                className={`w-full p-3 rounded-lg text-left transition-colors ${
                  sessionId === t.sessionId ? 'bg-gray-700' : 'bg-gray-700/50 hover:bg-gray-700'
                }`}
              >
                <div className="flex items-center gap-2 mb-1">
                  <span className={`px-2 py-0.5 rounded text-xs ${getStatusBadge(t.status)}`}>
                    {t.status}
                  </span>
                </div>
                <p className="text-gray-300 text-sm truncate">{t.task}</p>
                <p className="text-gray-500 text-xs mt-1">
                  {new Date(t.createdAt).toLocaleString()}
                </p>
              </button>
            ))}
          </div>
        </div>
      </div>

      {/* Main Content */}
      <div className="flex-1 flex flex-col">
        {/* Header */}
        <header className="h-14 bg-gray-800 border-b border-gray-700 flex items-center px-4">
          <button
            onClick={() => setShowHistory(!showHistory)}
            className="mr-4 text-gray-400 hover:text-white"
          >
            <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 6h16M4 12h16M4 18h16" />
            </svg>
          </button>

          <div className="flex-1">
            <h1 className="text-white font-medium">
              {sessionId ? 'Agent Task' : 'New Agent Task'}
            </h1>
          </div>

          <div className="flex items-center gap-4">
            <button
              onClick={() => navigate('/chat')}
              className="text-gray-400 hover:text-white text-sm"
            >
              Chat
            </button>
            <span className="text-gray-400 text-sm">{user?.username}</span>
            <button
              onClick={logout}
              className="text-gray-400 hover:text-white text-sm"
            >
              Logout
            </button>
          </div>
        </header>

        {/* Task Input */}
        {!sessionId && (
          <div className="p-6 border-b border-gray-700 bg-gray-800/50">
            <div className="max-w-4xl mx-auto">
              <h2 className="text-lg font-medium text-white mb-4">Create a New Task</h2>

              {/* Agent Selection */}
              <div className="mb-4">
                <label className="block text-sm text-gray-400 mb-2">Select Agent</label>
                <select
                  value={selectedAgent?.id || ''}
                  onChange={(e) => {
                    const agent = agents.find(a => a.id === parseInt(e.target.value))
                    setSelectedAgent(agent)
                  }}
                  className="w-full px-4 py-2 bg-gray-700 border border-gray-600 rounded-lg text-white focus:outline-none focus:border-primary-500"
                >
                  {agents.map((agent) => (
                    <option key={agent.id} value={agent.id}>
                      {agent.name} ({agent.provider})
                    </option>
                  ))}
                </select>
              </div>

              {/* Task Input */}
              <div className="mb-4">
                <label className="block text-sm text-gray-400 mb-2">Task Description</label>
                <textarea
                  value={task}
                  onChange={(e) => setTask(e.target.value)}
                  placeholder="Describe the task you want the agent to perform..."
                  rows={4}
                  className="w-full px-4 py-2 bg-gray-700 border border-gray-600 rounded-lg text-white placeholder-gray-500 focus:outline-none focus:border-primary-500 resize-none"
                />
              </div>

              <button
                onClick={startTask}
                disabled={!task.trim() || !selectedAgent}
                className="px-6 py-2 bg-primary-600 text-white rounded-lg hover:bg-primary-700 disabled:opacity-50 disabled:cursor-not-allowed"
              >
                Start Task
              </button>
            </div>
          </div>
        )}

        {/* Status Bar */}
        {sessionId && status && (
          <div className="px-6 py-3 bg-gray-800/80 border-b border-gray-700 flex items-center justify-between">
            <div className="flex items-center gap-4">
              <span className={`px-3 py-1 rounded-full text-sm font-medium ${getStatusBadge(status.status)}`}>
                {status.status}
              </span>
              {status.message && (
                <span className="text-gray-400 text-sm">{status.message}</span>
              )}
              {status.iterations && (
                <span className="text-gray-500 text-sm">Iterations: {status.iterations}</span>
              )}
            </div>

            {isRunning && (
              <button
                onClick={cancelTask}
                className="px-4 py-1.5 bg-red-600/20 text-red-400 rounded-lg hover:bg-red-600/30 flex items-center gap-2"
              >
                <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
                </svg>
                Cancel
              </button>
            )}
          </div>
        )}

        {/* Event Log */}
        <div className="flex-1 overflow-y-auto p-6">
          <div className="max-w-4xl mx-auto">
            {/* Current Response */}
            {status?.content && (
              <div className="mb-6 bg-gray-800 rounded-lg p-4">
                <h3 className="text-white font-medium mb-2">Final Response</h3>
                <div className="text-gray-300 whitespace-pre-wrap">{status.content}</div>
              </div>
            )}

            {/* Error Message */}
            {status?.errorMessage && (
              <div className="mb-6 bg-red-900/20 border border-red-800 rounded-lg p-4">
                <h3 className="text-red-400 font-medium mb-2">Error</h3>
                <div className="text-red-300">{status.errorMessage}</div>
              </div>
            )}

            {/* Tool Calls */}
            {toolCalls.length > 0 && (
              <div className="mb-6">
                <h3 className="text-white font-medium mb-3">Tool Calls</h3>
                <div className="space-y-3">
                  {toolCalls.map((tc, index) => (
                    <div
                      key={tc.toolCallId || index}
                      className={`rounded-lg border ${
                        tc.success === false
                          ? 'bg-red-900/20 border-red-800'
                          : tc.result
                            ? 'bg-green-900/20 border-green-800'
                            : 'bg-yellow-900/20 border-yellow-800'
                      }`}
                    >
                      <div className="px-4 py-2 border-b border-gray-700/50 flex items-center justify-between">
                        <div className="flex items-center gap-2">
                          <span className="text-yellow-400">
                            <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M8 9l3 3-3 3m5 0h3M5 20h14a2 2 0 002-2V6a2 2 0 00-2-2H5a2 2 0 00-2 2v12a2 2 0 002 2z" />
                            </svg>
                          </span>
                          <span className="text-white font-mono text-sm">{tc.toolName}</span>
                        </div>
                        {tc.success === false && (
                          <span className="text-red-400 text-xs">Failed</span>
                        )}
                        {tc.result && tc.success !== false && (
                          <span className="text-green-400 text-xs">Completed</span>
                        )}
                        {!tc.result && tc.success !== false && (
                          <span className="text-yellow-400 text-xs animate-pulse">Running...</span>
                        )}
                      </div>
                      <div className="px-4 py-2">
                        <pre className="text-gray-400 text-xs overflow-x-auto">
                          {JSON.stringify(tc.arguments || tc, null, 2)}
                        </pre>
                        {tc.result && (
                          <div className="mt-2 pt-2 border-t border-gray-700/50">
                            <p className="text-gray-500 text-xs mb-1">Result:</p>
                            <pre className="text-gray-300 text-xs overflow-x-auto max-h-48">
                              {tc.result}
                            </pre>
                          </div>
                        )}
                      </div>
                    </div>
                  ))}
                </div>
              </div>
            )}

            {/* Event Stream */}
            {events.length > 0 && (
              <div className="mb-6">
                <h3 className="text-white font-medium mb-3">Event Log</h3>
                <div className="bg-gray-800/50 rounded-lg p-4 space-y-2">
                  {events.map((event, index) => (
                    <div key={index} className="flex items-start gap-3 text-sm">
                      <span className="text-gray-500 text-xs whitespace-nowrap">
                        {new Date(event.time).toLocaleTimeString()}
                      </span>
                      <span className={`px-2 py-0.5 rounded text-xs ${
                        event.type === 'tool_call' ? 'bg-yellow-500/20 text-yellow-400' :
                        event.type === 'tool_result' ? 'bg-green-500/20 text-green-400' :
                        event.type === 'complete' ? 'bg-blue-500/20 text-blue-400' :
                        event.type === 'error' ? 'bg-red-500/20 text-red-400' :
                        'bg-gray-500/20 text-gray-400'
                      }`}>
                        {event.type}
                      </span>
                      <span className="text-gray-400 truncate">
                        {event.data.toolName || event.data.message || event.data.status || ''}
                      </span>
                    </div>
                  ))}
                </div>
              </div>
            )}

            {/* Messages */}
            {messages.length > 0 && (
              <div className="mb-6">
                <h3 className="text-white font-medium mb-3">Messages</h3>
                <div className="space-y-3">
                  {messages.map((msg, index) => (
                    <div
                      key={index}
                      className={`rounded-lg p-4 ${
                        msg.role === 'user'
                          ? 'bg-blue-900/20 border border-blue-800/50 ml-8'
                          : msg.role === 'tool'
                            ? 'bg-yellow-900/20 border border-yellow-800/50 mr-8'
                            : 'bg-gray-800 border border-gray-700 mr-8'
                      }`}
                    >
                      <div className="flex items-center gap-2 mb-2">
                        <span className={`text-sm font-medium ${
                          msg.role === 'user' ? 'text-blue-400' :
                          msg.role === 'tool' ? 'text-yellow-400' :
                          'text-green-400'
                        }`}>
                          {msg.role === 'user' ? 'User' : msg.role === 'tool' ? 'Tool Result' : 'Agent'}
                        </span>
                      </div>
                      <div className="text-gray-300 text-sm whitespace-pre-wrap">
                        {msg.content}
                      </div>
                    </div>
                  ))}
                </div>
              </div>
            )}

            {/* Empty State */}
            {!sessionId && events.length === 0 && toolCalls.length === 0 && (
              <div className="text-center py-12">
                <div className="text-gray-500 mb-4">
                  <svg className="w-16 h-16 mx-auto" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M9.75 17L9 20l-1 1h8l-1-1-.75-3M3 13h18M5 17h14a2 2 0 002-2V5a2 2 0 00-2-2H5a2 2 0 00-2 2v10a2 2 0 002 2z" />
                  </svg>
                </div>
                <h3 className="text-lg font-medium text-gray-400 mb-2">Agent Task System</h3>
                <p className="text-gray-500">
                  Create a new task above to start an agent session.
                  The agent can use tools like bash, file operations, and more.
                </p>
              </div>
            )}

            <div ref={messagesEndRef} />
          </div>
        </div>
      </div>
    </div>
  )
}
