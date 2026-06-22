import SwiftUI

/// Full-screen recording takeover (方案二): launched from the red record key on
/// the 我的录音 list. Starts recording on appear → big stopwatch + live waveform
/// → stop → upload ring → returns to the list. The Phase machine + recorder /
/// uploader / location logic is unchanged from the old root screen.
struct RecordSession: View {
    /// Called to dismiss back to the list (after upload, on 在后台上传, or cancel).
    var onFinish: () -> Void

    enum Phase: Equatable { case starting, denied, recording, uploading, done, failed(String) }

    @State private var recorder = AudioRecorder()
    @State private var uploader = Uploader()
    @State private var location = LocationTagger()
    @State private var phase: Phase = .starting
    @State private var recordedLabel = "00:00"
    @State private var spin = false

    var body: some View {
        ZStack {
            Theme.appBG.ignoresSafeArea()
            switch phase {
            case .starting:
                ProgressView().tint(Theme.recordRed)
            case .denied:
                messageScreen(title: "需要麦克风权限", subtitle: "VoiceDrop 要用麦克风录音。", primary: "去设置") { openSettings() }
            case .recording:
                recordingScreen
            case .uploading:
                uploadingScreen
            case .done:
                ProgressView().tint(Theme.recordRed)
            case .failed(let msg):
                messageScreen(title: "上传未完成", subtitle: msg + "\n录音已存好，会自动重传。", primary: "好") { onFinish() }
            }
        }
        .task {
            recorder.onInterrupted = { take in Task { await finalize(take) } }
            let granted = await AudioRecorder.ensurePermission()
            guard granted else { phase = .denied; return }
            location.start()
            do { try recorder.start(); phase = .recording }
            catch { phase = .failed("无法开始录音：\(error.localizedDescription)") }
        }
        .onDisappear { _ = recorder.stop() }
    }

    // MARK: Recording (frame ②)

    private var recordingScreen: some View {
        VStack(spacing: 0) {
            HStack(spacing: 8) {
                Circle().fill(Theme.recordRed).frame(width: 9, height: 9)
                Text("正在录音").font(.system(size: 14)).tracking(2).foregroundStyle(Theme.secondary)
            }
            .padding(.top, 64)

            Spacer()
            VStack(spacing: 34) {
                Text(timeString(recorder.elapsed))
                    .font(.system(size: 78, weight: .ultraLight).monospacedDigit())
                    .foregroundStyle(Theme.ink)
                    .contentTransition(.numericText())
                waveform
            }
            Spacer()

            VStack(spacing: 7) {
                Button { Task { await stopAndUpload() } } label: {
                    Circle().fill(Theme.card).frame(width: 66, height: 66)
                        .overlay(Circle().stroke(Color(hex: "E8DECF"), lineWidth: 1))
                        .overlay(RoundedRectangle(cornerRadius: 6).fill(Theme.recordRed).frame(width: 26, height: 26))
                        .shadow(color: .black.opacity(0.06), radius: 7, x: 0, y: 4)
                }
                .buttonStyle(.plain).accessibilityLabel("停止")
                Text("点击停止").font(.system(size: 12)).tracking(1).foregroundStyle(Theme.secondary)
            }
            .padding(.bottom, 26)
        }
    }

    /// 13 bars, heights driven by the live input level (text-free, no fake data).
    private var waveform: some View {
        let pattern: [Double] = [0.30, 0.56, 0.82, 0.48, 0.95, 0.65, 0.38, 0.74, 0.52, 0.86, 0.34, 0.62, 0.44]
        return HStack(alignment: .bottom, spacing: 3) {
            ForEach(pattern.indices, id: \.self) { i in
                let frac = pattern[i] * (0.22 + recorder.level * 0.95)
                RoundedRectangle(cornerRadius: 2)
                    .fill(barColor(frac))
                    .frame(width: 3, height: max(6, 46 * frac))
            }
        }
        .frame(height: 46)
        .animation(.easeOut(duration: 0.1), value: recorder.level)
    }

    private func barColor(_ frac: Double) -> Color {
        if frac > 0.6 { return Theme.recordRed }
        if frac > 0.3 { return Color(hex: "EBA89F") }
        return Color(hex: "E5C8C3")
    }

    // MARK: Uploading (frame ③)

