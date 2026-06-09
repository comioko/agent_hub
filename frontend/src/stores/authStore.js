import { create } from 'zustand'
import { authApi } from '../api/agenthub'

export const useAuthStore = create((set, get) => ({
  token: localStorage.getItem('agenthub_token') || null,
  user: JSON.parse(localStorage.getItem('agenthub_user') || 'null'),
  loading: false,
  error: null,

  setToken: (token) => {
    localStorage.setItem('agenthub_token', token)
    set({ token })
  },

  setUser: (user) => {
    localStorage.setItem('agenthub_user', JSON.stringify(user))
    set({ user })
  },

  login: async (username, password) => {
    set({ loading: true, error: null })
    try {
      const response = await authApi.login(username, password)
      get().setToken(response.data.token)
      get().setUser(response.data.user)
      return response.data
    } catch (error) {
      set({ error: error.message })
      throw error
    } finally {
      set({ loading: false })
    }
  },

  register: async (username, password, nickname) => {
    set({ loading: true, error: null })
    try {
      const response = await authApi.register(username, password, nickname)
      get().setToken(response.data.token)
      get().setUser(response.data.user)
      return response.data
    } catch (error) {
      set({ error: error.message })
      throw error
    } finally {
      set({ loading: false })
    }
  },

  logout: () => {
    localStorage.removeItem('agenthub_token')
    localStorage.removeItem('agenthub_user')
    set({ token: null, user: null })
  },

  checkAuth: async () => {
    const token = get().token
    if (!token) return null
    try {
      const response = await authApi.getCurrentUser()
      get().setUser(response.data)
      return response.data
    } catch {
      get().logout()
      return null
    }
  }
}))
