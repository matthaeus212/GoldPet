import { useState, useEffect } from 'react';
import { useQuery } from '@tanstack/react-query';
import { petService } from '../services/petService';
import type { Pet } from '../services/petService';

interface UsePetSelectorResult {
  pets: Pet[];
  selectedPet: Pet | null;
  setSelectedPet: (pet: Pet) => void;
  isLoading: boolean;
}

export function usePetSelector(): UsePetSelectorResult {
  const [selectedPet, setSelectedPet] = useState<Pet | null>(null);

  const { data: pets = [], isLoading } = useQuery({
    queryKey: ['pets', 'my'],
    queryFn: petService.getMyPets,
  });

  useEffect(() => {
    if (selectedPet == null && pets.length > 0) {
      // eslint-disable-next-line react-hooks/set-state-in-effect
      setSelectedPet(pets[0]);
    }
  }, [pets, selectedPet]);

  return {
    pets,
    selectedPet,
    setSelectedPet,
    isLoading,
  };
}
