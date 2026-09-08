// Shared card flip animation timing constants
// Used with direct props on m.div (NOT variants - domAnimation doesn't support variants)

// Main card squeeze (scaleX)
export const FLIP_DURATION = 0.5; // 500ms total (250ms out + 250ms in)
export const FLIP_HALF_DURATION = 0.25; // Each half of the squeeze
export const FLIP_EASING: [number, number, number, number] = [0.4, 0.0, 0.2, 1]; // Material Design standard

// Staggered text info animation
export const INFO_DURATION = 0.35;
export const INFO_DELAY = 0.1; // 100ms after image squeeze starts
export const INFO_EASING: [number, number, number, number] = [0.4, 0.0, 0.2, 1];

// Profile tab slide
export const TAB_DURATION = 0.4;
export const TAB_EASING: [number, number, number, number] = [0.4, 0.0, 0.2, 1];
