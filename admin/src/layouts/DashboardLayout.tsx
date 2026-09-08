import { useState } from 'react';
import { Link, Outlet, useLocation } from 'react-router-dom';
import {
  LayoutDashboard,
  Users,
  MessageSquare,
  MessageCircle,
  Megaphone,
  AlertTriangle,
  Coins,
  Map,
  Trophy,
  Award,
  Database,
  Settings,
  Menu,
  X,
  Bell,
  User,
  Sparkles,
  BellRing,
  Smile,
  MapPin,
  Microscope
} from 'lucide-react';
import clsx from 'clsx';
import { useAuth } from '../context/AuthContext';

const NAVIGATION = [
  { name: '대시보드', path: '/', icon: LayoutDashboard },
  { name: '내 정보', path: '/profile', icon: User },
  { name: '회원 관리', path: '/users', icon: Users },
  { name: '채팅 관리', path: '/chat', icon: MessageSquare },
  { name: '이모티콘 관리', path: '/emoticons', icon: Smile },
  { name: '커뮤니티 관리', path: '/community', icon: MessageCircle },
  { name: '마케팅 관리', path: '/marketing', icon: Megaphone },
  { name: '신고/콘텐츠', path: '/reports', icon: AlertTriangle },
  { name: 'Gold/결제', path: '/economy', icon: Coins },
  { name: 'AI 프로필', path: '/ai-profile', icon: Sparkles },
  { name: '산책/지도', path: '/lbs', icon: Map },
  { name: '코스 관리', path: '/courses', icon: MapPin },
  { name: '베스트 랭킹', path: '/walk-ranking', icon: Award },
  { name: '게이미피케이션', path: '/gamification', icon: Trophy },
  { name: '코드/데이터', path: '/data', icon: Database },
  { name: '공지/팝업 관리', path: '/notices', icon: BellRing },
  { name: '배변 건강 분석', path: '/stool-analysis', icon: Microscope },
  { name: '시스템 관리', path: '/system', icon: Settings },
];

export default function DashboardLayout() {
  const [isSidebarOpen, setSidebarOpen] = useState(true);
  const location = useLocation();
  const { logout } = useAuth();

  return (
    <div className="min-h-screen bg-gray-50 flex">
      {/* Sidebar */}
      <aside 
        className={clsx(
          "fixed inset-y-0 left-0 z-50 w-64 bg-white border-r border-gray-200 transform transition-transform duration-200 ease-in-out lg:relative lg:translate-x-0",
          !isSidebarOpen && "-translate-x-full"
        )}
      >
        <div className="h-16 flex items-center px-6 border-b border-gray-200">
          <span className="text-xl font-bold text-amber-500">GoldPet Admin</span>
        </div>

        <nav className="p-4 space-y-1 overflow-y-auto h-[calc(100vh-4rem)]">
          {NAVIGATION.map((item) => {
            const isActive = location.pathname === item.path || (item.path !== '/' && location.pathname.startsWith(item.path));
            const Icon = item.icon;
            
            return (
              <Link
                key={item.path}
                to={item.path}
                className={clsx(
                  "flex items-center px-4 py-3 text-sm font-medium rounded-lg transition-colors",
                  isActive 
                    ? "bg-amber-50 text-amber-700" 
                    : "text-gray-700 hover:bg-gray-100"
                )}
              >
                <Icon className="w-5 h-5 mr-3" />
                {item.name}
              </Link>
            );
          })}
        </nav>
      </aside>

      {/* Main Content */}
      <div className="flex-1 flex flex-col min-w-0 overflow-hidden">
        {/* Top Header */}
        <header className="bg-white border-b border-gray-200 h-16 flex items-center justify-between px-6">
          <button 
            onClick={() => setSidebarOpen(!isSidebarOpen)}
            className="lg:hidden p-2 rounded-md text-gray-400 hover:text-gray-500 hover:bg-gray-100"
          >
            {isSidebarOpen ? <X className="w-6 h-6" /> : <Menu className="w-6 h-6" />}
          </button>
          
          <div className="flex items-center space-x-4">
            <button className="p-2 text-gray-400 hover:text-gray-500 relative">
              <Bell className="w-6 h-6" />
              <span className="absolute top-1.5 right-1.5 block h-2.5 w-2.5 rounded-full bg-red-500 ring-2 ring-white"></span>
            </button>
            <div className="flex items-center space-x-2">
              <Link to="/profile" className="w-8 h-8 rounded-full bg-gray-200 flex items-center justify-center text-sm font-medium text-gray-600 hover:bg-gray-300 transition-colors">
                {useAuth().user?.name?.[0] || 'A'}
              </Link>
              <div className="flex flex-col items-start">
                <Link to="/profile" className="text-sm font-medium text-gray-700 hover:text-amber-600 transition-colors">
                  {useAuth().user?.name || 'Admin'}
                </Link>
                <button 
                  onClick={logout} 
                  className="text-xs text-red-500 hover:text-red-700"
                >
                  로그아웃
                </button>
              </div>
            </div>
          </div>
        </header>

        {/* Page Content */}
        <main className="flex-1 overflow-auto p-6">
          <Outlet />
        </main>
      </div>
    </div>
  );
}
