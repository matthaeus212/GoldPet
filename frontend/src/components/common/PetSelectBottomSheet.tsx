import type { Pet } from '../../services/petService';
import './PetSelectBottomSheet.css';

interface PetSelectBottomSheetProps {
  isOpen: boolean;
  pets: Pet[];
  selectedPetId?: number;
  onSelect: (petId: number) => void;
  onClose: () => void;
}

export function PetSelectBottomSheet({
  isOpen,
  pets,
  selectedPetId,
  onSelect,
  onClose,
}: PetSelectBottomSheetProps) {
  if (!isOpen) return null;

  return (
    <div className="pet-sheet-backdrop" onClick={onClose}>
      <div className="pet-sheet" onClick={(e) => e.stopPropagation()}>
        <div className="pet-sheet-handle" />
        <div className="pet-sheet-menu">
          <div className="pet-sheet-label">반려동물 선택</div>
          <ul className="pet-sheet-list">
            {pets.map((pet, index) => (
              <li key={pet.id}>
                {index > 0 && <div className="pet-sheet-divider" />}
                <button
                  type="button"
                  className={`pet-sheet-item${selectedPetId === pet.id ? ' selected' : ''}`}
                  onClick={() => {
                    onSelect(pet.id);
                    onClose();
                  }}
                >
                  {pet.name}
                </button>
              </li>
            ))}
          </ul>
        </div>
      </div>
    </div>
  );
}
