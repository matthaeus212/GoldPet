import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { checkinService } from '../../services/checkinService';
import { CACHE_TIME } from '../../config/queryConfig';
import PlaceCard from './components/PlaceCard';
import './CheckInPage.css';

const categories = ['전체', '카페', '동물병원', '공원', '반려동물용품점'];

const PlaceListPage: React.FC = () => {
  const navigate = useNavigate();
  const [searchQuery, setSearchQuery] = useState('');
  const [selectedCategory, setSelectedCategory] = useState('전체');

  const { data: places, isLoading, error } = useQuery({
    queryKey: ['places', selectedCategory, searchQuery],
    queryFn: async () => {
      if (searchQuery.trim()) {
        return await checkinService.searchPlaces(searchQuery);
      }
      return await checkinService.getPlaces(
        selectedCategory === '전체' ? undefined : selectedCategory
      );
    },
    ...CACHE_TIME.DYNAMIC,
  });

  const handlePlaceClick = (placeId: number) => {
    navigate(`/places/${placeId}`);
  };

  return (
    <div className="checkin-container">
      <div style={{ paddingTop: '4.44vw' }}>
        <input
          type="text"
          className="place-search"
          placeholder="장소 검색..."
          value={searchQuery}
          onChange={(e) => setSearchQuery(e.target.value)}
        />

        <div className="category-filter">
          {categories.map((category) => (
            <button
              key={category}
              className={selectedCategory === category ? 'active' : ''}
              onClick={() => setSelectedCategory(category)}
            >
              {category}
            </button>
          ))}
        </div>

        {isLoading && <div className="checkin-loading">로딩 중...</div>}

        {error && (
          <div className="checkin-error">
            장소를 불러오는 중 오류가 발생했습니다.
          </div>
        )}

        {places && places.length === 0 && (
          <div className="place-list-empty">검색 결과가 없습니다.</div>
        )}

        <div className="place-list">
          {places?.map((place) => (
            <PlaceCard
              key={place.id}
              place={place}
              onClick={() => handlePlaceClick(place.id)}
            />
          ))}
        </div>
      </div>
    </div>
  );
};

export default PlaceListPage;
