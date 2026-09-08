import { useNavigate } from 'react-router-dom';

const QUICK_MENU = [
    { label: '산책 코스', icon: '/assets/images/home/quick_course.svg', color: '#EFDDFF', path: '/courses' },
    { label: '건강 체크', icon: '/assets/images/home/quick_health.svg', color: '#FFDDEA', path: '/health' },
    { label: '랭킹', icon: '/assets/images/home/quick_ranking.svg', color: '#DDF0FF', path: '/walk/ranking' },
    { label: '미션', icon: '/assets/images/home/quick_mission.svg', color: '#D8F9C3', path: '/missions' },
] as const;

export function HomeQuickMenu() {
    const navigate = useNavigate();

    return (
        <section className="home_quickmenu" data-testid="home-quick-menu">
            {QUICK_MENU.map((menu) => (
                <button
                    key={menu.label}
                    type="button"
                    className="home_quickmenu__item"
                    onClick={() => navigate(menu.path)}
                >
                    <span className="home_quickmenu__bg" style={{ background: menu.color }} />
                    <img src={menu.icon} alt="" />
                    <span className="home_quickmenu__label">{menu.label}</span>
                </button>
            ))}
        </section>
    );
}
