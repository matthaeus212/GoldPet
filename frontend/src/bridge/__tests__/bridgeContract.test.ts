// 브리지 계약(SSoT JSON)과 React 쪽 타입/상수가 일치하는지 강제하는 계약 테스트 (ARCH-001)
import { describe, it, expect } from 'vitest';
import { NATIVE_METHODS } from '../bridgeTypes';
import type { NativeEventType } from '../bridgeTypes';

/**
 * ARCH-001: 브리지 계약이 Flutter 상수·Flutter 스위치·React 유니온 3곳에 흩어져 서로 어긋나
 * 있었다(실제로 쓰는 syncAuthToken/startWalk/clearAuthToken 등이 React 유니온에 없었고,
 * callMethod(method: string) 이라 컴파일타임 강제도 0이었다).
 *
 * 이제 `contracts/bridge-methods.json` 이 단일 진실 소스이고, 이 테스트가 React 쪽을,
 * app/test/core/bridge/bridge_contract_test.dart 가 Flutter 쪽을 각각 그 JSON 에 묶는다.
 * 한쪽만 고치면 반드시 어느 한 테스트가 깨진다.
 */
import contract from '../../../../contracts/bridge-methods.json';

describe('브리지 계약 (SSoT: contracts/bridge-methods.json)', () => {
  it('NATIVE_METHODS 가 계약의 메서드 목록과 정확히 일치한다', () => {
    expect([...NATIVE_METHODS].sort()).toEqual([...contract.methods].sort());
  });

  it('계약에 중복 메서드가 없다', () => {
    expect(new Set(contract.methods).size).toBe(contract.methods.length);
  });

  // NativeEventType 은 타입이라 런타임에 열거할 수 없으므로, 계약의 각 이벤트가 이 유니온에
  // 대입 가능한지로 검증한다. 유니온에서 이벤트가 빠지면 여기서 타입 에러가 난다.
  it('계약의 이벤트가 모두 NativeEventType 에 존재한다', () => {
    const events: NativeEventType[] = [
      'pushNotificationReceived',
      'pushNotificationClicked',
      'locationUpdated',
      'permissionStatusChanged',
      'appStateChanged',
      'authExpired',
      'tokenSync',
      'walkCompleted',
      'deviceRegistered',
    ];
    expect([...events].sort()).toEqual([...contract.events].sort());
  });
});
