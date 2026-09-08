import { describe, it, expect } from 'vitest';
import { render } from '@testing-library/react';
import { ContentRenderer } from '../contentRenderer';

// YoutubeEmbed renders an iframe — jsdom supports it
describe('ContentRenderer', () => {
  it('plain text renders a single span', () => {
    const { container } = render(<ContentRenderer content="Hello world" />);
    const spans = container.querySelectorAll('span');
    expect(spans).toHaveLength(1);
    expect(spans[0].textContent).toBe('Hello world');
    expect(container.querySelectorAll('iframe')).toHaveLength(0);
  });

  it('youtu.be URL renders a single YoutubeEmbed', () => {
    const { container } = render(
      <ContentRenderer content="https://youtu.be/dQw4w9WgXcQ" />
    );
    expect(container.querySelectorAll('iframe')).toHaveLength(1);
    const iframe = container.querySelector('iframe')!;
    expect(iframe.src).toContain('dQw4w9WgXcQ');
  });

  it('text + URL + text renders span + embed + span', () => {
    const { container } = render(
      <ContentRenderer content="before https://youtu.be/dQw4w9WgXcQ after" />
    );
    const spans = container.querySelectorAll('span');
    expect(spans).toHaveLength(2);
    expect(spans[0].textContent).toBe('before ');
    expect(spans[1].textContent).toBe(' after');
    expect(container.querySelectorAll('iframe')).toHaveLength(1);
  });

  it('trailing period is not consumed by URL regex', () => {
    const { container } = render(
      <ContentRenderer content="봐봐 https://youtu.be/dQw4w9WgXcQ." />
    );
    expect(container.querySelectorAll('iframe')).toHaveLength(1);
    const spans = container.querySelectorAll('span');
    const allText = Array.from(spans).map(s => s.textContent).join('');
    expect(allText).toContain('.');
  });

  it('URL inside parentheses: closing paren excluded from URL', () => {
    const { container } = render(
      <ContentRenderer content="(see https://youtu.be/dQw4w9WgXcQ)" />
    );
    expect(container.querySelectorAll('iframe')).toHaveLength(1);
    const spans = container.querySelectorAll('span');
    const allText = Array.from(spans).map(s => s.textContent).join('');
    expect(allText).toContain('(');
    expect(allText).toContain(')');
  });

  it('subdomain spoofing youtube.com.evil.com is not embedded', () => {
    const { container } = render(
      <ContentRenderer content="https://www.youtube.com.evil.com/watch?v=dQw4w9WgXcQ" />
    );
    expect(container.querySelectorAll('iframe')).toHaveLength(0);
    expect(container.querySelectorAll('span')).toHaveLength(1);
  });

  it('non-YouTube domain with YouTube query param is not embedded', () => {
    const { container } = render(
      <ContentRenderer content="https://evil.com/watch?v=dQw4w9WgXcQ" />
    );
    expect(container.querySelectorAll('iframe')).toHaveLength(0);
  });

  it('multiple YouTube URLs each render a YoutubeEmbed', () => {
    const { container } = render(
      <ContentRenderer content="https://youtu.be/dQw4w9WgXcQ and https://youtu.be/abcdefghijk" />
    );
    expect(container.querySelectorAll('iframe')).toHaveLength(2);
  });

  it('video ID shorter than 11 chars is not embedded', () => {
    const { container } = render(
      <ContentRenderer content="https://youtu.be/short" />
    );
    expect(container.querySelectorAll('iframe')).toHaveLength(0);
  });

  it('javascript: scheme is not matched by https regex', () => {
    const { container } = render(
      <ContentRenderer content="javascript:alert(1)" />
    );
    expect(container.querySelectorAll('iframe')).toHaveLength(0);
    expect(container.querySelectorAll('span')).toHaveLength(1);
  });

  it('watch?v= form is embedded', () => {
    const { container } = render(
      <ContentRenderer content="https://www.youtube.com/watch?v=dQw4w9WgXcQ" />
    );
    expect(container.querySelectorAll('iframe')).toHaveLength(1);
    expect(container.querySelector('iframe')!.src).toContain('dQw4w9WgXcQ');
  });

  it('shorts URL is embedded', () => {
    const { container } = render(
      <ContentRenderer content="https://www.youtube.com/shorts/dQw4w9WgXcQ" />
    );
    expect(container.querySelectorAll('iframe')).toHaveLength(1);
    expect(container.querySelector('iframe')!.src).toContain('dQw4w9WgXcQ');
  });
});
