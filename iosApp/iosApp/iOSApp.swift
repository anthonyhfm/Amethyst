import SwiftUI
import ComposeApp

@main
struct iOSApp: App {
    init() {
        SentrySetupKt.initializeSentry()
        Zip.shared.zipAPI = ZipImplementation()
    }
    
    var body: some Scene {
        WindowGroup {
            ContentView()
                .preferredColorScheme(.dark)
        }
    }
}
