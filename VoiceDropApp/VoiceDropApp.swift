import SwiftUI

@main
struct VoiceDropApp: App {
    @StateObject private var router = AppRouter.shared   // shared so App Intents (开始录音) can reach it
    // APNs 注册 + device token 上传（「文章已生成」推送 / 运维报警都靠它）。
    @UIApplicationDelegateAdaptor(PushRegistrar.self) private var pushRegistrar

    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(router)
                .onOpenURL { router.handle($0) }   // voicedrop://<page> + universal links — see AppRouter/DeepLink
                // Universal links (https://voicedrop.cn/…) arrive as an NSUserActivity;
                // some iOS versions deliver ONLY here, not via onOpenURL. Same handler.
                .onContinueUserActivity(NSUserActivityTypeBrowsingWeb) { activity in
                    if let url = activity.webpageURL { router.handle(url) }
                }
        }
    }
}
