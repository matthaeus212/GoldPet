import { useQuery } from '@tanstack/react-query';
import { dashboardService } from '../../services/dashboardService';
import { StatCard } from '../../components/common/StatCard';
import {
  Users,
  PawPrint,
  Footprints,
  MapPin,
  FileText,
  MessageCircle,
  AlertTriangle,
  Coins,
} from 'lucide-react';

export default function DashboardPage() {
  const { data: stats, isLoading: isStatsLoading } = useQuery({
    queryKey: ['dashboard', 'stats'],
    queryFn: dashboardService.getStats,
  });

  const { data: activities = [], isLoading: isActivitiesLoading } = useQuery({
    queryKey: ['dashboard', 'activity'],
    queryFn: () => dashboardService.getRecentActivity(10),
  });

  const isLoading = isStatsLoading || isActivitiesLoading;

  if (isLoading) {
    return <div className="flex items-center justify-center h-64">로딩 중...</div>;
  }

  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-bold text-gray-900">대시보드</h1>

      {/* Stats Grid */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
        <StatCard
          title="총 회원수"
          value={stats?.totalUsers ?? 0}
          icon={Users}
          trend={{ value: stats?.newUsersToday ?? 0, label: '오늘 신규' }}
        />
        <StatCard
          title="총 반려동물"
          value={stats?.totalPets ?? 0}
          icon={PawPrint}
        />
        <StatCard
          title="총 산책 횟수"
          value={stats?.totalWalks ?? 0}
          icon={Footprints}
        />
        <StatCard
          title="총 산책 거리"
          value={`${(stats?.totalDistance ?? 0).toFixed(1)}km`}
          icon={MapPin}
        />
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
        <StatCard
          title="게시글"
          value={stats?.totalPosts ?? 0}
          icon={FileText}
        />
        <StatCard
          title="댓글"
          value={stats?.totalComments ?? 0}
          icon={MessageCircle}
        />
        <StatCard
          title="미처리 신고"
          value={stats?.pendingReports ?? 0}
          icon={AlertTriangle}
        />
        <StatCard
          title="매출 (원)"
          value={stats?.revenue ?? 0}
          icon={Coins}
        />
      </div>

      {/* Recent Activity */}
      <div className="bg-white rounded-lg shadow-sm border border-gray-200 p-6">
        <h2 className="text-lg font-semibold mb-4">최근 활동</h2>
        <div className="space-y-4">
          {activities.map((activity) => (
            <div key={activity.id} className="flex items-center gap-4 py-2 border-b border-gray-100 last:border-0">
              <span className="text-xl">
                {activity.type === 'USER_JOIN' && '👋'}
                {activity.type === 'POST_CREATE' && '📝'}
                {activity.type === 'REPORT' && '⚠️'}
              </span>
              <div className="flex-1">
                <p className="text-sm text-gray-900">{activity.description}</p>
                <p className="text-xs text-gray-500">{activity.createdAt}</p>
              </div>
            </div>
          ))}
          {activities.length === 0 && (
            <p className="text-gray-500 text-center py-4">최근 활동이 없습니다.</p>
          )}
        </div>
      </div>
    </div>
  );
}
