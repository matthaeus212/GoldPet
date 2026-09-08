import React from 'react';
import type { Place } from '../../../services/checkinService';
import '../CheckInPage.css';

interface PlaceCardProps {
  place: Place;
  onClick: () => void;
}

const PlaceCard: React.FC<PlaceCardProps> = ({ place, onClick }) => {
  const getCategoryClass = (category: string): string => {
    const categoryMap: Record<string, string> = {
      카페: 'category-cafe',
      동물병원: 'category-hospital',
      공원: 'category-park',
      반려동물용품점: 'category-shop',
    };
    return categoryMap[category] || 'category-cafe';
  };

  return (
    <div className="place-card" onClick={onClick}>
      <img
        src={place.imageUrl || '/assets/images/placeholder-place.png'}
        alt={place.name}
        className="place-image"
      />
      <div className="place-info">
        <div className="place-name">{place.name}</div>
        <span className={`place-category ${getCategoryClass(place.category)}`}>
          {place.category}
        </span>
        <div className="place-address">{place.address}</div>
        <div className="place-stats">체크인 {place.checkinCount}회</div>
      </div>
    </div>
  );
};

export default PlaceCard;
