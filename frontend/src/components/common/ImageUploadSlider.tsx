
import React, { useRef } from 'react';
// import { Swiper, SwiperSlide } from 'swiper/react'; // Ensure swiper is installed
// import 'swiper/css';



interface ImageUploadSliderProps {
    images: (string | null)[];
    maxCount?: number;
    onUpload: (files: File[], targetIndex: number) => void;
    onDelete: (index: number) => void;
}

const ImageUploadSlider: React.FC<ImageUploadSliderProps> = ({ 
    images, 
    maxCount = 5, 
    onUpload, 
    onDelete 
}) => {
    const fileInputRef = useRef<HTMLInputElement>(null);
    const activeSlotRef = useRef<number | null>(null);

    const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
        if (e.target.files && e.target.files.length > 0) {
            if (activeSlotRef.current !== null) {
                onUpload(Array.from(e.target.files), activeSlotRef.current);
            }
        }
        if (fileInputRef.current) {
            fileInputRef.current.value = '';
        }
        activeSlotRef.current = null;
    };

    const handleTriggerUpload = (index: number) => {
        activeSlotRef.current = index;
        fileInputRef.current?.click();
    };

    // Determine the last occupied index to know where to stop rendering "ghost" (deleted) slots
    // We render up to the last non-null index.
    // e.g. [A, null, B, null, null] -> max index is 2. We render 0, 1, 2.
    // 0: A
    // 1: null (Camera)
    // 2: B
    // Then we append the (+) button at the end.
    
    let lastOccupiedIndex = -1;
    images.forEach((img, index) => {
        if (img !== null) lastOccupiedIndex = index;
    });

    // Valid slots to render: 0 to lastOccupiedIndex
    const visibleSlots = [];
    for (let i = 0; i <= lastOccupiedIndex; i++) {
        visibleSlots.push({ index: i, url: images[i] });
    }
    
    // Check if we can add more (total used slots < maxCount? OR total logical slots < maxCount?)
    // Usually maxCount limits the number of actual images.
    // If we have 5 images, we shouldn't show (+).
    // If we have [A, B, C, D, E], lastOccupied=4. count=5. Max=5. No (+).
    // If we have [A, null, C] (length 3, count 2). Users might want to fill gap or add new.
    // But our logic is: (+) adds to *next available*.
    // If I have [A, null, C], next available logic:
    //   Option 1: Append to end (index 3).
    //   Option 2: Fill gap (index 1).
    // The previous (+) behavior is usually Append.
    // So we show (+) if lastOccupiedIndex < maxCount - 1.
    const showMoreBtn = lastOccupiedIndex < maxCount - 1;

    return (
        <div className="img_upload_wrap">
             <p>이미지 <span>({images.filter(Boolean).length}/{maxCount})</span></p>
             <input 
                 type="file" 
                 ref={fileInputRef} 
                 style={{ display: 'none' }} 
                 accept="image/jpeg,image/png,image/gif,image/webp,image/heic"
                 multiple 
                 onChange={handleFileChange}
             />
             <ul>
                 {visibleSlots.map((slot) => (
                     <li key={slot.index}>
                         {slot.url ? (
                            <button type="button" className="img_upload_btn">
                                <span className="upload_img">
                                    <img src={slot.url} alt={`Uploaded ${slot.index}`} />
                                </span>
                                <span 
                                    className="delete_btn" 
                                    onClick={(e) => {
                                        e.stopPropagation(); // Prevent button click
                                        onDelete(slot.index);
                                    }}
                                ></span>
                            </button>
                         ) : (
                             <button 
                                type="button" 
                                className="img_upload_btn"
                                onClick={() => handleTriggerUpload(slot.index)}
                             >
                                {/* Empty button styles as camera icon via CSS */}
                             </button>
                         )}
                     </li>
                 ))}

                 {showMoreBtn && (
                     <li>
                        <button 
                            type="button" 
                            className="more_btn" 
                            onClick={() => {
                                // Target the next index after the last one rendered
                                // If lastOccupiedIndex is 2 ([A, null, B]), target is 3.
                                // If lastOccupiedIndex is -1 (empty), target is 0.
                                handleTriggerUpload(lastOccupiedIndex + 1);
                            }}
                        ></button>
                    </li>
                 )}
             </ul>
        </div>
    );
};

export default ImageUploadSlider;
