// 개발 빌드에서만 출력되는 디버그 로거
/**
 * STYLE-004: console.log 가 DEV 가드 없이 8개 파일 17곳에 흩어져 프로덕션 번들에 그대로 실렸다.
 * 하이브리드 앱에서는 WebView 콘솔이 Flutter 로그로 포워딩되므로, 무심코 찍은 값이 앱 로그에
 * 그대로 남는다.
 *
 * 에러/경고(console.error/warn)는 프로덕션에서도 필요하므로 건드리지 않는다.
 * 디버그 출력만 이 로거로 모아 DEV 에서만 나가게 한다.
 */
export const logger = {
  debug: (...args: unknown[]): void => {
    if (import.meta.env.DEV) {
      console.log(...args);
    }
  },
};
