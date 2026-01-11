import SwiftUI
import ComposeApp

@main
struct iOSApp: App {
    init() {
        Zip.shared.zipAPI = ZipImplementation()
        UIApplication.shared.isIdleTimerDisabled = true
    }
    
    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
