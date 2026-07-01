// STUB — full implementation in Task 10; do not build on this.
import SwiftUI

/// Placeholder for the image share sheet — uploads the shared photo(s) as a
/// 场景照片-style take (placeholder audio + images). Task 10 replaces this file
/// with the real compose UI; keep the initializer signature (`payload:`,
/// `close:`) stable so `ShareRootView`'s call site doesn't need to change.
struct PhotoComposeView: View {
    let payload: SharePayload
    let close: () -> Void

    var body: some View {
        VStack(spacing: 16) {
            Text("导入照片")
                .font(.system(size: 17, weight: .semibold))
                .foregroundStyle(ShareTheme.ink)
            Text("STUB — Task 10 会替换成真正的界面")
                .font(.system(size: 14))
                .foregroundStyle(ShareTheme.secondary)
            Button("关闭", action: close)
                .font(.system(size: 16, weight: .semibold))
                .foregroundStyle(.white)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 12)
                .background(ShareTheme.accent, in: RoundedRectangle(cornerRadius: 10))
        }
        .padding(20)
        .padding(.bottom, 8)
        .background(ShareTheme.sheetBG)
        .clipShape(RoundedRectangle(cornerRadius: 16))
        .overlay(RoundedRectangle(cornerRadius: 16).stroke(ShareTheme.border, lineWidth: 1))
        .padding(.horizontal, 12)
        .padding(.bottom, 12)
    }
}
