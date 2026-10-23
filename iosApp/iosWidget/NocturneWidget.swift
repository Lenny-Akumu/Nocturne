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
    let style: String
}

struct NowProvider: TimelineProvider {
    func placeholder(in context: Context) -> NowEntry {
        NowEntry(date: Date(), title: "Nocturne", artist: "", playing: false, art: nil, style: "GLASS")
    }

    func getSnapshot(in context: Context, completion: @escaping (NowEntry) -> Void) { completion(read()) }

    func getTimeline(in context: Context, completion: @escaping (Timeline<NowEntry>) -> Void) {
        completion(Timeline(entries: [read()], policy: .never))
    }

    private func read() -> NowEntry {
        guard let dir = FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: group),
              let data = try? Data(contentsOf: dir.appendingPathComponent("now.json")),
              let obj = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any]
        else { return NowEntry(date: Date(), title: "Nocturne", artist: "", playing: false, art: nil, style: "GLASS") }
        let art = (try? Data(contentsOf: dir.appendingPathComponent("art.jpg"))).flatMap { UIImage(data: $0) }
        return NowEntry(
            date: Date(),
            title: obj["title"] as? String ?? "Nocturne",
            artist: obj["artist"] as? String ?? "",
            playing: obj["playing"] as? Bool ?? false,
            art: art,
            style: obj["style"] as? String ?? "GLASS"
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

// Looks per the app's design style.
private struct Look {
    let bg: LinearGradient
    let accent: Color
    let radius: CGFloat

    init(_ style: String) {
        switch style {
        case "MATERIAL":
            bg = LinearGradient(colors: [Color(red: 0.18, green: 0.14, blue: 0.27), Color(red: 0.10, green: 0.08, blue: 0.15)], startPoint: .top, endPoint: .bottom)
            accent = Color(red: 0.80, green: 0.72, blue: 1.0)
            radius = 18
        case "WINDOWS":
            bg = LinearGradient(colors: [Color(white: 0.16), Color(white: 0.11)], startPoint: .top, endPoint: .bottom)
            accent = Color(red: 0.38, green: 0.70, blue: 1.0)
            radius = 6
        default:
            bg = LinearGradient(colors: [Color(red: 0.13, green: 0.10, blue: 0.22), Color(red: 0.05, green: 0.04, blue: 0.08)], startPoint: .top, endPoint: .bottom)
            accent = .white
            radius = 14
        }
    }
}

private struct ControlButton: View {
    let command: String
    let symbol: String
    let size: CGFloat
    var filled = false
    var accent: Color = .white
    var plain = false

    var body: some View {
        let label = Image(systemName: symbol)
            .font(.system(size: size, weight: .bold))
            .foregroundStyle(filled ? Color.black.opacity(0.85) : Color.white)
            .frame(width: plain ? size * 1.6 : (filled ? size * 2.6 : size * 1.9), height: plain ? size * 1.6 : size * 2.1)
            .background(plain ? AnyShapeStyle(Color.clear) : (filled ? AnyShapeStyle(accent) : AnyShapeStyle(Color.white.opacity(0.12))),
                        in: RoundedRectangle(cornerRadius: size * 0.8, style: .continuous))
        if #available(iOS 17.0, *) {
            Button(intent: ControlIntent(command)) { label }.buttonStyle(.plain)
        } else {
            Link(destination: URL(string: "nocturne://player")!) { label }
        }
    }
}

private struct Cover: View {
    let entry: NowEntry
    var radius: CGFloat
    var body: some View {
        Group {
            if let art = entry.art { Image(uiImage: art).resizable().aspectRatio(contentMode: .fill) }
            else { LinearGradient(colors: [Color(red: 0.49, green: 0.36, blue: 1), Color(red: 0.17, green: 0.11, blue: 0.42)], startPoint: .topLeading, endPoint: .bottomTrailing) }
        }
        .clipShape(RoundedRectangle(cornerRadius: radius, style: .continuous))
    }
}

private struct Titles: View {
    let entry: NowEntry
    var size: CGFloat = 15
    var lines = 1
    var center = false
    var body: some View {
        VStack(alignment: center ? .center : .leading, spacing: 2) {
            Text(entry.title).font(.system(size: size, weight: .bold)).foregroundStyle(.white).lineLimit(lines)
            Text(entry.artist).font(.system(size: size * 0.82)).foregroundStyle(.white.opacity(0.7)).lineLimit(1)
        }
    }
}

private struct Controls: View {
    let entry: NowEntry
    let look: Look
    var size: CGFloat = 14
    var body: some View {
        HStack(spacing: size * 0.7) {
            ControlButton(command: "prev", symbol: "backward.fill", size: size)
            ControlButton(command: "toggle", symbol: entry.playing ? "pause.fill" : "play.fill", size: size + 2, filled: true, accent: look.accent)
            ControlButton(command: "next", symbol: "forward.fill", size: size)
        }
    }
}

// Classic player: every home screen size plus the lock screen.
struct NowPlayingView: View {
    @Environment(\.widgetFamily) var family
    let entry: NowEntry

