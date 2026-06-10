import { useState, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { agentApi } from '../api/agenthub'
import { useAuthStore } from '../stores/authStore'

export default function MyAgentsPage() {
  const navigate = useNavigate()
  const { token } = useAuthStore()
  const [myAgents, setMyAgents] = useState([])
  const [loading, setLoading] = useState(true)
  const [showForm, setShowForm] = useState(false)
  const [editingAgent, setEditingAgent] = useState(null)
  const [formData, setFormData] = useState({
    name: '',
    description: '',
    systemPrompt: '',
    provider: 'BUILTIN',
    providerModel: 'builtin',
    enabled: true,
    isOrchestrator: false
  })
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState(null)

  useEffect(() => {
    if (!token) {
      navigate('/login')
      return
    }
    loadMyAgents()
  }, [token, navigate])

  const loadMyAgents = async () => {
    try {
      setLoading(true)
      const response = await agentApi.getMyAgents()
      setMyAgents(response.data || [])
    } catch (err) {
      setError(err.message)
    } finally {
      setLoading(false)
    }
  }

  const handleSubmit = async (e) => {
    e.preventDefault()
    if (!formData.name.trim()) return

    setSaving(true)
    setError(null)
    try {
      if (editingAgent) {
        await agentApi.updateAgent(editingAgent.id, formData)
      } else {
        await agentApi.createAgent(formData)
      }
      setShowForm(false)
      setEditingAgent(null)
      setFormData({
        name: '',
        description: '',
        systemPrompt: '',
        provider: 'BUILTIN',
        providerModel: 'builtin',
        enabled: true,
        isOrchestrator: false
      })
      loadMyAgents()
    } catch (err) {
      setError(err.message)
    } finally {
      setSaving(false)
    }
  }

  const handleEdit = (agent) => {
    setEditingAgent(agent)
    setFormData({
      name: agent.name || '',
      description: agent.description || '',
      systemPrompt: agent.systemPrompt || '',
      provider: agent.provider || 'BUILTIN',
      providerModel: agent.providerModel || 'builtin',
      enabled: agent.enabled !== false,
      isOrchestrator: agent.isOrchestrator === true
    })
    setShowForm(true)
  }

  const handleDelete = async (agentId) => {
    if (!confirm('Are you sure you want to delete this agent?')) return

    try {
      await agentApi.deleteAgent(agentId)
      loadMyAgents()
    } catch (err) {
      setError(err.message)
    }
  }

  const handleCancel = () => {
    setShowForm(false)
    setEditingAgent(null)
    setFormData({
      name: '',
      description: '',
      systemPrompt: '',
      provider: 'BUILTIN',
      providerModel: 'builtin',
      enabled: true,
      isOrchestrator: false
    })
  }

  return (
    <div className="min-h-screen bg-gray-900 text-white">
      {/* Header */}
      <header className="bg-gray-800 border-b border-gray-700">
        <div className="max-w-4xl mx-auto px-4 py-4 flex items-center justify-between">
          <div className="flex items-center gap-4">
            <button
              onClick={() => navigate('/chat')}
              className="text-gray-400 hover:text-white"
            >
              <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M10 19l-7-7m0 0l7-7m-7 7h18" />
              </svg>
            </button>
            <h1 className="text-xl font-bold">My Agents</h1>
          </div>
          <button
            onClick={() => setShowForm(true)}
            className="px-4 py-2 bg-primary-600 text-white rounded hover:bg-primary-700"
          >
            Create Agent
          </button>
        </div>
      </header>

      {/* Content */}
      <div className="max-w-4xl mx-auto px-4 py-8">
        {error && (
          <div className="mb-4 p-4 bg-red-500/20 border border-red-500 rounded text-red-400">
            {error}
          </div>
        )}

        {loading ? (
          <div className="flex justify-center py-12">
            <svg className="animate-spin w-8 h-8 text-primary-500" viewBox="0 0 24 24">
              <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" fill="none" />
              <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
            </svg>
          </div>
        ) : myAgents.length === 0 ? (
          <div className="text-center py-12">
            <p className="text-gray-400 mb-4">You haven't created any agents yet.</p>
            <button
              onClick={() => setShowForm(true)}
              className="px-4 py-2 bg-primary-600 text-white rounded hover:bg-primary-700"
            >
              Create Your First Agent
            </button>
          </div>
        ) : (
          <div className="space-y-4">
            {myAgents.map((agent) => (
              <div
                key={agent.id}
                className="bg-gray-800 rounded-lg p-4 border border-gray-700"
              >
                <div className="flex items-start gap-4">
                  <div className="w-12 h-12 rounded-full bg-primary-600 flex items-center justify-center text-white text-xl font-bold flex-shrink-0">
                    {agent.name?.charAt(0).toUpperCase()}
                  </div>
                  <div className="flex-1 min-w-0">
                    <div className="flex items-center gap-2">
                      <h3 className="text-lg font-medium">{agent.name}</h3>
                      <span className={`px-2 py-0.5 rounded text-xs ${
                        agent.enabled ? 'bg-green-500/20 text-green-400' : 'bg-red-500/20 text-red-400'
                      }`}>
                        {agent.enabled ? 'Enabled' : 'Disabled'}
                      </span>
                      {agent.isOrchestrator && (
                        <span className="px-2 py-0.5 rounded text-xs bg-purple-500/20 text-purple-400">
                          Orchestrator
                        </span>
                      )}
                    </div>
                    {agent.description && (
                      <p className="text-gray-400 text-sm mt-1">{agent.description}</p>
                    )}
                    <div className="flex items-center gap-4 mt-2 text-xs text-gray-500">
                      <span>Provider: {agent.provider} / {agent.providerModel}</span>
                      <span>Created: {new Date(agent.createdAt).toLocaleDateString()}</span>
                    </div>
                  </div>
                  <div className="flex gap-2">
                    <button
                      onClick={() => handleEdit(agent)}
                      className="px-3 py-1 text-sm bg-gray-700 text-gray-300 rounded hover:bg-gray-600"
                    >
                      Edit
                    </button>
                    <button
                      onClick={() => handleDelete(agent.id)}
                      className="px-3 py-1 text-sm bg-red-500/20 text-red-400 rounded hover:bg-red-500/30"
                    >
                      Delete
                    </button>
                  </div>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>

      {/* Create/Edit Form Modal */}
      {showForm && (
        <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50 p-4">
          <div className="bg-gray-800 rounded-lg p-6 w-full max-w-lg max-h-[90vh] overflow-y-auto">
            <div className="flex justify-between items-center mb-6">
              <h2 className="text-xl font-bold">
                {editingAgent ? 'Edit Agent' : 'Create New Agent'}
              </h2>
              <button onClick={handleCancel} className="text-gray-400 hover:text-white">
                <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
                </svg>
              </button>
            </div>

            <form onSubmit={handleSubmit} className="space-y-4">
              <div>
                <label className="block text-sm font-medium text-gray-300 mb-1">
                  Name *
                </label>
                <input
                  type="text"
                  value={formData.name}
                  onChange={(e) => setFormData({ ...formData, name: e.target.value })}
                  required
                  className="w-full px-4 py-2 bg-gray-700 border border-gray-600 rounded text-white placeholder-gray-400 focus:outline-none focus:border-primary-500"
                  placeholder="My Custom Agent"
                />
              </div>

              <div>
                <label className="block text-sm font-medium text-gray-300 mb-1">
                  Description
                </label>
                <input
                  type="text"
                  value={formData.description}
                  onChange={(e) => setFormData({ ...formData, description: e.target.value })}
                  className="w-full px-4 py-2 bg-gray-700 border border-gray-600 rounded text-white placeholder-gray-400 focus:outline-none focus:border-primary-500"
                  placeholder="A brief description of your agent"
                />
              </div>

              <div>
                <label className="block text-sm font-medium text-gray-300 mb-1">
                  System Prompt
                </label>
                <textarea
                  value={formData.systemPrompt}
                  onChange={(e) => setFormData({ ...formData, systemPrompt: e.target.value })}
                  rows={6}
                  className="w-full px-4 py-2 bg-gray-700 border border-gray-600 rounded text-white placeholder-gray-400 focus:outline-none focus:border-primary-500 font-mono text-sm"
                  placeholder="You are a helpful AI assistant that..."
                />
              </div>

              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="block text-sm font-medium text-gray-300 mb-1">
                    Provider
                  </label>
                  <select
                    value={formData.provider}
                    onChange={(e) => setFormData({ ...formData, provider: e.target.value })}
                    className="w-full px-4 py-2 bg-gray-700 border border-gray-600 rounded text-white focus:outline-none focus:border-primary-500"
                  >
                    <option value="BUILTIN">Built-in</option>
                    <option value="DEEPSEEK">DeepSeek</option>
                    <option value="MINIMAX">MiniMax</option>
                    <option value="OPENAI">OpenAI</option>
                    <option value="ANTHROPIC">Anthropic</option>
                    <option value="VOLCANO">Volcano (Doubao)</option>
                    <option value="CLI">Claude Code CLI</option>
                  </select>
                </div>

                <div>
                  <label className="block text-sm font-medium text-gray-300 mb-1">
                    Model
                  </label>
                  <input
                    type="text"
                    value={formData.providerModel}
                    onChange={(e) => setFormData({ ...formData, providerModel: e.target.value })}
                    className="w-full px-4 py-2 bg-gray-700 border border-gray-600 rounded text-white placeholder-gray-400 focus:outline-none focus:border-primary-500"
                    placeholder="e.g., gpt-4, claude-3, doubao-pro-32k"
                  />
                </div>
              </div>

              <div className="flex items-center gap-2">
                <input
                  type="checkbox"
                  id="enabled"
                  checked={formData.enabled}
                  onChange={(e) => setFormData({ ...formData, enabled: e.target.checked })}
                  className="w-4 h-4 rounded bg-gray-700 border-gray-600 text-primary-600 focus:ring-primary-500"
                />
                <label htmlFor="enabled" className="text-sm text-gray-300">
                  Enabled (visible and usable)
                </label>
              </div>

              <div className="flex items-center gap-2">
                <input
                  type="checkbox"
                  id="isOrchestrator"
                  checked={formData.isOrchestrator}
                  onChange={(e) => setFormData({ ...formData, isOrchestrator: e.target.checked })}
                  className="w-4 h-4 rounded bg-gray-700 border-gray-600 text-purple-600 focus:ring-purple-500"
                />
                <label htmlFor="isOrchestrator" className="text-sm text-gray-300">
                  Orchestrator (acts as supervisor, can delegate tasks to other agents)
                </label>
              </div>

              <div className="flex gap-3 pt-4">
                <button
                  type="button"
                  onClick={handleCancel}
                  className="flex-1 py-2 px-4 border border-gray-600 text-gray-300 font-medium rounded hover:bg-gray-700"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={saving || !formData.name.trim()}
                  className="flex-1 py-2 px-4 bg-primary-600 text-white font-medium rounded hover:bg-primary-700 disabled:opacity-50 disabled:cursor-not-allowed"
                >
                  {saving ? 'Saving...' : (editingAgent ? 'Update' : 'Create')}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  )
}
