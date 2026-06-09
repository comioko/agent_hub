import { useState, useEffect } from 'react'
import { agentApi } from '../api/agenthub'
import AgentBadge from '../components/AgentBadge'

export default function AgentDetailModal({ agentId, onClose }) {
  const [agent, setAgent] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)

  useEffect(() => {
    loadAgent()
  }, [agentId])

  const loadAgent = async () => {
    try {
      setLoading(true)
      const response = await agentApi.getAgent(agentId)
      setAgent(response.data)
    } catch (err) {
      setError(err.message)
    } finally {
      setLoading(false)
    }
  }

  if (loading) {
    return (
      <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50">
        <div className="bg-gray-800 rounded-lg p-6 w-full max-w-md">
          <div className="flex justify-center">
            <svg className="animate-spin w-8 h-8 text-primary-500" viewBox="0 0 24 24">
              <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" fill="none" />
              <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
            </svg>
          </div>
        </div>
      </div>
    )
  }

  if (error) {
    return (
      <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50">
        <div className="bg-gray-800 rounded-lg p-6 w-full max-w-md">
          <div className="text-red-400 mb-4">{error}</div>
          <button onClick={onClose} className="btn-secondary">Close</button>
        </div>
      </div>
    )
  }

  if (!agent) return null

  return (
    <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50">
      <div className="bg-gray-800 rounded-lg p-6 w-full max-w-md">
        <div className="flex justify-between items-center mb-4">
          <h2 className="text-xl font-bold text-white">Agent Details</h2>
          <button onClick={onClose} className="text-gray-400 hover:text-white">
            <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
            </svg>
          </button>
        </div>

        <div className="space-y-4">
          <div className="flex items-center gap-4">
            <div className="w-16 h-16 rounded-full bg-primary-600 flex items-center justify-center text-white text-2xl font-bold">
              {agent.name?.charAt(0).toUpperCase()}
            </div>
            <div>
              <div className="flex items-center gap-2">
                <h3 className="text-lg font-medium text-white">{agent.name}</h3>
                <AgentBadge isSystem={agent.isSystem} ownerUsername={agent.ownerUsername} />
              </div>
              <span className="text-xs text-gray-400 bg-gray-700 px-2 py-1 rounded">
                {agent.provider} / {agent.providerModel}
              </span>
            </div>
          </div>

          {agent.description && (
            <div>
              <label className="text-sm text-gray-400">Description</label>
              <p className="text-white mt-1">{agent.description}</p>
            </div>
          )}

          <div>
            <label className="text-sm text-gray-400">System Prompt</label>
            <div className="mt-1 p-3 bg-gray-900 rounded text-sm text-gray-300 font-mono max-h-40 overflow-y-auto">
              {agent.systemPrompt || 'No system prompt configured'}
            </div>
          </div>

          <div className="flex items-center gap-2">
            <span className="text-sm text-gray-400">Status:</span>
            {agent.enabled ? (
              <span className="flex items-center gap-1 text-green-400">
                <span className="w-2 h-2 bg-green-400 rounded-full"></span>
                Enabled
              </span>
            ) : (
              <span className="flex items-center gap-1 text-red-400">
                <span className="w-2 h-2 bg-red-400 rounded-full"></span>
                Disabled
              </span>
            )}
          </div>
        </div>

        <div className="mt-6 flex justify-end">
          <button onClick={onClose} className="px-4 py-2 bg-gray-700 text-white rounded hover:bg-gray-600">
            Close
          </button>
        </div>
      </div>
    </div>
  )
}
