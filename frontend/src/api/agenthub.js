const API_BASE = '/api'

function getAuthHeaders() {
  const token = localStorage.getItem('agenthub_token')
  return token ? { Authorization: `Bearer ${token}` } : {}
}

async function handleResponse(response) {
  const data = await response.json()
  if (response.ok) {
    return data
  }
  throw new Error(data.message || 'Request failed')
}

export const authApi = {
  async login(username, password) {
    const res = await fetch(`${API_BASE}/auth/login`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username, password })
    })
    return handleResponse(res)
  },

  async register(username, password, nickname) {
    const res = await fetch(`${API_BASE}/auth/register`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username, password, nickname })
    })
    return handleResponse(res)
  },

  async getCurrentUser() {
    const res = await fetch(`${API_BASE}/auth/me`, {
      headers: getAuthHeaders()
    })
    return handleResponse(res)
  }
}

export const sessionApi = {
  async getConversations() {
    const res = await fetch(`${API_BASE}/sessions`, {
      headers: getAuthHeaders()
    })
    return handleResponse(res)
  },

  async createConversation(agentIds, agentId, title) {
    const res = await fetch(`${API_BASE}/sessions`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', ...getAuthHeaders() },
      body: JSON.stringify({ agentIds, agentId, title })
    })
    return handleResponse(res)
  },

  async getConversation(conversationId) {
    const res = await fetch(`${API_BASE}/sessions/${conversationId}`, {
      headers: getAuthHeaders()
    })
    return handleResponse(res)
  },

  async deleteConversation(conversationId) {
    const res = await fetch(`${API_BASE}/sessions/${conversationId}`, {
      method: 'DELETE',
      headers: getAuthHeaders()
    })
    return handleResponse(res)
  },

  async pinConversation(conversationId, pinned) {
    const res = await fetch(`${API_BASE}/sessions/${conversationId}/pin?pinned=${pinned}`, {
      method: 'PUT',
      headers: getAuthHeaders()
    })
    return handleResponse(res)
  },

  async archiveConversation(conversationId, archived) {
    const res = await fetch(`${API_BASE}/sessions/${conversationId}/archive?archived=${archived}`, {
      method: 'PUT',
      headers: getAuthHeaders()
    })
    return handleResponse(res)
  },

  async searchConversations(keyword) {
    const res = await fetch(`${API_BASE}/sessions/search?keyword=${encodeURIComponent(keyword)}`, {
      headers: getAuthHeaders()
    })
    return handleResponse(res)
  }
}

export const messageApi = {
  async getMessages(conversationId) {
    const res = await fetch(`${API_BASE}/messages/conversation/${conversationId}`, {
      headers: getAuthHeaders()
    })
    return handleResponse(res)
  },

  async sendMessage(conversationId, content, parentId) {
    const res = await fetch(`${API_BASE}/messages`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', ...getAuthHeaders() },
      body: JSON.stringify({ conversationId, content, parentId })
    })
    return handleResponse(res)
  },

  async pinMessage(messageId, pinned) {
    const res = await fetch(`${API_BASE}/messages/${messageId}/pin?pinned=${pinned}`, {
      method: 'PUT',
      headers: getAuthHeaders()
    })
    return handleResponse(res)
  },

  async getMessageVersions(messageId) {
    const res = await fetch(`${API_BASE}/messages/${messageId}/versions`, {
      headers: getAuthHeaders()
    })
    return handleResponse(res)
  },

  async saveMessageVersion(messageId, content) {
    const res = await fetch(`${API_BASE}/messages/${messageId}/versions`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', ...getAuthHeaders() },
      body: JSON.stringify({ content })
    })
    return handleResponse(res)
  }
}

export const agentApi = {
  async getAgents() {
    const res = await fetch(`${API_BASE}/agents`, {
      headers: getAuthHeaders()
    })
    return handleResponse(res)
  },

  async getAgent(agentId) {
    const res = await fetch(`${API_BASE}/agents/${agentId}`, {
      headers: getAuthHeaders()
    })
    return handleResponse(res)
  },

  async getMyAgents() {
    const res = await fetch(`${API_BASE}/agents/my`, {
      headers: getAuthHeaders()
    })
    return handleResponse(res)
  },

  async createAgent(data) {
    const res = await fetch(`${API_BASE}/agents`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', ...getAuthHeaders() },
      body: JSON.stringify(data)
    })
    return handleResponse(res)
  },

  async updateAgent(agentId, data) {
    const res = await fetch(`${API_BASE}/agents/${agentId}`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json', ...getAuthHeaders() },
      body: JSON.stringify(data)
    })
    return handleResponse(res)
  },

  async deleteAgent(agentId) {
    const res = await fetch(`${API_BASE}/agents/${agentId}`, {
      method: 'DELETE',
      headers: getAuthHeaders()
    })
    return handleResponse(res)
  }
}

export const codeBlockApi = {
  async updateCodeBlock(blockId, content) {
    const res = await fetch(`${API_BASE}/code-blocks/${blockId}`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json', ...getAuthHeaders() },
      body: JSON.stringify({ content })
    })
    return handleResponse(res)
  },

  async executeCode(blockId) {
    const res = await fetch(`${API_BASE}/code-blocks/${blockId}/execute`, {
      method: 'POST',
      headers: getAuthHeaders()
    })
    return handleResponse(res)
  },

  async applyDiff(filePath, diff, content) {
    const res = await fetch(`${API_BASE}/diff/apply`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', ...getAuthHeaders() },
      body: JSON.stringify({ filePath, diff, content })
    })
    return handleResponse(res)
  }
}

export const taskApi = {
  async getTasks() {
    const res = await fetch(`${API_BASE}/tasks`, {
      headers: getAuthHeaders()
    })
    return handleResponse(res)
  },

  async getTask(sessionId) {
    const res = await fetch(`${API_BASE}/tasks/${sessionId}`, {
      headers: getAuthHeaders()
    })
    return handleResponse(res)
  },

  async createTask(agentId, task, conversationId) {
    const res = await fetch(`${API_BASE}/tasks`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', ...getAuthHeaders() },
      body: JSON.stringify({ agentId, task, conversationId })
    })
    return handleResponse(res)
  },

  async cancelTask(sessionId) {
    const res = await fetch(`${API_BASE}/tasks/${sessionId}`, {
      method: 'DELETE',
      headers: getAuthHeaders()
    })
    return handleResponse(res)
  },

  async getTaskMessages(sessionId) {
    const res = await fetch(`${API_BASE}/tasks/${sessionId}/messages`, {
      headers: getAuthHeaders()
    })
    return handleResponse(res)
  },

  getTaskStreamUrl(sessionId) {
    const token = localStorage.getItem('agenthub_token')
    return `${API_BASE}/tasks/${sessionId}/stream?token=${token}`
  }
}
