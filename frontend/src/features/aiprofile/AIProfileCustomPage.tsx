import { useState, useRef, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { useQuery, useMutation } from '@tanstack/react-query';
import { petService } from '../../services/petService';
import { goldService } from '../../services/goldService';
import { aiProfileService } from '../../services/aiProfileService';
import { useAlert } from '../../contexts/AlertContext';
import { PAYMENT_ENABLED } from '../../config/featureFlags';
import { SubPageLayout } from '../../components/layout/SubPageLayout';
import CustomSelect from '../../components/common/CustomSelect';
import { AIProfileTabMenu } from './components/AIProfileTabMenu';
import apiClient from '../../services/api/client';
import { useKeyboardDismiss } from '../../hooks/useKeyboardDismiss';
import './AIProfilePage.css';

const CUSTOM_GOLD_COST = 100;
const MAX_CHARS = 500;

type PetType = 'dog' | 'cat' | 'other';

export default function AIProfileCustomPage() {
  const navigate = useNavigate();
  const { showAlert } = useAlert();

  const [selectedPetId, setSelectedPetId] = useState<number | null>(null);
  const [petName, setPetName] = useState('');
  const [petType, setPetType] = useState<PetType>('dog');
  const [uploadedImages, setUploadedImages] = useState<(string | null)[]>([null, null, null, null]);
  const [uploading, setUploading] = useState<boolean[]>([false, false, false, false]);
  const [prompt, setPrompt] = useState('');
  const fileInputRefs = useRef<(HTMLInputElement | null)[]>([null, null, null, null]);

  const { data: pets = [] } = useQuery({
    queryKey: ['pets', 'my'],
    queryFn: () => petService.getMyPets(),
  });

  const selectedPet = pets.find((p) => p.id === selectedPetId);

  // Auto-populate when pet is selected
  useEffect(() => {
    if (!selectedPet) return;
    if (selectedPet.speciesId === 1) setPetType('dog');
    else if (selectedPet.speciesId === 2) setPetType('cat');
    else if (selectedPet.speciesId != null) setPetType('other');

    const petImages = selectedPet.profileImageUrls ? [...selectedPet.profileImageUrls] : [];
    if (selectedPet.profileImageUrl && !petImages.includes(selectedPet.profileImageUrl)) {
      petImages.unshift(selectedPet.profileImageUrl);
    }
    const newImages: (string | null)[] = [null, null, null, null];
    petImages.slice(0, 4).forEach((url, i) => { newImages[i] = url; });
    setUploadedImages(newImages);
    // eslint-disable-next-line react-hooks/exhaustive-deps -- keyed on selectedPetId; selectedPet is derived from it and changes only when the ID changes
  }, [selectedPetId]);

  const { data: goldBalance } = useQuery({
    queryKey: ['gold', 'balance'],
    queryFn: () => goldService.getBalance(),
  });

  const createMutation = useMutation({
    mutationFn: aiProfileService.createRequest,
    onSuccess: (data) => {
      navigate(`/ai-profile/result/${data.id}`, { replace: true });
    },
    onError: (error: unknown) => {
      const err = error as { response?: { data?: { message?: string } } };
      const message = err?.response?.data?.message || '프로필 생성에 실패했습니다. 다시 시도해주세요.';
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

  const filledImages = uploadedImages.filter(Boolean) as string[];

  const handleSubmit = () => {
    if (!prompt.trim()) {
      showAlert('원하는 스타일을 설명해주세요.');
      return;
    }
    if (goldBalance && goldBalance.balance < CUSTOM_GOLD_COST) {
      showAlert(PAYMENT_ENABLED ? '골드가 부족합니다. 골드를 충전해주세요.' : '골드가 부족합니다. 산책·미션으로 골드를 모아보세요.');
      return;
    }
    const sourceImageUrl = filledImages[0] || null;
    createMutation.mutate({
      petId: selectedPetId ?? undefined,
      type: 'IMAGE',
      sourceImageUrl: sourceImageUrl ?? '/assets/images/common/pet_none_img.svg',
      stylePrompt: prompt.trim(),
      petType,
      mode: 'CUSTOM',
    });
  };

  const keyboardDismiss = useKeyboardDismiss();

  return (
    <SubPageLayout title="">
      <div {...keyboardDismiss}>
      <AIProfileTabMenu activeTab="custom" />
      <div className="ai-form-section">
        <div>
          <h2 className="ai-page-title">원하는 대로 만들기</h2>
          <p className="ai-page-subtitle">옵션 선택과 프롬프트를 입력하여 만들기</p>
        </div>

        {/* A. 반려동물 이름 - input or dropdown */}
        <div className="ai-form-field">
          <label className="ai-form-label">반려동물 이름</label>
          {pets.length > 0 ? (
            <CustomSelect
              value={selectedPetId ? String(selectedPetId) : ''}
              options={pets.map(p => ({ label: p.name, value: String(p.id) }))}
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

        {/* B. 종류 */}
        <div className="ai-form-field">
          <label className="ai-form-label">종류</label>
          <div className="ai-type-btns">
            {(['dog', 'cat', 'other'] as const).map(type => (
              <button key={type} type="button"
                className={`ai-type-btn${petType === type ? ' ai-type-btn--active' : ''}`}
                onClick={() => setPetType(type)}>
                {type === 'dog' ? '강아지' : type === 'cat' ? '고양이' : '기타'}
              </button>
            ))}
          </div>
        </div>

        {/* C. 사진 - 1장만 사용 */}
        <div className="ai-form-field">
          <label className="ai-form-label">사진</label>
          <div className="ai-photo-grid ai-photo-grid--single">
            <div className="ai-photo-slot" onClick={() => !uploading[0] && handleSlotClick(0)}>
              {uploadedImages[0] ? (
                <>
                  <img src={uploadedImages[0]} alt="사진 1" />
                  <button type="button" className="ai-photo-delete" onClick={(e) => handleRemoveImage(e, 0)}>✕</button>
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
                ref={el => { fileInputRefs.current[0] = el; }}
                type="file"
                accept="image/*"
                style={{ display: 'none' }}
                onChange={e => handleFileChange(0, e.target.files?.[0])}
              />
            </div>
          </div>
        </div>

        {/* D. 프롬프트 */}
        <div className="ai-form-field">
          <label className="ai-form-label">프롬프트</label>
          <textarea
            className="ai-prompt-textarea"
            placeholder={"원하는 스타일을 자유롭게 설명해 주세요\n\n예) 꽃밭에서 뛰어노는 귀여운 모습\n예) 우주복을 입고 달 위에 서 있는 멋진 모습\n예) 지브리 애니메이션 스타일의 따뜻한 그림"}
            value={prompt}
            maxLength={MAX_CHARS}
            onChange={(e) => setPrompt(e.target.value)}
          />
        </div>
      </div>

      <div className="ai-submit-btn-wrap">
        <button type="button" className="ai-submit-btn" onClick={handleSubmit}
          disabled={!prompt.trim() || createMutation.isPending}>
          {createMutation.isPending ? '프로필 생성 중...' : '프로필 생성하기'}
        </button>
      </div>
      </div>
    </SubPageLayout>
  );
}
