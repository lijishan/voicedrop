import SwiftUI
import AuthenticationServices

/// The record screen — the app's root. One screen, one state machine.
/// requesting -> idle -> recording -> uploading -> done | failed (denied for perms).
/// "暖纸 · Warm Paper" light theme. Settings pushes from the gear; 我的录音 pulls up.
struct ContentView: View {

    enum Phase: Equatable {
        case needsSignIn         // unreachable while requireAppleSignIn == false
        case requesting
        case denied
        case idle
        case recording
        case uploading
        case done
        case failed(String)
    }

    @State private var recorder = AudioRecorder()
    @State private var uploader = Uploader()
    @State private var location = LocationTagger()
    @State private var authStore = AuthStore.shared
    @State private var phase: Phase = .idle

    @State private var showSettings = false
    @State private var showLibrary = false

    // Zero-login via anonymous iCloud-Keychain token. SiwA code stays wired up.
    private let requireAppleSignIn = false
    @Environment(\.scenePhase) private var scenePhase

    var body: some View {
        ZStack(alignment: .bottom) {
            Theme.appBG.ignoresSafeArea()
            VStack(spacing: 0) {
                topBar
                Spacer(minLength: 0)
                content
                Spacer(minLength: 0)
                Color.clear.frame(height: 120)   // keep content clear of the handle
            }
            handleCard.ignoresSafeArea(edges: .bottom)
        }
        .toolbar(.hidden, for: .navigationBar)
        .navigationDestination(isPresented: $showSettings) { SettingsView() }
        .sheet(isPresented: $showLibrary) {
            LibraryView()
                .presentationDetents([.large])
                .presentationDragIndicator(.visible)
        }
        .task { await begin() }
        .onChange(of: scenePhase) { _, newValue in
            guard newValue == .active, phase != .recording, phase != .uploading else { return }
            Task { await drainQueue() }
        }
        .onAppear { recorder.onInterrupted = { take in Task { await self.finalize(take) } } }
    }

    // MARK: - Chrome

    private var topBar: some View {
        HStack {
            HStack(spacing: 8) {
                WaveformBars(heights: [8, 14, 18, 11, 7], barWidth: 2.5, spacing: 2)
                Text("VoiceDrop")
                    .font(.system(size: 15, weight: .semibold))
                    .tracking(2)
                    .foregroundStyle(Theme.ink)
            }
            Spacer()
            NavSquare(systemName: "gearshape") { showSettings = true }
                .accessibilityLabel("设置")
        }
        .padding(.horizontal, 28)
        .padding(.top, 8)
        .padding(.bottom, 4)
    }

    private var handleCard: some View {
        Button { showLibrary = true } label: {
            VStack(spacing: 10) {
                Capsule().fill(Color(hex: "E0D6C6")).frame(width: 36, height: 4)
                HStack(spacing: 12) {
                    RoundedRectangle(cornerRadius: Theme.R.tile)
                        .fill(Theme.tileWarm)
                        .frame(width: 38, height: 38)
                        .overlay(WaveformBars(heights: [7, 12, 16, 10, 6], barWidth: 2.5, spacing: 2))
                    VStack(alignment: .leading, spacing: 2) {
                        Text("我的录音").font(.system(size: 16, weight: .semibold)).foregroundStyle(Theme.ink)
                        Text(handleSubtitle).font(.system(size: 12.5)).foregroundStyle(Theme.secondary)
                    }
                    Spacer()
                    Image(systemName: "chevron.up")
                        .font(.system(size: 13, weight: .semibold)).foregroundStyle(Theme.chevron)
                }
            }
            .padding(.top, 12).padding(.horizontal, 20).padding(.bottom, 30)
            .frame(maxWidth: .infinity)
            .background(Theme.card, in: UnevenRoundedRectangle(topLeadingRadius: 14, topTrailingRadius: 14))
            .overlay(alignment: .top) { Rectangle().fill(Theme.borderChrome).frame(height: 1) }
            .shadow(color: Color(.sRGB, red: 180/255, green: 140/255, blue: 100/255, opacity: 0.07), radius: 10, x: 0, y: -4)
        }
        .buttonStyle(.plain)
        .highPriorityGesture(
            DragGesture(minimumDistance: 10).onEnded { if $0.translation.height < -20 { showLibrary = true } }
        )
    }

    private var handleSubtitle: String {
        uploader.pendingCount > 0
            ? "\(uploader.pendingCount) 条待上传 · 已同步 iCloud"
            : "查看全部录音与文章"
    }

    // MARK: - Phase content

    @ViewBuilder private var content: some View {
        switch phase {
        case .needsSignIn, .idle:
            readyScreen(done: false)

        case .requesting:
            ProgressView().tint(Theme.accent)

        case .denied:
            messageScreen(title: "需要麦克风权限",
                          subtitle: "VoiceDrop 要用麦克风录音。",
                          actionTitle: "去设置", action: openSettings)

        case .recording:
            VStack(spacing: 46) {
                Text(timeString(recorder.elapsed))
                    .font(.system(size: 64, weight: .light, design: .monospaced))
                    .foregroundStyle(Theme.ink)
                    .contentTransition(.numericText())
                recordKey(title: "停止") { Task { await stopAndUpload() } }
            }

        case .uploading:
            VStack(spacing: 16) {
                ProgressView().tint(Theme.accent).scaleEffect(1.3)
                Text("上传中…").foregroundStyle(Theme.secondary).font(.system(size: 16))
            }

        case .done:
            readyScreen(done: true)

        case .failed(let msg):
            VStack(spacing: 22) {
                Image(systemName: "exclamationmark.triangle")
                    .font(.system(size: 40)).foregroundStyle(Theme.amberPending)
                Text(msg).foregroundStyle(Theme.ink).font(.system(size: 16))
                    .multilineTextAlignment(.center).padding(.horizontal, 40)
                Text("录音已存好，会自动重传。").foregroundStyle(Theme.secondary).font(.system(size: 13))
                accentButton("再录一条") { startRecording() }
            }
        }
    }

