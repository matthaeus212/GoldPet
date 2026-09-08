import { describe, it, expect, vi, beforeEach } from 'vitest'

// Mock external dependencies before importing the store
vi.mock('../../bridge/nativeBridge', () => ({
  nativeBridge: {
    isAvailable: vi.fn(() => false),
    callMethod: vi.fn(),
    onEvent: vi.fn(),
  },
}))

vi.mock('../../lib/queryClient', () => ({
  queryClient: {
    cancelQueries: vi.fn(),
    clear: vi.fn(),
  },
}))

vi.mock('../../services/websocket/chatWebSocket', () => ({
  chatWebSocket: {
    disconnect: vi.fn(),
  },
}))

import { useAuthStore } from '../authStore'
import { queryClient } from '../../lib/queryClient'
import { chatWebSocket } from '../../services/websocket/chatWebSocket'

describe('authStore', () => {
  beforeEach(() => {
    // Reset store state between tests
    useAuthStore.setState({
      token: null,
      refreshToken: null,
      user: null,
      isAuthenticated: false,
    })
    vi.clearAllMocks()
  })

  describe('initial state', () => {
    it('starts unauthenticated with no tokens', () => {
      const state = useAuthStore.getState()
      expect(state.isAuthenticated).toBe(false)
      expect(state.token).toBeNull()
      expect(state.refreshToken).toBeNull()
      expect(state.user).toBeNull()
    })
  })

  describe('login', () => {
    it('sets token, refreshToken, user and isAuthenticated', () => {
      const user = { id: 1, nickname: '테스터', email: 'test@example.com' }
      useAuthStore.getState().login('access-token', 'refresh-token', user)

      const state = useAuthStore.getState()
      expect(state.token).toBe('access-token')
      expect(state.refreshToken).toBe('refresh-token')
      expect(state.user).toEqual(user)
      expect(state.isAuthenticated).toBe(true)
    })

    it('clears react query cache on login', () => {
      const user = { id: 1, nickname: '테스터' }
      useAuthStore.getState().login('token', 'refresh', user)

      expect(queryClient.cancelQueries).toHaveBeenCalled()
      expect(queryClient.clear).toHaveBeenCalled()
    })
  })

  describe('logout', () => {
    it('clears all auth state', () => {
      const user = { id: 1, nickname: '테스터' }
      useAuthStore.getState().login('token', 'refresh', user)
      useAuthStore.getState().logout()

      const state = useAuthStore.getState()
      expect(state.token).toBeNull()
      expect(state.refreshToken).toBeNull()
      expect(state.user).toBeNull()
      expect(state.isAuthenticated).toBe(false)
    })

    it('disconnects websocket on logout', () => {
      useAuthStore.getState().login('token', 'refresh', { id: 1, nickname: '테스터' })
      useAuthStore.getState().logout()

      expect(chatWebSocket.disconnect).toHaveBeenCalled()
    })

    it('clears react query cache on logout', () => {
      useAuthStore.getState().login('token', 'refresh', { id: 1, nickname: '테스터' })
      vi.clearAllMocks()
      useAuthStore.getState().logout()

      expect(queryClient.cancelQueries).toHaveBeenCalled()
      expect(queryClient.clear).toHaveBeenCalled()
    })
  })

  describe('updateUser', () => {
    it('updates user data without affecting tokens', () => {
      const user = { id: 1, nickname: '기존' }
      useAuthStore.getState().login('token', 'refresh', user)

      const updated = { id: 1, nickname: '변경됨', goldBalance: 100 }
      useAuthStore.getState().updateUser(updated)

      const state = useAuthStore.getState()
      expect(state.user?.nickname).toBe('변경됨')
      expect(state.user?.goldBalance).toBe(100)
      expect(state.token).toBe('token')
    })
  })

  describe('setTokens', () => {
    it('updates both tokens', () => {
      useAuthStore.getState().login('old-token', 'old-refresh', { id: 1, nickname: '테스터' })
      useAuthStore.getState().setTokens('new-token', 'new-refresh')

      const state = useAuthStore.getState()
      expect(state.token).toBe('new-token')
      expect(state.refreshToken).toBe('new-refresh')
    })
  })
})
