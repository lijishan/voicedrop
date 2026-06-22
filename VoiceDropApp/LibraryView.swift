import SwiftUI

/// "我的录音" — pulled up as a sheet from the record screen. White-card list of
/// recordings + their mining status. Warm-paper light theme.
struct LibraryView: View {
    var active: Bool = true

    @State private var store = LibraryStore()
    @State private var confirmDelete: Recording?
    @State private var confirmReprocess: Recording?   // long-press 已成文

    private var minedCount: Int { store.recordings.filter(\.hasArticles).count }
    private var subtitle: String { "共 \(store.recordings.count) 条 · \(minedCount) 篇已成文" }

    var body: some View {
        NavigationStack {
            VStack(alignment: .leading, spacing: 0) {
                VStack(alignment: .leading, spacing: 6) {
                    Text("我的录音").font(.system(size: 30, weight: .semibold)).foregroundStyle(Theme.ink)
                    Text(subtitle).font(.system(size: 14)).foregroundStyle(Theme.metaChrome)
                }
                .padding(.top, 26).padding(.horizontal, 24).padding(.bottom, 14)

                content
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
            .background(Theme.appBG.ignoresSafeArea())
            .toolbar(.hidden, for: .navigationBar)
        }
        .tint(Theme.accent)
        .task { await store.load() }
        .onChange(of: active) { _, nowActive in if nowActive { Task { await store.load() } } }
        .alert("删除这条录音？", isPresented: .init(
            get: { confirmDelete != nil }, set: { if !$0 { confirmDelete = nil } }
        ), presenting: confirmDelete) { rec in
            Button("删除", role: .destructive) { Task { await store.delete(rec) } }
            Button("取消", role: .cancel) {}
        } message: { _ in Text("音频和已挖出的文章都会从云端删除，不可恢复。") }
        .alert("重新处理这篇文章？", isPresented: .init(
            get: { confirmReprocess != nil }, set: { if !$0 { confirmReprocess = nil } }
        ), presenting: confirmReprocess) { rec in
            Button("删除文章并重新生成", role: .destructive) { Task { await store.deleteArticle(rec) } }
            Button("取消", role: .cancel) {}
        } message: { _ in Text("会删掉已生成的文章、保留录音，下个周期重新挖一遍。") }
    }

    @ViewBuilder private var content: some View {
        if store.loading && store.recordings.isEmpty {
            Spacer(); ProgressView().tint(Theme.accent).frame(maxWidth: .infinity); Spacer()
        } else if let err = store.error, store.recordings.isEmpty {
            Spacer(); message("加载失败", err); Spacer()
        } else if store.recordings.isEmpty {
            Spacer(); message("还没有录音", "录一条，过会儿服务器会自动转写并挖成文章。"); Spacer()
        } else {
            List {
                ForEach(store.recordings) { rec in
                    NavigationLink {
                        RecordingDetailView(store: store, recording: rec)
                    } label: {
                        rowCard(rec)
                    }
                    .listRowBackground(Color.clear)
                    .listRowSeparator(.hidden)
                    .listRowInsets(EdgeInsets(top: 6, leading: 16, bottom: 6, trailing: 16))
                    .swipeActions(edge: .trailing, allowsFullSwipe: false) {
                        Button(role: .destructive) { confirmDelete = rec } label: { Label("删除", systemImage: "trash") }
                            .tint(.red)
                    }
                }
            }
            .listStyle(.plain)
            .scrollContentBackground(.hidden)
            .refreshable { await store.load() }
        }
    }

    private func rowCard(_ rec: Recording) -> some View {
        let empty = rec.isEmpty
        return HStack(spacing: 14) {
            RoundedRectangle(cornerRadius: Theme.R.card)
                .fill(empty ? Color(hex: "F1ECE3") : Theme.tileWarm)
                .frame(width: 44, height: 44)
                .overlay(WaveformBars(color: empty ? Color(hex: "C3B9A8") : Theme.accent,
                                      heights: [8, 16, 26, 14, 9], barWidth: 3, spacing: 2.5))

            VStack(alignment: .leading, spacing: 5) {
                Text(rec.displayTitle).font(.system(size: 16, weight: .semibold)).foregroundStyle(Theme.ink)
                    .lineLimit(1).truncationMode(.tail)
                HStack(spacing: 10) {
                    if let d = rec.durationLabel {
                        Text(d).font(.system(size: 13).monospacedDigit()).foregroundStyle(Theme.metaChrome)
                    }
                    statusBadge(rec)
                }
            }
            Spacer(minLength: 6)
            Image(systemName: "chevron.right").font(.system(size: 12, weight: .semibold)).foregroundStyle(Theme.chevron)
        }
        .padding(.vertical, 15).padding(.horizontal, 16)
        .background(Theme.card, in: RoundedRectangle(cornerRadius: Theme.R.card))
        .overlay(RoundedRectangle(cornerRadius: Theme.R.card).stroke(Theme.borderChrome, lineWidth: 1))
        .cardChromeShadow()
        .opacity(empty ? 0.72 : 1)
    }

    @ViewBuilder private func statusBadge(_ rec: Recording) -> some View {
        if rec.hasArticles {
            badge(dot: Theme.greenDone, text: "已成文", color: Theme.greenDone)
                .contentShape(Rectangle())
                .onLongPressGesture { confirmReprocess = rec }
        } else if rec.isEmpty {
            badge(dot: Theme.faint, text: "无语音", color: Theme.faint)
        } else {
            badge(dot: Theme.amberPending, text: "待处理", color: Theme.amberPending)
        }
    }

    private func badge(dot: Color, text: String, color: Color) -> some View {
        HStack(spacing: 5) {
            Circle().fill(dot).frame(width: 6, height: 6)
            Text(text).font(.system(size: 12.5)).foregroundStyle(color)
        }
    }

    private func message(_ title: String, _ subtitle: String) -> some View {
        VStack(spacing: 10) {
            Text(title).foregroundStyle(Theme.ink).font(.system(size: 17, weight: .semibold))
            Text(subtitle).foregroundStyle(Theme.secondary).font(.system(size: 15))
                .multilineTextAlignment(.center).padding(.horizontal, 40)
        }
        .frame(maxWidth: .infinity)
    }
}