    var body: some View {
        let look = Look(entry.style)
        switch family {
        case .systemSmall:
            VStack(alignment: .leading, spacing: 6) {
                HStack(alignment: .top) {
                    Cover(entry: entry, radius: look.radius).frame(width: 62, height: 62)
                    Spacer(minLength: 0)
                    ControlButton(command: "toggle", symbol: entry.playing ? "pause.fill" : "play.fill", size: 13, filled: true, accent: look.accent)
                }
                Spacer(minLength: 0)
                Titles(entry: entry, size: 14)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        case .systemLarge, .systemExtraLarge:
            VStack(spacing: 12) {
                Cover(entry: entry, radius: look.radius + 4).aspectRatio(1, contentMode: .fit).frame(maxHeight: .infinity)
                Titles(entry: entry, size: 18, center: true)
                Controls(entry: entry, look: look, size: 17)
            }
        case .accessoryCircular:
            ZStack {
                AccessoryWidgetBackground()
                Image(systemName: entry.playing ? "pause.fill" : "play.fill").font(.system(size: 20, weight: .bold))
            }
        case .accessoryRectangular:
            VStack(alignment: .leading, spacing: 1) {
                Label(entry.playing ? "Playing" : "Paused", systemImage: "waveform").font(.system(size: 11, weight: .semibold))
                Text(entry.title).font(.system(size: 14, weight: .bold)).lineLimit(1)
                Text(entry.artist).font(.system(size: 12)).lineLimit(1).opacity(0.75)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        case .accessoryInline:
            Label("\(entry.title) · \(entry.artist)", systemImage: "music.note")
        default:
            HStack(spacing: 14) {
                Cover(entry: entry, radius: look.radius).frame(width: 110, height: 110)
                VStack(alignment: .leading, spacing: 4) {
                    Titles(entry: entry, size: 16, lines: 2)
                    Spacer(minLength: 0)
                    Controls(entry: entry, look: look)
                }
            }
        }
    }
}

// Minimal: just the song and a play glyph.
struct MinimalView: View {
    @Environment(\.widgetFamily) var family
    let entry: NowEntry
    var body: some View {
        if family == .systemSmall {
            VStack(alignment: .leading) {
                Image(systemName: "waveform").font(.system(size: 18, weight: .semibold)).foregroundStyle(.white.opacity(0.6))
                Spacer(minLength: 0)
                Titles(entry: entry, size: 15, lines: 2)
                ControlButton(command: "toggle", symbol: entry.playing ? "pause.fill" : "play.fill", size: 16, plain: true)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        } else {
            HStack {
                Titles(entry: entry, size: 17, lines: 2)
                Spacer(minLength: 8)
                ControlButton(command: "toggle", symbol: entry.playing ? "pause.fill" : "play.fill", size: 22, plain: true)
            }
        }
    }
}

// Cover: full-bleed artwork, small play button.
struct CoverView: View {
    let entry: NowEntry
    var body: some View {
        ZStack(alignment: .bottomTrailing) {
            Cover(entry: entry, radius: 0)
            LinearGradient(colors: [.clear, .black.opacity(0.55)], startPoint: .center, endPoint: .bottom)
            HStack(alignment: .bottom) {
                Titles(entry: entry, size: 13)
                Spacer(minLength: 4)
                ControlButton(command: "toggle", symbol: entry.playing ? "pause.fill" : "play.fill", size: 12, filled: true, accent: Look(entry.style).accent)
            }
            .padding(12)
        }
    }
}

private extension View {
    @ViewBuilder func widgetBackground(_ entry: NowEntry, edgeToEdge: Bool = false) -> some View {
        let bg = Look(entry.style).bg
        if #available(iOS 17.0, *) { containerBackground(for: .widget) { bg } }
        else if edgeToEdge { background(bg) }
        else { padding().background(bg) }
    }
}

struct NowPlayingWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "NocturneNowPlaying", provider: NowProvider()) { entry in
            NowPlayingView(entry: entry).widgetBackground(entry).widgetURL(URL(string: "nocturne://player"))
        }
        .configurationDisplayName("Nocturne")
        .description("Now playing with controls")
        .supportedFamilies([.systemSmall, .systemMedium, .systemLarge, .systemExtraLarge, .accessoryCircular, .accessoryRectangular, .accessoryInline])
    }
}

struct MinimalWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "NocturneMinimal", provider: NowProvider()) { entry in
            MinimalView(entry: entry).widgetBackground(entry).widgetURL(URL(string: "nocturne://player"))
        }
        .configurationDisplayName("Nocturne · Minimal")
        .description("Just the song and a play button")
        .supportedFamilies([.systemSmall, .systemMedium])
    }
}

struct CoverWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "NocturneCover", provider: NowProvider()) { entry in
            CoverView(entry: entry).widgetBackground(entry, edgeToEdge: true).widgetURL(URL(string: "nocturne://player"))
        }
        .configurationDisplayName("Nocturne · Cover")
        .description("Full artwork with a small play button")
        .supportedFamilies([.systemSmall, .systemLarge])
    }
}

@main
struct NocturneWidgets: WidgetBundle {
    var body: some Widget {
        NowPlayingWidget()
        MinimalWidget()
        CoverWidget()
    }
}
