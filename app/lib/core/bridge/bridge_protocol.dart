/// JavaScript Bridge 메시지 프로토콜 정의
///
/// ARCH-001: 이 상수 목록은 브리지 계약의 단일 진실 소스(`contracts/bridge-methods.json`)를
/// 그대로 반영한다. 예전에는 여기(상수)·native_bridge.dart(리터럴 case)·bridgeTypes.ts(유니온)가
/// 서로 어긋나 있었고, 실제로 쓰이는 login/syncAuthToken/startWalk/clearAuthToken 등이 이 파일에
/// 아예 없었다. bridge_contract_test.dart 가 JSON 과의 일치를 강제한다.
class BridgeProtocol {
  // 푸시 알림
  static const String requestPushPermission = 'requestPushPermission';
  static const String getPushToken = 'getPushToken';
  static const String subscribeToTopic = 'subscribeToTopic';
  static const String registerFcmToken = 'registerFcmToken';

  // 위치
  static const String requestLocationPermission = 'requestLocationPermission';
  static const String getCurrentLocation = 'getCurrentLocation';
  static const String startLocationTracking = 'startLocationTracking';
  static const String stopLocationTracking = 'stopLocationTracking';

  // 카메라/사진
  static const String requestCameraPermission = 'requestCameraPermission';
  static const String requestPhotoPermission = 'requestPhotoPermission';
  static const String openCamera = 'openCamera';
  static const String openPhotoLibrary = 'openPhotoLibrary';
  static const String pickImage = 'pickImage';

  // 앱 정보/권한
  static const String getAppVersion = 'getAppVersion';
  static const String getDeviceInfo = 'getDeviceInfo';
  static const String requestAllPermissions = 'requestAllPermissions';

  // 오버레이 색상 제어
  static const String setOverlayColor = 'setOverlayColor';
  static const String clearOverlayColor = 'clearOverlayColor';

  // 인증
  static const String login = 'login';
  static const String syncAuthToken = 'syncAuthToken';
  static const String clearAuthToken = 'clearAuthToken';

  // 산책
  static const String startWalk = 'startWalk';
  static const String startCourseWalk = 'startCourseWalk';

  // 기타
  static const String setCurrentChatRoom = 'setCurrentChatRoom';
  static const String shareContent = 'shareContent';
  static const String downloadFile = 'downloadFile';
  static const String openExternalUrl = 'openExternalUrl';

  /// 브리지가 지원하는 전체 메서드. 계약 테스트가 SSoT(JSON)와 대조한다.
  static const List<String> allMethods = [
    requestPushPermission,
    getPushToken,
    subscribeToTopic,
    registerFcmToken,
    requestLocationPermission,
    getCurrentLocation,
    startLocationTracking,
    stopLocationTracking,
    requestCameraPermission,
    requestPhotoPermission,
    openCamera,
    openPhotoLibrary,
    pickImage,
    getAppVersion,
    getDeviceInfo,
    requestAllPermissions,
    setOverlayColor,
    clearOverlayColor,
    login,
    syncAuthToken,
    clearAuthToken,
    startWalk,
    startCourseWalk,
    setCurrentChatRoom,
    shareContent,
    downloadFile,
    openExternalUrl,
  ];

  // 이벤트 타입 (Flutter → React)
  static const String pushNotificationReceived = 'pushNotificationReceived';
  static const String pushNotificationClicked = 'pushNotificationClicked';
  static const String locationUpdated = 'locationUpdated';
  static const String permissionStatusChanged = 'permissionStatusChanged';
  static const String appStateChanged = 'appStateChanged';
  static const String authExpired = 'authExpired';
  static const String tokenSync = 'tokenSync';
  static const String walkCompleted = 'walkCompleted';
  static const String deviceRegistered = 'deviceRegistered';

  /// 브리지가 발행하는 전체 이벤트. 계약 테스트가 SSoT(JSON)와 대조한다.
  static const List<String> allEvents = [
    pushNotificationReceived,
    pushNotificationClicked,
    locationUpdated,
    permissionStatusChanged,
    appStateChanged,
    authExpired,
    tokenSync,
    walkCompleted,
    deviceRegistered,
  ];
}
