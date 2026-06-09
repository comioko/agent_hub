import { useState } from 'react'
import { sessionApi } from '../api/agenthub'

export default function SessionList({ sessions, currentSession, onSelectSession, onNewSession, onDeleteSession, onPinSession, onArchiveSession }) {
  const [searchKeyword, setSearchKeyword] = useState('')
  const [searchResults, setSearchResults] = useState(null)
  const [isSearching, setIsSearching] = useState(false)

  const formatDate = (dateStr) => {
    const date = new Date(dateStr)
    const now = new Date()
    const diff = now - date
    const days = Math.floor(diff / (1000 * 60 * 60 * 24))

    if (days === 0) return 'Today'
    if (days === 1) return 'Yesterday'
    if (days < 7) return `${days} days ago`
    return date.toLocaleDateString()
  }

  const handleSearch = async (e) => {
    const keyword = e.target.value
    setSearchKeyword(keyword)

    if (!keyword.trim()) {
      setSearchResults(null)
      return
    }

    setIsSearching(true)
    try {
      const response = await sessionApi.searchConversations(keyword)
      setSearchResults(response.data || [])
    } catch (error) {
      console.error('Search failed:', error)
      setSearchResults([])
    } finally {
      setIsSearching(false)
    }
  }

  const clearSearch = () => {
    setSearchKeyword('')
    setSearchResults(null)
  }

  const displayedSessions = searchResults !== null ? searchResults : sessions

  return (
    <div className="h-full bg-gray-800 flex flex-col">
      <div className="p-4 border-b border-gray-700 space-y-3">
        <button
          onClick={onNewSession}
          className="w-full py-2 px-4 bg-primary-600 hover:bg-primary-700 text-white font-medium rounded transition flex items-center justify-center gap-2"
        >
          <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 4v16m8-8H4" />
          </svg>
          New Chat
        </button>

        {/* Search input */}
        <div className="relative">
          <input
            type="text"
            value={searchKeyword}
            onChange={handleSearch}
            placeholder="Search conversations..."
            className="w-full px-3 py-2 bg-gray-700 border border-gray-600 rounded text-white text-sm placeholder-gray-400 focus:outline-none focus:border-primary-500"
          />
          {searchKeyword && (
            <button
              onClick={clearSearch}
              className="absolute right-2 top-1/2 -translate-y-1/2 text-gray-400 hover:text-white"
            >
              <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
              </svg>
            </button>
          )}
        </div>
      </div>

      <div className="flex-1 overflow-y-auto">
        {searchResults !== null && searchResults.length === 0 ? (
          <div className="p-4 text-gray-500 text-center text-sm">
            No conversations found
          </div>
        ) : displayedSessions.length === 0 ? (
          <div className="p-4 text-gray-500 text-center text-sm">
            No conversations yet
          </div>
        ) : (
          <div className="py-2">
            {displayedSessions.map((session) => (
              <div
                key={session.id}
                onClick={() => onSelectSession(session)}
                className={`px-4 py-3 cursor-pointer group flex items-center justify-between ${
                  currentSession?.id === session.id ? 'bg-gray-700' : ''
                } ${session.pinned ? 'bg-blue-900/20' : ''}`}
              >
                <div className="flex items-center gap-2 flex-1 min-w-0">
                  {session.pinned && (
                    <svg className="w-4 h-4 text-blue-400 flex-shrink-0" fill="currentColor" viewBox="0 0 20 20">
                      <path d="M10.707 2.293a1 1 0 00-1.414 0l-7 7a1 1 0 001.414 1.414L4 10.414V17a1 1 0 001 1h2a1 1 0 001-1v-2a1 1 0 011-1h2a1 1 0 011 1v2a1 1 0 001 1h2a1 1 0 001-1v-6.586l.293.293a1 1 0 001.414-1.414l-7-7z" />
                    </svg>
                  )}
                  <div className="flex items-center gap-1 flex-1 min-w-0">
                    {session.type === 'GROUP' ? (
                      <svg className="w-4 h-4 text-purple-400 flex-shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M17 20h5v-2a3 3 0 00-5.356-1.857M17 20H7m10 0v-2c0-.656-.126-1.283-.356-1.857M7 20H2v-2a3 3 0 015.356-1.857M7 20v-2c0-.656.126-1.283.356-1.857m0 0a5.002 5.002 0 019.288 0M15 7a3 3 0 11-6 0 3 3 0 016 0zm6 3a2 2 0 11-4 0 2 2 0 014 0zM7 10a2 2 0 11-4 0 2 2 0 014 0z" />
                      </svg>
                    ) : (
                      <svg className="w-4 h-4 text-gray-500 flex-shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M8 12h.01M12 12h.01M16 12h.01M21 12c0 4.418-4.03 8-9 8a9.863 9.863 0 01-4.255-.949L3 20l1.395-3.72C3.512 15.042 3 13.574 3 12c0-4.418 4.03-8 9-8s9 3.582 9 8z" />
                      </svg>
                    )}
                    <div className="flex-1 min-w-0">
                      <div className="text-white text-sm truncate">{session.title}</div>
                      <div className="text-gray-500 text-xs mt-1">{formatDate(session.updatedAt)}</div>
                    </div>
                  </div>
                </div>

                {/* Action buttons */}
                <div className="flex items-center gap-1 opacity-0 group-hover:opacity-100">
                  {/* Pin/Unpin button */}
                  <button
                    onClick={(e) => {
                      e.stopPropagation()
                      onPinSession(session.id, !session.pinned)
                    }}
                    className={`p-1.5 rounded hover:bg-gray-600 ${session.pinned ? 'text-blue-400' : 'text-gray-400'}`}
                    title={session.pinned ? 'Unpin' : 'Pin to top'}
                  >
                    <svg className="w-4 h-4" fill="currentColor" viewBox="0 0 20 20">
                      <path d="M10.707 2.293a1 1 0 00-1.414 0l-7 7a1 1 0 001.414 1.414L4 10.414V17a1 1 0 001 1h2a1 1 0 001-1v-2a1 1 0 011-1h2a1 1 0 011 1v2a1 1 0 001 1h2a1 1 0 001-1v-6.586l.293.293a1 1 0 001.414-1.414l-7-7z" />
                    </svg>
                  </button>

                  {/* Archive button */}
                  <button
                    onClick={(e) => {
                      e.stopPropagation()
                      if (confirm('Archive this conversation?')) {
                        onArchiveSession(session.id)
                      }
                    }}
                    className="p-1.5 rounded text-gray-400 hover:bg-gray-600 hover:text-yellow-400"
                    title="Archive"
                  >
                    <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 8h14M5 8a2 2 0 110-4h14a2 2 0 110 4M5 8v10a2 2 0 002 2h10a2 2 0 002-2V8m-9 4h4" />
                    </svg>
                  </button>

                  {/* Delete button */}
                  <button
                    onClick={(e) => {
                      e.stopPropagation()
                      if (confirm('Delete this conversation? This cannot be undone.')) {
                        onDeleteSession(session.id)
                      }
                    }}
                    className="p-1.5 rounded text-gray-400 hover:bg-red-500/20 hover:text-red-400"
                    title="Delete"
                  >
                    <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" />
                    </svg>
                  </button>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  )
}
