import SwiftUI

/// List-first navigation (方案二): 我的录音 is the root. Settings pushes from the
/// gear; the red record key opens a full-screen recording takeover. No tab bar.
struct RootView: View {
    var body: some View {
        NavigationStack {
            LibraryView()
        }
        .tint(Theme.accent)
        .preferredColorScheme(.light)
    }
}
