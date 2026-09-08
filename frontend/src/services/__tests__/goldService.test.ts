import { describe, it, expect, vi, beforeEach } from 'vitest'

vi.mock('../api/client', () => {
  const client = {
    get: vi.fn(),
    post: vi.fn(),
    interceptors: {
      request: { use: vi.fn() },
      response: { use: vi.fn() },
    },
  }
  return { apiClient: client, default: client }
})

import { apiClient } from '../api/client'
import { goldService } from '../goldService'

const mockedClient = vi.mocked(apiClient)

describe('goldService', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  describe('getBalance', () => {
    it('fetches current gold balance', async () => {
      const mockBalance = { balance: 1000, totalCharged: 5000, totalSpent: 4000 }
      mockedClient.get.mockResolvedValueOnce({ data: mockBalance })

      const result = await goldService.getBalance()

      expect(mockedClient.get).toHaveBeenCalledWith('/gold/balance', undefined)
      expect(result.balance).toBe(1000)
      expect(result.totalCharged).toBe(5000)
    })
  })

  describe('getProducts', () => {
    it('fetches available gold products', async () => {
      const mockProducts = [
        { id: 'p1', name: '골드 100', goldAmount: 100, price: 1100, bonus: 0, productType: 'ONE_TIME', durationMonths: null, discountPercent: 0, monthlyPrice: null },
      ]
      mockedClient.get.mockResolvedValueOnce({ data: mockProducts })

      const result = await goldService.getProducts()

      expect(mockedClient.get).toHaveBeenCalledWith('/gold/products', undefined)
      expect(result).toHaveLength(1)
      expect(result[0].goldAmount).toBe(100)
    })
  })

  describe('getTransactions', () => {
    it('fetches transactions with pagination params', async () => {
      const mockPage = { content: [], totalPages: 0, totalElements: 0 }
      mockedClient.get.mockResolvedValueOnce({ data: mockPage })

      await goldService.getTransactions(0, 20)

      expect(mockedClient.get).toHaveBeenCalledWith('/gold/transactions', { params: { page: 0, size: 20 } })
    })

    it('includes type param when type is not ALL', async () => {
      const mockPage = { content: [], totalPages: 0, totalElements: 0 }
      mockedClient.get.mockResolvedValueOnce({ data: mockPage })

      await goldService.getTransactions(0, 10, 'CHARGE')

      expect(mockedClient.get).toHaveBeenCalledWith('/gold/transactions', {
        params: { page: 0, size: 10, type: 'CHARGE' },
      })
    })

    it('omits type param when type is ALL', async () => {
      const mockPage = { content: [], totalPages: 0, totalElements: 0 }
      mockedClient.get.mockResolvedValueOnce({ data: mockPage })

      await goldService.getTransactions(0, 10, 'ALL')

      expect(mockedClient.get).toHaveBeenCalledWith('/gold/transactions', {
        params: { page: 0, size: 10 },
      })
    })
  })

  describe('chargeGold', () => {
    it('posts charge request and returns transaction', async () => {
      const mockTx = { id: 1, type: 'CHARGE', amount: 500, description: '골드 충전', createdAt: '2024-01-01', balanceAfter: 1500, status: 'COMPLETED' }
      mockedClient.post.mockResolvedValueOnce({ data: mockTx })

      const result = await goldService.chargeGold({ amount: 500, paymentMethod: 'CARD' })

      expect(mockedClient.post).toHaveBeenCalledWith('/gold/charge', { amount: 500, paymentMethod: 'CARD' }, undefined)
      expect(result.type).toBe('CHARGE')
      expect(result.amount).toBe(500)
    })
  })

  describe('spendGold', () => {
    it('posts spend request and returns transaction', async () => {
      const mockTx = { id: 2, type: 'SPEND', amount: 100, description: '프리미엄 기능', createdAt: '2024-01-01', balanceAfter: 900, status: 'COMPLETED' }
      mockedClient.post.mockResolvedValueOnce({ data: mockTx })

      const result = await goldService.spendGold({ amount: 100, description: '프리미엄 기능' })

      expect(mockedClient.post).toHaveBeenCalledWith('/gold/spend', { amount: 100, description: '프리미엄 기능' }, undefined)
      expect(result.type).toBe('SPEND')
      expect(result.balanceAfter).toBe(900)
    })
  })
})
