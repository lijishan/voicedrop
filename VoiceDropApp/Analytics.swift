import Foundation
import PostHog

/// PostHog 产品分析。key 经 Secrets.xcconfig → Info.plist 注入；
/// 拿不到 key（本地没配 / CI secret 缺失）就整体不启用，App 行为不变。
enum Analytics {
    static func setup() {
        guard let key = Bundle.main.object(forInfoDictionaryKey: "PostHogAPIKey") as? String,
              key.hasPrefix("phc_") else { return }
        let config = PostHogConfig(apiKey: key, host: "https://us.i.posthog.com")
        #if DEBUG
        config.debug = true   // Xcode console 打印每条事件的捕获/上送日志
        #endif
        PostHogSDK.shared.setup(config)
    }
}
