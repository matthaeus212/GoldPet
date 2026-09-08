import { describe, it, expect, vi, beforeEach } from 'vitest'

vi.mock('../api/client', () => {
  const client = {
    get: vi.fn(),
    post: vi.fn(),
    delete: vi.fn(),
    interceptors: {
      request: { use: vi.fn() },
      response: { use: vi.fn() },
    },
  }
  return { apiClient: client, default: client }
})

import { apiClient } from '../api/client'
import { friendService } from '../friendService'

const mockedClient = vi.mocked(apiClient)

const mockFriend = {
  id: 1,
  nickname: '테스터',
  profileImages: [],
  ownerInterests: [],
  ownerHobbies: [],
  petName: '코코',
  petBreed: '말티즈',
  petAge: 2,
  petGender: 'FEMALE' as const,
  isNeutered: false,
  onWalking: false,
  description: '반가워요',
  tags: [],
  images: [],
  distance: 500,
  status: 'online' as const,
}

describe('friendService', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  describe('getFriends', () => {
    it('fetches friends with filter params', async () => {
      mockedClient.get.mockResolvedValueOnce({ data: { content: [mockFriend], last: true } })

      const result = await friendService.getFriends(JSON.stringify({ distance: 5000 }), 0, 20)

      expect(mockedClient.get).toHaveBeenCalledWith('/friends', {
        params: expect.objectContaining({ page: 0, size: 20, distance: 5000 }),
      })
      expect(result.content).toHaveLength(1)
      expect(result.last).toBe(true)
    })

    it('throws on fetch failure', async () => {
      mockedClient.get.mockRejectedValueOnce(new Error('network error'))

      await expect(friendService.getFriends(JSON.stringify({}))).rejects.toThrow()
    })
  })

  describe('getNearbyFriends', () => {
    it('fetches nearby users with lat/lon/radius params', async () => {
      mockedClient.get.mockResolvedValueOnce({ data: [mockFriend] })

      const result = await friendService.getNearbyFriends(37.5, 127.0, 5000)

      expect(mockedClient.get).toHaveBeenCalledWith('/users/search/nearby', {
        params: { lat: 37.5, lon: 127.0, radius: 5000 },
      })
      expect(result).toHaveLength(1)
    })
  })

  describe('searchFriends', () => {
    it('searches friends by query string', async () => {
      mockedClient.get.mockResolvedValueOnce({ data: [mockFriend] })

      const result = await friendService.searchFriends('코코', 10)

      expect(mockedClient.get).toHaveBeenCalledWith('/users/search', {
        params: { query: '코코', limit: 10 },
      })
      expect(result).toHaveLength(1)
    })
  })

  describe('sendFriendRequest', () => {
    it('posts like to target user', async () => {
      mockedClient.post.mockResolvedValueOnce({ data: {} })

      const result = await friendService.sendFriendRequest(99)

      expect(mockedClient.post).toHaveBeenCalledWith('/likes', { toUserId: 99 })
      expect(result.success).toBe(true)
    })

    it('throws on failure', async () => {
      mockedClient.post.mockRejectedValueOnce(new Error('error'))

      await expect(friendService.sendFriendRequest(99)).rejects.toThrow('친구 요청에 실패했습니다.')
    })
  })

  describe('likeUser', () => {
    it('posts to friends like endpoint and returns result', async () => {
      mockedClient.post.mockResolvedValueOnce({ data: { likeId: 1, toUserId: 5, isMutual: false } })

      const result = await friendService.likeUser(5)

      expect(mockedClient.post).toHaveBeenCalledWith('/friends/5/like', undefined, undefined)
      expect(result.isMutual).toBe(false)
    })

    it('forwards source as a query param when provided', async () => {
      mockedClient.post.mockResolvedValueOnce({ data: { likeId: 1, toUserId: 5, isMutual: false } })

      await friendService.likeUser(5, 'compatible')

      expect(mockedClient.post).toHaveBeenCalledWith('/friends/5/like', undefined, {
        params: { source: 'compatible' },
      })
    })
  })

  describe('getMutualLikes', () => {
    it('fetches mutual likes list', async () => {
      mockedClient.get.mockResolvedValueOnce({ data: [mockFriend] })

      const result = await friendService.getMutualLikes()

      expect(mockedClient.get).toHaveBeenCalledWith('/friends/likes/mutual', undefined)
      expect(result).toHaveLength(1)
    })
  })

  describe('checkLikeStatus', () => {
    it('returns true when user has liked target', async () => {
      mockedClient.get.mockResolvedValueOnce({ data: { hasLiked: true } })

      const result = await friendService.checkLikeStatus(10)

      expect(mockedClient.get).toHaveBeenCalledWith('/friends/10/like/status', undefined)
      expect(result).toBe(true)
    })

    it('returns false on error', async () => {
      mockedClient.get.mockRejectedValueOnce(new Error('error'))

      const result = await friendService.checkLikeStatus(10)

      expect(result).toBe(false)
    })
  })
})
