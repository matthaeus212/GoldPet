import { useState, useRef, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { useQuery, useMutation } from '@tanstack/react-query';
import { petService, type Pet, FALLBACK_ATTRIBUTES_SCHEMA } from '../../services/petService';
import { aiProfileService } from '../../services/aiProfileService';
import { useAlert } from '../../contexts/AlertContext';
import { SubPageLayout } from '../../components/layout/SubPageLayout';
import CustomSelect from '../../components/common/CustomSelect';
import { AIProfileTabMenu } from './components/AIProfileTabMenu';
import apiClient from '../../services/api/client';
import './AIProfilePage.css';

export default function AIProfileSimplePage() {
  const navigate = useNavigate();
  const { showAlert } = useAlert();

  const [selectedPetId, setSelectedPetId] = useState<number | null>(null);
  const [petName, setPetName] = useState('');
  const [speciesType, setSpeciesType] = useState<'dog' | 'cat' | 'other'>('dog');
  const [breedId, setBreedId] = useState<number | null>(null);
  const [uploadedImages, setUploadedImages] = useState<(string | null)[]>([null, null, null, null]);
  const [uploading, setUploading] = useState<boolean[]>([false, false, false, false]);
  const fileInputRefs = useRef<(HTMLInputElement | null)[]>([null, null, null, null]);

  const { data: pets = [] } = useQuery({
    queryKey: ['pets', 'my'],
    queryFn: () => petService.getMyPets(),
  });

  const speciesId = speciesType === 'dog' ? 1 : speciesType === 'cat' ? 2 : 3;

  const { data: breeds = [] } = useQuery({
    queryKey: ['breeds', speciesId],
    queryFn: () => petService.getBreeds(speciesId),
    enabled: !!speciesId,
  });

  const selectedPet: Pet | undefined = pets.find((p) => p.id === selectedPetId);

  // When pet is selected, auto-derive speciesType and populate images
  useEffect(() => {
    if (!selectedPet) return;

    if (selectedPet.speciesId === 1) setSpeciesType('dog');
    else if (selectedPet.speciesId === 2) setSpeciesType('cat');
    else if (selectedPet.speciesId != null) setSpeciesType('other');

    if (selectedPet.breedId) setBreedId(selectedPet.breedId);

    // Populate images from pet's profileImageUrls
    const petImages = selectedPet.profileImageUrls ?? [];
    if (selectedPet.profileImageUrl && !petImages.includes(selectedPet.profileImageUrl)) {
      petImages.unshift(selectedPet.profileImageUrl);
    }
    const newImages: (string | null)[] = [null, null, null, null];
    petImages.slice(0, 4).forEach((url, i) => { newImages[i] = url; });
    setUploadedImages(newImages);
    // eslint-disable-next-line react-hooks/exhaustive-deps -- keyed on selectedPetId; selectedPet is derived from it and changes only when the ID changes
  }, [selectedPetId]);

  const createMutation = useMutation({
    mutationFn: aiProfileService.createRequest,
    onSuccess: (data) => navigate(`/ai-profile/result/${data.id}`, { replace: true }),
    onError: (error: unknown) => {
      const err = error as { response?: { data?: { message?: string } } };
      const message = err?.response?.data?.message || '프로필 생성에 실패했습니다.';
      showAlert(message);
    },
  });

  const handleSlotClick = (index: number) => {
    fileInputRefs.current[index]?.click();
  };

  const handleFileChange = async (index: number, file: File | undefined) => {
    if (!file) return;

    const newUploading = [...uploading];
    newUploading[index] = true;
    setUploading(newUploading);

    try {
      const formData = new FormData();
      formData.append('file', file);
      formData.append('category', 'ai-profile');
      const response = await apiClient.post<{ url: string }>('/files/upload', formData, {
        headers: { 'Content-Type': 'multipart/form-data' },
      });
      const newImages = [...uploadedImages];
      newImages[index] = response.data.url;
      setUploadedImages(newImages);
    } catch {
      const objectUrl = URL.createObjectURL(file);
      const newImages = [...uploadedImages];
      newImages[index] = objectUrl;
      setUploadedImages(newImages);
    } finally {
      const finishedUploading = [...uploading];
      finishedUploading[index] = false;
      setUploading(finishedUploading);
    }
  };

  const handleRemoveImage = (e: React.MouseEvent, index: number) => {
    e.stopPropagation();
    const newImages = [...uploadedImages];
    newImages[index] = null;
    setUploadedImages(newImages);
    if (fileInputRefs.current[index]) {
      fileInputRefs.current[index]!.value = '';
    }
  };

  const traitAttributes = FALLBACK_ATTRIBUTES_SCHEMA.filter((a) => a.category === 'TRAIT');

  const hasPets = pets.length > 0;

  const handleSubmit = () => {
    if (hasPets && !selectedPet) {
      showAlert('반려동물을 선택해주세요.');
      return;
    }
    if (!hasPets && !petName.trim()) {
      showAlert('반려동물 이름을 입력해주세요.');
      return;
    }

    const filledImages = uploadedImages.filter(Boolean) as string[];
    const sourceImageUrl =
      filledImages[0] ||
      (selectedPet?.profileImageUrl) ||
      (selectedPet?.profileImageUrls?.[0]) ||
      '/assets/images/common/pet_none_img.svg';

    createMutation.mutate({
      petId: selectedPet?.id ?? undefined,
      type: 'IMAGE',
      sourceImageUrl,
      stylePrompt: undefined,
      mode: 'SIMPLE',
    });
  };

  return (
    <SubPageLayout title="">
      <AIProfileTabMenu activeTab="simple" />

      <div className="ai-form-section">
        <h2 className="ai-page-title">간단하게 만들기</h2>
        <p className="ai-page-subtitle">프로필 사진과 성향으로 간단하게 만들기</p>

        {/* A. Pet name - input or dropdown */}
        <div className="ai-form-field">
          <label className="ai-form-label">반려동물 이름</label>
          {pets.length > 0 ? (
            <CustomSelect
              value={selectedPetId ? String(selectedPetId) : ''}
              options={pets.map((p) => ({ label: p.name, value: String(p.id) }))}
              placeholder="반려동물을 선택해 주세요"
              onChange={(v) => setSelectedPetId(Number(v))}
            />
          ) : (
            <input
              type="text"
              className="ai-form-input"
              placeholder="반려동물 이름을 입력해 주세요"
              value={petName}
              onChange={(e) => setPetName(e.target.value)}
            />
          )}
        </div>

        {/* B. Species type tabs */}
        <div className="ai-form-field">
          <label className="ai-form-label">종류</label>
          <div className="ai-type-btns">
            {(['dog', 'cat', 'other'] as const).map((type) => (
              <button
                key={type}
                type="button"
                className={`ai-type-btn${speciesType === type ? ' ai-type-btn--active' : ''}`}
                onClick={() => setSpeciesType(type)}
              >
                {type === 'dog' ? '강아지' : type === 'cat' ? '고양이' : '기타'}
              </button>
            ))}
          </div>
        </div>

        {/* C. Breed dropdown */}
        <div className="ai-form-field">
          <label className="ai-form-label">품종</label>
          <CustomSelect
            value={breedId ? String(breedId) : ''}
            options={breeds.map((b) => ({ label: b.name, value: String(b.id) }))}
            placeholder="선택해 주세요"
            onChange={(v) => setBreedId(Number(v))}
          />
        </div>

        {/* D. Photo upload - 1 slot only (remaining slots commented out for future use) */}
        <div className="ai-form-field">
          <label className="ai-form-label">사진</label>
          <div className="ai-photo-grid ai-photo-grid--single">
            <div
              className="ai-photo-slot"
              onClick={() => !uploading[0] && handleSlotClick(0)}
            >
              {uploadedImages[0] ? (
                <>
                  <img src={uploadedImages[0]} alt="사진 1" />
                  <button
                    type="button"
                    className="ai-photo-delete"
                    onClick={(e) => handleRemoveImage(e, 0)}
                  >
                    ✕
                  </button>
                </>
              ) : (
                <div className="ai-photo-slot-icon">
                  {uploading[0] ? '⏳' : (
                    <svg width="32" height="32" viewBox="0 0 32 32" fill="none" xmlns="http://www.w3.org/2000/svg">
                      <rect x="5.33333" y="7.99935" width="21.3333" height="16" rx="2.66667" stroke="#614108" strokeWidth="2.66667"/>
                      <rect x="16" y="5.33301" width="8" height="4" rx="2" fill="#614108"/>
                      <circle cx="15.9998" cy="15.9993" r="4" stroke="#614108" strokeWidth="2.66667"/>
                      <circle cx="9.33333" cy="11.9993" r="1.33333" fill="#614108"/>
                    </svg>
                  )}
                </div>
              )}
              <input
                ref={(el) => { fileInputRefs.current[0] = el; }}
                type="file"
                accept="image/*"
                style={{ display: 'none' }}
                onChange={(e) => handleFileChange(0, e.target.files?.[0])}
              />
            </div>
          </div>
        </div>

        {/* E. Additional info cards (shown when pet is selected) */}
        {selectedPet && (() => {
          // Parse "Activity:ACTIVE,Friendliness:SOCIAL" into {Activity: "ACTIVE", ...}
          const parsedAttrs: Record<string, string> = {};
          selectedPet.temperamentTags?.forEach((tag) => {
            const parts = tag.split(':');
            if (parts.length === 2) {
              parsedAttrs[parts[0].trim()] = parts[1].trim();
            }
          });

          return (
            <div className="ai-form-field">
              <label className="ai-form-label">추가 정보</label>
              <div className="ai-info-cards">
                {traitAttributes.map((attr) => {
                  const rawValue = parsedAttrs[attr.code];
                  let displayValue = '미등록';
                  if (rawValue) {
                    if (attr.inputType === 'TEXT') {
                      displayValue = rawValue;
                    } else {
                      const option = attr.options.find((opt) => opt.value === rawValue);
                      displayValue = option ? option.label : rawValue;
                    }
                  }
                  return (
                    <div key={attr.code} className="ai-info-card">
                      <span className="ai-info-label">{attr.name}</span>
                      <span className="ai-info-value">{displayValue}</span>
                    </div>
                  );
                })}
              </div>
            </div>
          );
        })()}

        {/* Submit button */}
        <div className="ai-submit-btn-wrap">
          <button
            type="button"
            className="ai-submit-btn"
            onClick={handleSubmit}
            disabled={(!hasPets ? !petName.trim() : !selectedPet) || createMutation.isPending}
          >
            {createMutation.isPending ? 'AI 프로필 생성 중...' : '프로필 생성하기'}
          </button>
        </div>
      </div>
    </SubPageLayout>
  );
}
