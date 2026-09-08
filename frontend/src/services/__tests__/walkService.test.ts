import { describe, it, expect, vi, beforeEach } from 'vitest'
import { pathToCoords, getTodayStats } from '../walkService'

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
import { walkService } from '../walkService'

const mockedClient = vi.mocked(apiClient)

describe('walkService', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  describe('getWalkStats', () => {
    it('returns formatted walk stats', async () => {
      mockedClient.get.mockResolvedValueOnce({
        data: { totalDistanceKm: 3.567, totalCalories: 250, totalWalks: 10 },
      })

      const result = await walkService.getWalkStats()

      expect(mockedClient.get).toHaveBeenCalledWith('/walks/my/stats', undefined)
      expect(result.todayDistance).toBe('3.6')
      expect(result.todayCalories).toBe('250')
      expect(result.totalWalks).toBe(10)
    })

    it('returns zeros when data is empty', async () => {
      mockedClient.get.mockResolvedValueOnce({ data: {} })

      const result = await walkService.getWalkStats()
      expect(result.todayDistance).toBe('0.0')
      expect(result.todayCalories).toBe('0')
    })

    it('propagates error on failure', async () => {
      mockedClient.get.mockRejectedValueOnce(new Error('API error'))

      await expect(walkService.getWalkStats()).rejects.toThrow('API error')
    })
  })

  describe('getWalkRecords', () => {
    it('maps backend response to WalkRecord format', async () => {
      const backendWalks = [
        {
          id: 1,
          startTime: '2026-03-19T10:00:00',
          endTime: '2026-03-19T10:30:00',
          distanceKm: 2.5,
          durationSeconds: 1800,
          caloriesBurned: 150,
        },
      ]
      mockedClient.get.mockResolvedValueOnce({ data: { content: backendWalks } })

      const result = await walkService.getWalkRecords()

      expect(result).toHaveLength(1)
      expect(result[0].distance).toBe('2.5 Km')
      expect(result[0].duration).toBe('총 30분')
      expect(result[0].points).toBe('25G 적립')
    })

    it('handles flat array response', async () => {
      const backendWalks = [
        {
          id: 2,
          startTime: '2026-03-19T08:00:00',
          endTime: '2026-03-19T08:20:00',
          distanceKm: 1.0,
          durationSeconds: 1200,
        },
      ]
      mockedClient.get.mockResolvedValueOnce({ data: backendWalks })

      const result = await walkService.getWalkRecords()
      expect(result).toHaveLength(1)
      expect(result[0].id).toBe(2)
    })
  })

  describe('getWalkDetail', () => {
    it('returns walk session with mapped path', async () => {
      mockedClient.get.mockResolvedValueOnce({
        data: {
          id: 5,
          startTime: '2026-03-19T10:00:00',
          endTime: '2026-03-19T10:30:00',
          distanceKm: 2.0,
          caloriesBurned: 120,
          durationSeconds: 1800,
          path: [[37.5, 127.0], [37.51, 127.01]],
          spots: [],
        },
      })

      const result = await walkService.getWalkDetail(5)

      expect(mockedClient.get).toHaveBeenCalledWith('/walks/5', undefined)
      expect(result.id).toBe(5)
      expect(result.pathPoints).toEqual([{ lat: 37.5, lng: 127.0 }, { lat: 37.51, lng: 127.01 }])
    })

    it('throws on failure', async () => {
      mockedClient.get.mockRejectedValueOnce(new Error('not found'))

      await expect(walkService.getWalkDetail(999)).rejects.toThrow('산책 기록을 불러올 수 없습니다.')
    })
  })
})

describe('pathToCoords', () => {
  it('converts [[lat, lng]] array to {lat, lng} objects', () => {
    const result = pathToCoords([[37.5, 127.0], [37.51, 127.01]])
    expect(result).toEqual([
      { lat: 37.5, lng: 127.0 },
      { lat: 37.51, lng: 127.01 },
    ])
  })

  it('returns empty array for empty input', () => {
    expect(pathToCoords([])).toEqual([])
  })
})

describe('getTodayStats', () => {
  it('sums distance and calories for today', () => {
    const today = new Date()
    const todayStr = `${today.getFullYear()}.${String(today.getMonth() + 1).padStart(2, '0')}.${String(today.getDate()).padStart(2, '0')}`

    const records = [
      { id: 1, date: todayStr, distance: '2.0 Km', duration: '30분', points: '20G', distanceKm: 2.0, caloriesBurned: 100 },
      { id: 2, date: todayStr, distance: '1.5 Km', duration: '20분', points: '15G', distanceKm: 1.5, caloriesBurned: 80 },
      { id: 3, date: '2026.01.01', distance: '3.0 Km', duration: '40분', points: '30G', distanceKm: 3.0, caloriesBurned: 200 },
    ]

    const result = getTodayStats(records)
    expect(result.distance).toBeCloseTo(3.5)
    expect(result.calories).toBe(180)
  })

  it('returns zeros when no records today', () => {
    const result = getTodayStats([])
    expect(result.distance).toBe(0)
    expect(result.calories).toBe(0)
  })
})
