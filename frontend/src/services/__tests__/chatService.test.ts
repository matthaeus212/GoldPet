import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'

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
import { chatService } from '../chatService'

const mockedClient = vi.mocked(apiClient)

describe('chatService', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  describe('getChatRooms', () => {
    it('returns chat rooms list', async () => {
      const mockRooms = [
        { id: 1, name: '방1', lastMessage: '안녕', lastMessageTime: '2024-01-01', unreadCount: 0, participants: [], isGroup: false },
      ]
      mockedClient.get.mockResolvedValueOnce({ data: mockRooms })

      const result = await chatService.getChatRooms()

      expect(mockedClient.get).toHaveBeenCalledWith('/chat/rooms', undefined)
      expect(result).toHaveLength(1)
      expect(result[0].id).toBe(1)
    })
  })

  describe('getMessages', () => {
    it('fetches messages for a room with page parameter', async () => {
      const mockMessages = [
        { id: 1, roomId: 1, senderId: 1, senderNickname: '테스터', textContent: '안녕', timestamp: '2024-01-01', type: 'text' },
      ]
      mockedClient.get.mockResolvedValueOnce({ data: { content: mockMessages } })

      const result = await chatService.getMessages('1', 0)

      expect(mockedClient.get).toHaveBeenCalledWith('/chat/rooms/1/messages', { params: { page: 0 } })
      expect(result).toHaveLength(1)
    })

    it('handles flat array response (no content wrapper)', async () => {
      const mockMessages = [
        { id: 2, roomId: 1, senderId: 1, senderNickname: '유저', textContent: '테스트', timestamp: '2024-01-01', type: 'text' },
      ]
      mockedClient.get.mockResolvedValueOnce({ data: mockMessages })

      const result = await chatService.getMessages('1', 1)
      expect(result).toHaveLength(1)
    })
  })

  describe('sendMessage', () => {
    it('posts message to correct endpoint', async () => {
      const mockMsg = { id: 10, roomId: 1, senderId: 1, senderNickname: '유저', textContent: '안녕', timestamp: '2024-01-01', type: 'text' }
      mockedClient.post.mockResolvedValueOnce({ data: mockMsg })

      const result = await chatService.sendMessage('1', '안녕')

      expect(mockedClient.post).toHaveBeenCalledWith('/chat/rooms/1/messages', {
        content: '안녕',
        messageType: 'TEXT',
        fileId: undefined,
        emoticonId: undefined,
        replyToId: undefined,
      }, undefined)
      expect(result.id).toBe(10)
    })

    it('throws on send failure', async () => {
      mockedClient.post.mockRejectedValueOnce(new Error('network error'))

      await expect(chatService.sendMessage('1', '안녕')).rejects.toThrow('메시지 전송에 실패했습니다.')
    })
  })

  describe('markAsRead', () => {
    it('posts to read endpoint', async () => {
      mockedClient.post.mockResolvedValueOnce({ data: {} })

      await chatService.markAsRead('5')

      expect(mockedClient.post).toHaveBeenCalledWith('/chat/rooms/5/read', undefined, undefined)
    })

    it('silently ignores errors', async () => {
      mockedClient.post.mockRejectedValueOnce(new Error('network error'))

      // Should not throw
      await expect(chatService.markAsRead('5')).resolves.toBeUndefined()
    })
  })

  describe('markAsReadDebounced', () => {
    beforeEach(() => {
      vi.useFakeTimers()
      mockedClient.post.mockResolvedValue({ data: {} })
    })

    afterEach(() => {
      // Flush any in-flight timers before restoring real timers so state is clean.
      vi.runAllTimers()
      vi.useRealTimers()
    })

    it('collapses 10 rapid calls into a single POST after 300ms quiet', () => {
      for (let i = 0; i < 10; i++) {
        chatService.markAsReadDebounced('7')
      }
      // Still within debounce window → no HTTP call yet.
      expect(mockedClient.post).not.toHaveBeenCalled()

      vi.advanceTimersByTime(299)
      expect(mockedClient.post).not.toHaveBeenCalled()

      vi.advanceTimersByTime(1)
      expect(mockedClient.post).toHaveBeenCalledTimes(1)
      expect(mockedClient.post).toHaveBeenCalledWith('/chat/rooms/7/read', undefined, undefined)
    })

    it('keeps separate timers per room so concurrent rooms do not interfere', () => {
      chatService.markAsReadDebounced('1')
      chatService.markAsReadDebounced('2')

      vi.advanceTimersByTime(300)
      expect(mockedClient.post).toHaveBeenCalledTimes(2)
      expect(mockedClient.post).toHaveBeenCalledWith('/chat/rooms/1/read', undefined, undefined)
      expect(mockedClient.post).toHaveBeenCalledWith('/chat/rooms/2/read', undefined, undefined)
    })

    it('cancelMarkAsReadDebounced prevents pending call from firing', () => {
      chatService.markAsReadDebounced('9')
      chatService.cancelMarkAsReadDebounced('9')

      vi.advanceTimersByTime(500)
      expect(mockedClient.post).not.toHaveBeenCalled()
    })

    it('accepts override delay for testing / future tuning', () => {
      chatService.markAsReadDebounced('3', 50)

      vi.advanceTimersByTime(49)
      expect(mockedClient.post).not.toHaveBeenCalled()

      vi.advanceTimersByTime(1)
      expect(mockedClient.post).toHaveBeenCalledTimes(1)
    })
  })

  describe('leaveRoom', () => {
    it('deletes room membership', async () => {
      mockedClient.delete.mockResolvedValueOnce({ data: {} })

      await chatService.leaveRoom('3')

      expect(mockedClient.delete).toHaveBeenCalledWith('/chat/rooms/3/leave', undefined)
    })

    it('throws on leave failure', async () => {
      mockedClient.delete.mockRejectedValueOnce(new Error('error'))

      await expect(chatService.leaveRoom('3')).rejects.toThrow('채팅방 나가기에 실패했습니다.')
    })
  })

  describe('getOrCreateDirectRoom', () => {
    it('creates/returns direct room for user', async () => {
      const mockRoom = { id: 99, name: '1:1 채팅', lastMessage: '', lastMessageTime: '', unreadCount: 0, participants: [], isGroup: false }
      mockedClient.post.mockResolvedValueOnce({ data: mockRoom })

      const result = await chatService.getOrCreateDirectRoom(42)

      expect(mockedClient.post).toHaveBeenCalledWith('/chat/rooms/direct/42', undefined, undefined)
      expect(result.id).toBe(99)
    })

    it('throws 매칭된 상대가 아닙니다 on 400', async () => {
      mockedClient.post.mockRejectedValueOnce({ response: { status: 400 } })

      await expect(chatService.getOrCreateDirectRoom(42)).rejects.toThrow('매칭된 상대가 아닙니다.')
    })

    it('attaches code NO_MATCH and status 400 from errorCode payload', async () => {
      mockedClient.post.mockRejectedValueOnce({ response: { status: 400, data: { errorCode: 'NO_MATCH' } } })

      await expect(chatService.getOrCreateDirectRoom(42)).rejects.toMatchObject({
        message: '매칭된 상대가 아닙니다.',
        code: 'NO_MATCH',
        status: 400,
      })
    })

    it('attaches code FORBIDDEN and status 403 on blocked relationship', async () => {
      mockedClient.post.mockRejectedValueOnce({ response: { status: 403, data: { errorCode: 'FORBIDDEN' } } })

      await expect(chatService.getOrCreateDirectRoom(42)).rejects.toMatchObject({
        message: '채팅방 생성에 실패했습니다.',
        code: 'FORBIDDEN',
        status: 403,
      })
    })
  })
})
