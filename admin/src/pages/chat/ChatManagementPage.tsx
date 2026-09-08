import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { chatManagementService } from '../../services/chatManagementService';
import type { ChatRoomItem } from '../../services/chatManagementService';
import { Button } from '../../components/common/Button';
import { Pagination } from '../../components/common/Pagination';
import { toast } from 'sonner'
import { useConfirm } from '@/hooks/useConfirm'

export default function ChatManagementPage() {
  const queryClient = useQueryClient();
  const { confirm: confirmDialog, ConfirmDialog } = useConfirm()
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(20);
  const [selectedRoom, setSelectedRoom] = useState<ChatRoomItem | null>(null);

  const { data: roomsData, isLoading } = useQuery({
    queryKey: ['chat', 'list', { page, size }],
    queryFn: () => chatManagementService.getChatRooms({ page, size }),
    placeholderData: (prev) => prev,
  });

  const { data: messagesData } = useQuery({
    queryKey: ['chat', 'messages', selectedRoom?.id],
    queryFn: () => chatManagementService.getRoomMessages(selectedRoom!.id),
    enabled: !!selectedRoom,
  });

  const deleteMutation = useMutation({
    mutationFn: (roomId: number) => chatManagementService.deleteRoom(roomId),
    onSuccess: (_data, roomId) => {
      queryClient.invalidateQueries({ queryKey: ['chat'] });
      if (selectedRoom?.id === roomId) setSelectedRoom(null);
    },
    onError: () => toast.error('채팅방 삭제에 실패했습니다.'),
  });

  const handleDeleteRoom = async (roomId: number) => {
    if (!(await confirmDialog({ description: '이 채팅방을 삭제하시겠습니까?', variant: 'destructive' }))) return;
    deleteMutation.mutate(roomId);
  };

  const deleteMessageMutation = useMutation({
    mutationFn: (messageId: number) => chatManagementService.deleteMessage(messageId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['chat', 'messages', selectedRoom?.id] });
    },
    onError: () => toast.error('메시지 삭제에 실패했습니다.'),
  });

  const handleDeleteMessage = async (messageId: number) => {
    if (!(await confirmDialog({ description: '이 메시지를 삭제하시겠습니까?', variant: 'destructive' }))) return;
    deleteMessageMutation.mutate(messageId);
  };

  const rooms = roomsData?.content ?? [];
  const messages = messagesData?.content ?? [];

  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-bold text-gray-900">채팅 관리</h1>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* Room List */}
        <div className="bg-white rounded-lg shadow-sm border border-gray-200">
          <div className="p-4 border-b">
            <h2 className="font-semibold">채팅방 목록</h2>
          </div>
          <div className="divide-y max-h-[600px] overflow-y-auto">
            {isLoading ? (
              <div className="p-8 text-center text-gray-500">로딩 중...</div>
            ) : rooms.length === 0 ? (
              <div className="p-8 text-center text-gray-500">채팅방이 없습니다.</div>
            ) : (
              rooms.map((room) => (
                <div
                  key={room.id}
                  className={`p-4 cursor-pointer hover:bg-gray-50 ${selectedRoom?.id === room.id ? 'bg-indigo-50' : ''}`}
                  onClick={() => setSelectedRoom(room)}
                >
                  <div className="flex justify-between items-start">
                    <div>
                      <div className="flex items-center gap-2">
                        <span className="font-medium">{room.name}</span>
                        <span className={`px-2 py-0.5 text-xs rounded ${room.type === 'GROUP' ? 'bg-blue-100 text-blue-800' : 'bg-gray-100'}`}>
                          {room.type === 'GROUP' ? '그룹' : '1:1'}
                        </span>
                      </div>
                      <p className="text-sm text-gray-500 mt-1">{room.lastMessage || '메시지 없음'}</p>
                      <p className="text-xs text-gray-400 mt-1">참여자 {room.participantCount}명</p>
                    </div>
                    <Button
                      variant="danger"
                      size="sm"
                      onClick={(e) => { e.stopPropagation(); handleDeleteRoom(room.id); }}
                    >
                      삭제
                    </Button>
                  </div>
                </div>
              ))
            )}
          </div>
          {roomsData && roomsData.totalElements > 0 && (
            <div className="border-t">
              <Pagination
                page={page}
                totalPages={roomsData.totalPages}
                totalElements={roomsData.totalElements}
                size={size}
                onPageChange={setPage}
                onSizeChange={(newSize) => { setSize(newSize); setPage(0); }}
              />
            </div>
          )}
        </div>

        {/* Messages */}
        <div className="bg-white rounded-lg shadow-sm border border-gray-200">
          <div className="p-4 border-b">
            <h2 className="font-semibold">{selectedRoom ? selectedRoom.name : '채팅방을 선택하세요'}</h2>
          </div>
          <div className="p-4 max-h-[600px] overflow-y-auto space-y-3">
            {!selectedRoom ? (
              <div className="text-center text-gray-500 py-8">채팅방을 선택하면 메시지가 표시됩니다.</div>
            ) : messages.length === 0 ? (
              <div className="text-center text-gray-500 py-8">메시지가 없습니다.</div>
            ) : (
              messages.map((msg) => (
                <div key={msg.id} className="p-3 bg-gray-50 rounded-lg">
                  <div className="flex justify-between items-start">
                    <span className="font-medium text-sm">{msg.senderNickname}</span>
                    <div className="flex items-center gap-2">
                      <span className="text-xs text-gray-400">{msg.createdAt}</span>
                      <Button
                        variant="link"
                        size="sm"
                        className="text-red-600 hover:text-red-900"
                        onClick={() => handleDeleteMessage(msg.id)}
                      >
                        삭제
                      </Button>
                    </div>
                  </div>
                  <p className="text-sm mt-1">{msg.content}</p>
                </div>
              ))
            )}
          </div>
        </div>
      </div>
    {ConfirmDialog}
    </div>
  );
}
