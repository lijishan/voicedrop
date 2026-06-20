import SwiftUI

/// Two tabs: the recorder and the library of recordings + mined articles.
struct RootView: View {
    var body: some View {
        TabView {
            Tab("录音", systemImage: "mic.fill") {
                ContentView()
            }
            Tab("文章", systemImage: "books.vertical.fill") {
                LibraryView()
            }
        }
        .tint(.white)
        .preferredColorScheme(.dark)
    }
}
