import Foundation
import Observation

/// Maintains a persistent WebSocket to wss://jianshuo.dev/agent/status and
/// delivers real-time mining status updates to the app. mine.py pushes a
/// notification when it starts or finishes processing a recording, so the UI
/// can flip between 待处理 / 处理中 / 已成文 without polling.
@MainActor
@Observable
final class StatusSession {
    var onProcessing: ((String) -> Void)?   // stem that started processing
    var onDone: ((String) -> Void)?         // stem that finished (ready or empty)

    private var task: URLSessionWebSocketTask?
    private var urlSession: URLSession?
    private var closed = false

    private let base = "wss://jianshuo.dev/agent/status"

    func connect() {
        guard task == nil else { return }   // already connected or connecting
        closed = false
        open()
    }

    private func open() {
        let token = AuthStore.shared.bearer
        guard !token.isEmpty, let url = URL(string: base) else { return }
        var req = URLRequest(url: url)
        req.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        let s = URLSession(configuration: .default)
        urlSession = s
        let t = s.webSocketTask(with: req)
        task = t
        t.resume()
        receive()
    }

    private func receive() {
        task?.receive { [weak self] result in
            Task { @MainActor in
                guard let self, !self.closed else { return }
                switch result {
                case .failure:
                    self.reconnect()
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
              (obj["type"] as? String) == "status_update",
              let stem = obj["stem"] as? String,
              let status = obj["status"] as? String else { return }
        switch status {
        case "processing": onProcessing?(stem)
        case "ready", "empty": onDone?(stem)
        default: break
        }
    }

    private func reconnect() {
        task?.cancel(with: .goingAway, reason: nil)
        task = nil
        urlSession?.invalidateAndCancel()
        urlSession = nil
        guard !closed else { return }
        Task { @MainActor in
            try? await Task.sleep(nanoseconds: 3_000_000_000)
            if !self.closed { self.open() }
        }
    }

    func disconnect() {
        closed = true
        task?.cancel(with: .goingAway, reason: nil)
        task = nil
        urlSession?.invalidateAndCancel()
        urlSession = nil
    }
}
