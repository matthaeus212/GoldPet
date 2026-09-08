interface PhotoUploaderProps {
  onPhotoSelected: (url: string) => void;
  currentPhoto?: string;
}

export default function PhotoUploader({ onPhotoSelected, currentPhoto }: PhotoUploaderProps) {
  return (
    <div style={{ textAlign: 'center', margin: '6.67vw 0' }}>
      <div
        style={{
          width: '44.44vw',
          height: '44.44vw',
          margin: '0 auto',
          borderRadius: '50%',
          overflow: 'hidden',
          backgroundColor: '#F9ECD2',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
        }}
      >
        <img
          src={currentPhoto || '/assets/images/common/pet_none_img.svg'}
          alt="Pet"
          style={{ width: '100%', height: '100%', objectFit: 'cover' }}
        />
      </div>
      <button
        type="button"
        onClick={() => onPhotoSelected('/assets/images/common/pet_none_img.svg')}
        style={{
          marginTop: '4.44vw',
          padding: '2.22vw 6.67vw',
          borderRadius: '6.67vw',
          backgroundColor: 'var(--color-primary)',
          color: '#fff',
          fontSize: '3.89vw',
          fontWeight: 500,
          border: 'none',
          cursor: 'pointer',
        }}
      >
        사진 업로드
      </button>
    </div>
  );
}
