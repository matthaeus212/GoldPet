import { describe, it, expect, vi, beforeEach } from 'vitest'

vi.mock('../api/client', () => {
  const client = {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    delete: vi.fn(),
    interceptors: {
      request: { use: vi.fn() },
      response: { use: vi.fn() },
    },
  }
  return { apiClient: client, default: client }
})

import { apiClient } from '../api/client'
import { communityService } from '../communityService'

const mockedClient = vi.mocked(apiClient)

const mockPost = {
  id: 1,
  authorId: 1,
  categoryId: 2,
  categoryName: '자유',
  title: '테스트 글',
  content: '내용',
  authorNickname: '작성자',
  viewCount: 10,
  likeCount: 3,
  commentCount: 1,
  postType: 'GENERAL' as const,
  isLiked: false,
  isCommentedByMe: false,
  isMine: true,
  imageUrls: [],
  createdAt: '2024-01-01',
}

describe('communityService', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  describe('getCategories', () => {
    it('fetches community categories', async () => {
      const mockCategories = [{ id: 1, name: '자유', slug: 'free', postCount: 10 }]
      mockedClient.get.mockResolvedValueOnce({ data: mockCategories })

      const result = await communityService.getCategories()

      expect(mockedClient.get).toHaveBeenCalledWith('/community/categories', undefined)
      expect(result).toHaveLength(1)
      expect(result[0].name).toBe('자유')
    })
  })

  describe('getPosts', () => {
    it('fetches posts with field mapping applied', async () => {
      const rawPost = { ...mockPost, authorId: 99, authorNickname: '매핑작성자', isLiked: true }
      mockedClient.get.mockResolvedValueOnce({
        data: { content: [rawPost], last: false, totalElements: 1 },
      })

      const result = await communityService.getPosts(2, 0, 20)

      expect(mockedClient.get).toHaveBeenCalledWith('/community/posts', {
        params: { page: 0, size: 20, categoryId: 2 },
      })
      expect(result.posts[0].authorNickname).toBe('매핑작성자')
      expect(result.posts[0].isLiked).toBe(true)
      expect(result.hasMore).toBe(true)
    })
  })

  describe('getPostDetail', () => {
    it('fetches post detail with comments', async () => {
      const mockDetail = {
        ...mockPost,
        authorId: 1,
        authorNickname: '작성자',
        comments: [{ id: 10, content: '댓글', authorNickname: '댓글러', isMine: false, replyCount: 0, isLiked: false, isRepliedByMe: false }],
      }
      mockedClient.get.mockResolvedValueOnce({ data: mockDetail })

      const result = await communityService.getPostDetail(1)

      expect(mockedClient.get).toHaveBeenCalledWith('/community/posts/1', undefined)
      expect(result.comments).toHaveLength(1)
      expect(result.comments[0].authorNickname).toBe('댓글러')
    })

    it('throws 게시글을 불러올 수 없습니다 on error', async () => {
      mockedClient.get.mockRejectedValueOnce(new Error('error'))

      await expect(communityService.getPostDetail(999)).rejects.toThrow('게시글을 불러올 수 없습니다.')
    })
  })

  describe('toggleLike', () => {
    it('posts to like endpoint and returns updated state', async () => {
      mockedClient.post.mockResolvedValueOnce({ data: { isLiked: true, likeCount: 4 } })

      const result = await communityService.toggleLike(1)

      expect(mockedClient.post).toHaveBeenCalledWith('/community/posts/1/like', undefined, undefined)
      expect(result.isLiked).toBe(true)
      expect(result.likeCount).toBe(4)
    })
  })

  describe('createComment', () => {
    it('posts comment to post endpoint', async () => {
      const mockComment = { id: 5, postId: 1, content: '좋아요', authorNickname: '유저', isAdopted: false, createdAt: '2024-01-01', likeCount: 0, isLiked: false, isRepliedByMe: false, depth: 0, replyCount: 0, isMine: true }
      mockedClient.post.mockResolvedValueOnce({ data: mockComment })

      const result = await communityService.createComment(1, '좋아요')

      expect(mockedClient.post).toHaveBeenCalledWith('/community/posts/1/comments', {
        content: '좋아요',
        parentCommentId: undefined,
      }, undefined)
      expect(result.id).toBe(5)
    })

    it('throws on comment failure', async () => {
      mockedClient.post.mockRejectedValueOnce(new Error('error'))

      await expect(communityService.createComment(1, '댓글')).rejects.toThrow('댓글 등록에 실패했습니다.')
    })
  })

  describe('deletePost', () => {
    it('deletes post by id', async () => {
      mockedClient.delete.mockResolvedValueOnce({ data: {} })

      await communityService.deletePost(1)

      expect(mockedClient.delete).toHaveBeenCalledWith('/community/posts/1', undefined)
    })

    it('throws on delete failure', async () => {
      mockedClient.delete.mockRejectedValueOnce(new Error('error'))

      await expect(communityService.deletePost(1)).rejects.toThrow('게시글 삭제에 실패했습니다.')
    })
  })

  describe('updatePost', () => {
    it('sends put request with updated data', async () => {
      mockedClient.put.mockResolvedValueOnce({ data: {} })

      await communityService.updatePost(1, { title: '수정된 제목', content: '수정된 내용' })

      expect(mockedClient.put).toHaveBeenCalledWith('/community/posts/1', {
        title: '수정된 제목',
        content: '수정된 내용',
      }, undefined)
    })
  })
})
