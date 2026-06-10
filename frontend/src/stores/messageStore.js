import { create } from 'zustand'
import { messageApi } from '../api/agenthub'

export const useMessageStore = create((set, get) => ({
  messages: [],
  loading: false,
  error: null,
  toolCallsInProgress: new Map(), // Map<messageId, { toolCall, result }[]>

  setMessages: (messages) => set({ messages: messages.sort((a, b) => a.id - b.id) }),

  addMessage: (message) => set((state) => {
    // Prevent duplicate messages by checking ID
    if (!message || !message.id) {
      return state
    }
    const exists = state.messages.some(m => m.id === message.id)
    if (exists) {
      console.warn('Duplicate message prevented:', message.id)
      return state
    }
    // Insert message in sorted position by ID (ascending - oldest first)
    const newMessages = [...state.messages, message]
    newMessages.sort((a, b) => a.id - b.id)
    return { messages: newMessages }
  }),

  updateMessage: (messageId, updates) => set((state) => {
    if (!messageId) return state
    return {
      messages: state.messages.map(m =>
        m.id === messageId ? { ...m, ...updates } : m
      )
    }
  }),

  // Tool call state management
  addToolCall: (messageId, toolCall) => set((state) => {
    const newMap = new Map(state.toolCallsInProgress)
    const calls = newMap.get(messageId) || []
    calls.push({ toolCall, result: null })
    newMap.set(messageId, calls)
    return { toolCallsInProgress: newMap }
  }),

  updateToolCallResult: (messageId, callId, result) => set((state) => {
    const newMap = new Map(state.toolCallsInProgress)
    const calls = newMap.get(messageId)
    if (calls) {
      const updated = calls.map(c => {
        if (c.toolCall.id === callId || c.toolCall.callId === callId) {
          return { ...c, result }
        }
        return c
      })
      newMap.set(messageId, updated)
    }
    return { toolCallsInProgress: newMap }
  }),

  clearToolCalls: (messageId) => set((state) => {
    const newMap = new Map(state.toolCallsInProgress)
    newMap.delete(messageId)
    return { toolCallsInProgress: newMap }
  }),

  getToolCalls: (messageId) => {
    const state = get()
    return state.toolCallsInProgress.get(messageId) || []
  },

  fetchMessages: async (conversationId) => {
    set({ loading: true, error: null })
    try {
      const response = await messageApi.getMessages(conversationId)
      const msgs = response.data || []
      // Sort by ID ascending (oldest first = user message first, AI response second)
      msgs.sort((a, b) => a.id - b.id)
      set({ messages: msgs })
    } catch (error) {
      set({ error: error.message })
    } finally {
      set({ loading: false })
    }
  },

  sendMessage: async (conversationId, content, parentId) => {
    set({ loading: true, error: null })
    try {
      const response = await messageApi.sendMessage(conversationId, content, parentId)
      // Add user message from POST response (deduplication handled by addMessage)
      get().addMessage(response.data)
      return response.data
    } catch (error) {
      set({ error: error.message })
      throw error
    } finally {
      set({ loading: false })
    }
  },

  pinMessage: async (messageId, pinned) => {
    try {
      await messageApi.pinMessage(messageId, pinned)
      set((state) => ({
        messages: state.messages.map(m =>
          m.id === messageId ? { ...m, pinned } : m
        )
      }))
    } catch (error) {
      set({ error: error.message })
      throw error
    }
  },

  updateMessageContext: async (messageId, contextType, contextPriority) => {
    try {
      await messageApi.updateMessageContext(messageId, contextType, contextPriority)
      set((state) => ({
        messages: state.messages.map(m =>
          m.id === messageId ? { ...m, contextType, contextPriority } : m
        )
      }))
    } catch (error) {
      set({ error: error.message })
      throw error
    }
  }
}))