    private var uploadingScreen: some View {
        VStack(spacing: 0) {
            Text("已录制 \(recordedLabel)").font(.system(size: 14)).tracking(2).foregroundStyle(Theme.secondary)
                .padding(.top, 64)
            Spacer()
            VStack(spacing: 26) {
                ZStack {
                    Circle().fill(Theme.card)
                        .overlay(Circle().stroke(Theme.borderChrome, lineWidth: 1))
                        .shadow(color: Color(.sRGB, red: 180/255, green: 140/255, blue: 100/255, opacity: 0.12), radius: 9, x: 0, y: 6)
                    Circle().stroke(Color(hex: "F0E8DA"), lineWidth: 3).padding(4)
                    Circle().trim(from: 0, to: 0.7)
                        .stroke(Theme.recordRed, style: StrokeStyle(lineWidth: 3, lineCap: .round))
                        .padding(4)
                        .rotationEffect(.degrees(spin ? 360 : 0))
                    Image(systemName: "arrow.up").font(.system(size: 20, weight: .semibold)).foregroundStyle(Theme.recordRed)
                }
                .frame(width: 88, height: 88)
                .onAppear { withAnimation(.linear(duration: 1).repeatForever(autoreverses: false)) { spin = true } }

                VStack(spacing: 5) {
                    Text("正在上传…").font(.system(size: 17, weight: .semibold)).foregroundStyle(Theme.ink)
                    Text("上传后将自动转写、成文").font(.system(size: 13)).foregroundStyle(Theme.metaChrome)
                }
            }
            Spacer()
            Button { onFinish() } label: {
                Text("在后台上传").font(.system(size: 15, weight: .semibold)).foregroundStyle(Theme.secondary)
                    .frame(maxWidth: .infinity).padding(.vertical, 14)
                    .background(Theme.card, in: RoundedRectangle(cornerRadius: Theme.R.primary))
                    .overlay(RoundedRectangle(cornerRadius: Theme.R.primary).stroke(Theme.borderChrome, lineWidth: 1))
            }
            .buttonStyle(.plain)
            .padding(.horizontal, 24).padding(.bottom, 30)
        }
    }

    private func messageScreen(title: String, subtitle: String, primary: String, action: @escaping () -> Void) -> some View {
        VStack(spacing: 16) {
            Text(title).font(.system(size: 22, weight: .semibold)).foregroundStyle(Theme.ink)
            Text(subtitle).font(.system(size: 16)).foregroundStyle(Theme.secondary)
                .multilineTextAlignment(.center).padding(.horizontal, 40)
            Button(action: action) {
                Text(primary).font(.system(size: 15, weight: .semibold)).foregroundStyle(.white)
                    .padding(.vertical, 13).padding(.horizontal, 24)
                    .background(Theme.recordRed, in: RoundedRectangle(cornerRadius: Theme.R.primary))
            }
            .buttonStyle(.plain).padding(.top, 4)
            Button("取消") { onFinish() }.tint(Theme.secondary)
        }
    }

    // MARK: Flow (unchanged logic)

    private func stopAndUpload() async {
        guard let take = recorder.stop() else { onFinish(); return }
        recordedLabel = timeString(take.duration)
        phase = .uploading
        await finalize(take)
    }

    private func finalize(_ take: AudioRecorder.Recording) async {
        phase = .uploading
        let place = await location.placeTag()
        let finalName = RecordingName.make(start: take.start, duration: take.duration, place: place)
        var toUpload = take.url
        let finalURL = AudioRecorder.documentsDir.appending(path: finalName)
        do {
            try FileManager.default.moveItem(at: take.url, to: finalURL)
            toUpload = finalURL
        } catch {
            let basicURL = AudioRecorder.documentsDir
                .appending(path: "VoiceDrop-\(RecordingName.timestamp(take.start)).m4a")
            if (try? FileManager.default.moveItem(at: take.url, to: basicURL)) != nil { toUpload = basicURL }
        }
        if Prefs.shared.iCloudBackup {
            let toArchive = toUpload
            await Task.detached { ICloudArchive.save(toArchive) }.value
        }
        let ok = await uploader.upload(toUpload)
        if ok { phase = .done; onFinish() }          // back to the list (it refreshes)
        else { phase = .failed(uploader.lastError ?? "上传失败") }
    }

    private func openSettings() {
        if let url = URL(string: UIApplication.openSettingsURLString) { UIApplication.shared.open(url) }
    }

    private func timeString(_ t: TimeInterval) -> String {
        let total = Int(t); return String(format: "%02d:%02d", total / 60, total % 60)
    }
}
