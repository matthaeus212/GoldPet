import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { friendService, type Friend } from '../../services/friendService';
import { chatService } from '../../services/chatService';
import { useAlert } from '../../contexts/AlertContext';
import { CACHE_TIME } from '../../config/queryConfig';

interface InviteGroupChatModalProps {
    isOpen: boolean;
    onClose: () => void;
    roomId: string;
    existingParticipantIds: number[];
    onInviteSuccess?: (roomType: string) => void;
}

const InviteGroupChatModal = ({ isOpen, onClose, roomId, existingParticipantIds, onInviteSuccess }: InviteGroupChatModalProps) => {
    const { showAlert } = useAlert();
    const queryClient = useQueryClient();
    const [selectedFriends, setSelectedFriends] = useState<Set<number>>(new Set());

    const { data: mutualFriends = [], isLoading } = useQuery({
        queryKey: ['mutualLikes'],
        queryFn: friendService.getMutualLikes,
        enabled: isOpen,
        // PERF-021: staleTime 기본 0 → 모달을 열 때마다 서로 좋아요 목록을 다시 받아왔다.
        ...CACHE_TIME.DYNAMIC,
    });

    // Exclude friends already in the room
    const invitableFriends = mutualFriends.filter(
        (f: Friend) => !existingParticipantIds.includes(f.id)
    );

    const inviteMutation = useMutation({
        mutationFn: async () => {
            const ids = Array.from(selectedFriends);
            let resultRoomType = '';
            for (const userId of ids) {
                const result = await chatService.inviteToRoom(Number(roomId), userId);
                if (!resultRoomType && result?.roomType) {
                    resultRoomType = result.roomType;
                }
            }
            return resultRoomType;
        },
        onSuccess: (roomType) => {
            queryClient.invalidateQueries({ queryKey: ['chatRooms'] });
            if (onInviteSuccess && roomType) {
                onInviteSuccess(roomType);
            }
            handleClose();
        },
        onError: () => {
            showAlert('초대에 실패했습니다.');
        },
    });

    const handleClose = () => {
        setSelectedFriends(new Set());
        onClose();
    };

    const toggleFriend = (friendId: number) => {
        setSelectedFriends(prev => {
            const newSet = new Set(prev);
            if (newSet.has(friendId)) {
                newSet.delete(friendId);
            } else {
                newSet.add(friendId);
            }
            return newSet;
        });
    };

    if (!isOpen) return null;

    return (
        <div className="group_modal_overlay" onClick={handleClose}>
            <div className="group_modal_panel" onClick={(e) => e.stopPropagation()}>
                <div className="group_modal_header">
                    <h2 className="group_modal_title">대화상대 초대하기</h2>
                </div>

                <div className="group_modal_body">
                    {isLoading ? (
                        <div className="group_modal_loading">불러오는 중...</div>
                    ) : invitableFriends.length === 0 ? (
                        <div className="group_modal_empty">
                            <p className="group_modal_empty_title">초대할 수 있는 친구가 없어요</p>
                            <p className="group_modal_empty_desc">이미 모두 참여 중이거나 매칭된 친구가 없어요</p>
                        </div>
                    ) : (
                        <ul className="group_modal_friend_list">
                            {invitableFriends.map((friend: Friend) => {
                                const isSelected = selectedFriends.has(friend.id);
                                return (
                                    <li key={friend.id}>
                                        <button
                                            type="button"
                                            className={`group_modal_friend_item btn-effect${isSelected ? ' selected' : ''}`}
                                            onClick={() => toggleFriend(friend.id)}
                                        >
                                            <div className={`group_modal_radio${isSelected ? ' selected' : ''}`}>
                                                {isSelected && <div className="group_modal_radio_inner" />}
                                            </div>
                                            <div className="group_modal_friend_img">
                                                <img
                                                    src={friend.profileImages?.[0] || friend.profileImageUrl || '/assets/images/common/profile_none_img.svg'}
                                                    alt={friend.nickname}
                                                />
                                            </div>
                                            <span className="group_modal_friend_name">{friend.nickname}</span>
                                        </button>
                                    </li>
                                );
                            })}
                        </ul>
                    )}
                </div>

                <div className="group_modal_footer">
                    <button
                        type="button"
                        className="group_modal_btn group_modal_btn_cancel btn-effect"
                        onClick={handleClose}
                    >
                        취소
                    </button>
                    <button
                        type="button"
                        className="group_modal_btn group_modal_btn_confirm btn-effect"
                        onClick={() => inviteMutation.mutate()}
                        disabled={selectedFriends.size === 0 || inviteMutation.isPending}
                    >
                        {inviteMutation.isPending ? '초대 중...' : '확인'}
                    </button>
                </div>
            </div>
        </div>
    );
};

export default InviteGroupChatModal;
