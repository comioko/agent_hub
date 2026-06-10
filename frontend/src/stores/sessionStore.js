import { create } from 'zustand'
import { sessionApi } from '../api/agenthub'

export const useSessionStore = create((set, get) => ({
  sessions: [],
  currentSession: null,
  currentSessionAgents: [],
  loading: false,
  error: null,

  setCurrentSession: (session) => {
    // Extract agent participants from the session
    const agentParticipants = session?.participants?.filter(p => p.agentId) || []
    const agents = agentParticipants.map(p => ({
      id: p.agentId,
      name: p.name,
      avatarUrl: p.avatarUrl,
      type: p.type
    }))
    set({ currentSession: session, currentSessionAgents: agents })
  },

  fetchSessions: async () => {
    set({ loading: true, error: null })
    try {
      const response = await sessionApi.getConversations()
      set({ sessions: response.data || [] })
    } catch (error) {
      set({ error: error.message })
    } finally {
      set({ loading: false })
    }
  },

  createSession: async (agentIds, agentId, title) => {
    set({ loading: true, error: null })
    try {
      const response = await sessionApi.createConversation(agentIds, agentId, title)
      const newSession = response.data
      // Set current timestamp for proper sort order
      newSession.updatedAt = new Date().toISOString()
      newSession.pinned = false
      set((state) => {
        // Insert new session in correct sorted position (pinned first, then by updatedAt desc)
        const updatedSessions = [newSession, ...state.sessions]
        updatedSessions.sort((a, b) => {
          if (a.pinned !== b.pinned) return (b.pinned ? 1 : 0) - (a.pinned ? 1 : 0)
          return new Date(b.updatedAt) - new Date(a.updatedAt)
        })
        return {
          sessions: updatedSessions,
          currentSession: newSession
        }
      })
      return newSession
    } catch (error) {
      set({ error: error.message })
      throw error
    } finally {
      set({ loading: false })
    }
  },

  deleteSession: async (sessionId) => {
    try {
      await sessionApi.deleteConversation(sessionId)
      set((state) => ({
        sessions: state.sessions.filter((s) => s.id !== sessionId),
        currentSession: state.currentSession?.id === sessionId ? null : state.currentSession
      }))
    } catch (error) {
      set({ error: error.message })
      throw error
    }
  },

  pinSession: async (sessionId, pinned) => {
    try {
      await sessionApi.pinConversation(sessionId, pinned)
      set((state) => {
        const updatedSessions = state.sessions.map((s) =>
          s.id === sessionId ? { ...s, pinned } : s
        )
        // Re-sort: pinned sessions first, then by updatedAt
        updatedSessions.sort((a, b) => {
          if (a.pinned !== b.pinned) return b.pinned - a.pinned
          return new Date(b.updatedAt) - new Date(a.updatedAt)
        })
        return {
          sessions: updatedSessions,
          currentSession: state.currentSession?.id === sessionId
            ? { ...state.currentSession, pinned }
            : state.currentSession
        }
      })
    } catch (error) {
      set({ error: error.message })
      throw error
    }
  },

  archiveSession: async (sessionId) => {
    try {
      await sessionApi.archiveConversation(sessionId, true)
      set((state) => ({
        sessions: state.sessions.filter((s) => s.id !== sessionId),
        currentSession: state.currentSession?.id === sessionId ? null : state.currentSession
      }))
    } catch (error) {
      set({ error: error.message })
      throw error
    }
  }
}))
