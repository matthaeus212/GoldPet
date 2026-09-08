import {
    Chart as ChartJS,
    CategoryScale,
    LinearScale,
    PointElement,
    LineElement,
    Title,
    Tooltip,
    Legend,
    Filler,
    ScatterController,
} from 'chart.js';
import { Line, Scatter } from 'react-chartjs-2';

ChartJS.register(
    CategoryScale,
    LinearScale,
    PointElement,
    LineElement,
    ScatterController,
    Title,
    Tooltip,
    Legend,
    Filler,
);

export interface WalkDataPoint {
    label: string; // x-axis label
    value: number; // distance in km
}

interface WalkChartProps {
    period: 'daily' | 'weekly' | 'monthly';
    data: WalkDataPoint[];
}

const LINE_COLOR = '#6B9A2B';
const DOT_COLOR = '#6B9A2B';
const GRID_COLOR = 'rgba(80,80,80,0.12)';
const TEXT_COLOR = '#505050';

const commonOptions = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: {
        legend: { display: false },
        tooltip: {
            callbacks: {
                label: (ctx: { parsed: { y: number | null } }) => `${(ctx.parsed.y ?? 0).toFixed(2)} km`,
            },
        },
    },
    scales: {
        x: {
            grid: { color: GRID_COLOR },
            ticks: {
                color: TEXT_COLOR,
                font: { family: 'Pretendard, sans-serif', size: 11 },
                maxRotation: 0,
            },
            border: { display: false },
        },
        y: {
            position: 'right' as const,
            grid: { color: GRID_COLOR },
            ticks: {
                color: TEXT_COLOR,
                font: { family: 'Pretendard, sans-serif', size: 11 },
                count: 6,
            },
            border: { display: false },
            beginAtZero: true,
        },
    },
};

export function WalkChart({ period, data }: WalkChartProps) {
    if (period === 'monthly') {
        // Scatter chart for monthly view
        const scatterData = {
            datasets: [
                {
                    label: '산책 거리',
                    data: data.map((d) => ({ x: parseFloat(d.label), y: d.value })),
                    backgroundColor: DOT_COLOR,
                    pointRadius: 5,
                    pointHoverRadius: 7,
                },
            ],
        };
        const scatterOptions = {
            responsive: true,
            maintainAspectRatio: false,
            plugins: {
                legend: { display: false },
                tooltip: {
                    callbacks: {
                        label: (ctx: { parsed: { x: number | null; y: number | null } }) =>
                            `${ctx.parsed.x ?? 0}일: ${(ctx.parsed.y ?? 0).toFixed(2)} km`,
                    },
                },
            },
            scales: {
                x: {
                    type: 'linear' as const,
                    min: 1,
                    max: 31,
                    grid: { color: GRID_COLOR },
                    ticks: {
                        color: TEXT_COLOR,
                        font: { family: 'Pretendard, sans-serif', size: 11 },
                        stepSize: 5,
                        callback: (val: number | string) => `${val}일`,
                    },
                    border: { display: false },
                },
                y: {
                    position: 'right' as const,
                    grid: { color: GRID_COLOR },
                    ticks: {
                        color: TEXT_COLOR,
                        font: { family: 'Pretendard, sans-serif', size: 11 },
                        count: 6,
                    },
                    border: { display: false },
                    beginAtZero: true,
                },
            },
        };
        return <Scatter data={scatterData} options={scatterOptions} />;
    }

    // Line chart for daily / weekly
    const lineData = {
        labels: data.map((d) => d.label),
        datasets: [
            {
                label: '산책 거리',
                data: data.map((d) => d.value),
                borderColor: LINE_COLOR,
                backgroundColor: LINE_COLOR,
                pointBackgroundColor: LINE_COLOR,
                pointRadius: 4,
                pointHoverRadius: 6,
                borderWidth: 2,
                tension: 0.3,
                fill: false,
            },
        ],
    };

    const lineOptions = period === 'daily'
        ? {
            ...commonOptions,
            scales: {
                ...commonOptions.scales,
                x: {
                    ...commonOptions.scales.x,
                    ticks: {
                        ...commonOptions.scales.x.ticks,
                        maxTicksLimit: 8,
                        callback: (val: number | string) => `${val}시`,
                    },
                },
            },
        }
        : commonOptions;

    return <Line data={lineData} options={lineOptions} />;
}

// --- Aggregation helpers ---

interface RawWalk {
    startTime: string;
    endTime?: string;
    distanceKm: number;
    durationSeconds: number;
    caloriesBurned?: number;
    goldEarned?: number;
    pathPoints?: { lat: number; lng: number }[];
}

/** Aggregate walks into daily chart data (x = hour 0-23) */
// eslint-disable-next-line react-refresh/only-export-components
export function aggregateDaily(walks: RawWalk[], targetDate: Date): WalkDataPoint[] {
    const hours: number[] = Array(24).fill(0);
    const dateStr = formatDateOnly(targetDate);

    for (const walk of walks) {
        const start = new Date(walk.startTime);
        if (formatDateOnly(start) !== dateStr) continue;
        const hour = start.getHours();
        hours[hour] += walk.distanceKm;
    }

    return hours.map((val, h) => ({
        label: `${h}`,
        value: Math.round(val * 100) / 100,
    }));
}

/** Aggregate walks into weekly chart data (x = date label for 7 days of the week) */
// eslint-disable-next-line react-refresh/only-export-components
export function aggregateWeekly(walks: RawWalk[], weekStart: Date): WalkDataPoint[] {
    const days: { label: string; value: number }[] = [];
    for (let i = 0; i < 7; i++) {
        const d = new Date(weekStart);
        d.setDate(d.getDate() + i);
        const label = `${String(d.getMonth() + 1).padStart(2, '0')}.${String(d.getDate()).padStart(2, '0')}`;
        const dateStr = formatDateOnly(d);
        const total = walks
            .filter((w) => formatDateOnly(new Date(w.startTime)) === dateStr)
            .reduce((acc, w) => acc + w.distanceKm, 0);
        days.push({ label, value: Math.round(total * 100) / 100 });
    }
    return days;
}

/** Aggregate walks into monthly chart data (x = day 1-31) */
// eslint-disable-next-line react-refresh/only-export-components
export function aggregateMonthly(walks: RawWalk[], year: number, month: number): WalkDataPoint[] {
    const daysInMonth = new Date(year, month, 0).getDate();
    const days: number[] = Array(daysInMonth + 1).fill(0);

    for (const walk of walks) {
        const start = new Date(walk.startTime);
        if (start.getFullYear() !== year || start.getMonth() + 1 !== month) continue;
        const day = start.getDate();
        days[day] += walk.distanceKm;
    }

    const result: WalkDataPoint[] = [];
    for (let d = 1; d <= daysInMonth; d++) {
        if (days[d] > 0) {
            result.push({ label: `${d}`, value: Math.round(days[d] * 100) / 100 });
        }
    }
    return result;
}

function formatDateOnly(date: Date): string {
    return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
}
