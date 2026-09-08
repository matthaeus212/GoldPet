import Flutter
import UIKit
import UserNotifications
import ActivityKit
import GoogleMaps

@main
@objc class AppDelegate: FlutterAppDelegate {
  private var walkActivity: Any? = nil
  private var deepLinkChannel: FlutterMethodChannel? = nil
  private var pendingDeepLink: String? = nil

  override func application(
    _ application: UIApplication,
    didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]?
  ) -> Bool {
    GMSServices.provideAPIKey("AIzaSyBCsxN4ucISDFjks0gnrqRPbl6fTgQ85a0")
    GeneratedPluginRegistrant.register(with: self)

    // WKWebView 키보드 위 네비게이션 바(↑↓✓) 제거 (카카오톡 스타일)
    Self.removeWKWebViewInputAccessoryView()

    // 알림 권한은 Flutter PermissionService.requestAllPermissions()에서 일괄 요청
    // 여기서는 delegate만 설정 (권한 요청 제거)
    UNUserNotificationCenter.current().delegate = self

    let controller = window?.rootViewController as! FlutterViewController

    // Deep link MethodChannel
    deepLinkChannel = FlutterMethodChannel(
      name: "com.mannam.goldpet/deep_link",
      binaryMessenger: controller.binaryMessenger
    )
    deepLinkChannel?.setMethodCallHandler { [weak self] call, result in
      switch call.method {
      case "getInitialLink":
        let link = self?.pendingDeepLink
        self?.pendingDeepLink = nil
        result(link)
      default:
        result(FlutterMethodNotImplemented)
      }
    }

    // Cold start: check if launched via custom-scheme deep link URL
    if let url = launchOptions?[.url] as? URL {
      handleDeepLink(url, isColdStart: true)
    }

    // Cold start: check if launched via Universal Link (NSUserActivity)
    if let activityDict = launchOptions?[.userActivityDictionary] as? [UIApplication.LaunchOptionsKey: Any],
       let activity = activityDict[.userActivityType] as? NSUserActivity,
       activity.activityType == NSUserActivityTypeBrowsingWeb,
       let url = activity.webpageURL {
      handleDeepLink(url, isColdStart: true)
    }

    // Walk Live Activity MethodChannel
    let walkChannel = FlutterMethodChannel(
      name: "com.mannam.goldpet/walk_live_activity",
      binaryMessenger: controller.binaryMessenger
    )
    walkChannel.setMethodCallHandler { [weak self] call, result in
      switch call.method {
      case "start":
        self?.startWalkActivity()
        result(nil)
      case "update":
        guard let args = call.arguments as? [String: Any] else { result(nil); return }
        self?.updateWalkActivity(args)
        result(nil)
      case "end":
        self?.endWalkActivity()
        result(nil)
      default:
        result(FlutterMethodNotImplemented)
      }
    }

    return super.application(application, didFinishLaunchingWithOptions: launchOptions)
  }

  // MARK: - Deep Link Handling

  override func application(_ app: UIApplication, open url: URL, options: [UIApplication.OpenURLOptionsKey: Any] = [:]) -> Bool {
    if handleDeepLink(url, isColdStart: false) {
      return true
    }
    // Let Naver SDK or other plugins handle their callbacks
    return super.application(app, open: url, options: options)
  }

  // Universal Link warm/hot start entry point
  override func application(_ application: UIApplication,
                             continue userActivity: NSUserActivity,
                             restorationHandler: @escaping ([UIUserActivityRestoring]?) -> Void) -> Bool {
    if userActivity.activityType == NSUserActivityTypeBrowsingWeb,
       let url = userActivity.webpageURL {
      return handleDeepLink(url, isColdStart: false)
    }
    return false
  }

  @discardableResult
  private func handleDeepLink(_ url: URL, isColdStart: Bool) -> Bool {
    let isCustomScheme = url.scheme == "goldpet"
    let isUniversalLink = url.scheme == "https" &&
      (url.host == "app.mannamsquare.com" || url.host == "app.goldpet.com")
    guard isCustomScheme || isUniversalLink else { return false }

    // For goldpet:// scheme, only handle walk/spot hosts (not Naver OAuth callbacks)
    if isCustomScheme {
      guard let host = url.host, host == "walk" || host == "spot" else { return false }
    }

    let urlString = url.absoluteString
    if isColdStart {
      // Flutter not ready yet — buffer for getInitialLink pull
      pendingDeepLink = urlString
    } else {
      // Warm start — push directly
      deepLinkChannel?.invokeMethod("onDeepLink", arguments: urlString)
    }
    return true
  }

  // MARK: - Live Activity Helpers

  @available(iOS 16.2, *)
  private func typedWalkActivity() -> Activity<WalkActivityAttributes>? {
    return walkActivity as? Activity<WalkActivityAttributes>
  }

  private func startWalkActivity() {
    if #available(iOS 16.2, *) {
      guard walkActivity == nil else { return } // Prevent duplicate Live Activity
      guard ActivityAuthorizationInfo().areActivitiesEnabled else { return }
      let attributes = WalkActivityAttributes(petName: "")
      let initialState = WalkActivityAttributes.ContentState(
        distanceKm: 0.0,
        durationSeconds: 0,
        caloriesBurned: 0.0
      )
      do {
        let activity = try Activity.request(
          attributes: attributes,
          content: .init(state: initialState, staleDate: nil),
          pushType: nil
        )
        walkActivity = activity
      } catch {
        print("Failed to start Live Activity: \(error)")
      }
    }
  }

  private func updateWalkActivity(_ args: [String: Any]) {
    if #available(iOS 16.2, *) {
      guard let activity = typedWalkActivity() else { return }
      let distance = args["distanceKm"] as? Double ?? 0.0
      let duration = args["durationSeconds"] as? Int ?? 0
      let calories = args["caloriesBurned"] as? Double ?? 0.0
      let updatedState = WalkActivityAttributes.ContentState(
        distanceKm: distance,
        durationSeconds: duration,
        caloriesBurned: calories
      )
      Task {
        await activity.update(.init(state: updatedState, staleDate: nil))
      }
    }
  }

  private func endWalkActivity() {
    if #available(iOS 16.2, *) {
      guard let activity = typedWalkActivity() else { return }
      let finalState = activity.content.state
      walkActivity = nil
      Task {
        await activity.end(.init(state: finalState, staleDate: nil), dismissalPolicy: .immediate)
      }
    }
  }

  // MARK: - Foreground Notification Suppression

  // Foreground 알림 처리: 원격(FCM)은 Firebase 처리 후 배너 억제, 로컬은 표시
  override func userNotificationCenter(
    _ center: UNUserNotificationCenter,
    willPresent notification: UNNotification,
    withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
  ) {
    if notification.request.trigger is UNPushNotificationTrigger {
      // 원격 FCM 푸시 → super 호출로 Firebase SDK가 onMessage 트리거하도록 처리
      // completionHandler를 래핑하여 시스템 배너는 억제 (Flutter onMessage가 로컬 알림으로 대체)
      super.userNotificationCenter(center, willPresent: notification) { _ in
        completionHandler([])
      }
    } else {
      // 로컬 알림 (flutter_local_notifications) → 배너/소리/배지 표시
      if #available(iOS 14.0, *) {
        completionHandler([.banner, .sound, .badge])
      } else {
        completionHandler([.alert, .sound, .badge])
      }
    }
  }

  // MARK: - WKWebView Keyboard Accessory Removal

  /// WKContentView의 inputAccessoryView를 nil로 swizzle하여
  /// 키보드 위 네비게이션 바(↑↓✓)를 제거합니다.
  private static func removeWKWebViewInputAccessoryView() {
    guard let wkContentView: AnyClass = NSClassFromString("WKContentView") else { return }
    let original = #selector(getter: UIResponder.inputAccessoryView)
    let swizzled = #selector(AppDelegate.nilInputAccessoryView)
    guard let swizzledMethod = class_getInstanceMethod(AppDelegate.self, swizzled) else { return }
    // WKContentView는 UIResponder에서 inputAccessoryView를 상속받으므로
    // class_addMethod로 직접 메서드를 추가한 뒤, 실패하면 교체
    if class_addMethod(wkContentView, original, method_getImplementation(swizzledMethod), method_getTypeEncoding(swizzledMethod)) {
      // 성공: WKContentView에 직접 메서드가 추가됨
    } else {
      // 이미 존재: 기존 구현을 교체
      guard let originalMethod = class_getInstanceMethod(wkContentView, original) else { return }
      method_exchangeImplementations(originalMethod, swizzledMethod)
    }
  }

  @objc dynamic func nilInputAccessoryView() -> UIView? {
    return nil
  }

  // MARK: - APNs

  override func application(_ application: UIApplication,
                            didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data) {
    super.application(application, didRegisterForRemoteNotificationsWithDeviceToken: deviceToken)
  }

  override func application(_ application: UIApplication,
                            didFailToRegisterForRemoteNotificationsWithError error: Error) {
    print("Failed to register for remote notifications: \(error.localizedDescription)")
  }
}
