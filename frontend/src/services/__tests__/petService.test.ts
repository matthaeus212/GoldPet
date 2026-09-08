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
import { petService } from '../petService'

const mockedClient = vi.mocked(apiClient)

const mockPet = {
  id: 1,
  name: '코코',
  species: '강아지',
  speciesId: 1,
  breed: '말티즈',
  breedId: 2,
  gender: 'FEMALE' as const,
  birthDate: '2022-05-01',
  weightKg: 5,
  isNeutered: true,
}

describe('petService', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  describe('getSpecies', () => {
    it('fetches species list', async () => {
      const mockSpecies = [{ id: 1, code: 'DOG', name: '강아지', breedCount: 0 }]
      mockedClient.get.mockResolvedValueOnce({ data: mockSpecies })

      const result = await petService.getSpecies()

      expect(mockedClient.get).toHaveBeenCalledWith('/pets/species', undefined)
      expect(result).toHaveLength(1)
      expect(result[0].code).toBe('DOG')
    })
  })

  describe('getBreeds', () => {
    it('fetches all breeds when no speciesId given', async () => {
      mockedClient.get.mockResolvedValueOnce({ data: [{ id: 1, speciesId: 1, name: '말티즈' }] })

      const result = await petService.getBreeds()

      expect(mockedClient.get).toHaveBeenCalledWith('/pets/breeds', { params: undefined })
      expect(result).toHaveLength(1)
    })

    it('fetches breeds filtered by speciesId', async () => {
      mockedClient.get.mockResolvedValueOnce({ data: [{ id: 1, speciesId: 1, name: '말티즈' }] })

      await petService.getBreeds(1)

      expect(mockedClient.get).toHaveBeenCalledWith('/pets/breeds', { params: { speciesId: 1 } })
    })
  })

  describe('getMyPets', () => {
    it('fetches current user pets', async () => {
      mockedClient.get.mockResolvedValueOnce({ data: [mockPet] })

      const result = await petService.getMyPets()

      expect(mockedClient.get).toHaveBeenCalledWith('/pets/my', undefined)
      expect(result[0].name).toBe('코코')
    })
  })

  describe('getPet', () => {
    it('fetches a single pet by id', async () => {
      mockedClient.get.mockResolvedValueOnce({ data: mockPet })

      const result = await petService.getPet(1)

      expect(mockedClient.get).toHaveBeenCalledWith('/pets/1', undefined)
      expect(result.id).toBe(1)
    })

    it('throws 반려동물 정보를 불러올 수 없습니다 on error', async () => {
      mockedClient.get.mockRejectedValueOnce(new Error('not found'))

      await expect(petService.getPet(999)).rejects.toThrow('반려동물 정보를 불러올 수 없습니다.')
    })
  })

  describe('createPet', () => {
    it('posts new pet data and returns created pet', async () => {
      mockedClient.post.mockResolvedValueOnce({ data: mockPet })

      const result = await petService.createPet({ name: '코코', speciesId: 1 })

      expect(mockedClient.post).toHaveBeenCalledWith('/pets', { name: '코코', speciesId: 1 }, undefined)
      expect(result.name).toBe('코코')
    })

    it('throws on create failure', async () => {
      mockedClient.post.mockRejectedValueOnce(new Error('error'))

      await expect(petService.createPet({ name: '코코', speciesId: 1 })).rejects.toThrow('반려동물 등록에 실패했습니다.')
    })
  })

  describe('updatePet', () => {
    it('sends put request and returns updated pet', async () => {
      const updated = { ...mockPet, name: '초코' }
      mockedClient.put.mockResolvedValueOnce({ data: updated })

      const result = await petService.updatePet(1, { name: '초코' })

      expect(mockedClient.put).toHaveBeenCalledWith('/pets/1', { name: '초코' }, undefined)
      expect(result.name).toBe('초코')
    })
  })

  describe('deletePet', () => {
    it('deletes pet by id', async () => {
      mockedClient.delete.mockResolvedValueOnce({ data: {} })

      await petService.deletePet(1)

      expect(mockedClient.delete).toHaveBeenCalledWith('/pets/1', undefined)
    })

    it('throws on delete failure', async () => {
      mockedClient.delete.mockRejectedValueOnce(new Error('error'))

      await expect(petService.deletePet(1)).rejects.toThrow('반려동물 삭제에 실패했습니다.')
    })
  })
})
