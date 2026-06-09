import { useState } from 'react'
import AgentBadge from './AgentBadge'

export default function NewSessionModal({ agents, onClose, onSubmit }) {
  const [chatMode, setChatMode] = useState('single') // 'single' or 'group'
  const [selectedAgents, setSelectedAgents] = useState([])
  const [title, setTitle] = useState('')
  const [loading, setLoading] = useState(false)

  // Group agents by type
  const systemAgents = agents.filter(a => a.isSystem)
  const userAgents = agents.filter(a => !a.isSystem)

  const handleAgentToggle = (agent) => {
    if (chatMode === 'single') {
      setSelectedAgents([agent])
    } else {
      // Group mode - toggle selection
      setSelectedAgents(prev => {
        const isSelected = prev.some(a => a.id === agent.id)
        if (isSelected) {
          return prev.filter(a => a.id !== agent.id)
        } else {
          return [...prev, agent]
        }
      })
    }
  }

  const handleSubmit = async (e) => {
    e.preventDefault()
    if (selectedAgents.length === 0) return

    setLoading(true)
    try {
      if (chatMode === 'single') {
        await onSubmit(null, selectedAgents[0].id, title || `Chat with ${selectedAgents[0].name}`)
      } else {
        // Group chat - pass agentIds array
        await onSubmit(selectedAgents.map(a => a.id), null, title || `Group with ${selectedAgents.length} agents`)
      }
    } finally {
      setLoading(false)
    }
  }

  const renderAgentList = (agentList, title) => {
    if (agentList.length === 0) return null
    return (
      <div className="mb-4">
        {title && (
          <div className="text-xs text-gray-500 uppercase tracking-wide mb-2">{title}</div>
        )}
        <div className="space-y-2">
          {agentList.map((agent) => {
            const isSelected = selectedAgents.some(a => a.id === agent.id)
            return (
              <div
                key={agent.id}
                onClick={() => handleAgentToggle(agent)}
                className={`p-3 rounded-lg cursor-pointer border transition ${
                  isSelected
                    ? 'border-primary-500 bg-gray-700'
                    : 'border-gray-600 hover:border-gray-500'
                }`}
              >
                <div className="flex items-center gap-3">
                  {chatMode === 'group' && (
                    <div className={`w-5 h-5 rounded border flex items-center justify-center ${
                      isSelected ? 'bg-primary-600 border-primary-600' : 'border-gray-500'
                    }`}>
                      {isSelected && (
                        <svg className="w-3 h-3 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={3} d="M5 13l4 4L19 7" />
                        </svg>
                      )}
                    </div>
                  )}
                  <div className="w-10 h-10 rounded-full bg-primary-600 flex items-center justify-center text-white font-bold flex-shrink-0">
                    {agent.name?.charAt(0).toUpperCase()}
                  </div>
                  <div className="flex-1 min-w-0">
                    <div className="flex items-center gap-2">
                      <div className="text-white font-medium">{agent.name}</div>
                      <AgentBadge isSystem={agent.isSystem} ownerUsername={agent.ownerUsername} />
                    </div>
                    <div className="text-gray-400 text-sm truncate">{agent.description}</div>
                  </div>
                </div>
              </div>
            )
          })}
        </div>
      </div>
    )
  }

  return (
    <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50">
      <div className="bg-gray-800 rounded-lg p-6 w-full max-w-md mx-4 max-h-[90vh] overflow-hidden flex flex-col">
        <div className="flex justify-between items-center mb-4">
          <h2 className="text-xl font-bold text-white">New Conversation</h2>
          <button onClick={onClose} className="text-gray-400 hover:text-white">
            <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
            </svg>
          </button>
        </div>

        {/* Mode Toggle */}
        <div className="flex gap-2 mb-4">
          <button
            type="button"
            onClick={() => {
              setChatMode('single')
              setSelectedAgents([])
            }}
            className={`flex-1 py-2 px-4 rounded font-medium transition ${
              chatMode === 'single'
                ? 'bg-primary-600 text-white'
                : 'bg-gray-700 text-gray-400 hover:bg-gray-600'
            }`}
          >
            Single Chat
          </button>
          <button
            type="button"
            onClick={() => {
              setChatMode('group')
              setSelectedAgents([])
            }}
            className={`flex-1 py-2 px-4 rounded font-medium transition ${
              chatMode === 'group'
                ? 'bg-primary-600 text-white'
                : 'bg-gray-700 text-gray-400 hover:bg-gray-600'
            }`}
          >
            Group Chat
          </button>
        </div>

        <form onSubmit={handleSubmit} className="flex-1 overflow-hidden flex flex-col">
          <div className="mb-4 flex-1 overflow-y-auto">
            <label className="block text-sm font-medium text-gray-300 mb-2">
              {chatMode === 'single' ? 'Choose an Agent' : 'Select Agents (pick 2 or more)'}
            </label>
            {renderAgentList(systemAgents, 'System Agents')}
            {renderAgentList(userAgents, 'My Agents')}
            {agents.length === 0 && (
              <p className="text-gray-500 text-sm text-center py-4">No agents available</p>
            )}
          </div>

          {/* Selected agents summary for group chat */}
          {chatMode === 'group' && selectedAgents.length > 0 && (
            <div className="mb-4 p-3 bg-gray-700 rounded-lg">
              <div className="text-sm text-gray-400 mb-2">Selected: {selectedAgents.length} agents</div>
              <div className="flex flex-wrap gap-2">
                {selectedAgents.map(agent => (
                  <span key={agent.id} className="px-2 py-1 bg-primary-600/30 text-primary-300 rounded text-xs">
                    {agent.name}
                  </span>
                ))}
              </div>
            </div>
          )}

          <div className="mb-4">
            <label className="block text-sm font-medium text-gray-300 mb-2">Title (optional)</label>
            <input
              type="text"
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              placeholder={chatMode === 'single' ? 'Conversation title' : 'Group title'}
              className="w-full px-4 py-2 bg-gray-700 border border-gray-600 rounded text-white placeholder-gray-400 focus:outline-none focus:border-primary-500"
            />
          </div>

          <div className="flex gap-3">
            <button
              type="button"
              onClick={onClose}
              className="flex-1 py-2 px-4 border border-gray-600 text-gray-300 font-medium rounded hover:bg-gray-700"
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={
                selectedAgents.length === 0 ||
                (chatMode === 'group' && selectedAgents.length < 2) ||
                loading
              }
              className="flex-1 py-2 px-4 bg-primary-600 text-white font-medium rounded hover:bg-primary-700 disabled:opacity-50 disabled:cursor-not-allowed"
            >
              {loading ? 'Creating...' : chatMode === 'single' ? 'Create' : 'Create Group'}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}
