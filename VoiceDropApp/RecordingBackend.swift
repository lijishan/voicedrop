import Foundation

/// Shared interface so RecordSession can drive either recording backend without a
/// mid-session switch. Since 2026-07-08 the DEFAULT for every take is the
/// AVAudioEngine path (`RealtimeInterviewer` wrapping `EngineRecorder`), which can
/// toggle the AI 采访员 side-path on/off MID-RECORDING; `AudioRecorder` remains as
/// the `Prefs.classicRecorder` escape hatch (no 采访 key). The backend is chosen once
/// per session (pinned in RecordSession's @State) and never switched mid-take, so
/// recording is never interrupted. Both produce the same `AudioRecorder.Recording`
/// → identical promote/upload downstream.
@MainActor
protocol RecordingBackend: AnyObject {
    var isRecording: Bool { get }
    var elapsed: TimeInterval { get }
    var level: Double { get }
    var startDate: Date? { get }
    var onInterrupted: ((AudioRecorder.Recording) -> Void)? { get set }
    func start() throws
    @discardableResult func stop() -> AudioRecorder.Recording?
}
