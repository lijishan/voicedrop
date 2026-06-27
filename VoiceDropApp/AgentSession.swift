import Foundation
import Observation

enum AgentState: Equatable { case idle, connecting, working, error }

/// A 320×320 thumbnail to send alongside a voice instruction so the model
/// can see the image content and decide where to place it.
struct AgentImage: Equatable {
    let key: String      // relative R2 key, e.g. "photos/2026-06-25-131500/ts.jpg"
    let base64: String   // base64-encoded JPEG
}

/// A live WebSocket conversation with the article-editing Agent (Cloudflare
/// Durable Object behind wss://jianshuo.dev/agent/edit). You `connect` once for a
/// recording, `send` spoken instructions, and each time the server finishes
/// rewriting it pushes the new article doc back — delivered via `onUpdate` so the
/// detail view can reload it in place. The socket stays open for an unbounded
/// back-and-forth; `disconnect` ends it.
@MainActor
@Observable
final class ArticleAgentSession {
    /// One queued spoken instruction, optionally with image thumbnails attached.
    struct EditRequest: Identifiable, Equatable {
        let id = UUID()
        let text: String
        let images: [AgentImage]   // empty for text-only edits
    }

    var state: AgentState = .idle
    var error: String?

    /// Outstanding edits. `queue.first` is the one in flight once `processing`;
    /// the rest are waiting their turn. Drives the stacked queue UI.
    var queue: [EditRequest] = []
    private var processing = false

    /// Called on the main actor whenever the server pushes a rewritten article.
    var onUpdate: ((ArticleDoc) -> Void)?

    /// Called on the main actor when the agent sends a one-line reply (text + ok).
    /// Display-only — does not affect the edit queue.
    var onReply: ((String, Bool) -> Void)?

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
        req.setBearer(token)
        let s = URLSession(configuration: .default)
        session = s
        let t = s.webSocketTask(with: req)
        task = t
        t.resume()
        state = .idle               // URLSession buffers sends until the socket opens
        receive()
        if !queue.isEmpty { processing = false; pump() }   // resume after a reconnect
    }

    /// Queue a spoken instruction, optionally with image thumbnails so the model
    /// can see the photos and decide where to insert them.
    /// Edits run strictly serially — each builds on the previous result.
    func enqueue(_ instruction: String, images: [AgentImage] = []) {
        let text = instruction.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !text.isEmpty else { return }
        queue.append(EditRequest(text: text, images: images))
        pump()
    }

    /// Send the head of the queue, but only if nothing is in flight.
    private func pump() {
        guard !processing, let head = queue.first, let task else { return }
        processing = true
        state = .working
        error = nil
        var payload: [String: Any] = ["type": "instruct", "text": head.text]
        if !head.images.isEmpty {
            payload["images"] = head.images.map { ["key": $0.key, "data": $0.base64, "mediaType": "image/jpeg"] }
        }
        guard let data = try? JSONSerialization.data(withJSONObject: payload),
              let str = String(data: data, encoding: .utf8) else { return }
        task.send(.string(str)) { [weak self] err in
            guard let err else { return }
            Task { @MainActor in self?.failHead(err.localizedDescription) }
        }
    }

    /// The in-flight edit failed: surface it, drop the head, keep draining the rest.
    private func failHead(_ message: String) {
        error = message
        if processing, !queue.isEmpty { queue.removeFirst() }
        processing = false
        if queue.isEmpty { state = .error } else { pump() }
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
            if processing, !queue.isEmpty { queue.removeFirst() }   // this edit is done
            processing = false
            state = queue.isEmpty ? .idle : .working
            pump()                                                  // start the next, if any
        case "reply":
            if let text = obj["text"] as? String, !text.isEmpty {
                let ok = obj["ok"] as? Bool ?? true
                onReply?(text, ok)
            }
        case "error":
            failHead((obj["message"] as? String) ?? "出错了")
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
        queue.removeAll()
        processing = false
        state = .idle
        error = nil
    }
}
