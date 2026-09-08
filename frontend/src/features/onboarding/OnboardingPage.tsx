import { useRef, useState, useEffect } from 'react';
import { OnboardingScreen1 } from './components/OnboardingScreen1';
import { OnboardingScreen2 } from './components/OnboardingScreen2';
import { OnboardingScreen3 } from './components/OnboardingScreen3';
import { OnboardingScreen4 } from './components/OnboardingScreen4';
import './OnboardingPage.css';

export const OnboardingPage = () => {
    const carouselRef = useRef<HTMLDivElement>(null);
    const [activeIndex, setActiveIndex] = useState(0);

    const handleNext = () => {
        if (!carouselRef.current) return;

        const nextIndex = activeIndex + 1;
        if (nextIndex < 4) {
            const scrollLeft = carouselRef.current.clientWidth * nextIndex;
            carouselRef.current.scrollTo({ left: scrollLeft, behavior: 'smooth' });
        }
    };

    // Track scroll position to update active index
    useEffect(() => {
        const carousel = carouselRef.current;
        if (!carousel) return;

        const handleScroll = () => {
            const scrollLeft = carousel.scrollLeft;
            const width = carousel.clientWidth;
            const index = Math.round(scrollLeft / width);
            setActiveIndex(index);
        };

        carousel.addEventListener('scroll', handleScroll);
        return () => carousel.removeEventListener('scroll', handleScroll);
    }, []);

    return (
        <div className="onboarding-page">
            <div className="onboarding-carousel" ref={carouselRef}>
                <OnboardingScreen1 onNext={handleNext} />
                <OnboardingScreen2 onNext={handleNext} />
                <OnboardingScreen3 onNext={handleNext} />
                <OnboardingScreen4 onNext={handleNext} />
            </div>
        </div>
    );
};

