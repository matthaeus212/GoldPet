import { describe, it, expect } from 'vitest';
import { buildCreatePlacePayload, buildUpdatePlacePayload, PlaceFormError } from './lbsForm';

describe('buildCreatePlacePayload', () => {
  it('정상 입력 → 좌표 parseFloat 후 그대로', () => {
    const result = buildCreatePlacePayload({
      name: '한강공원',
      category: 'PARK',
      latitude: '37.5',
      longitude: '127.0',
    });
    expect(result).toEqual({
      name: '한강공원',
      category: 'PARK',
      latitude: 37.5,
      longitude: 127.0,
    });
  });

  it('빈 문자열 위도 → PlaceFormError', () => {
    expect(() =>
      buildCreatePlacePayload({ name: 'x', category: 'PARK', latitude: '', longitude: '127.0' })
    ).toThrow(PlaceFormError);
  });

  it('비숫자 경도 → PlaceFormError', () => {
    expect(() =>
      buildCreatePlacePayload({ name: 'x', category: 'PARK', latitude: '37.5', longitude: 'abc' })
    ).toThrow(PlaceFormError);
  });
});

describe('buildUpdatePlacePayload', () => {
  it('빈 위도 + 유효 경도 → 위도 undefined, 경도 parseFloat', () => {
    const result = buildUpdatePlacePayload({
      name: '',
      category: '',
      latitude: '',
      longitude: '127.0',
    });
    expect(result).toEqual({
      name: undefined,
      category: undefined,
      latitude: undefined,
      longitude: 127.0,
    });
  });

  it('비숫자 위도 → PlaceFormError', () => {
    expect(() =>
      buildUpdatePlacePayload({ name: '', category: '', latitude: 'abc', longitude: '' })
    ).toThrow(PlaceFormError);
  });

  it('전부 빈 입력 → 모든 필드 undefined (no-op partial update)', () => {
    const result = buildUpdatePlacePayload({ name: '', category: '', latitude: '', longitude: '' });
    expect(result).toEqual({
      name: undefined,
      category: undefined,
      latitude: undefined,
      longitude: undefined,
    });
  });

  it('이름만 변경 + 좌표 유지', () => {
    const result = buildUpdatePlacePayload({
      name: '한강공원 잠실지구',
      category: '',
      latitude: '',
      longitude: '',
    });
    expect(result.name).toBe('한강공원 잠실지구');
    expect(result.latitude).toBeUndefined();
    expect(result.longitude).toBeUndefined();
  });
});
