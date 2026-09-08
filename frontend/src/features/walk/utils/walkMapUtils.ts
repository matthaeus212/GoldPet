export function spotMarkerImage(type: string): string {
    switch (type) {
        case 'PEE': return '/assets/images/walk/marker_pee.png';
        case 'POOP': return '/assets/images/walk/marker_poop.png';
        case 'PHOTO': return '/assets/images/walk/marker_camera.png';
        default: return '/assets/images/walk/marker_pee.png';
    }
}

export function spotTypeLabel(type: string): string {
    switch (type) {
        case 'PEE': return '쉬';
        case 'POOP': return '응가';
        case 'PHOTO': return '사진';
        case 'PROBLEM': return '문제';
        default: return '기타';
    }
}

export function spotTypeMarkerIcon(type: string): string {
    return spotMarkerImage(type);
}

export function formatSpotTimestamp(ts: string): string {
    const d = new Date(ts);
    const y = d.getFullYear();
    const m = String(d.getMonth() + 1).padStart(2, '0');
    const day = String(d.getDate()).padStart(2, '0');
    const h = String(d.getHours()).padStart(2, '0');
    const min = String(d.getMinutes()).padStart(2, '0');
    return `${y}.${m}.${day} ${h}:${min}`;
}

export function petCircleMarkerHtml(variant: 'start' | 'end', imageUrl?: string | null): string {
    const imgSrc = imageUrl || '/assets/images/common/pet_none_img.svg';
    return `<div class="walk_pet_circle walk_pet_circle--${variant}"><img src="${imgSrc}" alt="" /></div>`;
}

/** Create a Google Maps Polyline from an array of lat/lng points. */
export function createPolylinePath(
    map: google.maps.Map,
    points: Array<{lat: number, lng: number}>,
    options?: { strokeColor?: string; strokeWeight?: number; strokeOpacity?: number }
): google.maps.Polyline {
    const { strokeColor = '#6B9A2B', strokeWeight = 4, strokeOpacity = 0.9 } = options || {};
    const path = points.map(p => new google.maps.LatLng(p.lat, p.lng));
    const polyline = new google.maps.Polyline({
        path,
        strokeColor,
        strokeWeight,
        strokeOpacity,
        map,
    });
    return polyline;
}

/** Snap a point to the nearest position on a polyline path.
 *  GPS jitter means spot coords differ slightly from the tracked route;
 *  at high zoom this offset becomes visible. Snapping eliminates it. */
export function snapToPath(lat: number, lng: number, path: { lat: number; lng: number }[]): { lat: number; lng: number } {
    if (path.length === 0) return { lat, lng };
    let minDist = Infinity;
    let closest = { lat, lng };
    for (let i = 0; i < path.length - 1; i++) {
        const a = path[i], b = path[i + 1];
        const dx = b.lng - a.lng, dy = b.lat - a.lat;
        const lenSq = dx * dx + dy * dy;
        let t = lenSq === 0 ? 0 : ((lng - a.lng) * dx + (lat - a.lat) * dy) / lenSq;
        t = Math.max(0, Math.min(1, t));
        const projLat = a.lat + t * dy;
        const projLng = a.lng + t * dx;
        const dist = (lat - projLat) ** 2 + (lng - projLng) ** 2;
        if (dist < minDist) {
            minDist = dist;
            closest = { lat: projLat, lng: projLng };
        }
    }
    return closest;
}
