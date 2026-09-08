
import React, { useState, useRef, useEffect, useCallback, useMemo } from 'react';
import { useNavigate, useParams, useLocation } from 'react-router-dom';
import { useOverlayColor } from '../../hooks/useOverlayColor';
import { useKeyboardDismiss } from '../../hooks/useKeyboardDismiss';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { chatService } from '../../services/chatService';
import type { FileInfo, ChatMessage } from '../../services/chatService';
import { useChatWebSocket } from '../../hooks/useChatWebSocket';
import { useAuthStore } from '../../stores/authStore';
import { Loading } from '../../components/common/Loading';
import { BackButton } from '../../components/common/BackButton';
import { useToast } from '../../contexts/ToastContext';
import { useAlert } from '../../contexts/AlertContext';
import { nativeBridge } from '../../bridge/nativeBridge';
import { blockService } from '../../services/blockService';
import { reportService } from '../../services/reportService';
import { ReportModal } from '../../components/common/ReportModal';
import { CACHE_TIME } from '../../config/queryConfig';
import EmoticonPicker from './EmoticonPicker';
import { emoticonService } from '../../services/emoticonService';
import InviteGroupChatModal from './InviteGroupChatModal';
import { GpImage } from '../../components/common/GpImage';
import '../../styles/chat-detail.css';

const OPTIMISTIC_ENABLED = import.meta.env.VITE_CHAT_OPTIMISTIC_ENABLED === 'true';

// Component for expandable long messages (5 lines max when collapsed)
const ExpandableText = ({ content }: { content: string }) => {
    const contentRef = useRef<HTMLDivElement>(null);
    const [isOverflowing, setIsOverflowing] = useState(false);
    const [isExpanded, setIsExpanded] = useState(false);

    useEffect(() => {
        const el = contentRef.current;
        if (el) {
            // Check if content is overflowing (truncated by line-clamp)
            setIsOverflowing(el.scrollHeight > el.clientHeight);
        }
    }, [content]);

    return (
        <div className="chat_detail_message_text">
            <div
                ref={contentRef}
                className={`chat_detail_expandable_content ${!isExpanded ? 'chat_detail_expandable_clamped' : ''}`}
            >
                {content.split('\n').map((line: string, i: number) => (
                    <span key={i}>
                        {line}
                        {i < content.split('\n').length - 1 && <br />}
                    </span>
                ))}
            </div>
            {(isOverflowing || isExpanded) && (
                <button
                    type="button"
                    className="chat_detail_message_expand_btn"
                    onClick={() => setIsExpanded(!isExpanded)}
                >
                    {isExpanded ? '접기' : '더보기'}
                </button>
            )}
        </div>
    );
};

interface SelectedFile {
    file: File;
    preview: string;
    type: 'image' | 'video' | 'file';
}

const MAX_FILES = 10; // Maximum number of files allowed at once

// Helper to get file icon based on MIME type or extension
const getFileIcon = (file: File | { mimeType?: string; url?: string }) => {
    const mimeType = 'type' in file ? file.type : file.mimeType || '';
    const fileName = 'name' in file ? file.name : file.url?.split('/').pop() || '';
    const ext = fileName.split('.').pop()?.toLowerCase() || '';

    // PDF
    if (mimeType === 'application/pdf' || ext === 'pdf') {
        return (
            <svg width="24" height="24" viewBox="0 0 24 24" fill="none">
                <path d="M14 2H6C5.46957 2 4.96086 2.21071 4.58579 2.58579C4.21071 2.96086 4 3.46957 4 4V20C4 20.5304 4.21071 21.0391 4.58579 21.4142C4.96086 21.7893 5.46957 22 6 22H18C18.5304 22 19.0391 21.7893 19.4142 21.4142C19.7893 21.0391 20 20.5304 20 20V8L14 2Z" stroke="#E53935" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
                <path d="M14 2V8H20" stroke="#E53935" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
                <text x="12" y="17" textAnchor="middle" fontSize="6" fill="#E53935" fontWeight="bold">PDF</text>
            </svg>
        );
    }

    // Word documents
    if (mimeType.includes('word') || ['doc', 'docx'].includes(ext)) {
        return (
            <svg width="24" height="24" viewBox="0 0 24 24" fill="none">
                <path d="M14 2H6C5.46957 2 4.96086 2.21071 4.58579 2.58579C4.21071 2.96086 4 3.46957 4 4V20C4 20.5304 4.21071 21.0391 4.58579 21.4142C4.96086 21.7893 5.46957 22 6 22H18C18.5304 22 19.0391 21.7893 19.4142 21.4142C19.7893 21.0391 20 20.5304 20 20V8L14 2Z" stroke="#2196F3" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
                <path d="M14 2V8H20" stroke="#2196F3" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
                <text x="12" y="17" textAnchor="middle" fontSize="5" fill="#2196F3" fontWeight="bold">DOC</text>
            </svg>
        );
    }

    // Excel/Spreadsheet
    if (mimeType.includes('sheet') || mimeType.includes('excel') || ['xls', 'xlsx', 'csv'].includes(ext)) {
        return (
            <svg width="24" height="24" viewBox="0 0 24 24" fill="none">
                <path d="M14 2H6C5.46957 2 4.96086 2.21071 4.58579 2.58579C4.21071 2.96086 4 3.46957 4 4V20C4 20.5304 4.21071 21.0391 4.58579 21.4142C4.96086 21.7893 5.46957 22 6 22H18C18.5304 22 19.0391 21.7893 19.4142 21.4142C19.7893 21.0391 20 20.5304 20 20V8L14 2Z" stroke="#4CAF50" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
                <path d="M14 2V8H20" stroke="#4CAF50" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
                <text x="12" y="17" textAnchor="middle" fontSize="5" fill="#4CAF50" fontWeight="bold">XLS</text>
            </svg>
        );
    }

    // Text files
    if (mimeType.startsWith('text/') || ['txt', 'md', 'json', 'xml', 'html', 'css', 'js'].includes(ext)) {
        return (
            <svg width="24" height="24" viewBox="0 0 24 24" fill="none">
                <path d="M14 2H6C5.46957 2 4.96086 2.21071 4.58579 2.58579C4.21071 2.96086 4 3.46957 4 4V20C4 20.5304 4.21071 21.0391 4.58579 21.4142C4.96086 21.7893 5.46957 22 6 22H18C18.5304 22 19.0391 21.7893 19.4142 21.4142C19.7893 21.0391 20 20.5304 20 20V8L14 2Z" stroke="#757575" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
                <path d="M14 2V8H20" stroke="#757575" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
                <path d="M8 13H16M8 17H12" stroke="#757575" strokeWidth="1.5" strokeLinecap="round"/>
            </svg>
        );
    }

    // Compressed/Archive files
    if (mimeType.includes('zip') || mimeType.includes('compressed') || ['zip', 'rar', '7z', 'tar', 'gz'].includes(ext)) {
        return (
            <svg width="24" height="24" viewBox="0 0 24 24" fill="none">
                <path d="M14 2H6C5.46957 2 4.96086 2.21071 4.58579 2.58579C4.21071 2.96086 4 3.46957 4 4V20C4 20.5304 4.21071 21.0391 4.58579 21.4142C4.96086 21.7893 5.46957 22 6 22H18C18.5304 22 19.0391 21.7893 19.4142 21.4142C19.7893 21.0391 20 20.5304 20 20V8L14 2Z" stroke="#FF9800" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
                <path d="M14 2V8H20" stroke="#FF9800" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
                <text x="12" y="17" textAnchor="middle" fontSize="5" fill="#FF9800" fontWeight="bold">ZIP</text>
            </svg>
        );
    }

    // Default file icon
    return (
        <svg width="24" height="24" viewBox="0 0 24 24" fill="none">
            <path d="M14 2H6C5.46957 2 4.96086 2.21071 4.58579 2.58579C4.21071 2.96086 4 3.46957 4 4V20C4 20.5304 4.21071 21.0391 4.58579 21.4142C4.96086 21.7893 5.46957 22 6 22H18C18.5304 22 19.0391 21.7893 19.4142 21.4142C19.7893 21.0391 20 20.5304 20 20V8L14 2Z" stroke="var(--color-primary)" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
            <path d="M14 2V8H20" stroke="var(--color-primary)" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
        </svg>
    );
};

