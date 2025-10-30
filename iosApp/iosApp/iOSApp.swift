import SwiftUI
import ComposeApp

@main
struct iOSApp: App {
    init() {
        Zip.shared.zipAPI = ZipImplementation()
    }
    
    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
