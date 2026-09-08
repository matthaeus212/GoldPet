import React, { useState } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { checkinService } from '../../../services/checkinService';
import '../CheckInPage.css';

interface CheckInButtonProps {
  placeId: number;
  placeName: string;
  onSuccess: () => void;
}

const CheckInButton: React.FC<CheckInButtonProps> = ({
  placeId,
  placeName,
  onSuccess,
}) => {
  const [showConfirm, setShowConfirm] = useState(false);
  const [memo, setMemo] = useState('');
  const queryClient = useQueryClient();

  const checkInMutation = useMutation({
    mutationFn: () => checkinService.checkIn({ placeId, memo: memo || undefined }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['place', placeId] });
      queryClient.invalidateQueries({ queryKey: ['placeCheckIns', placeId] });
      queryClient.invalidateQueries({ queryKey: ['myCheckIns'] });
      queryClient.invalidateQueries({ queryKey: ['checkInCount'] });
      setShowConfirm(false);
      setMemo('');
      onSuccess();
    },
  });

  const handleCheckIn = () => {
    if (!showConfirm) {
      setShowConfirm(true);
      return;
    }
    checkInMutation.mutate();
  };

  const handleCancel = () => {
    setShowConfirm(false);
    setMemo('');
  };

  if (showConfirm) {
    return (
      <div style={{ marginBottom: '4.44vw' }}>
        <div
          style={{
            backgroundColor: '#fff',
            borderRadius: '4.44vw',
            padding: '4.44vw',
            marginBottom: '2.22vw',
          }}
        >
          <div
            style={{
              fontSize: '4.44vw',
              fontWeight: '600',
              color: 'var(--color-text-secondary)',
              marginBottom: '3.33vw',
            }}
          >
            {placeName}에 체크인하시겠습니까?
          </div>
          <textarea
            value={memo}
            onChange={(e) => setMemo(e.target.value)}
            placeholder="메모를 남겨보세요 (선택사항)"
            style={{
              width: '100%',
              minHeight: '22.22vw',
              padding: '3.33vw',
              borderRadius: '2.22vw',
              border: '1px solid var(--color-border)',
              fontSize: '3.89vw',
              resize: 'vertical',
            }}
          />
        </div>
        <div style={{ display: 'flex', gap: '2.22vw' }}>
          <button
            onClick={handleCancel}
            style={{
              flex: 1,
              height: '13.33vw',
              borderRadius: '13.33vw',
              backgroundColor: '#fff',
              border: '1px solid var(--color-border)',
              color: '#727272',
              fontSize: '4.44vw',
              fontWeight: '600',
            }}
          >
            취소
          </button>
          <button
            onClick={handleCheckIn}
            disabled={checkInMutation.isPending}
            className="checkin-btn"
            style={{ flex: 1 }}
          >
            {checkInMutation.isPending ? '체크인 중...' : '확인'}
          </button>
        </div>
      </div>
    );
  }

  return (
    <button
      onClick={handleCheckIn}
      className="checkin-btn"
      style={{ marginBottom: '4.44vw' }}
    >
      체크인하기
    </button>
  );
};

export default CheckInButton;
