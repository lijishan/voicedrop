import SwiftUI
import UIKit

// 键盘精修 v2（2026-07-14 重做；v1 因真机错误百出整体 revert，见 STATE.md）。
// 用户拍板的三条铁律：
//   1. 长按的所有 behavior 不变——菜单还是 0.35s 长按弹出（v1 为了拿触点坐标把手势
//      改成 sequenced(LongPress→Drag)，导致菜单变成松手才出现，这就是行为回归）。
//   2. 编辑全程排版零变化——没有描边框、没有 -10 出血、没有内边距、不淡出其他段落；
//      字体/行距/宽度与只读 Text 逐项对齐，切换瞬间文字纹丝不动。
//   3. 编辑完恢复原来的阅读状态。
// 光标不做「落在长按点」（v1 的坐标换算是 bug 之源）：进编辑后光标落段尾，
// 用户点哪儿改哪儿——全部走 UITextView 原生触摸行为，没有自定义坐标数学。

/// 只读段落原位换成的可编辑 UITextView。SwiftUI 的 TextEditor 撑不起这里的两个
/// 硬需求（textContainerInset 清零对齐 Text、回车拦截成「完成」），所以用
/// UIViewRepresentable；高度由 sizeThatFits 按提议宽度自适应，跟着打字实时长高。
struct InlineParagraphEditor: UIViewRepresentable {
    @Binding var text: String
    let onDone: () -> Void

    /// 与只读段落 `Text(...).font(.system(size: 16)).lineSpacing(9)` 逐项对齐；
    /// typingAttributes 同一份，保证新敲的字不丢行距。
    static let attributes: [NSAttributedString.Key: Any] = {
        let style = NSMutableParagraphStyle()
        style.lineSpacing = 9
        return [.font: UIFont.systemFont(ofSize: 16),
                .foregroundColor: UIColor(Theme.bodyRead),
                .paragraphStyle: style]
    }()

    func makeUIView(context: Context) -> UITextView {
        let tv = UITextView()
        tv.delegate = context.coordinator
        tv.backgroundColor = .clear
        tv.textContainerInset = .zero            // 只读 Text 没有内边距，这里也不能有
        tv.textContainer.lineFragmentPadding = 0
        tv.isScrollEnabled = false               // 高度交给 sizeThatFits，不自己滚
        tv.tintColor = UIColor(Theme.accent)     // 光标用 app 赭红
        tv.autocapitalizationType = .none
        tv.typingAttributes = Self.attributes
        tv.attributedText = NSAttributedString(string: text, attributes: Self.attributes)
        DispatchQueue.main.async {
            tv.becomeFirstResponder()
            let end = tv.endOfDocument
            tv.selectedTextRange = tv.textRange(from: end, to: end)
        }
        return tv
    }

    func updateUIView(_ tv: UITextView, context: Context) {
        // 打字会让 binding 变化再走回这里；textView 自己是真源，只有外部改动
        // （目前没有这种路径）才重设内容，否则会把光标弹走。
        if tv.text != text {
            tv.attributedText = NSAttributedString(string: text, attributes: Self.attributes)
        }
    }

    /// representable 不实现这个的话拿不到合适高度（UITextView 不滚时 SwiftUI 只给
    /// 提议尺寸）。按提议宽度让内容自算高度——文字变多时随之长高，排版与只读态一致。
    func sizeThatFits(_ proposal: ProposedViewSize, uiView: UITextView, context: Context) -> CGSize? {
        guard let width = proposal.width, width > 0, width.isFinite else { return nil }
        let h = uiView.sizeThatFits(CGSize(width: width, height: .greatestFiniteMagnitude)).height
        return CGSize(width: width, height: h)
    }

    func makeCoordinator() -> Coordinator { Coordinator(text: $text, onDone: onDone) }

    final class Coordinator: NSObject, UITextViewDelegate {
        let text: Binding<String>
        let onDone: () -> Void

        init(text: Binding<String>, onDone: @escaping () -> Void) {
            self.text = text
            self.onDone = onDone
        }

        /// 回车 = 完成。段落是文章结构里的单行 block，换行进正文会把一段裂成两段。
        func textView(_ textView: UITextView, shouldChangeTextIn range: NSRange, replacementText t: String) -> Bool {
            if t == "\n" { onDone(); return false }
            return true
        }

        func textViewDidChange(_ textView: UITextView) {
            text.wrappedValue = textView.text
        }
    }
}
