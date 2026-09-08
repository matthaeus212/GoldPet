import { Fragment } from 'react';
import { GpImage } from '../../../components/common/GpImage';
import type { HomeTodayWalk } from '../../../services/homeService';

interface TodayWalkCardProps {
    data: HomeTodayWalk;
    onCreateCourse: () => void;
    onStartWalk: () => void;
}

/** 마지막 글자의 받침 유무로 '와'/'과' 반환 */
function getPostposition(name: string): string {
    if (!name) return '와';
    const lastChar = name.charCodeAt(name.length - 1);
    if (lastChar < 0xac00 || lastChar > 0xd7a3) return '와';
    return (lastChar - 0xac00) % 28 === 0 ? '와' : '과';
}

export function TodayWalkCard({ data, onCreateCourse, onStartWalk }: TodayWalkCardProps) {
    const petName = data.petName ?? '반려동물';
    const headline = `${petName}${getPostposition(petName)} 함께 산책해요`;
    const subline = [data.petBreed, data.locationText ?? '위치 미설정']
        .filter(Boolean)
        .join(' ∙ ');

    const stats: { label: string; value: string }[] = [
        { label: '거리(km)', value: data.distanceKm.toFixed(1) },
        { label: '시간(분)', value: String(data.durationMinutes) },
        { label: '칼로리(Kcal)', value: data.caloriesBurned.toFixed(1) },
        { label: '적립(Gold)', value: String(data.earnedGold) },
    ];

    return (
        <div className="today_walk__card" data-testid="home-today-walk-card">
            <img className="today_walk__bg" src="/assets/images/home/walk_card_bg.svg" alt="" />

            <div className="today_walk__head">
                <div className="today_walk__avatar">
                    {data.petProfileImageUrl ? (
                        <GpImage
                            src={data.petProfileImageUrl}
                            thumbnailSrc={data.petProfileImageUrlThumbnail}
                            variant="thumbnail"
                            alt={petName}
                            loading="eager"
                            decoding="sync"
                        />
                    ) : (
                        <img src="/assets/images/common/pet_none_img02.svg" alt="" />
                    )}
                </div>
                <div className="today_walk__headtxt">
                    <strong>{headline}</strong>
                    <span>{subline}</span>
                </div>
            </div>

            <div className="today_walk__stats">
                {stats.map((stat, index) => (
                    <Fragment key={stat.label}>
                        {index > 0 && <span className="today_walk__divider" />}
                        <div className="today_walk__stat">
                            <span className="label">{stat.label}</span>
                            <span className="value">{stat.value}</span>
                        </div>
                    </Fragment>
                ))}
            </div>

            <div className="today_walk__actions">
                <button type="button" className="today_walk__btn--secondary" onClick={onCreateCourse}>
                    코스 만들기
                </button>
                <button type="button" className="today_walk__btn--primary" onClick={onStartWalk} data-testid="walk-start-button">
                    산책 시작하기
                </button>
            </div>
        </div>
    );
}
