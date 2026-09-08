/**
 * JavaScript Bridge 타입 정의
 * Flutter와 React 간 통신을 위한 메시지 프로토콜 타입
 */

export interface BridgeMessage {
  id: string;
  method: string;
  params?: Record<string, unknown>;
  timestamp: number;
}

export interface BridgeResponse {
  id: string;
  success: boolean;
  data?: unknown;
  error?: {
    code: string;
    message: string;
  };
  timestamp: number;
}

export type NativeEventType =
  | 'pushNotificationReceived'
  | 'pushNotificationClicked'
  | 'locationUpdated'
  | 'permissionStatusChanged'
  | 'appStateChanged'
  | 'authExpired'
  | 'tokenSync'
  | 'walkCompleted'
  | 'deviceRegistered';

/**
 * ARCH-001: 브리지 계약의 단일 진실 소스는 `contracts/bridge-methods.json` 이다.
 * 이 유니온과 아래 NATIVE_METHODS 가 그 JSON 과 일치하는지 bridgeContract 테스트가 강제한다.
 * 예전에는 이 유니온이 Flutter 스위치와 어긋나 있었고(실제로 쓰는 syncAuthToken/startWalk/
 * clearAuthToken 등이 누락), callMethod(method: string) 이라 아무도 잡지 못했다.
 */
export type NativeMethod =
  | 'requestPushPermission'
  | 'getPushToken'
  | 'subscribeToTopic'
  | 'registerFcmToken'
  | 'requestLocationPermission'
  | 'getCurrentLocation'
  | 'startLocationTracking'
  | 'stopLocationTracking'
  | 'requestCameraPermission'
  | 'requestPhotoPermission'
  | 'openCamera'
  | 'openPhotoLibrary'
  | 'pickImage'
  | 'getAppVersion'
  | 'getDeviceInfo'
  | 'requestAllPermissions'
  | 'setOverlayColor'
  | 'clearOverlayColor'
  | 'login'
  | 'syncAuthToken'
  | 'clearAuthToken'
  | 'startWalk'
  | 'startCourseWalk'
  | 'setCurrentChatRoom'
  | 'shareContent'
  | 'downloadFile'
  | 'openExternalUrl';

/** 런타임에서도 계약을 대조할 수 있게 같은 목록을 값으로 노출한다. */
export const NATIVE_METHODS = [
  'requestPushPermission',
  'getPushToken',
  'subscribeToTopic',
  'registerFcmToken',
  'requestLocationPermission',
  'getCurrentLocation',
  'startLocationTracking',
  'stopLocationTracking',
  'requestCameraPermission',
  'requestPhotoPermission',
  'openCamera',
  'openPhotoLibrary',
  'pickImage',
  'getAppVersion',
  'getDeviceInfo',
  'requestAllPermissions',
  'setOverlayColor',
  'clearOverlayColor',
  'login',
  'syncAuthToken',
  'clearAuthToken',
  'startWalk',
  'startCourseWalk',
  'setCurrentChatRoom',
  'shareContent',
  'downloadFile',
  'openExternalUrl',
] as const satisfies readonly NativeMethod[];

export interface Location {
  latitude: number;
  longitude: number;
  accuracy?: number;
  altitude?: number;
  speed?: number;
  timestamp: number;
}

export interface ImageResult {
  path: string;
  name: string;
  size: number;
  mimeType: string;
  base64?: string;
}

