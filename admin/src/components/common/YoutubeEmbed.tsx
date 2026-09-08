// Parity with frontend/src/components/common/YoutubeEmbed.tsx — keep in sync
interface YoutubeEmbedProps { url: string; title?: string; }
export function YoutubeEmbed({ url, title }: YoutubeEmbedProps) {
  return (
    <div className="aspect-video rounded-lg overflow-hidden">
      <iframe
        src={url}
        title={title ?? 'YouTube video'}
        loading="lazy"
        allow="accelerometer; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share"
        allowFullScreen
        referrerPolicy="strict-origin-when-cross-origin"
        sandbox="allow-scripts allow-same-origin allow-presentation allow-popups"
        className="w-full h-full border-0"
      />
    </div>
  );
}
export default YoutubeEmbed;
