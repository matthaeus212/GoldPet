// 브리지 계약(SSoT JSON)과 Flutter 쪽 상수/스위치가 일치하는지 강제하는 계약 테스트 (ARCH-001)
import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:goldpet_app/core/bridge/bridge_protocol.dart';

/// ARCH-001: 브리지 계약이 세 곳(Flutter 상수·Flutter 스위치·React 유니온)에 흩어져 서로
/// 어긋나 있었다. `contracts/bridge-methods.json` 을 단일 진실 소스로 두고, 이 테스트가
/// Flutter 쪽을 거기에 묶는다(React 쪽은 frontend/src/bridge/__tests__/bridgeContract.test.ts).
void main() {
  final contract = jsonDecode(
    File('../contracts/bridge-methods.json').readAsStringSync(),
  ) as Map<String, dynamic>;
  final methods = (contract['methods'] as List).cast<String>();
  final events = (contract['events'] as List).cast<String>();

  group('브리지 계약 (SSoT: contracts/bridge-methods.json)', () {
    test('BridgeProtocol.allMethods 가 계약과 정확히 일치한다', () {
      expect(BridgeProtocol.allMethods.toSet(), equals(methods.toSet()));
      expect(BridgeProtocol.allMethods.length, methods.length);
    });

    test('BridgeProtocol.allEvents 가 계약과 정확히 일치한다', () {
      expect(BridgeProtocol.allEvents.toSet(), equals(events.toSet()));
      expect(BridgeProtocol.allEvents.length, events.length);
    });

    // 상수만 맞고 실제 라우팅(switch)이 빠지면 런타임에 조용히 UNKNOWN_METHOD 가 된다.
    // native_bridge.dart 의 case 라벨을 소스에서 추출해 계약 전건이 라우팅되는지 확인한다.
    // (리터럴 case 는 금지 — 반드시 BridgeProtocol 상수를 쓰게 강제한다.)
    test('native_bridge 의 switch 가 계약의 모든 메서드를 라우팅한다', () {
      final source = File('lib/core/bridge/native_bridge.dart').readAsStringSync();

      final literalCases = RegExp(r"case\s+'[^']+'\s*:").allMatches(source);
      expect(
        literalCases,
        isEmpty,
        reason: '리터럴 case 는 계약 drift 의 원인이다. BridgeProtocol 상수를 사용하라.',
      );

      final routed = RegExp(r'case\s+BridgeProtocol\.(\w+)\s*:')
          .allMatches(source)
          .map((m) => m.group(1)!)
          .toSet();

      final missing = methods
          .where((m) => !routed.contains(m))
          .toList();
      expect(
        missing,
        isEmpty,
        reason: '계약에 있으나 native_bridge switch 가 처리하지 않는 메서드: $missing',
      );
    });
  });
}