// Format file size
const formatFileSize = (bytes: number): string => {
    if (bytes < 1024) return `${bytes} B`;
    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
    return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
};

interface ChatMessageItemProps {
    msg: ChatMessage;
    isMine: boolean;
    isGroupChat: boolean;
    currentRoom: { participants?: { id: number; profileImageUrl?: string; nickname?: string }[] } | null | undefined;
    chatUser: { name: string; profileImg?: string; profileImgs?: string[] };
    onLongPress: (msg: ChatMessage, isMine: boolean, position: { x: number; y: number }) => void;
    renderMessageContent: (msg: ChatMessage) => React.ReactNode;
}

const ChatMessageItem = React.memo(function ChatMessageItem({ msg, isMine, isGroupChat, currentRoom, chatUser, onLongPress, renderMessageContent }: ChatMessageItemProps) {
    const handleClick = useCallback((e: React.MouseEvent) => {
        if (msg.deletedAt) return;
        const rect = (e.currentTarget as HTMLElement).getBoundingClientRect();
        onLongPress(msg, isMine, { x: rect.left + rect.width / 2, y: rect.top });
    }, [msg, isMine, onLongPress]);

    const isDeleted = !!msg.deletedAt;
    const isEmoticon = msg.messageType === 'EMOTICON' || msg.type === 'emoticon';
    const hasReply = !!msg.replyToId;
    const timestamp = msg.timestamp || msg.createdAt;
    const time = timestamp ? new Date(timestamp).toLocaleTimeString('ko-KR', { hour: '2-digit', minute: '2-digit', hour12: true }) : '';

    return (
        <div className={`chat_detail_message ${isMine ? 'chat_detail_message_mine' : 'chat_detail_message_other'}`}>
            {!isMine && (
                <div className="chat_detail_message_profile">
                    <img
                        src={
                            isGroupChat
                                ? (currentRoom?.participants?.find(p => p.id === Number(msg.senderId))?.profileImageUrl || '/assets/images/temp/temp_dog_profile.png')
                                : (chatUser.profileImg || '/assets/images/temp/temp_dog_profile.png')
                        }
                        alt={isGroupChat ? 'User' : chatUser.name}
                        loading="lazy"
                        decoding="async"
                    />
                </div>
            )}
            <div className="chat_detail_message_content">
                <div
                    className={[
                        'chat_detail_message_bubble',
                        isMine ? 'chat_detail_message_bubble_mine' : 'chat_detail_message_bubble_other',
                        isEmoticon ? 'chat_detail_message_bubble_emoticon' : '',
                        hasReply ? 'has_reply' : '',
                        msg._optimistic && !msg._rejectedReason ? 'chat_detail_message_optimistic' : '',
                        msg._rejectedReason ? `chat_detail_message_rejected chat_detail_message_rejected_${msg._rejectedReason.toLowerCase()}` : '',
                    ].filter(Boolean).join(' ')}
                    data-testid="chat-message-bubble"
                    {...(isDeleted ? {} : { onClick: handleClick })}
                >
                    {hasReply && (
                        <div className="chat_detail_reply_quote">
                            <div className="chat_detail_reply_quote_header">
                                <span className="chat_detail_reply_quote_name">{msg.replyToSenderNickname}에게 답장</span>
                                {(msg.replyToMessageType === 'IMAGE' || msg.replyToMessageType === 'VIDEO') && msg.replyToFileUrl && (
                                    <img className="chat_detail_reply_quote_thumb" src={msg.replyToFileUrl} alt="사진" />
                                )}
                                {msg.replyToMessageType === 'EMOTICON' && msg.replyToEmoticonImageUrl && (
                                    <GpImage
                                        className="chat_detail_reply_quote_thumb"
                                        src={msg.replyToEmoticonImageUrl}
                                        thumbnailSrc={msg.replyToEmoticonImageUrlThumbnail}
                                        viewerSrc={msg.replyToEmoticonImageUrlViewer}
                                        variant="thumbnail"
                                        alt="이모티콘"
                                    />
                                )}
                            </div>
                            <div className="chat_detail_reply_quote_divider" />
                            <span className="chat_detail_reply_quote_text">
                                {msg.replyToMessageType === 'FILE' ? `파일: ${msg.replyToFileName || '파일'}` :
                                 msg.replyToMessageType === 'IMAGE' ? '사진' :
                                 msg.replyToMessageType === 'VIDEO' ? '동영상' :
                                 msg.replyToMessageType === 'EMOTICON' ? '이모티콘' :
                                 msg.replyToTextContent}
                            </span>
                        </div>
                    )}
                    {isDeleted ? (
                        <span className="chat_detail_message_deleted">삭제된 메시지입니다</span>
                    ) : (
                        renderMessageContent(msg)
                    )}
                </div>
                <div className="chat_detail_message_actions">
                    {isMine && msg.unreadCount !== undefined && msg.unreadCount > 0 && (
                        <span className="chat_unread_count">{msg.unreadCount}</span>
                    )}
                    <span className="chat_detail_message_time">{time}</span>
                </div>
            </div>
        </div>
    );
});

