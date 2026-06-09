import { useState, useRef, useEffect } from 'react'
import MentionAutocomplete from './MentionAutocomplete'

export default function MessageInput({ onSend, disabled, agents = [] }) {
  const [content, setContent] = useState('')
  const [loading, setLoading] = useState(false)
  const [showMentionAutocomplete, setShowMentionAutocomplete] = useState(false)
  const [mentionQuery, setMentionQuery] = useState('')
  const [mentionPosition, setMentionPosition] = useState({ bottom: 0, left: 0 })
  const inputRef = useRef(null)
  const textareaRef = useRef(null)

  const handleSubmit = async (e) => {
    e.preventDefault()
    if (!content.trim() || loading || disabled) return

    setLoading(true)
    try {
      await onSend(content.trim())
      setContent('')
    } catch (error) {
      console.error('Failed to send message:', error)
    } finally {
      setLoading(false)
      inputRef.current?.focus()
    }
  }

  const handleKeyDown = (e) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault()
      handleSubmit(e)
    }
  }

  const handleChange = (e) => {
    const value = e.target.value
    setContent(value)

    // Check for @ mention trigger
    const cursorPos = e.target.selectionStart
    const textBeforeCursor = value.substring(0, cursorPos)
    const lastAtIndex = textBeforeCursor.lastIndexOf('@')

    if (lastAtIndex !== -1) {
      const textAfterAt = textBeforeCursor.substring(lastAtIndex + 1)
      // Only trigger if there's no space after @
      if (!textAfterAt.includes(' ') && textAfterAt.length < 20) {
        setMentionQuery(textAfterAt)

        // Calculate position
        if (textareaRef.current) {
          const rect = textareaRef.current.getBoundingClientRect()
          setMentionPosition({
            bottom: window.innerHeight - rect.top + 8,
            left: 16
          })
        }
        setShowMentionAutocomplete(true)
        return
      }
    }

    setShowMentionAutocomplete(false)
  }

  const handleMentionSelect = (agent) => {
    if (!agent) {
      setShowMentionAutocomplete(false)
      return
    }

    const cursorPos = inputRef.current.selectionStart
    const textBeforeCursor = content.substring(0, cursorPos)
    const lastAtIndex = textBeforeCursor.lastIndexOf('@')
    const textAfterAt = content.substring(lastAtIndex, cursorPos)

    // Replace the @query with @AgentName
    const newContent = content.substring(0, lastAtIndex) + '@' + agent.name + ' ' + content.substring(cursorPos)
    setContent(newContent)
    setShowMentionAutocomplete(false)

    // Set cursor position after agent name
    setTimeout(() => {
      const newCursorPos = lastAtIndex + agent.name.length + 2
      inputRef.current.setSelectionRange(newCursorPos, newCursorPos)
      inputRef.current.focus()
    }, 0)
  }

  return (
    <div className="border-t border-gray-700 p-4 bg-gray-800 relative">
      <form onSubmit={handleSubmit} className="flex items-center gap-3">
        <textarea
          ref={(el) => {
            inputRef.current = el
            textareaRef.current = el
          }}
          value={content}
          onChange={handleChange}
          onKeyDown={handleKeyDown}
          placeholder="Type your message... (Enter to send, Shift+Enter for new line, @ to mention agent)"
          className="flex-1 px-4 py-3 bg-gray-700 border border-gray-600 rounded-lg text-white placeholder-gray-400 focus:outline-none focus:border-primary-500 resize-none"
          rows={1}
          disabled={loading || disabled}
          style={{ minHeight: '48px', maxHeight: '120px' }}
        />
        <button
          type="submit"
          disabled={!content.trim() || loading || disabled}
          className="px-6 py-3 bg-primary-600 hover:bg-primary-700 text-white font-medium rounded-lg transition disabled:opacity-50 disabled:cursor-not-allowed"
        >
          {loading ? (
            <span className="flex items-center gap-2">
              <svg className="animate-spin w-5 h-5" viewBox="0 0 24 24">
                <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" fill="none" />
                <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
              </svg>
            </span>
          ) : (
            <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 19l9 2-9-18-9 18 9-2zm0 0v-8" />
            </svg>
          )}
        </button>
      </form>

      {showMentionAutocomplete && (
        <MentionAutocomplete
          query={mentionQuery}
          agents={agents}
          onSelect={handleMentionSelect}
          position={mentionPosition}
        />
      )}
    </div>
  )
}
