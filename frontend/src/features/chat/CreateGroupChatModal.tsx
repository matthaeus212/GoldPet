import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useQuery, useMutation } from '@tanstack/react-query';
import { friendService, type Friend } from '../../services/friendService';
import { chatService } from '../../services/chatService';
import { useAlert } from '../../contexts/AlertContext';
import { useKeyboardDismiss } from '../../hooks/useKeyboardDismiss';
import { CACHE_TIME } from '../../config/queryConfig';

interface CreateGroupChatModalProps {
    isOpen: boolean;
    onClose: () => void;
}

const CreateGroupChatModal = ({ isOpen, onClose }: CreateGroupChatModalProps) => {
    const navigate = useNavigate();
    const { showAlert } = useAlert();
    const [selectedFriends, setSelectedFriends] = useState<Set<number>>(new Set());
    const [groupName, setGroupName] = useState('');
    const [step, setStep] = useState<'select' | 'name'>('select');

    const { data: mutualFriends = [], isLoading } = useQuery({
        queryKey: ['mutualLikes'],
        queryFn: friendService.getMutualLikes,
        enabled: isOpen,
        // PERF-021: staleTime 기본 0 → 모달을 열 때마다 서로 좋아요 목록을 다시 받아왔다.
        ...CACHE_TIME.DYNAMIC,
    });

    const createMutation = useMutation({
        mutationFn: async () => {
            const participantIds = Array.from(selectedFriends);
            return chatService.createRoom(participantIds, groupName || undefined);
        },
        onSuccess: (room) => {
            handleClose();
            navigate(`/chat/group/${room.id}`, { replace: true });
        },
        onError: () => {
            showAlert('그룹 채팅 생성에 실패했습니다.');
        },
    });

    const handleClose = () => {
        setSelectedFriends(new Set());
        setGroupName('');
        setStep('select');
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

    const handleNext = () => {
        if (selectedFriends.size < 2) {
            showAlert('그룹 채팅은 2명 이상 선택해야 합니다.');
            return;
        }
        setStep('name');
    };

    const handleCreate = () => {
        createMutation.mutate();
    };

    const keyboardDismiss = useKeyboardDismiss();

    if (!isOpen) return null;

    const selectedFriendList = mutualFriends.filter((f: Friend) => selectedFriends.has(f.id));

    return (
        <div className="group_modal_overlay" onClick={handleClose} {...keyboardDismiss}>
            <div className="group_modal_panel" onClick={(e) => e.stopPropagation()}>
                {/* Modal Header */}
                <div className="group_modal_header">
                    <h2 className="group_modal_title">
                        {step === 'select' ? '대화상대 선택' : '그룹 이름 만들기'}
                    </h2>
                </div>

                {/* Modal Body */}
                <div className="group_modal_body">
                    {step === 'select' ? (
                        <>
                            {isLoading ? (
                                <div className="group_modal_loading">불러오는 중...</div>
                            ) : mutualFriends.length === 0 ? (
                                <div className="group_modal_empty">
                                    <p className="group_modal_empty_title">서로 좋아해 친구가 없어요</p>
                                    <p className="group_modal_empty_desc">먼저 친구와 매칭해보세요!</p>
                                </div>
                            ) : (
                                <ul className="group_modal_friend_list">
                                    {mutualFriends.map((friend: Friend) => {
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
                        </>
                    ) : (
                        <div className="group_modal_name_step">
                            {/* 2x2 avatar grid */}
                            <div className="group_modal_avatar_grid">
                                {selectedFriendList.slice(0, 4).map((friend: Friend) => (
                                    <div key={friend.id} className="group_modal_avatar_item">
                                        <img
                                            src={friend.profileImages?.[0] || friend.profileImageUrl || '/assets/images/common/profile_none_img.svg'}
                                            alt={friend.nickname}
                                        />
                                    </div>
                                ))}
                                {/* Fill remaining slots with empty placeholders */}
                                {Array.from({ length: Math.max(0, 4 - selectedFriendList.slice(0, 4).length) }).map((_, i) => (
                                    <div key={`empty-${i}`} className="group_modal_avatar_item empty" />
                                ))}
                            </div>

                            {/* Group name input */}
                            <div className="group_modal_input_wrap">
                                <label className="group_modal_input_label">그룹 이름을 입력해 주세요.</label>
                                <input
                                    type="text"
                                    className="group_modal_input"
                                    placeholder="최대 20자"
                                    value={groupName}
                                    onChange={(e) => setGroupName(e.target.value)}
                                    maxLength={20}
                                />
                            </div>
                        </div>
                    )}
                </div>

                {/* Modal Footer */}
                <div className="group_modal_footer">
                    <button
                        type="button"
                        className="group_modal_btn group_modal_btn_cancel btn-effect"
                        onClick={step === 'name' ? () => setStep('select') : handleClose}
                    >
                        취소
                    </button>
                    <button
                        type="button"
                        className="group_modal_btn group_modal_btn_confirm btn-effect"
                        onClick={step === 'select' ? handleNext : handleCreate}
                        disabled={createMutation.isPending}
                    >
                        {step === 'select' ? '다음' : (createMutation.isPending ? '생성 중...' : '확인')}
                    </button>
                </div>
            </div>
        </div>
    );
};

export default CreateGroupChatModal;
