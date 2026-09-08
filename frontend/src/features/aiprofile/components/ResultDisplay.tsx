interface ResultDisplayProps {
  imageUrl: string;
  promptText?: string;
}

export default function ResultDisplay({ imageUrl, promptText }: ResultDisplayProps) {
  return (
    <div>
      <div style={{ borderRadius: '4.44vw', overflow: 'hidden', marginTop: '11.11vw' }}>
        <img
          src={imageUrl}
          alt="Generated AI Profile"
          style={{ width: '100%', height: 'auto', display: 'block' }}
        />
      </div>
      {promptText && (
        <p
          style={{
            backgroundColor: '#fff',
            borderRadius: '4.44vw',
            padding: '3.61vw 4.44vw',
            fontSize: '3.89vw',
            fontWeight: 500,
            color: 'var(--color-primary)',
            lineHeight: '140%',
            margin: '6.67vw 0 0',
          }}
        >
          {promptText}
        </p>
      )}
    </div>
  );
}
