import { useState, useEffect, useRef } from 'react'
import ReactMarkdown from 'react-markdown'
import remarkGfm from 'remark-gfm'
import CodeBlock from './CodeBlock'
import ArtifactRenderer from './ArtifactCards/ArtifactRenderer'
import { useMessageStore } from '../stores/messageStore'
import { messageApi } from '../api/agenthub'
import { AgentAvatar } from './AgentStatusSidebar'

// Quote/reply modal
function QuoteModal({ message, onClose, onSend }) {
  const [quoteText, setQuoteText] = useState('')

  const handleSend = () => {
    onSend(quoteText)
    onClose()
  }

  return (
    <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50">
      <div className="bg-gray-800 rounded-lg p-6 w-full max-w-lg mx-4">
        <div className="flex justify-between items-center mb-4">
          <h3 className="text-lg font-bold text-white">回复 / 引用</h3>
          <button onClick={onClose} className="text-gray-400 hover:text-white">
            <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
            </svg>
          </button>
        </div>

        {/* Quoted message */}
        <div className="bg-gray-700 rounded p-3 mb-4 border-l-4 border-blue-500">
          <p className="text-gray-400 text-sm mb-1">{message.senderName}</p>
          <p className="text-gray-300 text-sm line-clamp-3">{message.content}</p>
        </div>

        {/* Reply input */}
        <textarea
          value={quoteText}
          onChange={(e) => setQuoteText(e.target.value)}
          placeholder="输入你的回复..."
          className="w-full px-4 py-3 bg-gray-700 border border-gray-600 rounded text-white placeholder-gray-400 focus:outline-none focus:border-primary-500 resize-none"
          rows={4}
        />

        <div className="flex justify-end gap-3 mt-4">
          <button
            onClick={onClose}
            className="px-4 py-2 text-gray-400 hover:text-white transition"
          >
            取消
          </button>
          <button
            onClick={handleSend}
            disabled={!quoteText.trim()}
            className="px-4 py-2 bg-primary-600 text-white rounded hover:bg-primary-700 disabled:opacity-50 transition"
          >
            发送
          </button>
        </div>
      </div>
    </div>
  )
}

