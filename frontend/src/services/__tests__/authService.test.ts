import { describe, it, expect, vi, beforeEach } from 'vitest'
vi.mock('axios')

// Mock the api client module
vi.mock('../api/client', () => {
  const client = {
    post: vi.fn(),
    get: vi.fn(),
    interceptors: {
      request: { use: vi.fn() },
      response: { use: vi.fn() },
    },
  }
  return { apiClient: client, default: client }
})

// Mock nativeBridge
vi.mock('../../bridge/nativeBridge', () => ({
  nativeBridge: {
    isAvailable: vi.fn(() => false),
    callMethod: vi.fn(),
  },
}))

// Mock authStore
vi.mock('../../stores/authStore', () => ({
  useAuthStore: {
    getState: vi.fn(() => ({
      refreshToken: null,
      logout: vi.fn(),
    })),
  },
}))

import apiClient from '../api/client'
import { authService } from '../authService'
import { useAuthStore } from '../../stores/authStore'

const mockedClient = vi.mocked(apiClient)

describe('authService', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  describe('login', () => {
    it('returns auth response on success', async () => {
      const mockResponse = {
        data: {
          accessToken: 'access-token',
          refreshToken: 'refresh-token',
          user: { id: 1, username: 'testuser', nickname: '테스트' },
        },
      }
      mockedClient.post.mockResolvedValueOnce(mockResponse)

      const result = await authService.login({ username: 'testuser', password: 'password' })

      expect(mockedClient.post).toHaveBeenCalledWith('/auth/login', {
        username: 'testuser',
        password: 'password',
      }, undefined)
      expect(result.accessToken).toBe('access-token')
      expect(result.user.id).toBe(1)
    })

    it('throws error with server message on failure', async () => {
      mockedClient.post.mockRejectedValueOnce({
        response: { data: { message: '아이디 또는 비밀번호가 올바르지 않습니다.' } },
      })

      await expect(
        authService.login({ username: 'bad', password: 'bad' })
      ).rejects.toThrow('아이디 또는 비밀번호가 올바르지 않습니다.')
    })

    it('throws default error when no server message', async () => {
      mockedClient.post.mockRejectedValueOnce({})

      await expect(
        authService.login({ username: 'bad', password: 'bad' })
      ).rejects.toThrow('로그인에 실패했습니다.')
    })
  })

  describe('signup', () => {
    it('returns auth response on success', async () => {
      const mockResponse = {
        data: {
          accessToken: 'token',
          refreshToken: 'refresh',
          user: { id: 2, username: 'newuser', nickname: '신규' },
        },
      }
      mockedClient.post.mockResolvedValueOnce(mockResponse)

      const result = await authService.signup({
        username: 'newuser',
        password: 'pass',
        nickname: '신규',
        name: '홍길동',
        birthDate: '1990-01-01',
        gender: 'MALE',
        phoneNumber: '010-1234-5678',
      })

      expect(mockedClient.post).toHaveBeenCalledWith('/auth/signup', expect.objectContaining({ username: 'newuser' }), undefined)
      expect(result.accessToken).toBe('token')
    })

    it('re-throws axios rejection with response intact on failure', async () => {
      const rejection = { response: { status: 409, data: { message: '이미 사용 중인 아이디입니다.' } } }
      mockedClient.post.mockRejectedValueOnce(rejection)

      await expect(authService.signup({
        username: 'dup',
        password: 'pass',
        nickname: 'dup',
        name: '중복',
        birthDate: '1990-01-01',
        gender: 'MALE',
        phoneNumber: '010-0000-0000',
      })).rejects.toMatchObject({ response: { status: 409, data: { message: '이미 사용 중인 아이디입니다.' } } })
    })
  })

  describe('checkUsername', () => {
    it('returns available: true when username is free', async () => {
      mockedClient.post.mockResolvedValueOnce({ data: { available: true } })

      const result = await authService.checkUsername('freeuser')
      expect(result.available).toBe(true)
    })

    it('returns available: false on error', async () => {
      mockedClient.post.mockRejectedValueOnce(new Error('network error'))

      const result = await authService.checkUsername('someuser')
      expect(result.available).toBe(false)
    })
  })

  describe('refreshToken', () => {
    it('throws when no refresh token in store', async () => {
      vi.mocked(useAuthStore.getState).mockReturnValueOnce({
        refreshToken: null,
        logout: vi.fn(),
        token: null,
        user: null,
        isAuthenticated: false,
        login: vi.fn(),
        updateUser: vi.fn(),
        setTokens: vi.fn(),
      })

      await expect(authService.refreshToken()).rejects.toThrow('No refresh token')
    })

    it('calls /auth/refresh with stored token', async () => {
      const mockLogout = vi.fn()
      vi.mocked(useAuthStore.getState).mockReturnValue({
        refreshToken: 'stored-refresh-token',
        logout: mockLogout,
        token: 'old-token',
        user: null,
        isAuthenticated: true,
        login: vi.fn(),
        updateUser: vi.fn(),
        setTokens: vi.fn(),
      })
      mockedClient.post.mockResolvedValueOnce({
        data: {
          accessToken: 'new-token',
          refreshToken: 'new-refresh',
          user: { id: 1, username: 'u', nickname: 'n' },
        },
      })

      const result = await authService.refreshToken()
      expect(mockedClient.post).toHaveBeenCalledWith('/auth/refresh', { refreshToken: 'stored-refresh-token' }, undefined)
      expect(result.accessToken).toBe('new-token')
    })
  })
})
