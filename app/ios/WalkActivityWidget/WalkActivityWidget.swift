import ActivityKit
import WidgetKit
import SwiftUI

// MARK: - Design Constants

private enum WalkWidgetStyle {
    static let goldColor = Color(red: 0.988, green: 0.902, blue: 0.486) // #FCE67C
    static let brownColor = Color(red: 0.38, green: 0.255, blue: 0.031) // #614108
    static let beigeColor = Color(red: 0.976, green: 0.925, blue: 0.824) // #F9ECD2
}

// MARK: - Lock Screen Spot Button (Image-based)

struct LockScreenSpotButton: View {
    let imageName: String
    let label: String
    let url: String
    let showBeigeBackground: Bool

    init(imageName: String, label: String, url: String, showBeigeBackground: Bool = false) {
        self.imageName = imageName
        self.label = label
        self.url = url
        self.showBeigeBackground = showBeigeBackground
    }

    var body: some View {
        Link(destination: URL(string: url)!) {
            VStack(spacing: 4) {
                if showBeigeBackground {
                    ZStack {
                        Circle()
                            .fill(WalkWidgetStyle.beigeColor)
                            .frame(width: 40, height: 40)
                        Image("IconCamera")
                            .resizable()
                            .aspectRatio(contentMode: .fit)
                            .frame(width: 20, height: 17)
                    }
                } else {
                    Image(imageName)
                        .resizable()
                        .aspectRatio(contentMode: .fit)
                        .frame(width: 40, height: 40)
                        .clipShape(Circle())
                }
                Text(label)
                    .font(.system(size: 10, weight: .medium))
                    .foregroundColor(.white)
            }
            .frame(maxWidth: .infinity)
        }
    }
}

// MARK: - Dynamic Island Spot Button (SF Symbol-based)

struct SpotActionButton: View {
    let icon: String
    let label: String
    let url: String
    let color: Color

    var body: some View {
        Link(destination: URL(string: url)!) {
            VStack(spacing: 2) {
                Image(systemName: icon)
                    .font(.system(size: 16))
                    .foregroundColor(color)
                Text(label)
                    .font(.system(size: 10, weight: .medium))
                    .foregroundColor(.white)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 6)
        }
    }
}

// MARK: - Lock Screen View

struct WalkLiveActivityView: View {
    let state: WalkActivityAttributes.ContentState

    private var formattedDistance: String {
        String(format: "%.2f", state.distanceKm)
    }

    private var formattedDuration: String {
        let h = state.durationSeconds / 3600
        let m = (state.durationSeconds % 3600) / 60
        let s = state.durationSeconds % 60
        if h > 0 {
            return String(format: "%d:%02d:%02d", h, m, s)
        }
        return String(format: "%02d:%02d", m, s)
    }

    private var formattedCalories: String {
        let cal = Int(state.caloriesBurned.rounded())
        return "\(cal)"
    }

    var body: some View {
        VStack(spacing: 10) {
            // GoldPet Logo
            HStack {
                Image("Logo")
                    .resizable()
                    .aspectRatio(contentMode: .fit)
                    .frame(width: 45, height: 12)
                Spacer()
            }

            // Stats row (no dividers)
            HStack(spacing: 0) {
                // Distance
                VStack(spacing: 2) {
                    Text(formattedDistance)
                        .font(.system(size: 22, weight: .bold, design: .rounded))
                        .foregroundColor(WalkWidgetStyle.goldColor)
                    Text("산책 거리 (km)")
                        .font(.system(size: 10))
                        .foregroundColor(.white)
                        .lineLimit(1)
                        .minimumScaleFactor(0.7)
                }
                .frame(maxWidth: .infinity)

                // Duration
                VStack(spacing: 2) {
                    Text(formattedDuration)
                        .font(.system(size: 22, weight: .bold, design: .rounded))
                        .foregroundColor(WalkWidgetStyle.goldColor)
                    Text("소요시간")
                        .font(.system(size: 10))
                        .foregroundColor(.white)
                        .lineLimit(1)
                        .minimumScaleFactor(0.7)
                }
                .frame(maxWidth: .infinity)

                // Calories
                VStack(spacing: 2) {
                    Text(formattedCalories)
                        .font(.system(size: 22, weight: .bold, design: .rounded))
                        .foregroundColor(WalkWidgetStyle.goldColor)
                    Text("소요 칼로리 (Kcal)")
                        .font(.system(size: 10))
                        .foregroundColor(.white)
                        .lineLimit(1)
                        .minimumScaleFactor(0.7)
                }
                .frame(maxWidth: .infinity)
            }

            Spacer().frame(height: 14) // 24px total gap (10 from VStack spacing + 14 here)

            // Spot action buttons: 응가 -> 쉬 -> 사진
            HStack(spacing: 6) {
                LockScreenSpotButton(
                    imageName: "IconPoop",
                    label: "응가",
                    url: "goldpet://spot/poop"
                )
                LockScreenSpotButton(
                    imageName: "IconPee",
                    label: "쉬",
                    url: "goldpet://spot/pee"
                )
                LockScreenSpotButton(
                    imageName: "IconCamera",
                    label: "사진",
                    url: "goldpet://spot/photo",
                    showBeigeBackground: true
                )
            }
        }
        .padding(.vertical, 12)
        .padding(.horizontal, 12)
    }
}

