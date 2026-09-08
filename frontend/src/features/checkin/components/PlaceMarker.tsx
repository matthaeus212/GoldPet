import React from 'react';
import type { Place } from '../../../services/checkinService';
import '../CheckInPage.css';

interface PlaceMarkerProps {
  place: Place;
  onClick: () => void;
}

const PlaceMarker: React.FC<PlaceMarkerProps> = ({ place, onClick }) => {
  const getCategoryIcon = (category: string): string => {
    const categoryMap: Record<string, string> = {
      카페: 'cafe',
      동물병원: 'hospital',
      공원: 'park',
      반려동물용품점: 'shop',
    };
    return categoryMap[category] || 'cafe';
  };

  return (
    <div className="place-marker" onClick={onClick}>
      <div className="place-marker-icon">
        <img
          src={`/assets/images/checkin/${getCategoryIcon(place.category)}_icon.svg`}
          alt={place.category}
        />
      </div>
      <span className="place-marker-label">{place.name}</span>
    </div>
  );
};

export default PlaceMarker;
