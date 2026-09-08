// Parity with frontend/src/utils/contentRenderer.tsx — keep in sync
import type { ReactElement } from 'react';
import { YoutubeEmbed } from '../components/common/YoutubeEmbed';

const URL_REGEX = /(https?:\/\/[^\s<>"'{}|^\\[\]]+?)(?=[.,;:!?)\]]*(?:\s|$))/g;
const YOUTUBE_HOSTS = new Set([
  'youtube.com', 'www.youtube.com', 'm.youtube.com',
  'youtu.be', 'www.youtube-nocookie.com',
]);
const VIDEO_ID_RE = /^[A-Za-z0-9_-]{11}$/;

function extractVideoId(rawUrl: string): string | null {
  try {
    const url = new URL(rawUrl);
    const host = url.host.toLowerCase();
    if (!YOUTUBE_HOSTS.has(host)) return null;
    let id: string | null = null;
    if (host === 'youtu.be') id = url.pathname.slice(1).split('/')[0] || null;
    else if (url.pathname === '/watch') id = url.searchParams.get('v');
    else if (url.pathname.startsWith('/shorts/')) id = url.pathname.split('/')[2] || null;
    else if (url.pathname.startsWith('/embed/')) id = url.pathname.split('/')[2] || null;
    return id && VIDEO_ID_RE.test(id) ? id : null;
  } catch { return null; }
}

type Part = { type: 'text'; value: string } | { type: 'embed'; videoId: string };

export function ContentRenderer({ content }: { content: string }): ReactElement {
  const parts: Part[] = [];
  let lastIndex = 0;
  for (const match of content.matchAll(URL_REGEX)) {
    const videoId = extractVideoId(match[1]);
    if (!videoId) continue;
    if (match.index! > lastIndex) parts.push({ type: 'text', value: content.slice(lastIndex, match.index) });
    parts.push({ type: 'embed', videoId });
    lastIndex = match.index! + match[1].length;
  }
  if (lastIndex < content.length) parts.push({ type: 'text', value: content.slice(lastIndex) });
  return (
    <>
      {parts.map((p, i) =>
        p.type === 'text'
          ? <span key={i} style={{ whiteSpace: 'pre-line' }}>{p.value}</span>
          : <YoutubeEmbed key={i} url={`https://www.youtube-nocookie.com/embed/${p.videoId}`} />
      )}
    </>
  );
}
