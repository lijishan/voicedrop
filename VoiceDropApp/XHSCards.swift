import UIKit

/// 小红书图文卡：把文章排成 3:4（1080×1440）的文字卡片——首张标题卡 + 正文分页卡。
/// 刷图可以读完全文；文字稿同时在剪贴板里，粘贴进笔记正文就是「看图也可以看字」。
/// 浅色米纸底 + 墨色正文 + 赭红点缀，与 App 阅读界面同一气质。
enum XHSCards {
    static let cardSize = CGSize(width: 1080, height: 1440)

    private static let bg     = UIColor(red: 0.961, green: 0.945, blue: 0.909, alpha: 1)  // 米纸
    private static let ink    = UIColor(red: 0.227, green: 0.208, blue: 0.180, alpha: 1)  // 墨
    private static let meta   = UIColor(red: 0.553, green: 0.514, blue: 0.447, alpha: 1)  // 弱化
    private static let accent = UIColor(red: 0.725, green: 0.314, blue: 0.180, alpha: 1)  // 赭红

    /// 标题卡 + 正文分页卡（正文最多 11 页，超出截断——上游已保证 ≤1000 字，用不满）。
    static func render(title: String, body: String, date: String) -> [UIImage] {
        // 正文分页：一套 NSLayoutManager 挂多个 textContainer，每个容器就是一页。
        let textRect = CGRect(x: 110, y: 150, width: cardSize.width - 220, height: 1120)
        let storage = NSTextStorage(attributedString: bodyAttributed(body))
        let lm = NSLayoutManager()
        storage.addLayoutManager(lm)
        var containers: [NSTextContainer] = []
        while containers.count < 11 {
            let tc = NSTextContainer(size: textRect.size)
            tc.lineFragmentPadding = 0
            lm.addTextContainer(tc)
            containers.append(tc)
            if lm.glyphRange(for: tc).upperBound >= lm.numberOfGlyphs { break }
        }
        containers.removeAll { lm.glyphRange(for: $0).length == 0 }

        let total = containers.count + 1
        let fmt = UIGraphicsImageRendererFormat()
        fmt.scale = 1
        var cards = [titleCard(title: title, date: date, page: "1 / \(total)", format: fmt)]
        for (i, tc) in containers.enumerated() {
            let range = lm.glyphRange(for: tc)
            cards.append(UIGraphicsImageRenderer(size: cardSize, format: fmt).image { _ in
                fill()
                lm.drawGlyphs(forGlyphRange: range, at: textRect.origin)
                footer("\(i + 2) / \(total)")
            })
        }
        return cards
    }

    // MARK: drawing

    private static func fill() {
        bg.setFill()
        UIBezierPath(rect: CGRect(origin: .zero, size: cardSize)).fill()
    }

    private static func footer(_ text: String) {
        let attrs: [NSAttributedString.Key: Any] = [
            .font: UIFont.systemFont(ofSize: 26, weight: .medium),
            .foregroundColor: meta, .kern: 3,
        ]
        let s = NSAttributedString(string: text, attributes: attrs)
        let w = s.size().width
        s.draw(at: CGPoint(x: (cardSize.width - w) / 2, y: cardSize.height - 84))
    }

    private static func titleCard(title: String, date: String, page: String, format: UIGraphicsImageRendererFormat) -> UIImage {
        UIGraphicsImageRenderer(size: cardSize, format: format).image { _ in
            fill()
            if !date.isEmpty {
                let d = NSAttributedString(string: date, attributes: [
                    .font: UIFont.systemFont(ofSize: 30, weight: .regular),
                    .foregroundColor: meta, .kern: 4,
                ])
                d.draw(at: CGPoint(x: 112, y: 200))
            }
            accent.setFill()
            UIBezierPath(roundedRect: CGRect(x: 112, y: 286, width: 76, height: 8), cornerRadius: 4).fill()
            let para = NSMutableParagraphStyle()
            para.lineSpacing = 26
            let t = NSAttributedString(string: title, attributes: [
                .font: UIFont.systemFont(ofSize: 78, weight: .semibold),
                .foregroundColor: ink, .paragraphStyle: para, .kern: 1,
            ])
            t.draw(in: CGRect(x: 110, y: 380, width: cardSize.width - 220, height: 800))
            footer(page)
        }
    }

    private static func bodyAttributed(_ body: String) -> NSAttributedString {
        let para = NSMutableParagraphStyle()
        para.lineSpacing = 26
        para.paragraphSpacing = 36
        return NSAttributedString(string: body, attributes: [
            .font: UIFont.systemFont(ofSize: 42, weight: .regular),
            .foregroundColor: ink, .paragraphStyle: para, .kern: 0.5,
        ])
    }
}
