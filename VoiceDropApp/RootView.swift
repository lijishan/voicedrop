import SwiftUI

/// Record-first navigation: the record screen is the root. Settings is pushed
/// from the top-right gear; "我的录音" pulls up as a sheet. No tab bar.
struct RootView: View {
    var body: some View {
        NavigationStack {
            ContentView()
        }
        .tint(Theme.accent)
        .preferredColorScheme(.light)
    }
}
