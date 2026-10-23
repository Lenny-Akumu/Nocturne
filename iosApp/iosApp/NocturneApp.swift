import SwiftUI
import WidgetKit
import ComposeApp

@main
struct NocturneApp: App {
    var body: some Scene {
        WindowGroup {
            ComposeView()
                .ignoresSafeArea()
                .preferredColorScheme(nil)
                .onOpenURL { url in MainViewControllerKt.handleUrl(url: url.absoluteString) }
        }
    }
}

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        let vc = MainViewControllerKt.MainViewController()
        WidgetBridge.start()
        return vc
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

// Now playing → App Group files for the widget; widget buttons → Darwin notifications back to the player.
enum WidgetBridge {
    static let group = "group.app.nocturne"
    private static var started = false
    private static var lastArt = ""

    static func start() {
        if started { return }
        started = true
        MainViewControllerKt.observeWidget { push() }
        let center = CFNotificationCenterGetDarwinNotifyCenter()
        for cmd in ["toggle", "next", "prev"] {
            CFNotificationCenterAddObserver(center, nil, { _, _, name, _, _ in
                guard let raw = name?.rawValue as String? else { return }
                let cmd = raw.replacingOccurrences(of: "app.nocturne.widget.", with: "")
                DispatchQueue.main.async { MainViewControllerKt.widgetCommand(cmd: cmd) }
            }, "app.nocturne.widget.\(cmd)" as CFString, nil, .deliverImmediately)
        }
    }

    private static func push() {
        guard let dir = FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: group) else { return }
        let json = MainViewControllerKt.widgetState()
        try? json.write(to: dir.appendingPathComponent("now.json"), atomically: true, encoding: .utf8)
        let obj = (try? JSONSerialization.jsonObject(with: Data(json.utf8))) as? [String: Any]
        let art = obj?["art"] as? String ?? ""
        if art == lastArt || art.isEmpty {
            WidgetCenter.shared.reloadAllTimelines()
            return
        }
        lastArt = art
        guard let url = URL(string: art) else { return }
        URLSession.shared.dataTask(with: url) { data, _, _ in
            if let data { try? data.write(to: dir.appendingPathComponent("art.jpg"), options: .atomic) }
            DispatchQueue.main.async { WidgetCenter.shared.reloadAllTimelines() }
        }.resume()
    }
}