const ChatDetailPage = () => {
    const navigate = useNavigate();
    const location = useLocation();
    const { chatId } = useParams<{ chatId: string }>();
    const isGroupChat = location.pathname.includes('/chat/group/');
    const [message, setMessage] = useState('');
    const [showAttachMenu, setShowAttachMenu] = useState(false);
    const [showEmoticonPicker, setShowEmoticonPicker] = useState(false);
    const [selectedEmoticon, setSelectedEmoticon] = useState<{ id: number; imageUrl: string } | null>(null);
    const [selectedFiles, setSelectedFiles] = useState<SelectedFile[]>([]);
    const [isUploading, setIsUploading] = useState(false);
    const [uploadProgress, setUploadProgress] = useState(0);
    const [previewImageUrl, setPreviewImageUrl] = useState<string | null>(null);
    const [replyingTo, setReplyingTo] = useState<{
        messageId: number;
        senderNickname: string;
        textContent: string;
        messageType: string;
        fileUrl?: string;
        fileName?: string;
        emoticonImageUrl?: string;
    } | null>(null);
    const [contextMenu, setContextMenu] = useState<{
        messageId: number;
        isMine: boolean;
        x: number;
        y: number;
        textContent: string;
        senderNickname: string;
        messageType: string;
        fileUrl?: string;
        fileName?: string;
        emoticonImageUrl?: string;
    } | null>(null);

    useOverlayColor(!!previewImageUrl);
    const keyboardDismiss = useKeyboardDismiss();

    const messagesEndRef = useRef<HTMLDivElement>(null);
    const messagesContainerRef = useRef<HTMLDivElement>(null);
    const fileInputRef = useRef<HTMLInputElement>(null);
    const textareaRef = useRef<HTMLTextAreaElement>(null);
    const queryClient = useQueryClient();
    // clientMsgId → ack timeout handle (for optimistic NETWORK fallback)
    const pendingAcksRef = useRef<Map<string, ReturnType<typeof setTimeout>>>(new Map());

    const { showToast } = useToast();
    const { showAlert, showConfirm } = useAlert();
    const [showHeaderMenu, setShowHeaderMenu] = useState(false);
    const [showInviteModal, setShowInviteModal] = useState(false);
    const [showReportModal, setShowReportModal] = useState(false);
    const [reportTargetMessageId, setReportTargetMessageId] = useState<number | null>(null);
    const [isReportSubmitting, setIsReportSubmitting] = useState(false);

    // PERF-014: 스토어 전체를 구독하면 token/deviceId 같은 무관한 필드가 바뀌어도 리렌더된다.
    // 다른 10곳처럼 셀렉터로 필요한 조각만 구독한다.
    const user = useAuthStore((s) => s.user);
    const currentUserId = user?.id || 0;

    // Listen for file download completion from native bridge
    useEffect(() => {
        const handler = (e: MessageEvent) => {
            if (e.data?.type === 'fileDownloaded') {
                showToast(`${e.data.data?.fileName || '파일'} 다운로드 완료`, 'success');
            }
        };
        window.addEventListener('message', handler);
        return () => window.removeEventListener('message', handler);
    }, [showToast]);

    // WebSocket for real-time messaging (disabled in mock mode)
    const { sendTypingEvent, typingUsers } = useChatWebSocket({
        roomId: chatId,
        currentUserId,
        enabled: !!chatId,
    });

    // Debounced typing event
    const typingTimeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null);
    const handleTyping = () => {
        sendTypingEvent(true);

        // Clear previous timeout
        if (typingTimeoutRef.current) {
            clearTimeout(typingTimeoutRef.current);
        }

        // Stop typing after 1 second of no input
        typingTimeoutRef.current = setTimeout(() => {
            sendTypingEvent(false);
        }, 1000);
    };

    // Fetch Chat Room info (to get participant details)
    const { data: chatRooms } = useQuery({
        queryKey: ['chatRooms'],
        queryFn: chatService.getChatRooms,
        ...CACHE_TIME.REAL_TIME,
    });

    // Fetch Messages — staleTime 30s; WS keeps cache fresh so always-refetch is unnecessary
    const { data: messages = [], isLoading } = useQuery({
        queryKey: ['chatMessages', chatId],
        queryFn: () => chatService.getMessages(chatId!),
        enabled: !!chatId,
        refetchOnMount: false,
        staleTime: 30_000,
        gcTime: CACHE_TIME.REAL_TIME.gcTime,
    });

    // Notify Flutter when entering/leaving chat room (to suppress push notifications)
    useEffect(() => {
        if (chatId) {
            nativeBridge.callMethod('setCurrentChatRoom', { chatRoomId: chatId });
        }
        return () => {
            nativeBridge.callMethod('setCurrentChatRoom', { chatRoomId: null });
        };
    }, [chatId]);

    // Prefetch compressImage on mount so it's ready when user picks a photo
    useEffect(() => {
        import('../../utils/imageCompression').catch(() => { /* ignore */ });
    }, []);

    // Prefetch emoticon metadata + warm first-page images so the picker opens
    // without a cold fetch + decode burst (첫 오픈 시 다운로드·GIF 디코딩 1~2초 체감 해소).
    useEffect(() => {
        let cancelled = false;
        queryClient
            .fetchQuery({
                queryKey: ['emoticons'],
                queryFn: emoticonService.getEmoticons,
                staleTime: Infinity,
            })
            .then((list) => {
                if (cancelled || !Array.isArray(list)) return;
                const warm = () => {
                    list.slice(0, 12).forEach((e) => {
                        const src = e.imageUrlThumbnail ?? e.imageUrl;
                        if (!src) return;
                        const img = new Image();
                        img.decoding = 'async';
                        img.src = src;
                    });
                };
                const ric = (window as unknown as { requestIdleCallback?: (cb: () => void) => void })
                    .requestIdleCallback;
                if (ric) ric(warm);
                else setTimeout(warm, 200);
            })
            .catch(() => { /* ignore — picker will still open */ });
        return () => { cancelled = true; };
    }, [queryClient]);

    // Cleanup pending ack timeouts on unmount
    useEffect(() => {
        const pending = pendingAcksRef.current;
        return () => { pending.forEach(clearTimeout); pending.clear(); };
    }, []);

    // Send Message Mutation
    const sendMutation = useMutation({
        mutationFn: async ({ content, messageType, fileId, emoticonId, replyToId, clientMsgId }: {
            content: string | null;
            messageType?: 'TEXT' | 'IMAGE' | 'VIDEO' | 'FILE' | 'EMOTICON';
            fileId?: number;
            emoticonId?: number;
            replyToId?: number;
            clientMsgId?: string;
        }) => {
            return chatService.sendMessage(chatId!, content, { messageType, fileId, emoticonId, replyToId, clientMsgId });
        },
        onError: () => showToast('메시지 전송에 실패했습니다.', 'error'),
        onSuccess: (newMessage) => {
            // Clear ack timeout on REST success (WS echo may still arrive but dedup handles it)
            if (newMessage.clientMsgId) {
                const tid = pendingAcksRef.current.get(newMessage.clientMsgId);
                if (tid !== undefined) { clearTimeout(tid); pendingAcksRef.current.delete(newMessage.clientMsgId); }
            }
            queryClient.setQueryData(['chatMessages', chatId], (old: ChatMessage[] | undefined) => {
                const oldMessages = old || [];
                // Replace optimistic row by clientMsgId if present
                if (newMessage.clientMsgId) {
                    const optIdx = oldMessages.findIndex(
                        (m: ChatMessage) => m.clientMsgId === newMessage.clientMsgId && m._optimistic
                    );
                    if (optIdx !== -1) {
                        const updated = [...oldMessages];
                        updated[optIdx] = newMessage;
                        return updated;
                    }
                }
                // Regular dedup by server ID
                if (oldMessages.some((m: ChatMessage) => String(m.id) === String(newMessage.id))) {
                    return oldMessages;
                }
                return [...oldMessages, newMessage];
            });
            setReplyingTo(null);
        },
    });

    // Upload File Mutation
    const uploadMutation = useMutation({
        mutationFn: (file: File) => chatService.uploadFile(file),
    });

    // Find current chat room from chatRooms list
    const currentRoom = chatRooms?.find(room => String(room.id) === chatId);

    // Get other participant info (for 1:1 chat, filter out current user)
    const otherParticipant = currentRoom?.participants?.find(p => p.id !== currentUserId);

    // PERF-008: 매 렌더 새 객체 리터럴이면 React.memo(ChatMessageItem) 의 얕은 비교가 항상 실패해
    // 입력창 타이핑·WS typing·메뉴 토글 같은 로컬 state 변경마다 메시지 목록 전체가 리렌더된다.
    const chatUser = useMemo(() => (isGroupChat ? {
        id: chatId || '1',
        name: currentRoom?.name || '그룹 채팅',
        profileImgs: currentRoom?.participants?.slice(0, 4).map(p =>
            p.profileImageUrl || '/assets/images/temp/temp_dog_profile.png'
        ) || [],
    } : {
        id: chatId || '1',
        name: otherParticipant?.nickname || currentRoom?.name || '상대방',
        profileImg: otherParticipant?.profileImageUrl || '/assets/images/temp/temp_dog_profile.png',
    }), [isGroupChat, chatId, currentRoom, otherParticipant]);

    // 메시지 아이템에 넘기는 축약 형태도 메모이즈한다. 여기서 다시 새 객체로 래핑하면
    // chatUser 를 메모이즈한 의미가 사라진다.
    const messageChatUser = useMemo(
        () => ('profileImg' in chatUser
            ? chatUser
            : { name: chatUser.name, profileImgs: chatUser.profileImgs }),
        [chatUser],
    );

    const quickReplies = [
        '안녕하세요. 반가워요',
        '산책 같이 할까요?',
        '지금 어디로 산책 가시나요?',
    ];

    const isInitialLoadRef = useRef(true);

    const scrollToBottom = useCallback((instant = false) => {
        const container = messagesContainerRef.current;
        if (container) {
            if (instant) {
                container.scrollTop = container.scrollHeight;
            } else {
                container.scrollTo({ top: container.scrollHeight, behavior: 'smooth' });
            }
        }
    }, []);

    useEffect(() => {
        if (messages.length > 0) {
            if (isInitialLoadRef.current) {
                // Initial load: scroll immediately, then again after images load
                scrollToBottom(true);
                const timer = setTimeout(() => scrollToBottom(true), 300);
                isInitialLoadRef.current = false;
                return () => clearTimeout(timer);
            } else {
                // New message: smooth scroll
                scrollToBottom(false);
            }
        }
    }, [messages, scrollToBottom]);

    useEffect(() => {
        if (chatId) {
            chatService.markAsRead(chatId).then(() => {
                // Invalidate chatRooms to update unread count in the list
                queryClient.invalidateQueries({ queryKey: ['chatRooms'] });
            });
        }
    }, [chatId, queryClient]);

    // Auto-scroll when keyboard opens (viewport shrinks)
    // --app-height is set globally by useAppHeight in App.tsx
    useEffect(() => {
        const viewport = window.visualViewport;
        let prevHeight = viewport ? viewport.height : window.innerHeight;
        let prevWidth = viewport ? viewport.width : window.innerWidth;
        let resizeTimer: ReturnType<typeof setTimeout> | null = null;

        const handleResize = () => {
            const currentHeight = viewport ? viewport.height : window.innerHeight;
            const currentWidth = viewport ? viewport.width : window.innerWidth;
            const keyboardOpened = currentHeight < prevHeight && currentWidth === prevWidth;

            prevHeight = currentHeight;
            prevWidth = currentWidth;

            if (keyboardOpened) {
                scrollToBottom(true);
                // Re-scroll after iOS keyboard animation settles
                if (resizeTimer) clearTimeout(resizeTimer);
                resizeTimer = setTimeout(() => {
                    requestAnimationFrame(() => scrollToBottom(true));
                }, 300);
            }
        };

        if (viewport) {
            viewport.addEventListener('resize', handleResize);
        }
        window.addEventListener('resize', handleResize);

        return () => {
            if (resizeTimer) clearTimeout(resizeTimer);
            if (viewport) {
                viewport.removeEventListener('resize', handleResize);
            }
            window.removeEventListener('resize', handleResize);
        };
    }, [scrollToBottom]);

    // Fallback: scroll on textarea focus (for browsers without visualViewport)
    useEffect(() => {
        const textarea = textareaRef.current;
        if (!textarea) return;

        const handleFocus = () => {
            setTimeout(() => scrollToBottom(true), 300);
        };

        textarea.addEventListener('focus', handleFocus);
        return () => textarea.removeEventListener('focus', handleFocus);
    }, [scrollToBottom]);

    const handleSendMessage = async () => {
        // Stop typing indicator when sending
        if (typingTimeoutRef.current) {
            clearTimeout(typingTimeoutRef.current);
            typingTimeoutRef.current = null;
        }
        sendTypingEvent(false);

        if (selectedFiles.length > 0) {
            await handleSendFiles();
            return;
        }
        if (message.trim()) {
            const replyToId = replyingTo?.messageId;
            const msgContent = message;
            setMessage('');

            if (OPTIMISTIC_ENABLED) {
                const clientMsgId = crypto.randomUUID();
                const sentAt = Date.now();

                // Add optimistic row immediately
                const optimisticMsg: ChatMessage = {
                    id: -sentAt,
                    roomId: Number(chatId),
                    senderId: currentUserId,
                    senderNickname: user?.nickname || '',
                    textContent: msgContent,
                    timestamp: new Date().toISOString(),
                    type: 'text',
                    replyToId,
                    clientMsgId,
                    _optimistic: true,
                    _sentAt: sentAt,
                };
                queryClient.setQueryData(['chatMessages', chatId], (old: ChatMessage[] | undefined) => [
                    ...(old || []),
                    optimisticMsg,
                ]);

                // Ack timeout: 5 000ms → NETWORK fallback
                const timeoutId = setTimeout(() => {
                    queryClient.setQueryData(['chatMessages', chatId], (old: ChatMessage[] | undefined) => {
                        if (!old) return old;
                        return old.map((m: ChatMessage) =>
                            m.clientMsgId === clientMsgId && m._optimistic
                                ? { ...m, _rejectedReason: 'NETWORK' as const }
                                : m
                        );
                    });
                    pendingAcksRef.current.delete(clientMsgId);
                }, 5_000);
                pendingAcksRef.current.set(clientMsgId, timeoutId);

                sendMutation.mutate({ content: msgContent, replyToId, clientMsgId });
            } else {
                sendMutation.mutate({ content: msgContent, replyToId });
            }
        }
    };

    const handleSendFiles = async () => {
        if (selectedFiles.length === 0) return;

        setIsUploading(true);
        setUploadProgress(0);

        try {
            const totalFiles = selectedFiles.length;

            for (let i = 0; i < totalFiles; i++) {
                const selectedFile = selectedFiles[i];
                setUploadProgress(Math.round((i / totalFiles) * 100));

                const messageType = selectedFile.type === 'image' ? 'IMAGE'
                    : selectedFile.type === 'video' ? 'VIDEO'
                    : 'FILE';

                // Optimistic image preview: show blob URL immediately
                let clientMsgId: string | undefined;
                if (OPTIMISTIC_ENABLED && selectedFile.type === 'image') {
                    clientMsgId = crypto.randomUUID();
                    const sentAt = Date.now();
                    const optimisticMsg: ChatMessage = {
                        id: -(sentAt + i),
                        roomId: Number(chatId),
                        senderId: currentUserId,
                        senderNickname: user?.nickname || '',
                        textContent: i === 0 ? (message || '') : '',
                        timestamp: new Date().toISOString(),
                        type: 'image',
                        clientMsgId,
                        _optimistic: true,
                        _sentAt: sentAt,
                        file: {
                            id: 0,
                            url: selectedFile.preview, // blob URL — swapped to server URL on WS echo
                            fileType: 'IMAGE',
                            mimeType: selectedFile.file.type,
                        },
                    };
                    queryClient.setQueryData(['chatMessages', chatId], (old: ChatMessage[] | undefined) => [
                        ...(old || []),
                        optimisticMsg,
                    ]);

                    // Ack timeout: 20 000ms → NETWORK fallback
                    const timeoutId = setTimeout(() => {
                        queryClient.setQueryData(['chatMessages', chatId], (old: ChatMessage[] | undefined) => {
                            if (!old) return old;
                            return old.map((m: ChatMessage) =>
                                m.clientMsgId === clientMsgId && m._optimistic
                                    ? { ...m, _rejectedReason: 'NETWORK' as const }
                                    : m
                            );
                        });
                        pendingAcksRef.current.delete(clientMsgId!);
                    }, 20_000);
                    pendingAcksRef.current.set(clientMsgId, timeoutId);
                }

                // Upload file first
                const uploadResult = await uploadMutation.mutateAsync(selectedFile.file);

                // Send message with file (only include text message with the first file)
                await sendMutation.mutateAsync({
                    content: i === 0 ? (message || '') : '',
                    messageType,
                    fileId: uploadResult.id,
                    clientMsgId,
                });
            }

            // Clear selected files and message
            selectedFiles.forEach(f => URL.revokeObjectURL(f.preview));
            setSelectedFiles([]);
            setMessage('');
        } catch (error) {
            console.error('Failed to send files:', error);
            showAlert('파일 전송에 실패했습니다.');
        } finally {
            setIsUploading(false);
            setUploadProgress(0);
        }
    };

    const handleQuickReply = (text: string) => {
        if (sendMutation.isPending || isUploading) return;
        sendTypingEvent(false);
        sendMutation.mutate({ content: text });
    };

    const handleSelectEmoticon = (emoticon: { id: number; imageUrl: string }) => {
        setSelectedEmoticon(emoticon);
    };

    const handleSendSelectedEmoticon = () => {
        if (!selectedEmoticon) return;
        sendMutation.mutate({ content: null, messageType: 'EMOTICON', emoticonId: selectedEmoticon.id });
        setSelectedEmoticon(null);
        setShowEmoticonPicker(false);
    };

    const handleBlockUser = () => {
        setShowHeaderMenu(false);
        if (!otherParticipant) return;
        showConfirm('이 사용자를 차단하시겠습니까?\n차단하면 서로의 게시글과 프로필이 보이지 않습니다.', async () => {
            try {
                await blockService.blockUser(otherParticipant.id);
                showAlert('사용자를 차단했습니다.', () => {
                    navigate('/chat', { state: { activeTab: 'one' } });
                });
            } catch {
                showAlert('차단에 실패했습니다.');
            }
        });
    };

    const handleLeaveRoom = () => {
        setShowHeaderMenu(false);
        showConfirm('채팅방을 나가시겠습니까?', async () => {
            try {
                await chatService.leaveRoom(chatId!);
            } catch {
                // ignore — still navigate away
            }
            navigate('/chat', { state: { activeTab: isGroupChat ? 'group' : 'one' } });
        });
    };

    const handleContextMenuOpen = useCallback((msg: ChatMessage, isMine: boolean, position: { x: number; y: number }) => {
        // Dismiss keyboard to prevent iOS WKWebView position: fixed issues
        if (document.activeElement instanceof HTMLElement) {
            document.activeElement.blur();
        }
        // Adjust Y for iOS visual viewport offset
        const vpOffset = window.visualViewport?.offsetTop || 0;
        setContextMenu({
            messageId: msg.id,
            isMine,
            x: position.x,
            y: position.y + vpOffset,
            textContent: msg.textContent || '',
            senderNickname: msg.senderNickname || '',
            messageType: msg.messageType || 'TEXT',
            fileUrl: msg.file?.url,
            fileName: msg.file?.originalFileName,
            emoticonImageUrl: msg.emoticonImageUrl,
        });
    }, []);

    const handleCopyMessage = useCallback(() => {
        if (!contextMenu) return;
        const text = contextMenu.textContent;
        navigator.clipboard?.writeText(text)
            .then(() => showToast('복사되었습니다'))
            .catch(() => {
                // Fallback for WebView
                const textarea = document.createElement('textarea');
                textarea.value = text;
                textarea.style.cssText = 'position:fixed;opacity:0';
                document.body.appendChild(textarea);
                textarea.select();
                document.execCommand('copy');
                document.body.removeChild(textarea);
                showToast('복사되었습니다');
            });
        setContextMenu(null);
    }, [contextMenu, showToast]);

    const handleReplyFromMenu = useCallback(() => {
        if (!contextMenu) return;
        setReplyingTo({
            messageId: contextMenu.messageId,
            senderNickname: contextMenu.senderNickname,
            textContent: contextMenu.textContent,
            messageType: contextMenu.messageType,
            fileUrl: contextMenu.fileUrl,
            fileName: contextMenu.fileName,
            emoticonImageUrl: contextMenu.emoticonImageUrl,
        });
        setContextMenu(null);
        setTimeout(() => textareaRef.current?.focus(), 100);
    }, [contextMenu]);

    const handleDeleteMessage = useCallback(() => {
        if (!contextMenu) return;
        setContextMenu(null);
        showConfirm('메시지를 삭제하시겠습니까?', async () => {
            try {
                await chatService.deleteMessage(chatId!, contextMenu.messageId);
                queryClient.invalidateQueries({ queryKey: ['chatMessages', chatId] });
                showToast('메시지가 삭제되었습니다');
            } catch {
                showToast('메시지 삭제에 실패했습니다');
            }
        });
    }, [contextMenu, chatId, queryClient, showConfirm, showToast]);

    const handleKeyDown = (e: React.KeyboardEvent<HTMLTextAreaElement>) => {
        // 한글 IME 조합 중이면 무시 (마지막 글자 중복 방지)
        if (e.nativeEvent.isComposing) return;

        // Mobile: Enter for newline, send button for sending
        // Desktop: Shift+Enter for newline, Enter for sending (optional - currently disabled)
        // Uncomment below to enable Enter-to-send on desktop only:
        // if (e.key === 'Enter' && !e.shiftKey && !e.metaKey && !e.ctrlKey) {
        //     e.preventDefault();
        //     handleSendMessage();
        // }
    };

    // Auto-resize textarea based on content
    const adjustTextareaHeight = () => {
        const textarea = textareaRef.current;
        if (textarea) {
            textarea.style.height = 'auto';
            const maxHeight = 88; // ~4 lines max
            textarea.style.height = `${Math.min(textarea.scrollHeight, maxHeight)}px`;
        }
    };

    useEffect(() => {
        adjustTextareaHeight();
    }, [message]);

    const handleAttachClick = () => {
        setShowAttachMenu(!showAttachMenu);
    };

    const handleFileSelect = (accept?: string, capture?: string) => {
        if (fileInputRef.current) {
            // For "all files", remove accept attribute entirely for best browser compatibility
            if (accept) {
                fileInputRef.current.accept = accept;
            } else {
                fileInputRef.current.removeAttribute('accept');
            }
            if (capture) {
                fileInputRef.current.setAttribute('capture', capture);
            } else {
                fileInputRef.current.removeAttribute('capture');
            }
            fileInputRef.current.click();
        }
        setShowAttachMenu(false);
    };

    const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
        const files = e.target.files;
        if (!files || files.length === 0) return;

        const newFiles: SelectedFile[] = [];
        const remainingSlots = MAX_FILES - selectedFiles.length;

        for (let i = 0; i < Math.min(files.length, remainingSlots); i++) {
            const file = files[i];

            // Determine file type
            let type: 'image' | 'video' | 'file' = 'file';
            if (file.type.startsWith('image/')) {
                type = 'image';
            } else if (file.type.startsWith('video/')) {
                type = 'video';
            }

            // Create preview URL
            const preview = URL.createObjectURL(file);
            newFiles.push({ file, preview, type });
        }

        if (files.length > remainingSlots) {
            showAlert(`최대 ${MAX_FILES}개까지만 첨부할 수 있습니다.`);
        }

        setSelectedFiles(prev => [...prev, ...newFiles]);

        // Reset input
        e.target.value = '';
    };

    const handleCancelFile = (index: number) => {
        setSelectedFiles(prev => {
            const newFiles = [...prev];
            URL.revokeObjectURL(newFiles[index].preview);
            newFiles.splice(index, 1);
            return newFiles;
        });
    };

    const handleCancelAllFiles = () => {
        selectedFiles.forEach(f => URL.revokeObjectURL(f.preview));
        setSelectedFiles([]);
    };

    const renderMessageContent = useCallback((msg: ChatMessage) => {
        const content = msg.textContent || '';
        const msgType = msg.messageType || msg.type || 'TEXT';
        const file: FileInfo | undefined = msg.file;

        // Handle image messages
        if ((msgType === 'IMAGE' || msgType === 'image') && file) {
            // 버블 안 이미지는 medium variant, 탭 시 풀뷰는 viewer(없으면 원본)
            const previewUrl = file.urlViewer || file.url;
            return (
                <div className="chat_detail_message_image">
                    <GpImage
                        src={file.url}
                        thumbnailSrc={file.urlThumbnail}
                        mediumSrc={file.urlMedium}
                        viewerSrc={file.urlViewer}
                        variant="medium"
                        alt="첨부 이미지"
                        onClick={() => setPreviewImageUrl(previewUrl)}
                        style={{ cursor: 'pointer' }}
                    />
                    {content && <div className="chat_detail_message_caption">{content}</div>}
                </div>
            );
        }

        // Handle video messages
        if ((msgType === 'VIDEO' || msgType === 'video') && file) {
            return (
                <div className="chat_detail_message_video">
                    <video src={file.url} controls />
                    {content && <div className="chat_detail_message_caption">{content}</div>}
                </div>
            );
        }

        // Handle file messages
        if ((msgType === 'FILE' || msgType === 'file') && file) {
            const fileName = file.originalFileName || file.url.split('/').pop() || '파일';
            const fileSize = file.sizeBytes ? formatFileSize(file.sizeBytes) : '';
            const handleDownload = async () => {
                try {
                    // Try native bridge (WebView) - download to device
                    if ((window as unknown as Record<string, unknown>).NativeBridge) {
                        const { nativeBridge } = await import('../../bridge/nativeBridge');
                        await nativeBridge.callMethod('downloadFile', { url: file.url, fileName });
                        return;
                    }
                } catch {
                    // fallback below
                }
                // Browser fallback
                window.open(file.url, '_blank');
            };
            return (
                <div className="chat_detail_message_file" onClick={handleDownload} style={{ cursor: 'pointer' }}>
                    <div className="chat_detail_file_icon">
                        {getFileIcon(file)}
                    </div>
                    <div className="chat_detail_file_info">
                        <span className="chat_detail_file_name">{fileName}</span>
                        {fileSize && <span className="chat_detail_file_size">{fileSize}</span>}
                    </div>
                </div>
            );
        }

        // Handle emoticon messages
        if ((msgType === 'EMOTICON' || msgType === 'emoticon') && (msg.emoticonImageUrl || msg.emoticonId)) {
            return (
                <div className="chat_detail_message_emoticon">
                    {msg.emoticonImageUrl ? (
                        <GpImage
                            src={msg.emoticonImageUrl}
                            thumbnailSrc={msg.emoticonImageUrlThumbnail}
                            viewerSrc={msg.emoticonImageUrlViewer}
                            variant="viewer"
                            alt={msg.emoticonName || '이모티콘'}
                        />
                    ) : (
                        <div className="chat_detail_message_emoticon_deleted">삭제된 이모티콘</div>
                    )}
                </div>
            );
        }

        // Default: text message - use ExpandableText component
        return <ExpandableText content={content} />;
    }, [setPreviewImageUrl]);

    if (isLoading) return <Loading description="대화 내용을 불러오고 있어요" />;

    return (
        <div className="chat_detail_page" {...keyboardDismiss}>
            {/* Hidden file input */}
            <input
                type="file"
                ref={fileInputRef}
                style={{ display: 'none' }}
                onChange={handleFileChange}
                multiple
            />

            {/* Header */}
            <div className="chat_detail_header">
                <div className="chat_detail_header_top">
                    <div className="chat_detail_header_spacer"></div>
                </div>
                <div className="chat_detail_header_content">
                    <div className="chat_detail_header_inner">
                        <BackButton onClick={() => navigate('/chat', { state: { activeTab: isGroupChat ? 'group' : 'one' } })} />
                        <div className="chat_detail_user_info">
                                {isGroupChat && 'profileImgs' in chatUser ? (
                                <div className="chat_detail_profile_img chat_detail_profile_img_group">
                                    {chatUser.profileImgs?.slice(0, 4).map((img, index) => (
                                        <div
                                            key={index}
                                            className="chat_detail_profile_img_item"
                                            style={{ zIndex: 4 - index }}
                                        >
                                            <img src={img} alt={`${chatUser.name} 멤버 ${index + 1}`} />
                                        </div>
                                    ))}
                                </div>
                            ) : (
                                <div className="chat_detail_profile_img">
                                    <img src={'profileImg' in chatUser ? chatUser.profileImg : ''} alt={chatUser.name} />
                                </div>
                            )}
                            <div className="chat_detail_user_name">
                                {chatUser.name}
                                {isGroupChat && currentRoom && (
                                    <span className="chat_detail_member_count">
                                        <svg width="13" height="13" viewBox="0 0 24 24" fill="none">
                                            <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2" stroke="#A58A54" strokeWidth="2" strokeLinecap="round"/>
                                            <circle cx="9" cy="7" r="4" stroke="#A58A54" strokeWidth="2"/>
                                            <path d="M23 21v-2a4 4 0 0 0-3-3.87M16 3.13a4 4 0 0 1 0 7.75" stroke="#A58A54" strokeWidth="2" strokeLinecap="round"/>
                                        </svg>
                                        {currentRoom.participants?.length || 0}
                                    </span>
                                )}
                            </div>
                        </div>
                        <button className="chat_detail_menu_btn" onClick={() => setShowHeaderMenu(!showHeaderMenu)}>
                            <img src="/assets/images/common/option_icon.svg" alt="메뉴" />
                        </button>
                    </div>
                    <div className="chat_detail_header_divider"></div>
                </div>
            </div>

            {/* Header Menu Bottom Sheet */}
            {showHeaderMenu && (
                <div className="chat_detail_menu_overlay" onClick={() => setShowHeaderMenu(false)}>
                    <div className="chat_detail_menu_sheet" onClick={(e) => e.stopPropagation()}>
                        <button type="button" onClick={() => { setShowHeaderMenu(false); setShowInviteModal(true); }}>대화상대 초대하기</button>
                        {!isGroupChat && (
                            <>
                                <div className="divider" />
                                <button type="button" onClick={handleBlockUser}>차단하기</button>
                            </>
                        )}
                        <div className="divider" />
                        <button type="button" onClick={handleLeaveRoom}>채팅방 나가기</button>
                        <div className="divider" />
                        <button type="button" onClick={() => setShowHeaderMenu(false)}>취소</button>
                    </div>
                </div>
            )}

            {/* Context Menu Overlay */}
            {contextMenu && (() => {
                const menuButtonCount = (contextMenu.messageType === 'TEXT' ? 1 : 0) + 1 + 1;
                const menuHeight = menuButtonCount * 48;
                return (
                <div className="chat_detail_context_overlay" onClick={() => setContextMenu(null)}>
                    <div
                        className="chat_detail_context_menu"
                        style={{
                            top: Math.min(contextMenu.y, window.innerHeight - menuHeight - 8),
                            left: Math.min(Math.max(contextMenu.x - 70, 8), window.innerWidth - 148),
                        }}
                        onClick={(e) => e.stopPropagation()}
                    >
                        {contextMenu.messageType === 'TEXT' && (
                            <button type="button" onClick={handleCopyMessage}>
                                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                                    <rect x="9" y="9" width="13" height="13" rx="2" ry="2"/>
                                    <path d="M5 15H4a2 2 0 01-2-2V4a2 2 0 012-2h9a2 2 0 012 2v1"/>
                                </svg>
                                복사
                            </button>
                        )}
                        <button type="button" onClick={handleReplyFromMenu}>
                            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                                <polyline points="9 17 4 12 9 7"/>
                                <path d="M20 18v-2a4 4 0 00-4-4H4"/>
                            </svg>
                            답장
                        </button>
                        {contextMenu.isMine ? (
                            <button type="button" className="chat_detail_context_menu_delete" onClick={handleDeleteMessage}>
                                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                                    <polyline points="3 6 5 6 21 6"/>
                                    <path d="M19 6v14a2 2 0 01-2 2H7a2 2 0 01-2-2V6m3 0V4a2 2 0 012-2h4a2 2 0 012 2v2"/>
                                </svg>
                                삭제
                            </button>
                        ) : (
                            <button type="button" onClick={() => {
                                setReportTargetMessageId(contextMenu.messageId);
                                setContextMenu(null);
                                setShowReportModal(true);
                            }}>
                                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                                    <path d="M4 15s1-1 4-1 5 2 8 2 4-1 4-1V3s-1 1-4 1-5-2-8-2-4 1-4 1z"/>
                                    <line x1="4" y1="22" x2="4" y2="15"/>
                                </svg>
                                신고하기
                            </button>
                        )}
                    </div>
                </div>
                );
            })()}

            {/* Messages */}
            <div className="chat_detail_messages" ref={messagesContainerRef}>
                {messages.map((msg: ChatMessage) => {
                    const isMine = Number(msg.senderId) === currentUserId;
                    return (
                        <ChatMessageItem
                            key={msg.id}
                            msg={msg}
                            isMine={isMine}
                            isGroupChat={isGroupChat}
                            currentRoom={currentRoom}
                            chatUser={messageChatUser}
                            onLongPress={handleContextMenuOpen}
                            renderMessageContent={renderMessageContent}
                        />
                    );
                })}

                {/* Typing Indicator */}
                {typingUsers.size > 0 && (
                    <div className="chat_detail_typing_indicator">
                        <div className="chat_detail_typing_dots">
                            <span></span>
                            <span></span>
                            <span></span>
                        </div>
                        <span className="chat_detail_typing_text">
                            {isGroupChat
                                ? `${Array.from(typingUsers).map(userId => currentRoom?.participants?.find(p => p.id === userId)?.nickname).filter(Boolean).join(', ') || typingUsers.size + '명'}님이 입력 중`
                                : `${chatUser.name}님이 입력 중`}
                        </span>
                    </div>
                )}

                <div ref={messagesEndRef} />
            </div>

            {/* Emoticon Preview Overlay */}
            {selectedEmoticon && (
                <div className="chat_detail_emoticon_preview_overlay">
                    <div className="chat_detail_emoticon_preview_content">
                        <img src={selectedEmoticon.imageUrl} alt="이모티콘 미리보기" />
                        <button
                            type="button"
                            className="chat_detail_emoticon_preview_close"
                            onClick={() => setSelectedEmoticon(null)}
                        >
                            <svg width="16" height="16" viewBox="0 0 16 16" fill="none">
                                <path d="M12 4L4 12M4 4L12 12" stroke="white" strokeWidth="2" strokeLinecap="round"/>
                            </svg>
                        </button>
                    </div>
                </div>
            )}

            {/* File Preview */}
            {selectedFiles.length > 0 && (
                <div className="chat_detail_file_preview_container">
                    <div className="chat_detail_file_preview_header">
                        <span className="chat_detail_file_preview_count">{selectedFiles.length}개 파일 선택됨</span>
                        <button
                            type="button"
                            className="chat_detail_file_preview_clear_all"
                            onClick={handleCancelAllFiles}
                        >
                            전체 삭제
                        </button>
                    </div>
                    <div className="chat_detail_file_preview_list">
                        {selectedFiles.map((selectedFile, index) => (
                            <div key={index} className="chat_detail_file_preview">
                                <div className="chat_detail_file_preview_content">
                                    {selectedFile.type === 'image' && (
                                        <img src={selectedFile.preview} alt="미리보기" />
                                    )}
                                    {selectedFile.type === 'video' && (
                                        <video src={selectedFile.preview} />
                                    )}
                                    {selectedFile.type === 'file' && (
                                        <div className="chat_detail_file_preview_doc">
                                            {getFileIcon(selectedFile.file)}
                                            <span className="chat_detail_file_preview_ext">
                                                {selectedFile.file.name.split('.').pop()?.toUpperCase()}
                                            </span>
                                        </div>
                                    )}
                                </div>
                                <button
                                    type="button"
                                    className="chat_detail_file_preview_remove"
                                    onClick={() => handleCancelFile(index)}
                                >
                                    <svg width="12" height="12" viewBox="0 0 24 24" fill="none">
                                        <path d="M18 6L6 18M6 6L18 18" stroke="var(--color-primary)" strokeWidth="2" strokeLinecap="round"/>
                                    </svg>
                                </button>
                            </div>
                        ))}
                    </div>
                </div>
            )}


            {/* Click outside to close attach menu */}
            {showAttachMenu && (
                <div
                    className="chat_detail_attach_menu_overlay"
                    onClick={() => setShowAttachMenu(false)}
                />
            )}

            {/* Input Area */}
            <div className="chat_detail_input_area">
                {/* Quick Replies */}
                {selectedFiles.length === 0 && (
                    <div className="chat_detail_quick_replies">
                        {quickReplies.map((reply, index) => (
                            <button
                                key={index}
                                type="button"
                                className="chat_detail_quick_reply_btn btn-effect"
                                onClick={() => handleQuickReply(reply)}
                                disabled={sendMutation.isPending || isUploading}
                            >
                                {reply}
                            </button>
                        ))}
                    </div>
                )}

                {/* Input */}
                <div className="chat_detail_input_container">
                    {/* Attachment Menu */}
                    {showAttachMenu && (
                        <div className="chat_detail_attach_menu">
                            <button type="button" onClick={() => handleFileSelect('image/*,video/*', 'environment')}>
                                <svg width="24" height="24" viewBox="0 0 24 24" fill="none">
                                    <path d="M23 19C23 19.5304 22.7893 20.0391 22.4142 20.4142C22.0391 20.7893 21.5304 21 21 21H3C2.46957 21 1.96086 20.7893 1.58579 20.4142C1.21071 20.0391 1 19.5304 1 19V8C1 7.46957 1.21071 6.96086 1.58579 6.58579C1.96086 6.21071 2.46957 6 3 6H7L9 3H15L17 6H21C21.5304 6 22.0391 6.21071 22.4142 6.58579C22.7893 6.96086 23 7.46957 23 8V19Z" stroke="var(--color-primary)" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
                                    <circle cx="12" cy="13" r="4" stroke="var(--color-primary)" strokeWidth="2"/>
                                </svg>
                                <span>카메라</span>
                            </button>
                            <button type="button" onClick={() => handleFileSelect('image/*,video/*')}>
                                <svg width="24" height="24" viewBox="0 0 24 24" fill="none">
                                    <rect x="3" y="3" width="18" height="18" rx="2" stroke="var(--color-primary)" strokeWidth="2"/>
                                    <circle cx="8.5" cy="8.5" r="1.5" fill="var(--color-primary)"/>
                                    <path d="M21 15L16 10L5 21" stroke="var(--color-primary)" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
                                </svg>
                                <span>앨범</span>
                            </button>
                            <button type="button" onClick={() => handleFileSelect()}>
                                <svg width="24" height="24" viewBox="0 0 24 24" fill="none">
                                    <path d="M14 2H6C5.46957 2 4.96086 2.21071 4.58579 2.58579C4.21071 2.96086 4 3.46957 4 4V20C4 20.5304 4.21071 21.0391 4.58579 21.4142C4.96086 21.7893 5.46957 22 6 22H18C18.5304 22 19.0391 21.7893 19.4142 21.4142C19.7893 21.0391 20 20.5304 20 20V8L14 2Z" stroke="var(--color-primary)" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
                                    <path d="M14 2V8H20" stroke="var(--color-primary)" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
                                </svg>
                                <span>파일</span>
                            </button>
                        </div>
                    )}

                    <div className="chat_detail_input_wrap">
                        <button
                            type="button"
                            className={`chat_detail_attach_btn btn-effect ${showAttachMenu ? 'active' : ''}`}
                            onClick={handleAttachClick}
                        >
                             <svg width="24" height="24" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                                <path d="M12.544 4.3847L5.44005 11.4887C3.59572 13.333 3.75132 16.4789 5.57312 18.3007C7.39492 20.1225 10.6668 20.4041 12.5111 18.5597L19.6151 11.4558C20.9564 10.1144 20.8255 8.09719 19.5006 6.77225C18.1756 5.44731 16.1584 5.31639 14.817 6.65773L8.82838 12.6464C8.0265 13.4483 7.60881 14.6088 8.4369 15.4369C9.26499 16.265 10.4255 15.8473 11.2274 15.0454L16.6368 9.63603" stroke="var(--color-primary)" strokeWidth="2" strokeLinecap="round"/>
                            </svg>
                        </button>
                        <div className="chat_detail_input_field">
                            {replyingTo && (
                                <div className="chat_detail_reply_inline">
                                    <div className="chat_detail_reply_inline_header">
                                        <div className="chat_detail_reply_inline_left">
                                            <svg width="16" height="16" viewBox="0 0 16 16" fill="none">
                                                <path d="M14 10L8 4L2 10" stroke="#A58A54" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" transform="rotate(180 8 7) translate(0 -2)"/>
                                            </svg>
                                            <span className="chat_detail_reply_inline_name">{replyingTo.senderNickname}에게 답장</span>
                                        </div>
                                        <div className="chat_detail_reply_inline_right">
                                            {(replyingTo.messageType === 'IMAGE' || replyingTo.messageType === 'VIDEO') && replyingTo.fileUrl && (
                                                <img className="chat_detail_reply_inline_thumb" src={replyingTo.fileUrl} alt="사진" />
                                            )}
                                            {replyingTo.messageType === 'EMOTICON' && replyingTo.emoticonImageUrl && (
                                                <img className="chat_detail_reply_inline_thumb" src={replyingTo.emoticonImageUrl} alt="이모티콘" />
                                            )}
                                            <button
                                                type="button"
                                                className="chat_detail_reply_inline_cancel"
                                                onClick={() => setReplyingTo(null)}
                                            >
                                                <svg width="12" height="12" viewBox="0 0 12 12" fill="none">
                                                    <path d="M9 3L3 9M3 3L9 9" stroke="#A58A54" strokeWidth="1.5" strokeLinecap="round"/>
                                                </svg>
                                            </button>
                                        </div>
                                    </div>
                                    <span className="chat_detail_reply_inline_text">
                                        {replyingTo.messageType === 'FILE' ? `파일: ${replyingTo.fileName || '파일'}` :
                                         replyingTo.messageType === 'IMAGE' ? '사진' :
                                         replyingTo.messageType === 'VIDEO' ? '동영상' :
                                         replyingTo.messageType === 'EMOTICON' ? '이모티콘' :
                                         replyingTo.textContent}
                                    </span>
                                    <div className="chat_detail_reply_inline_divider" />
                                </div>
                            )}
                            <div className="chat_detail_input_row">
                            <textarea
                                ref={textareaRef}
                                className="chat_detail_input"
                                placeholder={selectedFiles.length > 0 ? "캡션을 입력하세요..." : "메세지를 입력하세요..."}
                                value={message}
                                maxLength={5000}
                                rows={1}
                                onChange={(e) => {
                                    setMessage(e.target.value);
                                    handleTyping();
                                }}
                                onKeyDown={handleKeyDown}
                            />
                            {message.length > 100 && (
                                <span className="chat_detail_char_count">
                                    {message.length}/5000
                                </span>
                            )}
                            <button
                                type="button"
                                className={`chat_detail_emoji_btn btn-effect ${showEmoticonPicker ? 'active' : ''}`}
                                onClick={() => {
                                    const opening = !showEmoticonPicker;
                                    setShowEmoticonPicker(opening);
                                    setShowAttachMenu(false);
                                    if (opening) { textareaRef.current?.blur(); }
                                    if (!opening) { setSelectedEmoticon(null); }
                                }}
                                aria-label="이모티콘"
                            >
                                <svg width="24" height="24" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                                    <circle cx="12" cy="12" r="10" stroke="var(--color-primary)" strokeWidth="2"/>
                                    <path d="M8 14C8 14 9.5 16 12 16C14.5 16 16 14 16 14" stroke="var(--color-primary)" strokeWidth="2" strokeLinecap="round"/>
                                    <circle cx="9" cy="10" r="1" fill="var(--color-primary)"/>
                                    <circle cx="15" cy="10" r="1" fill="var(--color-primary)"/>
                                </svg>
                            </button>
                            </div>
                        </div>
                        <button
                            type="button"
                            className="chat_detail_send_btn btn-effect"
                            onMouseDown={(e) => e.preventDefault()}
                            onClick={selectedEmoticon ? handleSendSelectedEmoticon : handleSendMessage}
                            disabled={(!message.trim() && selectedFiles.length === 0 && !selectedEmoticon) || isUploading}
                        >
                            <span>{isUploading ? `${uploadProgress}%` : '전송'}</span>
                        </button>
                    </div>
                </div>
            </div>

            {/* Emoticon Picker */}
            {showEmoticonPicker && (
                <EmoticonPicker
                    onSelect={handleSelectEmoticon}
                    onClose={() => setShowEmoticonPicker(false)}
                />
            )}

            {/* Image Preview Modal */}
            {previewImageUrl && (
                <div className="chat_detail_image_modal" onClick={() => setPreviewImageUrl(null)}>
                    <button
                        type="button"
                        className="chat_detail_image_modal_close"
                        onClick={(e) => { e.stopPropagation(); setPreviewImageUrl(null); }}
                    >
                        <svg width="24" height="24" viewBox="0 0 24 24" fill="none">
                            <path d="M18 6L6 18M6 6L18 18" stroke="white" strokeWidth="2.5" strokeLinecap="round"/>
                        </svg>
                    </button>
                    <img
                        src={previewImageUrl}
                        alt="이미지 미리보기"
                        className="chat_detail_image_modal_img"
                        onClick={(e) => e.stopPropagation()}
                    />
                </div>
            )}

            <InviteGroupChatModal
                isOpen={showInviteModal}
                onClose={() => setShowInviteModal(false)}
                roomId={chatId!}
                existingParticipantIds={currentRoom?.participants?.map(p => p.id) || []}
                onInviteSuccess={(roomType) => {
                    if (roomType === 'GROUP' && !isGroupChat) {
                        navigate(`/chat/group/${chatId}`, { replace: true });
                    }
                }}
            />

            <ReportModal
                isOpen={showReportModal}
                onClose={() => { setShowReportModal(false); setReportTargetMessageId(null); }}
                onSubmit={async (reason: string) => {
                    if (!reportTargetMessageId) return;
                    setIsReportSubmitting(true);
                    try {
                        await reportService.createReport({ type: 'CHAT', targetId: reportTargetMessageId, reason });
                        showToast('신고가 접수되었습니다.', 'success');
                        setShowReportModal(false);
                        setReportTargetMessageId(null);
                    } catch {
                        showToast('신고 접수에 실패했습니다.', 'error');
                    } finally {
                        setIsReportSubmitting(false);
                    }
                }}
                isSubmitting={isReportSubmitting}
            />
        </div>
    );
};

export default ChatDetailPage;