// Version history modal
function VersionHistoryModal({ message, onClose }) {
  const [versions, setVersions] = useState([])
  const [loading, setLoading] = useState(true)
  const [selectedVersion, setSelectedVersion] = useState(null)

  useEffect(() => {
    loadVersions()
  }, [message.id])

  const loadVersions = async () => {
    try {
      const response = await messageApi.getMessageVersions(message.id)
      setVersions(response.data || [])
    } catch (error) {
      console.error('Failed to load versions:', error)
    } finally {
      setLoading(false)
    }
  }

  const handleRestore = async (version) => {
    try {
      await messageApi.saveMessageVersion(message.id, version.content)
      alert('Version restored! Please refresh to see changes.')
      onClose()
    } catch (error) {
      console.error('Failed to restore version:', error)
      alert('Failed to restore version')
    }
  }

  const formatDate = (dateStr) => {
    return new Date(dateStr).toLocaleString()
  }

  return (
    <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50">
      <div className="bg-gray-800 rounded-lg p-6 w-full max-w-lg mx-4 max-h-[80vh] overflow-hidden flex flex-col">
        <div className="flex justify-between items-center mb-4">
          <h3 className="text-lg font-bold text-white">Version History</h3>
          <button onClick={onClose} className="text-gray-400 hover:text-white">
            <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
            </svg>
          </button>
        </div>

        {loading ? (
          <div className="text-gray-400 text-center py-8">Loading...</div>
        ) : versions.length === 0 ? (
          <div className="text-gray-400 text-center py-8">No versions yet</div>
        ) : (
          <div className="flex-1 overflow-y-auto space-y-3">
            <div
              className="p-3 bg-gray-700 rounded-lg cursor-pointer border-2 border-primary-500"
              onClick={() => setSelectedVersion(null)}
            >
              <div className="flex justify-between items-start">
                <div>
                  <div className="text-white font-medium">Current Version</div>
                  <div className="text-gray-400 text-xs mt-1">{formatDate(message.createdAt)}</div>
                </div>
                <span className="text-xs text-primary-400 bg-primary-900/30 px-2 py-0.5 rounded">Current</span>
              </div>
              <div className="text-gray-300 text-sm mt-2 line-clamp-3">{message.content}</div>
            </div>

            {versions.map((version) => (
              <div
                key={version.id}
                className={`p-3 bg-gray-700 rounded-lg cursor-pointer border-2 ${
                  selectedVersion?.id === version.id ? 'border-blue-500' : 'border-transparent hover:border-gray-600'
                }`}
                onClick={() => setSelectedVersion(version)}
              >
                <div className="flex justify-between items-start">
                  <div>
                    <div className="text-white font-medium">Version {version.versionNumber}</div>
                    <div className="text-gray-400 text-xs mt-1">{formatDate(version.createdAt)}</div>
                  </div>
                  {selectedVersion?.id === version.id && (
                    <button
                      onClick={(e) => {
                        e.stopPropagation()
                        handleRestore(version)
                      }}
                      className="text-xs bg-blue-600 hover:bg-blue-700 text-white px-2 py-1 rounded"
                    >
                      Restore
                    </button>
                  )}
                </div>
                <div className="text-gray-300 text-sm mt-2 line-clamp-3">{version.content}</div>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  )
}

// Expand preview modal
function ExpandPreviewModal({ block, onClose }) {
  return (
    <div className="fixed inset-0 bg-black/90 flex items-center justify-center z-50 p-8" onClick={onClose}>
      <button
        className="absolute top-4 right-4 p-2 text-white hover:text-gray-300 transition"
        onClick={onClose}
      >
        <svg className="w-8 h-8" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
        </svg>
      </button>
      <div className="max-w-6xl max-h-full overflow-auto bg-gray-800 rounded-lg" onClick={(e) => e.stopPropagation()}>
        <ArtifactRenderer block={block} messageId={null} />
      </div>
    </div>
  )
}

// Tool call card
function ToolCallCard({ toolCall, result }) {
  const isPending = result === null || result === undefined
  const isSuccess = result?.success !== false
  const toolName = toolCall.tool || toolCall.name || 'unknown'
  const args = toolCall.arguments || toolCall.input || {}

  return (
    <div className={`mt-2 p-3 rounded-md border ${
      isPending ? 'border-yellow-500/50 bg-yellow-500/10' :
      isSuccess ? 'border-green-500/50 bg-green-500/10' :
      'border-red-500/50 bg-red-500/10'
    }`}>
      <div className="flex items-center gap-2 mb-1">
        <span className={`text-xs px-2 py-0.5 rounded ${
          isPending ? 'bg-yellow-500/30 text-yellow-300' :
          isSuccess ? 'bg-green-500/30 text-green-300' :
          'bg-red-500/30 text-red-300'
        }`}>
          {isPending ? 'Running' : isSuccess ? 'Success' : 'Error'}
        </span>
        <span className="text-sm font-mono text-purple-400">{toolName}</span>
      </div>

      {args && Object.keys(args).length > 0 && (
        <div className="text-xs text-gray-400 mb-2">
          <span className="font-mono">{JSON.stringify(args, null, 2)}</span>
        </div>
      )}

      {result && (
        <div className={`text-xs font-mono mt-2 p-2 rounded ${
          isSuccess ? 'bg-black/20 text-gray-300' : 'bg-red-900/30 text-red-300'
        }`}>
          <pre className="whitespace-pre-wrap break-all max-h-32 overflow-auto">
            {isSuccess ? result.result || 'OK' : result.error || 'Unknown error'}
          </pre>
        </div>
      )}
    </div>
  )
}

// Message actions menu
function MessageActionsMenu({ message, onReply, onQuote, onRegenerate, onApplyDiff, onExpand, onCopy }) {
  const [isOpen, setIsOpen] = useState(false)
  const menuRef = useRef(null)

  useEffect(() => {
    const handleClickOutside = (e) => {
      if (menuRef.current && !menuRef.current.contains(e.target)) {
        setIsOpen(false)
      }
    }
    if (isOpen) {
      document.addEventListener('mousedown', handleClickOutside)
    }
    return () => document.removeEventListener('mousedown', handleClickOutside)
  }, [isOpen])

  const hasDiff = message.blocks?.some(b => b.blockType === 'DIFF')
  const hasExpandable = message.blocks?.some(b => ['IMAGE', 'WEB_PREVIEW', 'DEPLOY_STATUS'].includes(b.blockType))

  const menuItems = [
    { label: '回复', icon: '💬', action: onReply, show: true },
    { label: '引用', icon: '📝', action: onQuote, show: true },
    { label: '重新生成', icon: '🔄', action: onRegenerate, show: message.senderType !== 'USER' },
    { label: '复制内容', icon: '📋', action: () => { navigator.clipboard.writeText(message.content); setIsOpen(false) }, show: true },
    { label: '一键应用Diff', icon: '✅', action: onApplyDiff, show: hasDiff },
    { label: '展开预览', icon: '🔍', action: onExpand, show: hasExpandable },
  ].filter(item => item.show)

  return (
    <div className="relative" ref={menuRef}>
      <button
        onClick={(e) => { e.stopPropagation(); setIsOpen(!isOpen) }}
        className="p-1 rounded hover:bg-gray-600 transition"
      >
        <svg className="w-5 h-5 text-gray-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 5v.01M12 12v.01M12 19v.01M12 6a1 1 0 110-2 1 1 0 010 2zm0 7a1 1 0 110-2 1 1 0 010 2zm0 7a1 1 0 110-2 1 1 0 010 2z" />
        </svg>
      </button>

      {isOpen && (
        <div className="absolute right-0 top-full mt-1 bg-gray-700 rounded-lg shadow-xl border border-gray-600 py-1 min-w-[160px] z-50">
          {menuItems.map((item, idx) => (
            <button
              key={idx}
              onClick={(e) => { e.stopPropagation(); item.action?.(); setIsOpen(false) }}
              className="w-full px-4 py-2 text-left text-sm text-gray-200 hover:bg-gray-600 flex items-center gap-2 transition"
            >
              <span>{item.icon}</span>
              <span>{item.label}</span>
            </button>
          ))}
        </div>
      )}
    </div>
  )
}

// Highlight @mentions
function highlightMentions(children) {
  if (typeof children === 'string') {
    const mentionPattern = /@(\w+)/g
    const parts = children.split(mentionPattern)
    if (parts.length === 1) return children

    return parts.map((part, i) => {
      if (i % 2 === 1) {
        return <span key={i} className="text-primary-400 font-medium">@{part}</span>
      }
      return part
    })
  }
  return children
}

export default function MessageItem({ message, onPinMessage, onReply, onRegenerate }) {
  const isUser = message.senderType === 'USER'
  const toolCalls = useMessageStore(state => state.toolCallsInProgress.get(message.id) || [])
  const [showHistory, setShowHistory] = useState(false)
  const [showQuoteModal, setShowQuoteModal] = useState(false)
  const [expandBlock, setExpandBlock] = useState(null)

  const formatTime = (dateStr) => {
    const date = new Date(dateStr)
    return date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
  }

  const handleQuote = () => {
    setShowQuoteModal(true)
  }

  const handleApplyDiff = async () => {
    const diffBlock = message.blocks?.find(b => b.blockType === 'DIFF')
    if (!diffBlock) return

    try {
      // Apply diff logic - would need backend support
      alert('Diff apply functionality requires backend implementation')
    } catch (err) {
      console.error('Failed to apply diff:', err)
    }
  }

  const handleExpand = () => {
    const expandableBlock = message.blocks?.find(b => ['IMAGE', 'WEB_PREVIEW', 'DEPLOY_STATUS'].includes(b.blockType))
    if (expandableBlock) {
      setExpandBlock(expandableBlock)
    }
  }

  return (
    <div
      className={`flex ${isUser ? 'justify-end' : 'justify-start'}`}
    >
      {showHistory && (
        <VersionHistoryModal message={message} onClose={() => setShowHistory(false)} />
      )}
      {showQuoteModal && (
        <QuoteModal
          message={message}
          onClose={() => setShowQuoteModal(false)}
          onSend={(text) => {
            onReply?.(message.id, text)
            setShowQuoteModal(false)
          }}
        />
      )}
      {expandBlock && (
        <ExpandPreviewModal block={expandBlock} onClose={() => setExpandBlock(null)} />
      )}

      <div className={`max-w-[75%] ${isUser ? 'order-2' : 'order-1'}`}>
        {/* Message header */}
        <div className="flex items-center gap-2 mb-1 px-1">
          {!isUser && (
            <>
              <AgentAvatar agent={{ name: message.senderName }} size="sm" showStatus={false} />
              <span className="text-sm font-medium text-primary-400">{message.senderName}</span>
            </>
          )}
          {isUser && (
            <span className="text-xs text-gray-500 ml-auto">{formatTime(message.createdAt)}</span>
          )}
          {!isUser && <span className="text-xs text-gray-500">{formatTime(message.createdAt)}</span>}

          {/* Actions menu */}
          <div className="ml-auto">
            <MessageActionsMenu
              message={message}
              onReply={() => setShowQuoteModal(true)}
              onQuote={handleQuote}
              onRegenerate={() => onRegenerate?.(message.id)}
              onApplyDiff={handleApplyDiff}
              onExpand={handleExpand}
              onCopy={() => navigator.clipboard.writeText(message.content)}
            />
          </div>

          {/* Pin button */}
          {!isUser && onPinMessage && (
            <button
              onClick={() => onPinMessage(message.id, !message.pinned)}
              className="p-1 rounded hover:bg-gray-600 transition"
              title={message.pinned ? 'Unpin' : 'Pin'}
            >
              <svg
                className={`w-4 h-4 ${message.pinned ? 'text-blue-400' : 'text-gray-500'}`}
                fill={message.pinned ? 'currentColor' : 'none'}
                stroke="currentColor"
                viewBox="0 0 24 24"
              >
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 5a2 2 0 012-2h10a2 2 0 012 2v16l-7-3.5L5 21V5z" />
              </svg>
            </button>
          )}

          {/* Version history */}
          {!isUser && (
            <button
              onClick={() => setShowHistory(true)}
              className="p-1 rounded hover:bg-gray-600 transition"
              title="Version history"
            >
              <svg className="w-4 h-4 text-gray-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z" />
              </svg>
            </button>
          )}
        </div>

        {/* Message bubble */}
        <div
          className={`rounded-lg px-4 py-3 ${
            isUser ? 'bg-primary-600 text-white' : 'bg-gray-800 text-gray-200'
          }`}
        >
          <div className="chat-message-content">
            <ReactMarkdown
              remarkPlugins={[remarkGfm]}
              components={{
                code({ node, inline, className, children, ...props }) {
                  const match = /language-(\w+)/.exec(className || '')
                  if (!inline && match) {
                    return (
                      <CodeBlock language={match[1]} code={String(children).replace(/\n$/, '')} />
                    )
                  }
                  return <code className={className} {...props}>{children}</code>
                },
                pre({ children }) {
                  return <>{children}</>
                },
                p({ children }) {
                  return <p>{highlightMentions(children)}</p>
                }
              }}
            >
              {message.content}
            </ReactMarkdown>
          </div>

          {/* Tool calls */}
          {toolCalls.length > 0 && (
            <div className="mt-3 space-y-2">
              {toolCalls.map((item, idx) => (
                <ToolCallCard
                  key={item.toolCall.id || item.toolCall.callId || idx}
                  toolCall={item.toolCall}
                  result={item.result}
                />
              ))}
            </div>
          )}

          {/* Artifact blocks */}
          {message.blocks && message.blocks.length > 0 && (
            <div className="mt-3 space-y-2">
              {message.blocks.map((block, idx) => (
                <div key={idx} className="relative group">
                  <ArtifactRenderer block={block} messageId={message.id} />
                  {/* Expand button for certain block types */}
                  {['IMAGE', 'WEB_PREVIEW', 'DEPLOY_STATUS'].includes(block.blockType) && (
                    <button
                      onClick={() => setExpandBlock(block)}
                      className="absolute top-2 right-2 p-1 bg-gray-900/80 rounded opacity-0 group-hover:opacity-100 transition"
                      title="Expand"
                    >
                      <svg className="w-4 h-4 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0zM10 7v6m3-3H7" />
                      </svg>
                    </button>
                  )}
                </div>
              ))}
            </div>
          )}
        </div>
      </div>
    </div>
  )
}