// MARK: - Dynamic Island Spot Buttons

struct DynamicIslandSpotButtons: View {
    var body: some View {
        HStack(spacing: 8) {
            Link(destination: URL(string: "goldpet://spot/poop")!) {
                Image(systemName: "circle.fill")
                    .font(.system(size: 14))
                    .foregroundColor(Color(red: 0.55, green: 0.35, blue: 0.17))
            }
            Link(destination: URL(string: "goldpet://spot/pee")!) {
                Image(systemName: "drop.fill")
                    .font(.system(size: 14))
                    .foregroundColor(Color(red: 0.9, green: 0.7, blue: 0.2))
            }
            Link(destination: URL(string: "goldpet://spot/photo")!) {
                Image(systemName: "camera.fill")
                    .font(.system(size: 14))
                    .foregroundColor(Color(red: 0.3, green: 0.6, blue: 0.85))
            }
            Link(destination: URL(string: "goldpet://walk/stop")!) {
                Image(systemName: "stop.fill")
                    .font(.system(size: 14))
                    .foregroundColor(.red)
            }
        }
    }
}

// MARK: - Widget Configuration

struct WalkLiveActivity: Widget {
    var body: some WidgetConfiguration {
        ActivityConfiguration(for: WalkActivityAttributes.self) { context in
            // Lock screen / banner view
            WalkLiveActivityView(state: context.state)
                .widgetURL(URL(string: "goldpet://walk"))
                .activityBackgroundTint(WalkWidgetStyle.brownColor.opacity(0.9))
        } dynamicIsland: { context in
            DynamicIsland {
                DynamicIslandExpandedRegion(.leading) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(String(format: "%.2f", context.state.distanceKm))
                            .font(.system(size: 18, weight: .bold))
                        Text("km")
                            .font(.system(size: 10))
                            .foregroundColor(.secondary)
                    }
                }
                DynamicIslandExpandedRegion(.trailing) {
                    let minutes = context.state.durationSeconds / 60
                    let seconds = context.state.durationSeconds % 60
                    VStack(alignment: .trailing, spacing: 2) {
                        Text(String(format: "%02d:%02d", minutes, seconds))
                            .font(.system(size: 18, weight: .bold))
                        Text("시간")
                            .font(.system(size: 10))
                            .foregroundColor(.secondary)
                    }
                }
                DynamicIslandExpandedRegion(.bottom) {
                    DynamicIslandSpotButtons()
                }
            } compactLeading: {
                Text(String(format: "%.1fkm", context.state.distanceKm))
                    .font(.system(size: 12, weight: .semibold))
            } compactTrailing: {
                let minutes = context.state.durationSeconds / 60
                let seconds = context.state.durationSeconds % 60
                Text(String(format: "%02d:%02d", minutes, seconds))
                    .font(.system(size: 12, weight: .semibold))
            } minimal: {
                Text(String(format: "%.1f", context.state.distanceKm))
                    .font(.system(size: 12, weight: .semibold))
            }
        }
    }
}
