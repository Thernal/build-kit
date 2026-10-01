import SwiftUI
import FixtureAppsPartnerShared

@main
struct PartnerApp: App {
    var body: some Scene {
        WindowGroup {
            ComposeView().ignoresSafeArea()
        }
    }
}

/// The Compose root of ../shared, as a SwiftUI view.
struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController(appName: "Partner")
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
