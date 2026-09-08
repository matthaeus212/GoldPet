
import { useState } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { chatService } from '../../services/chatService';
import type { ChatRequestItem } from '../../services/chatService';
import { useAuthStore } from '../../stores/authStore';
import { CACHE_TIME } from '../../config/queryConfig';
import { useChatListWebSocket } from '../../hooks/useChatListWebSocket';
import '../../styles/chat.css';
import { Loading } from '../../components/common/Loading';
import CreateGroupChatModal from './CreateGroupChatModal';
import { PlacementBanner } from '../../components/common/PlacementBanner';

// Empty state component
const ChatEmptyState = () => {
    return (
        <div className="chat_empty_state">
            <div className="chat_empty_content">
                <div className="chat_empty_icon">
                    <div className="chat_empty_icon_bg"></div>
                </div>
                <div className="chat_empty_text">
                    <div className="chat_empty_title">채팅이 없어요ㅠ</div>
                    <div className="chat_empty_description">반려인과 매칭해주세요</div>
                </div>
            </div>
        </div>
    );
};

const ChatListPage = () => {
    const navigate = useNavigate();
    const location = useLocation();
    const queryClient = useQueryClient();
    // PERF-014: 스토어 전체 구독 → 셀렉터로 전환 (token/deviceId 변경 시 불필요한 리렌더 제거)
    const user = useAuthStore((s) => s.user);
    const currentUserId = user?.id;
    const locationActiveTab = (location.state && 'activeTab' in location.state)
        ? location.state.activeTab as string
        : null;
    const [activeTab, setActiveTab] = useState<string>(locationActiveTab ?? 'one'); // 'one', 'group', 'request'
    const [showCreateGroupModal, setShowCreateGroupModal] = useState(false);

    // WebSocket subscription for real-time chat list updates
    useChatListWebSocket();

    // Fetch Chat Rooms (WebSocket handles real-time updates)
    const { data: chatRooms, isLoading } = useQuery({
        queryKey: ['chatRooms'],
        queryFn: chatService.getChatRooms,
        ...CACHE_TIME.REAL_TIME,
    });

    // Fetch chat requests when on request tab
    const { data: chatRequests, isLoading: isRequestsLoading } = useQuery({
        queryKey: ['chatRequests'],
        queryFn: chatService.getChatRequests,
        enabled: activeTab === 'request',
        ...CACHE_TIME.REAL_TIME,
    });

    const handleAcceptRequest = async (req: ChatRequestItem) => {
        try {
            const chatRoom = await chatService.acceptChatRequest(req.id);
            queryClient.invalidateQueries({ queryKey: ['chatRequests'] });
            queryClient.invalidateQueries({ queryKey: ['chatRooms'] });
            navigate(`/chat/${chatRoom.id}`);
        } catch {
            // ignore
        }
    };

    const handleRejectRequest = async (req: ChatRequestItem) => {
        try {
            await chatService.rejectChatRequest(req.id);
            queryClient.invalidateQueries({ queryKey: ['chatRequests'] });
        } catch {
            // ignore
        }
    };

    const handleDeleteRequest = async (req: ChatRequestItem) => {
        try {
            await chatService.deleteChatRequest(req.id);
            queryClient.invalidateQueries({ queryKey: ['chatRequests'] });
        } catch {
            // ignore
        }
    };

    if (isLoading) return <Loading description="채팅 목록을 불러오고 있어요" />;

    // Filter chats based on tab
    const oneOnOneChats = chatRooms?.filter(room => !room.isGroup) || [];
    const groupChats = chatRooms?.filter(room => room.isGroup) || [];

    return (
        <div id="chatOne">
            <div className="group_tab">
                <button 
                    type="button" 
                    className={activeTab === 'one' ? 'active btn-effect' : 'btn-effect'}
                    onClick={() => setActiveTab('one')}
                >
                    1:1
                </button>
                <button 
                    type="button"
                    className={activeTab === 'group' ? 'active btn-effect' : 'btn-effect'}
                    onClick={() => setActiveTab('group')}
                >
                    그룹
                </button>
                <button 
                    type="button"
                    className={activeTab === 'request' ? 'active btn-effect' : 'btn-effect'}
                    onClick={() => setActiveTab('request')}
                >
                    대화요청
                </button>
            </div>

            {activeTab !== 'request' && <PlacementBanner placement="CHAT" />}

            {/* 1:1 Chat List */}
            {activeTab === 'one' && (
                <>
                    {oneOnOneChats.length === 0 ? (
                        <ChatEmptyState />
                    ) : (
                        <ul className="chat_list">
                            {oneOnOneChats.map((chat) => {
                                // Find the other participant (not current user)
                                const otherParticipant = chat.participants.find(p => p.id !== currentUserId) ?? null;
                                return (
                                <li key={chat.id}>
                                    <button
                                        type="button"
                                        className="btn-effect"
                                        onClick={() => navigate(`/chat/${chat.id}`)}
                                    >
                                        <div className="profile_img">
                                            {/* Show other participant's image for 1:1 */}
                                            <img src={otherParticipant?.profileImageUrl || '/assets/images/temp/temp_dog_profile.png'} alt={chat.name} />
                                        </div>
                                        <div className="txt_wrap">
                                            <strong>{chat.name}</strong>
                                            <p>{chat.lastMessage}</p>
                                        </div>
                                        <div className="day_txt">
                                            {chat.unreadCount > 0 && <p className="count_num">{chat.unreadCount}</p>}
                                            {/* Simple date formatting */}
                                            <p className="last_day">{chat.lastMessageTime ? new Date(chat.lastMessageTime).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }) : ''}</p>
                                        </div>
                                    </button>
                                </li>
                            );
                            })}
                        </ul>
                    )}
                </>
            )}

            {/* Group Chat List */}
            {activeTab === 'group' && (
                <>
                    {/* Create Group Chat Button - right-aligned below tabs */}
                    <div className="create_group_btn_wrap">
                        <button
                            type="button"
                            className="create_group_btn btn-effect"
                            onClick={() => setShowCreateGroupModal(true)}
                        >
                            <svg className="create_group_btn_vector left" width="16" height="36" viewBox="0 0 16 36" fill="none">
                                <path d="M0 18C0 27.9411 6 36 16 36V0C5 0 0 8.05887 0 18Z" fill="currentColor"/>
                            </svg>
                            <span className="create_group_btn_text">그룹 채팅 만들기</span>
                            <svg className="create_group_btn_vector right" width="16" height="36" viewBox="0 0 16 36" fill="none">
                                <path d="M0 18C0 27.9411 6 36 16 36V0C5 0 0 8.05887 0 18Z" fill="currentColor"/>
                            </svg>
                        </button>
                    </div>

                    {groupChats.length === 0 ? (
                        <ChatEmptyState />
                    ) : (
                        <ul className="chat_list">
                            {groupChats.map((chat) => (
                                <li key={chat.id}>
                                    <button 
                                        type="button" 
                                        className="btn-effect"
                                        onClick={() => navigate(`/chat/group/${chat.id}`)}
                                    >
                                        <div className="profile_img profile_img_group">
                                            {chat.participants.slice(0, 4).map((user, index) => (
                                                <div 
                                                    key={user.id} 
                                                    className="profile_img_item"
                                                    style={{ zIndex: 4 - index }}
                                                >
                                                    <img src={user.profileImageUrl || '/assets/images/temp/temp_dog_profile.png'} alt={`${user.nickname}`} />
                                                </div>
                                            ))}
                                        </div>
                                        <div className="txt_wrap">
                                            <strong>{chat.name}</strong>
                                            <p>{chat.lastMessage}</p>
                                        </div>
                                        <div className="day_txt">
                                            {chat.unreadCount > 0 && <p className="count_num">{chat.unreadCount}</p>}
                                            <p className="last_day">{chat.lastMessageTime ? new Date(chat.lastMessageTime).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }) : ''}</p>
                                        </div>
                                    </button>
                                </li>
                            ))}
                        </ul>
                    )}
                </>
            )}

            {/* Contact Request List */}
            {activeTab === 'request' && (
                <>
                    {isRequestsLoading ? (
                        <Loading description="대화 요청을 불러오고 있어요" />
                    ) : !chatRequests || chatRequests.length === 0 ? (
                        <div className="chat_empty_state">
                            <div className="chat_empty_content">
                                <div className="chat_empty_text">
                                    <div className="chat_empty_title">대화 요청이 없습니다</div>
                                </div>
                            </div>
                        </div>
                    ) : (
                        <ul className="contact_list">
                            {chatRequests.map((req) => (
                                <li key={req.id}>
                                    <div className="profile_img">
                                        <img
                                            src={req.requesterProfileImage || '/assets/images/temp/temp_dog_profile.png'}
                                            alt={req.requesterNickname}
                                        />
                                    </div>
                                    <div className="txt_wrap">
                                        <strong>{req.requesterNickname}</strong>
                                        <p>
                                            {req.status === 'REJECTED'
                                                ? '채팅 요청을 거절했어요.'
                                                : '채팅을 요청했어요.'}
                                        </p>
                                    </div>
                                    <div className="contact_btn_wrap">
                                        {req.status === 'PENDING' && (
                                            <>
                                                <button
                                                    type="button"
                                                    onClick={() => handleRejectRequest(req)}
                                                >
                                                    거절
                                                </button>
                                                <button
                                                    type="button"
                                                    onClick={() => handleAcceptRequest(req)}
                                                >
                                                    수락
                                                </button>
                                            </>
                                        )}
                                        {req.status === 'REJECTED' && (
                                            <button
                                                type="button"
                                                style={{ color: '#FF5005', backgroundColor: 'transparent', border: '1px solid #FF5005' }}
                                                onClick={() => handleDeleteRequest(req)}
                                            >
                                                삭제
                                            </button>
                                        )}
                                    </div>
                                </li>
                            ))}
                        </ul>
                    )}
                </>
            )}

            <CreateGroupChatModal
                isOpen={showCreateGroupModal}
                onClose={() => setShowCreateGroupModal(false)}
            />
        </div>
    );
};

export default ChatListPage;

