import Foundation
import PDFKit
import UIKit

// Pure extraction helpers for the Share Extension's incoming-item router (a
// later task). No UI, no third-party deps — PDFKit/Foundation/UIKit only.

/// What kind of thing the user shared — drives the later router's default
/// 用途 (mine vs style) and upload naming, mirroring `ShareViewController`'s
/// own ad-hoc type sniffing but as a first-class value the router can switch on.
enum ShareKind {
    case audio, image, web, document, text
}

// MARK: - PDF / rich document extraction

/// Extract plain text from a PDF at `url`, or nil if unreadable/empty.
func extractPDF(_ url: URL) -> String? {
    guard let doc = PDFDocument(url: url), let s = doc.string?.trimmingCharacters(in: .whitespacesAndNewlines), !s.isEmpty else { return nil }
    return s
}

/// Extract plain text from a rich document (docx/rtf/…) at `url` via
/// `NSAttributedString`'s document-type auto-detection, or nil if unreadable/empty.
func extractRichDocument(_ url: URL) -> String? {
    guard let a = try? NSAttributedString(url: url, options: [:], documentAttributes: nil) else { return nil }
    let s = a.string.trimmingCharacters(in: .whitespacesAndNewlines)
    return s.isEmpty ? nil : s
}

/// The first non-empty line of `text` (trimmed, capped at 40 chars), or `fallback`
/// if `text` has no usable first line.
func firstLineTitle(_ text: String, fallback: String) -> String {
    let line = text.split(whereSeparator: \.isNewline).first.map(String.init)?.trimmingCharacters(in: .whitespaces) ?? ""
    let t = line.isEmpty ? fallback : line
    return String(t.prefix(40))
}

// MARK: - Readability (shared web link → title + article text)

/// Minimal readability: fetch a URL with a browser UA and pull out a title +
/// article body. 微信 (`mp.weixin.qq.com`) gets a `#js_content` special-case;
/// everything else falls back to `<article>` then `<body>`.
enum Readability {
    static func fetch(_ url: URL) async -> (title: String?, text: String)? {
        var req = URLRequest(url: url, timeoutInterval: 10)
        req.setValue("Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.0 Mobile/15E148 Safari/604.1", forHTTPHeaderField: "User-Agent")
        guard let (data, _) = try? await URLSession.shared.data(for: req),
              let html = String(data: data, encoding: .utf8) ?? String(data: data, encoding: .isoLatin1) else { return nil }
        let title = firstMatch(html, #"<meta[^>]+property=[\"']og:title[\"'][^>]+content=[\"']([^\"']+)"#)
            ?? firstMatch(html, #"<title[^>]*>([^<]+)</title>"#)
        var body = html
        if url.host?.contains("mp.weixin.qq.com") == true, let m = firstMatch(html, #"(?s)<div[^>]+id=[\"']js_content[\"'][^>]*>(.*?)</div>"#) { body = m }
        else if let m = firstMatch(html, #"(?s)<article[^>]*>(.*?)</article>"#) { body = m }
        else if let m = firstMatch(html, #"(?s)<body[^>]*>(.*?)</body>"#) { body = m }
        let text = stripTags(body)
        return text.count < 40 ? (title, title ?? "") : (title, text)
    }
    private static func firstMatch(_ s: String, _ pat: String) -> String? {
        guard let re = try? NSRegularExpression(pattern: pat, options: [.caseInsensitive]),
              let m = re.firstMatch(in: s, range: NSRange(s.startIndex..., in: s)), m.numberOfRanges > 1,
              let r = Range(m.range(at: 1), in: s) else { return nil }
        return String(s[r]).trimmingCharacters(in: .whitespacesAndNewlines)
    }
    private static func stripTags(_ h: String) -> String {
        var s = h
        for p in [#"(?s)<script.*?</script>"#, #"(?s)<style.*?</style>"#] { s = s.replacingOccurrences(of: p, with: " ", options: .regularExpression) }
        s = s.replacingOccurrences(of: #"<[^>]+>"#, with: "\n", options: .regularExpression)
        for (e, c) in ["&nbsp;":" ", "&amp;":"&", "&lt;":"<", "&gt;":">", "&quot;":"\"", "&#39;":"'"] { s = s.replacingOccurrences(of: e, with: c) }
        return s.replacingOccurrences(of: #"\n[ \t]*\n(\s*\n)+"#, with: "\n\n", options: .regularExpression).trimmingCharacters(in: .whitespacesAndNewlines)
    }
}
