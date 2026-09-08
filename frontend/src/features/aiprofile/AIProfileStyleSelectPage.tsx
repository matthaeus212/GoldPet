import { useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { useQuery, useMutation } from '@tanstack/react-query';
import { aiProfileService, type AIStyleOption } from '../../services/aiProfileService';
import { goldService } from '../../services/goldService';
import { useAlert } from '../../contexts/AlertContext';
import StyleCard from './components/StyleCard';
import PhotoUploader from './components/PhotoUploader';
import './AIProfilePage.css';

export default function AIProfileStyleSelectPage() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const { showAlert } = useAlert();
  const mode = searchParams.get('mode') || 'simple';

  const [selectedStyle, setSelectedStyle] = useState<AIStyleOption | null>(null);
  const [selectedPhoto, setSelectedPhoto] = useState<string>('');
  const [petType, setPetType] = useState<'dog' | 'cat' | 'other'>('dog');
  const [customPrompt, setCustomPrompt] = useState('');

  const { data: styles } = useQuery({
    queryKey: ['ai-profile', 'styles'],
    queryFn: () => aiProfileService.getStyles(),
  });

  const { data: goldBalance } = useQuery({
    queryKey: ['gold', 'balance'],
    queryFn: () => goldService.getBalance(),
  });

  const createMutation = useMutation({
    mutationFn: aiProfileService.createRequest,
    onSuccess: (data) => {
      navigate(`/ai-profile/result/${data.id}`, { replace: true });
    },
  });

  const handleSubmit = () => {
    if (mode === 'simple') {
      if (!selectedStyle || !selectedPhoto) {
        showAlert('스타일과 사진을 선택해주세요.');
        return;
      }

      if (goldBalance && goldBalance.balance < selectedStyle.goldCost) {
        showAlert('골드가 부족합니다.');
        return;
      }

      createMutation.mutate({
        petId: 1,
        type: 'IMAGE',
        sourceImageUrl: selectedPhoto,
        stylePrompt: selectedStyle.id,
      });
    } else {
      if (!customPrompt.trim()) {
        showAlert('프롬프트를 입력해주세요.');
        return;
      }

      createMutation.mutate({
        petId: 1,
        type: 'IMAGE',
        sourceImageUrl: '/assets/images/common/pet_none_img.svg',
        stylePrompt: customPrompt,
      });
    }
  };

  return (
    <div id="aiMake">
      <div className="ai_txt_wrap">
        <strong>{mode === 'simple' ? '스타일을 선택해주세요' : '원하는 프로필을 설명해주세요'}</strong>
      </div>

      {goldBalance && (
        <div className="gold_balance">
          <span className="label">보유 골드</span>
          <span className="amount">{goldBalance.balance} G</span>
        </div>
      )}

      {mode === 'simple' ? (
        <>
          <div className="style-grid">
            {styles?.map((style) => (
              <StyleCard
                key={style.id}
                style={style}
                selected={selectedStyle?.id === style.id}
                onClick={() => setSelectedStyle(style)}
              />
            ))}
          </div>

          {selectedStyle && (
            <>
              <div className="ai_txt_wrap" style={{ marginTop: '6.67vw' }}>
                <strong>사진을 선택해주세요</strong>
              </div>
              <PhotoUploader onPhotoSelected={setSelectedPhoto} currentPhoto={selectedPhoto} />
            </>
          )}

          {selectedStyle && (
            <div style={{ textAlign: 'center', margin: '4.44vw 0' }}>
              <p style={{ fontSize: '14px', color: 'var(--color-text-secondary)' }}>
                필요한 골드: <strong style={{ color: '#FF9800' }}>{selectedStyle.goldCost} G</strong>
              </p>
            </div>
          )}
        </>
      ) : (
        <>
          <div className="pet_type_btn" style={{ marginTop: '6.67vw' }}>
            <button
              type="button"
              className={petType === 'dog' ? 'active' : ''}
              onClick={() => setPetType('dog')}
            >
              강아지
            </button>
            <button
              type="button"
              className={petType === 'cat' ? 'active' : ''}
              onClick={() => setPetType('cat')}
            >
              고양이
            </button>
            <button
              type="button"
              className={petType === 'other' ? 'active' : ''}
              onClick={() => setPetType('other')}
            >
              기타
            </button>
          </div>

          <textarea
            className="prompt_txt"
            placeholder="원하는 스타일을 자세히 설명해주세요&#10;예: 우리 강아지를 귀여운 카툰 스타일로 그려주세요. 배경은 꽃밭이면 좋겠어요."
            value={customPrompt}
            onChange={(e) => setCustomPrompt(e.target.value)}
          />
        </>
      )}

      <button
        type="button"
        className="submit_btn"
        onClick={handleSubmit}
        disabled={createMutation.isPending || (mode === 'simple' && (!selectedStyle || !selectedPhoto))}
      >
        {createMutation.isPending ? '생성 중...' : '프로필 생성하기'}
      </button>
    </div>
  );
}
