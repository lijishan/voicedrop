import Foundation
import Observation

enum AgentState: Equatable { case idle, connecting, working, error }

/// A live WebSocket conversation with the article-editing Agent (Cloudflare
/// Durable Object behind wss://jianshuo.dev/agent/edit). You `connect` once for a
/// recording, `send` spoken instructions, and each time the server finishes
/// rewriting it pushes the new article doc back — delivered via `onUpdate` so the
/// detail view can reload it in place. The socket stays open for an unbounded
/// back-and-forth; `disconnect` ends it.
@MainActor
@Observable
final class ArticleAgentSession {
    var state: AgentState = .idle
    var error: String?

    /// Called on the main actor whenever the server pushes a rewritten article.
    var onUpdate: ((ArticleDoc) -> Void)?

    private var task: URLSessionWebSocketTask?
    private var session: URLSession?
    private var rec: Recording?
    private var closed = false

    private let base = "wss://jianshuo.dev/agent/edit"
    private var token: String { AuthStore.shared.bearer }

    func connect(_ rec: Recording) {
        self.rec = rec
        closed = false
        openSocket()
    }

    private func openSocket() {
        guard let rec, !token.isEmpty else { state = .error; error = "未登录"; return }
        state = .connecting
        error = nil
        let stem = rec.stem.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? rec.stem
        guard let url = URL(string: "\(base)?stem=\(stem)") else { state = .error; return }
        var req = URLRequest(url: url)
        // Token rides the upgrade header, not the query string (avoids logging it).
        req.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        let s = URLSession(configuration: .default)
        session = s
        let t = s.webSocketTask(with: req)
        task = t
        t.resume()
        state = .idle               // URLSession buffers sends until the socket opens
        receive()
    }

    func send(_ instruction: String) {
        let text = instruction.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !text.isEmpty, let task else { return }
        state = .working
        error = nil
        let payload: [String: String] = ["type": "instruct", "text": text]
        guard let data = try? JSONSerialization.data(withJSONObject: payload),
              let str = String(data: data, encoding: .utf8) else { return }
        task.send(.string(str)) { [weak self] err in
            guard let err else { return }
            Task { @MainActor in self?.state = .error; self?.error = err.localizedDescription }
        }
    }

    private func receive() {
        task?.receive { [weak self] result in
            Task { @MainActor in
                guard let self else { return }
                switch result {
                case .failure:
                    if !self.closed { self.reconnect() }
                case .success(let message):
                    switch message {
                    case .string(let str): self.handle(str)
                    case .data(let d): if let str = String(data: d, encoding: .utf8) { self.handle(str) }
                    @unknown default: break
                    }
                    self.receive()
                }
            }
        }
    }

    private func handle(_ str: String) {
        guard let data = str.data(using: .utf8),
              let obj = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let type = obj["type"] as? String else { return }
        switch type {
        case "status":
            if (obj["state"] as? String) == "working" { state = .working }
        case "updated":
            if let art = obj["article"],
               let d = try? JSONSerialization.data(withJSONObject: art),
               let doc = try? JSONDecoder().decode(ArticleDoc.self, from: d) {
                onUpdate?(doc)
            }
            state = .idle
        case "error":
            state = .error
            error = (obj["message"] as? String) ?? "出错了"
        default:
            break
        }
    }

    private func reconnect() {
        guard !closed else { return }
        Task { @MainActor in
            try? await Task.sleep(nanoseconds: 1_500_000_000)
            if !self.closed { self.openSocket() }
        }
    }

    func disconnect() {
        closed = true
        task?.cancel(with: .goingAway, reason: nil)
        task = nil
        session?.invalidateAndCancel()
        session = nil
        state = .idle
        error = nil
    }
}
