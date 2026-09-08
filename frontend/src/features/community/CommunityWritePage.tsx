import { useState, useEffect } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { communityService } from '../../services/communityService';
import type { CreatePostRequest } from '../../services/communityService';
import { useAlert } from '../../contexts/AlertContext';
import { useKeyboardDismiss } from '../../hooks/useKeyboardDismiss';
import ImageUploadSlider from '../../components/common/ImageUploadSlider';
import CustomSelect from '../../components/common/CustomSelect';
export default function CommunityWritePage() {
  const navigate = useNavigate();
  const location = useLocation();
  const queryClient = useQueryClient();
  const { showAlert } = useAlert();
  const editMode = location.state?.mode === 'EDIT';
  const initialPost = location.state?.post;
  const initialCategoryId = location.state?.initialCategoryId;

  const [formData, setFormData] = useState<CreatePostRequest>({
    categoryId: initialPost?.categoryId || initialCategoryId || 1,
    title: initialPost?.title || '',
    content: initialPost?.content || '',
    postType: initialPost?.postType || 'GENERAL',
  });

  /* Image State */
  type MediaItem = { file?: File; url?: string; previewUrl: string };
  // Initialize 10 slots with nulls
  const [mediaSlots, setMediaSlots] = useState<(MediaItem | null)[]>(Array(10).fill(null));

  const { data: categories } = useQuery({
    queryKey: ['community', 'categories'],
    queryFn: communityService.getCategories,
    staleTime: 1000 * 60 * 60, // 1 hour - categories rarely change
  });

  // Initialize Edit Mode
  useEffect(() => {
      if (editMode && initialPost?.imageUrls) {
          const items: (MediaItem | null)[] = initialPost.imageUrls.map((url: string) => ({
              url: url,
              previewUrl: url
          }));
          // Pad to 10 slots
          const padded = items.length < 10
              ? [...items, ...Array(10 - items.length).fill(null)]
              : items.slice(0, 10);
          setTimeout(() => setMediaSlots(padded), 0);
      }
  }, [editMode, initialPost]);

  // Set default category when loaded
  useEffect(() => {
    if (categories && categories.length > 0 && !editMode) {
       const currentId = formData.categoryId;
       const exists = categories.find(c => c.id === currentId);
       if (!exists) {
           setTimeout(() => setFormData(prev => ({ ...prev, categoryId: categories[0].id })), 0);
       }
    }
  // eslint-disable-next-line react-hooks/exhaustive-deps -- only runs when available categories change; formData.categoryId is read to check validity, not as a trigger
  }, [categories, editMode]);

  const createMutation = useMutation({
    mutationFn: communityService.createPost,
    onSuccess: (data) => {
      // 목록/마이포스트/totalCount 등 community 캐시 전체 무효화 (생성 직후 stale 데이터 노출 방지).
      queryClient.invalidateQueries({ queryKey: ['community'] });
      showAlert('게시글이 등록되었습니다.', () => {
          if (data.postId) {
              navigate(`/community/${data.postId}`, { replace: true });
          } else {
              navigate('/community', { replace: true });
          }
      });
    },
    onError: () => {
       showAlert('게시글 등록에 실패했습니다.');
    },
  });

  const updateMutation = useMutation({
    mutationFn: async (data: CreatePostRequest) => {
        // Filter out null slots
        const validItems = mediaSlots.filter((item): item is MediaItem => !!item);

        // Upload new files
        const uploadPromises = validItems.map(async (item) => {
            if (item.file) {
                return await communityService.uploadFile(item.file);
            }
            return item.url!; // Existing URL
        });

        const finalUrls = await Promise.all(uploadPromises);

        return communityService.updatePost(initialPost.id, {
            title: data.title,
            content: data.content,
            categoryId: data.categoryId,
            imageUrls: finalUrls,
        });
    },
    onSuccess: () => {
        // 상세/목록/마이포스트 등 community 캐시 전체 무효화. 누락 시 수정 직후 옛 캐시가 노출돼
        // "수정이 안 된 것처럼" 보이는 사용자 신고 발생.
        queryClient.invalidateQueries({ queryKey: ['community'] });
        showAlert('게시글이 수정되었습니다.', () => {
             navigate(`/community/${initialPost.id}`, { replace: true });
        });
    },
     onError: () => showAlert('수정에 실패했습니다.')
  });

  /* Image Handling */
  const handleImageUpload = (files: File[], targetIndex: number) => {
      const maxCount = 10;
      const currentCount = mediaSlots.filter(Boolean).length;

      if (currentCount >= maxCount) {
          showAlert(`최대 ${maxCount}장을 초과할 수 없습니다.`);
          return;
      }

      const availableSlotsCount = maxCount - currentCount;
      const filesToUpload = files.slice(0, availableSlotsCount);
      
      if (filesToUpload.length < files.length) {
           showAlert(`최대 ${maxCount}장까지 등록 가능합니다. ${filesToUpload.length}장만 업로드됩니다.`);
      }

      setMediaSlots(prev => {
          const newSlots = [...prev];
          let fileIndex = 0;

          // First fill targetIndex if empty
          if (!newSlots[targetIndex] && fileIndex < filesToUpload.length) {
              const file = filesToUpload[fileIndex++];
              newSlots[targetIndex] = {
                  file,
                  previewUrl: URL.createObjectURL(file)
              };
          }

          // Then fill subsequent empty slots
          for (let i = 0; i < maxCount; i++) {
              if (fileIndex >= filesToUpload.length) break;
              if (!newSlots[i]) {
                  const file = filesToUpload[fileIndex++];
                  newSlots[i] = {
                      file,
                      previewUrl: URL.createObjectURL(file)
                  };
              }
          }
          return newSlots;
      });
  };

  const handleImageDelete = (index: number) => {
      setMediaSlots(prev => {
          const newSlots = [...prev];
          newSlots[index] = null;
          return newSlots;
      });
  };

  const handleSubmit = async (e?: React.FormEvent) => {
    e?.preventDefault();
    if (!formData.title.trim() || !formData.content.trim()) {
      showAlert('제목과 내용을 입력해주세요.');
      return;
    }

    if (editMode) {
        updateMutation.mutate(formData);
    } else {
        // Prepare files for create
        const validItems = mediaSlots.filter((item): item is MediaItem => !!item);
        const filesToUpload = validItems.map(item => item.file).filter((f): f is File => !!f);
        createMutation.mutate({ ...formData, images: filesToUpload });
    }
  };

  const keyboardDismiss = useKeyboardDismiss();

  return (
    <>
    <div id="writeModal" style={{ display: 'block' }} {...keyboardDismiss}>
      <div className="modal_wrap">
        {/* Title */}
        <div className="input_wrap">
            <label htmlFor="write_title">제목</label>
            <input
              type="text"
              id="write_title"
              placeholder="제목을 입력해주세요."
              value={formData.title}
              onChange={(e) => setFormData({ ...formData, title: e.target.value })}
              maxLength={100}
            />
        </div>

        {/* Content */}
        <div className="input_wrap">
            <label htmlFor="write_txt">내용</label>
            <textarea
              id="write_txt"
              placeholder="게시글 내용을 입력해주세요."
              value={formData.content}
              onChange={(e) => setFormData({ ...formData, content: e.target.value })}
              onFocus={(e) => {
                setTimeout(() => {
                  e.target.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
                }, 300);
              }}
            ></textarea>
        </div>

        {/* Category Selection */}
         <div className="input_wrap">
             <label>카테고리</label>
             <CustomSelect
                 value={String(formData.categoryId)}
                 options={(categories || []).map(cat => ({ label: cat.name, value: String(cat.id) }))}
                 onChange={(value) => setFormData({ ...formData, categoryId: Number(value) })}
             />
         </div>

        {/* Replaced Custom UI with ImageUploadSlider */}
        <div style={{ marginTop: '20px' }}> {/* Add some spacing wrapper if needed */}
            <ImageUploadSlider 
                images={mediaSlots.map(s => s?.previewUrl || null)}
                onUpload={handleImageUpload}
                onDelete={handleImageDelete}
                maxCount={10}
            />
        </div>
        
        {/* ... Submit Button & Close ... */}
        {/* Submit Button */}
        <button 
          type="button" 
          className={`submit_btn ${!formData.title || !formData.content ? 'disabled' : ''}`}
          onClick={() => handleSubmit()}
          disabled={!formData.title || !formData.content || createMutation.isPending || updateMutation.isPending}
        >
          {createMutation.isPending || updateMutation.isPending ? '처리 중...' : (editMode ? '수정하기' : '등록하기')}
        </button>

        <button type="button" className="modal_close_btn" onClick={() => navigate(-1)}>
            <img src="/assets/images/common/close_icon.svg" alt="" />
        </button>
      </div>
    </div>
    </>
  );
}
