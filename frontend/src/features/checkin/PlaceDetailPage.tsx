import React, { useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { checkinService } from '../../services/checkinService';
import CheckInButton from './components/CheckInButton';
import './CheckInPage.css';

const PlaceDetailPage: React.FC = () => {
  const { placeId } = useParams<{ placeId: string }>();
  const navigate = useNavigate();
  const [showSuccess, setShowSuccess] = useState(false);

  const placeIdNum = placeId ? parseInt(placeId, 10) : 0;

  const { data: place, isLoading: placeLoading, error: placeError } = useQuery({
    queryKey: ['place', placeIdNum],
    queryFn: () => checkinService.getPlace(placeIdNum),
    enabled: placeIdNum > 0,
  });

  const { data: checkIns, isLoading: checkInsLoading } = useQuery({
    queryKey: ['placeCheckIns', placeIdNum],
    queryFn: () => checkinService.getPlaceCheckIns(placeIdNum),
    enabled: placeIdNum > 0,
  });

  const getCategoryClass = (category: string): string => {
    const categoryMap: Record<string, string> = {
      카페: 'category-cafe',
      동물병원: 'category-hospital',
      공원: 'category-park',
      반려동물용품점: 'category-shop',
    };
    return categoryMap[category] || 'category-cafe';
  };

  const formatCheckInTime = (timestamp: string): string => {
    const date = new Date(timestamp);
    const now = new Date();
    const diff = now.getTime() - date.getTime();
    const minutes = Math.floor(diff / 60000);
    const hours = Math.floor(diff / 3600000);
    const days = Math.floor(diff / 86400000);

    if (minutes < 60) return `${minutes}분 전`;
    if (hours < 24) return `${hours}시간 전`;
    return `${days}일 전`;
  };

  const handleCheckInSuccess = () => {
    setShowSuccess(true);
    setTimeout(() => setShowSuccess(false), 3000);
  };

  if (placeLoading) {
    return (
      <div className="checkin-container">
        <div className="checkin-loading">로딩 중...</div>
      </div>
    );
  }

  if (placeError || !place) {
    return (
      <div className="checkin-container">
        <div className="checkin-error">
          장소 정보를 불러올 수 없습니다.
        </div>
        <button
          onClick={() => navigate('/places')}
          className="checkin-btn"
          style={{ marginTop: '4.44vw' }}
        >
          목록으로 돌아가기
        </button>
      </div>
    );
  }

  return (
    <div className="checkin-container">
      <div style={{ paddingTop: '4.44vw' }}>
        {showSuccess && (
          <div
            style={{
              backgroundColor: '#4CAF50',
              color: '#fff',
              padding: '3.33vw 4.44vw',
              borderRadius: '2.22vw',
              marginBottom: '4.44vw',
              fontSize: '3.89vw',
              textAlign: 'center',
            }}
          >
            ✓ 체크인이 완료되었습니다!
          </div>
        )}

        <div className="place-detail-info">
          <img
            src={place.imageUrl || '/assets/images/placeholder-place.png'}
            alt={place.name}
            className="place-detail-image"
          />
          <div className="place-detail-name">{place.name}</div>
          <span
            className={`place-detail-category ${getCategoryClass(
              place.category
            )}`}
          >
            {place.category}
          </span>
          <div className="place-detail-address">{place.address}</div>
          <div className="place-detail-description">{place.description}</div>
          <div className="place-detail-stats">
            총 체크인 {place.checkinCount}회
          </div>
        </div>

        <CheckInButton
          placeId={placeIdNum}
          placeName={place.name}
          onSuccess={handleCheckInSuccess}
        />

        <div className="checkin-history">
          <div className="checkin-history-title">최근 체크인</div>

          {checkInsLoading && (
            <div style={{ textAlign: 'center', padding: '4.44vw 0', color: '#727272', fontSize: '3.89vw' }}>
              로딩 중...
            </div>
          )}

          {checkIns && checkIns.length === 0 && (
            <div style={{ textAlign: 'center', padding: '4.44vw 0', color: '#727272', fontSize: '3.89vw' }}>
              아직 체크인 기록이 없습니다.
            </div>
          )}

          {checkIns?.map((checkIn) => (
            <div key={checkIn.id} className="checkin-history-item">
              <div className="checkin-header">
                <span className="nickname">{checkIn.userNickname}</span>
                <span className="time">
                  {formatCheckInTime(checkIn.createdAt ?? '')}
                </span>
              </div>
              {checkIn.memo && <div className="memo">{checkIn.memo}</div>}
            </div>
          ))}
        </div>
      </div>
    </div>
  );
};

export default PlaceDetailPage;