    private func readyScreen(done: Bool) -> some View {
        VStack(spacing: 46) {
            if done {
                VStack(spacing: 10) {
                    Image(systemName: "checkmark.circle.fill")
                        .font(.system(size: 46)).foregroundStyle(Theme.greenDone)
                    Text("已上传").foregroundStyle(Theme.secondary).font(.system(size: 17))
                }
            } else {
                Text("准备好，随时记录").font(.system(size: 18)).foregroundStyle(Theme.metaChrome)
            }
            recordKey(title: done ? "再录一条" : "开始录音") { startRecording() }
        }
    }

    /// White rounded-square key (100×100, r10) with a red rounded-square inside
    /// (54×54, r6). Same shape for record and stop — only the label differs.
    private func recordKey(title: String, action: @escaping () -> Void) -> some View {
        VStack(spacing: 14) {
            Button(action: action) {
                RoundedRectangle(cornerRadius: Theme.R.recordOuter)
                    .fill(Theme.card)
                    .overlay(RoundedRectangle(cornerRadius: Theme.R.recordOuter).stroke(Color(hex: "E4DACA"), lineWidth: 1))
                    .overlay(
                        RoundedRectangle(cornerRadius: Theme.R.recordInner)
                            .fill(Theme.accent)
                            .frame(width: 54, height: 54)
                            .shadow(color: Color(.sRGB, red: 216/255, green: 89/255, blue: 59/255, opacity: 0.30), radius: 6, x: 0, y: 4)
                    )
                    .frame(width: 100, height: 100)
                    .shadow(color: Color(.sRGB, red: 180/255, green: 120/255, blue: 90/255, opacity: 0.13), radius: 9, x: 0, y: 6)
            }
            .buttonStyle(.plain)
            .accessibilityLabel(title)
            Text(title).foregroundStyle(Theme.secondary).font(.system(size: 15)).tracking(1)
        }
    }

    private func messageScreen(title: String, subtitle: String,
                               actionTitle: String, action: @escaping () -> Void) -> some View {
        VStack(spacing: 16) {
            Text(title).foregroundStyle(Theme.ink).font(.system(size: 22, weight: .semibold))
            Text(subtitle).foregroundStyle(Theme.secondary).font(.system(size: 16))
                .multilineTextAlignment(.center).padding(.horizontal, 40)
            accentButton(actionTitle, action: action).padding(.top, 4)
        }
    }

    private func accentButton(_ title: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(title)
                .font(.system(size: 15, weight: .semibold)).foregroundStyle(.white)
                .padding(.vertical, 13).padding(.horizontal, 22)
                .background(Theme.accent, in: RoundedRectangle(cornerRadius: Theme.R.primary))
                .accentButtonShadow()
        }
        .buttonStyle(.plain)
    }

    // MARK: - Flow (unchanged logic; iCloud archive now respects the pref)

    private func begin() async {
        if requireAppleSignIn && !authStore.isAuthenticated { phase = .needsSignIn; return }
        let granted = await AudioRecorder.ensurePermission()
        guard granted else { phase = .denied; return }
        location.start()
        AudioRecorder.cleanupStaleStaging()
        phase = .idle
        Task { await drainQueue() }
    }

    private func startRecording() {
        do { try recorder.start(); phase = .recording }
        catch { phase = .failed("无法开始录音：\(error.localizedDescription)") }
    }

    private func stopAndUpload() async {
        guard let take = recorder.stop() else { phase = .done; return }
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
            if (try? FileManager.default.moveItem(at: take.url, to: basicURL)) != nil {
                toUpload = basicURL
            }
        }
        // Mirror to iCloud Drive before upload — only if the user keeps backup on.
        if Prefs.shared.iCloudBackup {
            let toArchive = toUpload
            await Task.detached { ICloudArchive.save(toArchive) }.value
        }
        let ok = await uploader.upload(toUpload)
        phase = ok ? .done : .failed(uploader.lastError ?? "上传失败")
    }

    private func drainQueue() async {
        guard !authStore.bearer.isEmpty, uploader.pendingCount > 0 else { return }
        _ = await uploader.drainPending()
    }

    /// Sign-in-with-Apple stays wired but unused (zero-login). Kept for the day
    /// Apple's AKAuthenticationError -7074 is resolved and requireAppleSignIn flips.
    private func handleSignIn(_ result: Result<ASAuthorization, Error>) {
        switch result {
        case .success(let auth):
            guard
                let cred = auth.credential as? ASAuthorizationAppleIDCredential,
                let tokenData = cred.identityToken,
                let token = String(data: tokenData, encoding: .utf8)
            else { authStore.lastError = "无法获取 Apple 凭证"; return }
            phase = .requesting
            Task {
                await authStore.exchange(identityToken: token)
                if authStore.isAuthenticated { await begin() } else { phase = .idle }
            }
        case .failure(let error):
            authStore.lastError = error.localizedDescription
        }
    }

    private func openSettings() {
        if let url = URL(string: UIApplication.openSettingsURLString) { UIApplication.shared.open(url) }
    }

    private func timeString(_ t: TimeInterval) -> String {
        let total = Int(t)
        return String(format: "%02d:%02d", total / 60, total % 60)
    }
}

#Preview { RootView() }
