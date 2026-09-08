/**
 * GpImage — WebP <picture> 패턴 단위 테스트.
 *
 * 검증 대상:
 *  1. webp props 제공 시 → <picture><source type="image/webp"> 렌더
 *  2. webp props 미제공 시 → plain <img> 렌더 (picture 없음)
 */
import React from 'react';
import { describe, it, expect } from 'vitest';
import { render } from '@testing-library/react';
import { GpImage } from '../GpImage';

describe('GpImage', () => {
  it('renders <picture><source type="image/webp"> when webpThumbnailSrc is provided', () => {
    const { container } = render(
      <GpImage
        src="image.jpg"
        thumbnailSrc="image_thumb.jpg"
        webpThumbnailSrc="image_thumb.webp"
        variant="thumbnail"
        alt="test"
      />,
    );

    const picture = container.querySelector('picture');
    expect(picture).not.toBeNull();

    const source = picture!.querySelector('source[type="image/webp"]');
    expect(source).not.toBeNull();
    expect(source!.getAttribute('srcset')).toBe('image_thumb.webp');

    const img = picture!.querySelector('img');
    expect(img).not.toBeNull();
    expect(img!.getAttribute('src')).toBe('image_thumb.jpg');
  });

  it('renders plain <img> (no <picture>) when no webp props are provided', () => {
    const { container } = render(
      <GpImage
        src="image.jpg"
        thumbnailSrc="image_thumb.jpg"
        variant="thumbnail"
        alt="test"
      />,
    );

    const picture = container.querySelector('picture');
    expect(picture).toBeNull();

    const img = container.querySelector('img');
    expect(img).not.toBeNull();
    expect(img!.getAttribute('src')).toBe('image_thumb.jpg');
  });

  it('renders <picture> for webpMediumSrc with medium variant', () => {
    const { container } = render(
      <GpImage
        src="image.jpg"
        mediumSrc="image_medium.jpg"
        webpMediumSrc="image_medium.webp"
        variant="medium"
        alt="test"
      />,
    );

    const source = container.querySelector('picture source[type="image/webp"]');
    expect(source).not.toBeNull();
    expect(source!.getAttribute('srcset')).toBe('image_medium.webp');
  });

  it('falls back to plain <img> when webp is null', () => {
    const { container } = render(
      <GpImage
        src="image.jpg"
        thumbnailSrc="image_thumb.jpg"
        webpThumbnailSrc={null}
        variant="thumbnail"
        alt="test"
      />,
    );

    expect(container.querySelector('picture')).toBeNull();
    expect(container.querySelector('img')).not.toBeNull();
  });
});
