import WidgetKit
import SwiftUI
import AppIntents

private let group = "group.app.nocturne"

struct NowEntry: TimelineEntry {
    let date: Date
    let title: String
    let artist: String
    let playing: Bool
    let art: UIImage?
}

struct NowProvider: TimelineProvider {
    func placeholder(in context: Context) -> NowEntry {
        NowEntry(date: Date(), title: "Nocturne", artist: "", playing: false, art: nil)
    }

    func getSnapshot(in context: Context, completion: @escaping (NowEntry) -> Void) {
        completion(read())
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<NowEntry>) -> Void) {
        completion(Timeline(entries: [read()], policy: .never))
    }

    private func read() -> NowEntry {
        guard let dir = FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: group),
              let data = try? Data(contentsOf: dir.appendingPathComponent("now.json")),
              let obj = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any]
        else { return NowEntry(date: Date(), title: "Nocturne", artist: "", playing: false, art: nil) }
        let art = (try? Data(contentsOf: dir.appendingPathComponent("art.jpg"))).flatMap { UIImage(data: $0) }
        return NowEntry(
            date: Date(),
            title: obj["title"] as? String ?? "Nocturne",
            artist: obj["artist"] as? String ?? "",
            playing: obj["playing"] as? Bool ?? false,
            art: art
        )
    }
}

@available(iOS 17.0, *)
struct ControlIntent: AudioPlaybackIntent {
    static var title: LocalizedStringResource = "Control playback"

    @Parameter(title: "Command") var command: String

    init() { command = "toggle" }
    init(_ command: String) { self.command = command }

    func perform() async throws -> some IntentResult {
        CFNotificationCenterPostNotification(
            CFNotificationCenterGetDarwinNotifyCenter(),
            CFNotificationName("app.nocturne.widget.\(command)" as CFString), nil, nil, true
        )
        return .result()
    }
}

private struct ControlButton: View {
    let command: String
    let symbol: String
    let size: CGFloat
    var filled = false

    var body: some View {
        let label = Image(systemName: symbol)
            .font(.system(size: size, weight: .bold))
            .foregroundStyle(filled ? Color.black.opacity(0.85) : Color.white)
            .frame(width: filled ? size * 2.6 : size * 1.9, height: size * 2.1)
            .background(filled ? AnyShapeStyle(Color(red: 0.73, green: 0.65, blue: 1.0)) : AnyShapeStyle(Color.white.opacity(0.12)),
                        in: RoundedRectangle(cornerRadius: size * 0.8, style: .continuous))
        if #available(iOS 17.0, *) {
            Button(intent: ControlIntent(command)) { label }.buttonStyle(.plain)
        } else {
            Link(destination: URL(string: "nocturne://player")!) { label }
        }
    }
}

struct NowPlayingView: View {
    @Environment(\.widgetFamily) var family
    let entry: NowEntry

    var artwork: some View {
        Group {
            if let art = entry.art { Image(uiImage: art).resizable().aspectRatio(contentMode: .fill) }
            else { LinearGradient(colors: [Color(red: 0.49, green: 0.36, blue: 1), Color(red: 0.17, green: 0.11, blue: 0.42)], startPoint: .topLeading, endPoint: .bottomTrailing) }
        }
        .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
    }

    var body: some View {
        if family == .systemSmall {
            VStack(alignment: .leading, spacing: 6) {
                artwork.frame(width: 64, height: 64)
                Spacer(minLength: 0)
                Text(entry.title).font(.system(size: 14, weight: .bold)).foregroundStyle(.white).lineLimit(1)
                Text(entry.artist).font(.system(size: 12)).foregroundStyle(.white.opacity(0.7)).lineLimit(1)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        } else {
            HStack(spacing: 14) {
                artwork.frame(width: 110, height: 110)
                VStack(alignment: .leading, spacing: 4) {
                    Text(entry.title).font(.system(size: 16, weight: .bold)).foregroundStyle(.white).lineLimit(2)
                    Text(entry.artist).font(.system(size: 13)).foregroundStyle(.white.opacity(0.7)).lineLimit(1)
                    Spacer(minLength: 0)
                    HStack(spacing: 10) {
                        ControlButton(command: "prev", symbol: "backward.fill", size: 14)
                        ControlButton(command: "toggle", symbol: entry.playing ? "pause.fill" : "play.fill", size: 16, filled: true)
                        ControlButton(command: "next", symbol: "forward.fill", size: 14)
                    }
                }
            }
        }
    }
}

private extension View {
    @ViewBuilder func widgetBackground() -> some View {
        let bg = LinearGradient(colors: [Color(red: 0.13, green: 0.10, blue: 0.22), Color(red: 0.05, green: 0.04, blue: 0.08)], startPoint: .top, endPoint: .bottom)
        if #available(iOS 17.0, *) { containerBackground(for: .widget) { bg } }
        else { padding().background(bg) }
    }
}

struct NowPlayingWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "NocturneNowPlaying", provider: NowProvider()) { entry in
            NowPlayingView(entry: entry)
                .widgetBackground()
                .widgetURL(URL(string: "nocturne://player"))
        }
        .configurationDisplayName("Nocturne")
        .description("Now playing with controls")
        .supportedFamilies([.systemSmall, .systemMedium])
    }
}

@main
struct NocturneWidgets: WidgetBundle {
    var body: some Widget { NowPlayingWidget() }
}
