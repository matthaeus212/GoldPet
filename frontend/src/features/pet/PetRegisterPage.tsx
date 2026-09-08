import { useState, useEffect } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { useAlert } from '../../contexts/AlertContext';
import { useKeyboardDismiss } from '../../hooks/useKeyboardDismiss';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { petService } from '../../services/petService';
import type { CreatePetRequest } from '../../services/petService';
import { BackButton } from '../../components/common/BackButton';
import CustomSelect from '../../components/common/CustomSelect';
import './PetPage.css';

export default function PetRegisterPage() {
  const navigate = useNavigate();
  const { petId } = useParams<{ petId: string }>();
  const queryClient = useQueryClient();
  const { showAlert } = useAlert();
  const isEdit = !!petId;

  const [formData, setFormData] = useState<CreatePetRequest>({
    name: '',
    speciesId: 1,
    breedId: undefined,
    gender: undefined,
    birthDate: undefined,
    weightKg: undefined,
    isNeutered: false,
    profileImageUrl: undefined,
    temperamentTags: '',
  });

  const [imagePreview, setImagePreview] = useState<string | null>(null);

  // Fetch species list (static data - cache indefinitely)
  const { data: speciesList } = useQuery({
    queryKey: ['pets', 'species'],
    queryFn: petService.getSpecies,
    staleTime: Infinity,
  });

  // Fetch breeds based on selected species (static data - cache indefinitely)
  const { data: breedList } = useQuery({
    queryKey: ['pets', 'breeds', formData.speciesId],
    queryFn: () => petService.getBreeds(formData.speciesId),
    enabled: !!formData.speciesId,
    staleTime: Infinity,
  });

  // Fetch existing pet data for edit mode
  const { data: existingPet } = useQuery({
    queryKey: ['pets', petId],
    queryFn: () => petService.getPet(Number(petId)),
    enabled: isEdit,
  });

  // Populate form when editing
  useEffect(() => {
    if (existingPet) {
      const newFormData = {
        name: existingPet.name,
        speciesId: existingPet.speciesId || 1,
        breedId: existingPet.breedId,
        gender: existingPet.gender,
        birthDate: existingPet.birthDate,
        weightKg: existingPet.weightKg,
        isNeutered: existingPet.isNeutered,
        profileImageUrl: existingPet.profileImageUrl,
        temperamentTags: existingPet.temperamentTags?.join(',') || '',
      };
      const preview = existingPet.profileImageUrl;
      setTimeout(() => {
        setFormData(newFormData);
        if (preview) setImagePreview(preview);
      }, 0);
    }
  }, [existingPet]);

  const createMutation = useMutation({
    mutationFn: petService.createPet,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['pets', 'my'] });
      showAlert('반려동물이 등록되었습니다!', () => {
        navigate('/home');
      });
    },
    onError: (error: Error) => {
      showAlert(error.message);
    },
  });

  const updateMutation = useMutation({
    mutationFn: (data: CreatePetRequest) => petService.updatePet(Number(petId), data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['pets', 'my'] });
      queryClient.invalidateQueries({ queryKey: ['pets', petId] });
      showAlert('반려동물 정보가 수정되었습니다!', () => {
        navigate('/home');
      });
    },
    onError: (error: Error) => {
      showAlert(error.message);
    },
  });

  const handleImageChange = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (file) {
      // Preview
      const reader = new FileReader();
      reader.onloadend = () => {
        setImagePreview(reader.result as string);
      };
      reader.readAsDataURL(file);

      // Upload
      try {
        const url = await petService.uploadPetImage(file);
        setFormData({ ...formData, profileImageUrl: url });
      } catch {
        showAlert('이미지 업로드에 실패했습니다.');
      }
    }
  };

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();

    if (!formData.name.trim()) {
      showAlert('이름을 입력해주세요.');
      return;
    }

    if (isEdit) {
      updateMutation.mutate(formData);
    } else {
      createMutation.mutate(formData);
    }
  };

  const temperamentOptions = [
    '활발함', '얌전함', '친화적', '낯가림', '산책 좋아함', '실내 선호',
    '간식 좋아함', '공놀이 좋아함', '다른 동물과 친함', '사람 좋아함'
  ];

  const toggleTemperament = (tag: string) => {
    const currentTags = formData.temperamentTags?.split(',').filter(Boolean) || [];
    if (currentTags.includes(tag)) {
      setFormData({ ...formData, temperamentTags: currentTags.filter(t => t !== tag).join(',') });
    } else {
      setFormData({ ...formData, temperamentTags: [...currentTags, tag].join(',') });
    }
  };

  const selectedTags = formData.temperamentTags?.split(',').filter(Boolean) || [];

  const keyboardDismiss = useKeyboardDismiss();

  return (
    <div id="petRegisterPage" {...keyboardDismiss}>
      {/* Header */}
      <div className="pet_header">
        <BackButton onClick={() => navigate(-1)} />
        <h1>{isEdit ? '반려동물 수정' : '반려동물 등록'}</h1>
        <div style={{ width: 32 }} />
      </div>

      <form onSubmit={handleSubmit} className="pet_form">
        {/* Profile Image */}
        <div className="pet_image_section">
          <div className="pet_image_preview">
            {imagePreview ? (
              <img src={imagePreview} alt="반려동물" />
            ) : (
              <div className="placeholder">🐾</div>
            )}
          </div>
          <label className="image_upload_btn btn-effect">
            📷 사진 {imagePreview ? '변경' : '추가'}
            <input type="file" accept="image/jpeg,image/png,image/gif,image/webp,image/heic" onChange={handleImageChange} hidden />
          </label>
        </div>

        {/* Name */}
        <div className="form_group">
          <label>이름 <span className="required">*</span></label>
          <input
            type="text"
            placeholder="반려동물 이름"
            value={formData.name}
            onChange={(e) => setFormData({ ...formData, name: e.target.value })}
            maxLength={20}
          />
        </div>

        {/* Species */}
        <div className="form_group">
          <label>종류 <span className="required">*</span></label>
          <div className="species_buttons">
            {speciesList?.map((species) => (
              <button
                key={species.id}
                type="button"
                className={`species_btn ${formData.speciesId === species.id ? 'active' : ''}`}
                onClick={() => setFormData({ ...formData, speciesId: species.id, breedId: undefined })}
              >
                {species.code === 'DOG' && '🐕'}
                {species.code === 'CAT' && '🐈'}
                {species.code === 'OTHER' && '🐾'}
                {species.name}
              </button>
            ))}
          </div>
        </div>

        {/* Breed */}
        <div className="form_group">
          <label>품종</label>
          <CustomSelect
            value={formData.breedId ? String(formData.breedId) : ''}
            options={(breedList || []).map(breed => ({ label: breed.name, value: String(breed.id) }))}
            placeholder="품종 선택"
            onChange={(value) => setFormData({ ...formData, breedId: Number(value) || undefined })}
          />
        </div>

        {/* Gender */}
        <div className="form_group">
          <label>성별</label>
          <div className="gender_buttons">
            <button
              type="button"
              className={`gender_btn ${formData.gender === 'MALE' ? 'active' : ''}`}
              onClick={() => setFormData({ ...formData, gender: 'MALE' })}
            >
              ♂ 수컷
            </button>
            <button
              type="button"
              className={`gender_btn ${formData.gender === 'FEMALE' ? 'active' : ''}`}
              onClick={() => setFormData({ ...formData, gender: 'FEMALE' })}
            >
              ♀ 암컷
            </button>
          </div>
        </div>

        {/* Birth Date */}
        <div className="form_group">
          <label>생년월일</label>
          <input
            type="date"
            value={formData.birthDate || ''}
            onChange={(e) => setFormData({ ...formData, birthDate: e.target.value })}
            max={new Date().toISOString().split('T')[0]}
          />
        </div>

        {/* Weight */}
        <div className="form_group">
          <label>몸무게 (kg)</label>
          <input
            type="number"
            placeholder="예: 5.5"
            step="0.1"
            min="0"
            max="100"
            value={formData.weightKg || ''}
            onChange={(e) => setFormData({ ...formData, weightKg: Number(e.target.value) || undefined })}
          />
        </div>

        {/* Neutered */}
        <div className="form_group">
          <label className="checkbox_label">
            <input
              type="checkbox"
              checked={formData.isNeutered || false}
              onChange={(e) => setFormData({ ...formData, isNeutered: e.target.checked })}
            />
            <span>중성화 완료</span>
          </label>
        </div>

        {/* Temperament Tags */}
        <div className="form_group">
          <label>성격 태그</label>
          <div className="temperament_tags">
            {temperamentOptions.map((tag) => (
              <button
                key={tag}
                type="button"
                className={`tag_btn ${selectedTags.includes(tag) ? 'active' : ''}`}
                onClick={() => toggleTemperament(tag)}
              >
                {tag}
              </button>
            ))}
          </div>
        </div>

        {/* Submit Button */}
        <button
          type="submit"
          className="submit_btn btn-effect"
          disabled={createMutation.isPending || updateMutation.isPending}
        >
          {createMutation.isPending || updateMutation.isPending ? '저장 중...' : (isEdit ? '수정하기' : '등록하기')}
        </button>
      </form>
    </div>
  );
}
