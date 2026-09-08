
import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { useQueryClient } from '@tanstack/react-query';
import { notificationService } from '../../services/notificationService';
import type { Notification } from '../../services/notificationService';
import { noticeService, type AppNoticeResponse } from '../../services/noticeService';
import { SubPageLayout } from '../../components/layout/SubPageLayout';
import './NotificationPage.css';

const TABS = [
    { id: 'ALL', label: '전체' },
    { id: 'FRIEND', label: '친구' },
    { id: 'CHAT', label: '채팅' },
    { id: 'WALK', label: '산책' },
    { id: 'COMMUNITY', label: '커뮤니티' },
    { id: 'NOTICE', label: '공지' },
    { id: 'EVENT', label: '이벤트' },
];

const BOARD_TABS = ['NOTICE', 'EVENT'];

// Map tab ID to backend AppNoticeType
const TAB_TO_NOTICE_TYPE: Record<string, string> = {
    NOTICE: 'NOTICE',
    EVENT: 'EVENT_BANNER',
};

const NotificationPage = () => {
    const navigate = useNavigate();
    const queryClient = useQueryClient();
    const [activeTab, setActiveTab] = useState('ALL');
    const [notifications, setNotifications] = useState<Notification[]>([]);
    const [notices, setNotices] = useState<AppNoticeResponse[]>([]);
    const [loading, setLoading] = useState(false);

    const isBoardTab = BOARD_TABS.includes(activeTab);

    useEffect(() => {
        if (isBoardTab) {
            loadNotices();
        } else if (activeTab === 'ALL') {
            loadAll();
        } else {
            loadNotifications();
        }
    // eslint-disable-next-line react-hooks/exhaustive-deps -- load functions defined without useCallback; adding them would cause infinite loop; activeTab is the sole trigger
    }, [activeTab]);

    const loadAll = async () => {
        setLoading(true);
        try {
            const [{ notifications: notiData }, noticeData, eventData] = await Promise.all([
                notificationService.getNotifications('ALL'),
                noticeService.getNoticesByType('NOTICE'),
                noticeService.getNoticesByType('EVENT_BANNER'),
            ]);
            setNotifications(notiData);
            setNotices([...noticeData, ...eventData]);
        } catch {
            // silent
        } finally {
            setLoading(false);
        }
    };

    const loadNotifications = async () => {
        setLoading(true);
        try {
            const { notifications: data } = await notificationService.getNotifications(activeTab);
            setNotifications(data);
            setNotices([]);
        } catch {
            // silent
        } finally {
            setLoading(false);
        }
    };

    const loadNotices = async () => {
        setLoading(true);
        try {
            const type = TAB_TO_NOTICE_TYPE[activeTab];
            const data = await noticeService.getNoticesByType(type);
            setNotices(data);
        } catch {
            // silent
        } finally {
            setLoading(false);
        }
    };

    const handleNotificationClick = async (item: Notification) => {
        if (!item.isRead) {
            await notificationService.markAsRead(item.id);
            setNotifications(prev =>
                prev.map(n => n.id === item.id ? { ...n, isRead: true } : n)
            );
            queryClient.invalidateQueries({ queryKey: ['notificationUnread'] });
        }
        if (item.targetType && item.targetId) {
            switch (item.targetType) {
                case 'CHAT_ROOM': navigate(`/chat/${item.targetId}`); break;
                case 'POST': navigate(`/community/${item.targetId}`); break;
                case 'MATCH': navigate('/friend-list'); break;
            }
        }
    };

    const handleNoticeClick = (item: AppNoticeResponse) => {
        if (item.linkUrl) {
            window.location.href = item.linkUrl;
        } else {
            navigate(`/notices/${item.id}`);
        }
    };

    const handleMarkAllAsRead = async () => {
        await notificationService.markAllAsRead();
        setNotifications(prev => prev.map(n => ({ ...n, isRead: true })));
        queryClient.invalidateQueries({ queryKey: ['notificationUnread'] });
    };

    const formatTime = (isoString?: string) => {
        if (!isoString) return '';
        const date = new Date(isoString);
        const now = new Date();
        const diff = now.getTime() - date.getTime();

        const minutes = Math.floor(diff / (1000 * 60));
        const hours = Math.floor(diff / (1000 * 60 * 60));
        const days = Math.floor(diff / (1000 * 60 * 60 * 24));

        if (minutes < 60) return `${minutes}분 전`;
        if (hours < 24) return `${hours}시간 전`;
        return `${days}일 전`;
    };

    const formatDate = (isoString: string) => {
        const date = new Date(isoString);
        return `${date.getFullYear()}.${String(date.getMonth() + 1).padStart(2, '0')}.${String(date.getDate()).padStart(2, '0')}`;
    };

    const emptyMessage = isBoardTab
        ? activeTab === 'NOTICE' ? '등록된 공지가 없습니다.' : '등록된 이벤트가 없습니다.'
        : '새로운 알림이 없습니다.';

    return (
        <SubPageLayout
            title="알림"
            rightAction={
                !isBoardTab ? (
                    <button type="button" className="mark-all-read-btn" onClick={handleMarkAllAsRead}>
                        모두 읽음
                    </button>
                ) : undefined
            }
        >
            <div id="notificationContainer">
                {/* Category Tabs */}
                <div className="community_tab">
                    <ul>
                        {TABS.map(tab => (
                            <li key={tab.id}>
                                <button
                                    type="button"
                                    className={activeTab === tab.id ? 'active' : ''}
                                    onClick={() => setActiveTab(tab.id)}
                                >
                                    {tab.label}
                                </button>
                            </li>
                        ))}
                    </ul>
                </div>

                {/* Content */}
                {loading ? (
                    <div className="notification_list">
                        <div className="loading">로딩 중...</div>
                    </div>
                ) : isBoardTab ? (
                    /* Board-style list for NOTICE/EVENT */
                    <div className="notice_board_list">
                        {notices.length === 0 ? (
                            <div className="empty_state">
                                <img src="/assets/images/common/comment_icon03.svg" alt="" />
                                <p>{emptyMessage}</p>
                            </div>
                        ) : (
                            <ul>
                                {notices.map(item => (
                                    <li key={item.id} className="notice_board_item" onClick={() => handleNoticeClick(item)}>
                                        {item.imageUrls.length > 0 && (
                                            <div className="notice_board_thumb">
                                                <img src={item.imageUrls[0]} alt="" />
                                            </div>
                                        )}
                                        <div className="notice_board_content">
                                            <strong className="notice_board_title">{item.title}</strong>
                                            {item.content && (
                                                <p className="notice_board_desc">{item.content}</p>
                                            )}
                                            <span className="notice_board_date">{formatDate(item.startAt)}</span>
                                        </div>
                                    </li>
                                ))}
                            </ul>
                        )}
                    </div>
                ) : (
                    <>
                        {/* Board items in ALL tab */}
                        {activeTab === 'ALL' && notices.length > 0 && (
                            <div className="notice_board_list">
                                <ul>
                                    {notices.map(item => (
                                        <li key={`notice-${item.id}`} className="notice_board_item" onClick={() => handleNoticeClick(item)}>
                                            {item.imageUrls.length > 0 && (
                                                <div className="notice_board_thumb">
                                                    <img src={item.imageUrls[0]} alt="" />
                                                </div>
                                            )}
                                            <div className="notice_board_content">
                                                <span className="notice_board_badge">{item.type === 'NOTICE' ? '공지' : '이벤트'}</span>
                                                <strong className="notice_board_title">{item.title}</strong>
                                                <span className="notice_board_date">{formatDate(item.startAt)}</span>
                                            </div>
                                        </li>
                                    ))}
                                </ul>
                            </div>
                        )}
                        {/* Notification list */}
                        <div className="notification_list">
                            {notifications.length === 0 && notices.length === 0 ? (
                                <div className="empty_state">
                                    <img src="/assets/images/common/comment_icon03.svg" alt="" />
                                    <p>{emptyMessage}</p>
                                </div>
                            ) : notifications.length === 0 ? null : (
                                <ul>
                                    {notifications.map(item => {
                                        // senderId가 없으면 시스템 알림(산책·골드·공지 등) — 빈 사람 대신 골드펫 로고 노출
                                        const isSystem = !item.senderId;
                                        return (
                                        <li key={item.id} className="notification_item" onClick={() => handleNotificationClick(item)}>
                                            <div className={`notification_profile${isSystem ? ' notification_profile--system' : ''}`}>
                                                <img
                                                    src={isSystem ? '/icon-192.png' : (item.senderProfileImage || '/assets/images/common/profile_none_img.svg')}
                                                    alt=""
                                                />
                                                {!item.isRead && <span className="unread_dot" />}
                                            </div>
                                            <div className="notification_content">
                                                <strong className="notification_title">{item.title}</strong>
                                                <p className="notification_message">{item.message}</p>
                                                <span className="notification_time">{formatTime(item.createdAt)}</span>
                                            </div>
                                        </li>
                                        );
                                    })}
                                </ul>
                            )}
                        </div>
                    </>
                )}
            </div>
        </SubPageLayout>
    );
};

export default NotificationPage;
