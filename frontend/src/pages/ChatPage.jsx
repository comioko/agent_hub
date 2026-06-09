import { useEffect, useState, useRef, useCallback } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAuthStore } from '../stores/authStore'
import { useSessionStore } from '../stores/sessionStore'
import { useMessageStore } from '../stores/messageStore'
import { agentApi } from '../api/agenthub'
import { useSSE } from '../hooks/useSSE'
import SessionList from '../components/SessionList'
import MessageList from '../components/MessageList'
import MessageInput from '../components/MessageInput'
import NewSessionModal from '../components/NewSessionModal'
import AgentBadge from '../components/AgentBadge'

export default function ChatPage() {
  const navigate = useNavigate()
  const { user, token, logout } = useAuthStore()
  const { sessions, currentSession, fetchSessions, setCurrentSession } = useSessionStore()
  const { messages, fetchMessages, addMessage, updateMessage, addToolCall, updateToolCallResult, clearToolCalls } = useMessageStore()
  const [agents, setAgents] = useState([])
  const [showNewSession, setShowNewSession] = useState(false)
  const [showSidebar, setShowSidebar] = useState(true)
  const [isStreaming, setIsStreaming] = useState(false)
  const [showScrollButton, setShowScrollButton] = useState(false)
  const messageListRef = useRef(null)
  const streamingMessageIdRef = useRef(null)
  const lastMessageCountRef = useRef(0)

  useEffect(() => {
    if (!token) {
      navigate('/login')
      return
    }
    loadInitialData()
  }, [token, navigate])

  useEffect(() => {
    if (currentSession) {
      loadMessages(currentSession.id)
      setIsStreaming(false)
      streamingMessageIdRef.current = null
    }
  }, [currentSession])

  // Scroll to keep the streaming message visible at top area during generation
  const scrollToStreamingMessage = useCallback((messageId) => {
    if (!messageListRef.current) return
    const container = messageListRef.current
    const messageEl = container.querySelector(`[data-message-id="${messageId}"]`)
    if (messageEl) {
      const containerRect = container.getBoundingClientRect()
      const targetTop = containerRect.top + 80 // Position 80px from top of container
      const messageRect = messageEl.getBoundingClientRect()
      const currentTop = messageRect.top - containerRect.top + container.scrollTop
      const scrollNeeded = currentTop - targetTop
      container.scrollBy({ top: scrollNeeded, behavior: 'auto' })
    }
  }, [])

  // Handle auto-scroll during streaming
  useEffect(() => {
    if (!isStreaming || !streamingMessageIdRef.current) return

    const messageId = streamingMessageIdRef.current
    const container = messageListRef.current
    if (!container) return

    const messageEl = container.querySelector(`[data-message-id="${messageId}"]`)
    if (!messageEl) return

    // Check if message is still in view
    const containerRect = container.getBoundingClientRect()
    const messageRect = messageEl.getBoundingClientRect()
    const isVisible = messageRect.bottom > containerRect.top && messageRect.top < containerRect.bottom

    if (!isVisible) {
      scrollToStreamingMessage(messageId)
    }
  }, [messages, isStreaming, scrollToStreamingMessage])

  // Detect user scroll - disable auto-scroll if user scrolls up
  const handleScroll = useCallback(() => {
    if (!messageListRef.current) return
    const { scrollTop, scrollHeight, clientHeight } = messageListRef.current
    const isAtBottom = scrollHeight - scrollTop - clientHeight < 100
    const hasMessages = messages.length > 0

    // Only show scroll button if not streaming and not at bottom
    setShowScrollButton(!isAtBottom && !isStreaming && hasMessages)
  }, [messages, isStreaming])

  const scrollToBottom = useCallback(() => {
    if (!messageListRef.current) return
    const container = messageListRef.current
    container.scrollTo({ top: container.scrollHeight, behavior: 'smooth' })
    setShowScrollButton(false)
  }, [])

  const handleSSEMessage = useCallback((event) => {
    if (!event || !event.type) return

    const { type, data } = event
    const currentMessages = useMessageStore.getState().messages

    if (type === 'streaming') {
      setIsStreaming(true)

      // Check if this is a new streaming message (first chunk)
      const isFirstChunk = !streamingMessageIdRef.current || streamingMessageIdRef.current !== data.id
      if (isFirstChunk) {
        streamingMessageIdRef.current = data.id
      }

      // Streaming update - update existing agent message or add if first chunk
      const exists = currentMessages.some(m => m.id === data.id)
      if (exists) {
        updateMessage(data.id, { content: data.content })
      } else {
        addMessage(data)
        // On first chunk, scroll to position the new message at top area
        if (isFirstChunk) {
          setTimeout(() => scrollToStreamingMessage(data.id), 50)
        }
      }
    } else if (type === 'complete') {
      // Streaming finished
      setIsStreaming(false)
      streamingMessageIdRef.current = null
      // Final completion event - clear tool calls
      if (data && data.senderType === 'AGENT') {
        updateMessage(data.id, { content: data.content })
        clearToolCalls(data.id)
      }
    } else if (type === 'message') {
      // Regular message event
      if (data && data.senderType === 'AGENT') {
        addMessage(data)
      }
    } else if (type === 'tool_call') {
      // Tool call event - add to store
      if (data && data.callId) {
        const messageId = data.messageId || currentMessages[currentMessages.length - 1]?.id
        if (messageId) {
          addToolCall(messageId, data)
        }
      }
    } else if (type === 'tool_result') {
      // Tool result event - update store
      if (data && data.callId) {
        const messageId = data.messageId || currentMessages[currentMessages.length - 1]?.id
        if (messageId) {
          updateToolCallResult(messageId, data.callId, data)
        }
      }
    }
  }, [updateMessage, addMessage, clearToolCalls, addToolCall, updateToolCallResult, scrollToStreamingMessage])

  const loadInitialData = async () => {
    await Promise.all([fetchSessions(), loadAgents()])
  }

  const loadAgents = async () => {
    try {
      const response = await agentApi.getAgents()
      setAgents(response.data || [])
    } catch (error) {
      console.error('Failed to load agents:', error)
    }
  }

  const loadMessages = async (conversationId) => {
    try {
      await fetchMessages(conversationId)
    } catch (error) {
      console.error('Failed to load messages:', error)
    }
  }

  const handleNewSession = async (agentIds, agentId, title) => {
    try {
      const newSession = await useSessionStore.getState().createSession(agentIds, agentId, title)
      setShowNewSession(false)
    } catch (error) {
      console.error('Failed to create session:', error)
    }
  }

  const handleSendMessage = async (content) => {
    if (!currentSession) return
    try {
      await useMessageStore.getState().sendMessage(currentSession.id, content, null)
    } catch (error) {
      console.error('Failed to send message:', error)
    }
  }

  const { connect, disconnect, isConnected } = useSSE(handleSSEMessage)

  useEffect(() => {
    if (currentSession) {
      connect()
    }
    return () => disconnect()
  }, [currentSession])

  useEffect(() => {
    if (!isConnected && currentSession) {
      console.log('SSE not connected, attempting to reconnect...')
      connect()
    }
  }, [isConnected, currentSession, connect])

  const handleLogout = () => {
    logout()
    navigate('/login')
  }

  return (
    <div className="h-screen flex bg-gray-900">
      <div className={`${showSidebar ? 'w-64' : 'w-0'} flex-shrink-0 transition-all duration-300 overflow-hidden`}>
        <SessionList
          sessions={sessions}
          currentSession={currentSession}
          onSelectSession={setCurrentSession}
          onNewSession={() => setShowNewSession(true)}
          onDeleteSession={useSessionStore.getState().deleteSession}
          onPinSession={useSessionStore.getState().pinSession}
          onArchiveSession={useSessionStore.getState().archiveSession}
        />
      </div>

      <div className="flex-1 flex flex-col">
        <header className="h-14 bg-gray-800 border-b border-gray-700 flex items-center px-4">
          <button
            onClick={() => setShowSidebar(!showSidebar)}
            className="mr-4 text-gray-400 hover:text-white"
          >
            <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 6h16M4 12h16M4 18h16" />
            </svg>
          </button>

          <div className="flex-1">
            {currentSession ? (
              <h1 className="text-white font-medium">{currentSession.title}</h1>
            ) : (
              <h1 className="text-white font-medium">AgentHub</h1>
            )}
          </div>

          <div className="flex items-center gap-4">
            <button
              onClick={() => navigate('/my-agents')}
              className="text-gray-400 hover:text-white text-sm"
            >
              My Agents
            </button>
            <button
              onClick={() => navigate('/tasks')}
              className="text-gray-400 hover:text-white text-sm flex items-center gap-1"
            >
              <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9.75 17L9 20l-1 1h8l-1-1-.75-3M3 13h18M5 17h14a2 2 0 002-2V5a2 2 0 00-2-2H5a2 2 0 00-2 2v10a2 2 0 002 2z" />
              </svg>
              Agent Tasks
            </button>
            <span className="text-gray-400 text-sm">{user?.username}</span>
            <button
              onClick={handleLogout}
              className="text-gray-400 hover:text-white text-sm"
            >
              Logout
            </button>
          </div>
        </header>

        <div className="flex-1 overflow-hidden relative">
          {currentSession ? (
            <div className="h-full flex flex-col">
              <div
                ref={messageListRef}
                onScroll={handleScroll}
                className="flex-1 overflow-y-auto"
              >
                <MessageList messages={messages} onPinMessage={useMessageStore.getState().pinMessage} />
              </div>
              {showScrollButton && (
                <button
                  onClick={scrollToBottom}
                  className="absolute bottom-20 right-6 px-3 py-2 bg-primary-600 text-white rounded-full shadow-lg flex items-center gap-2 hover:bg-primary-700 transition-colors"
                >
                  <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 14l-7 7m0 0l-7-7m7 7V3" />
                  </svg>
                  <span className="text-sm">回到底部</span>
                </button>
              )}
              <MessageInput onSend={handleSendMessage} agents={agents} />
            </div>
          ) : (
            <div className="h-full flex items-center justify-center text-gray-500">
              <div className="text-center">
                <p className="text-xl mb-4">Welcome to AgentHub</p>
                <button
                  onClick={() => setShowNewSession(true)}
                  className="px-4 py-2 bg-primary-600 text-white rounded hover:bg-primary-700"
                >
                  Start a new conversation
                </button>
              </div>
            </div>
          )}
        </div>
      </div>

      {showNewSession && (
        <NewSessionModal
          agents={agents}
          onClose={() => setShowNewSession(false)}
          onSubmit={handleNewSession}
        />
      )}
    </div>
  )
}
