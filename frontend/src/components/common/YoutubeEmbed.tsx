interface YoutubeEmbedProps { url: string; title?: string; }
export function YoutubeEmbed({ url, title }: YoutubeEmbedProps) {
  return (
    <div className="youtube-embed" style={{ aspectRatio: '16/9', width: '100%' }}>
      <iframe
        src={url}
        title={title ?? 'YouTube video'}
        loading="lazy"
        allow="accelerometer; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share"
        allowFullScreen
        referrerPolicy="strict-origin-when-cross-origin"
        sandbox="allow-scripts allow-same-origin allow-presentation allow-popups"
        style={{ width: '100%', height: '100%', border: 0 }}
      />
    </div>
  );
}
export default YoutubeEmbed;
