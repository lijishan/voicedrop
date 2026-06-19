#!/usr/bin/env python3
"""VoiceDrop server-side miner (v1).

For every VoiceDrop-*.m4a in R2 (jianshuo.dev/files) that has no matching
article yet:  download -> Volcano ASR -> Claude (王建硕 voice) -> write the
article JSON back next to the audio, under  <prefix>/articles/<stem>.json
so the app can pull it. No WeChat. Idempotent: audio that already has an
article is skipped, so re-runs are safe and cheap.

Env:
  FILES_TOKEN            master/admin token for jianshuo.dev/files
  CLAUDE_API_KEY         Anthropic API key (ANTHROPIC_API_KEY also accepted)
  VOLC_ASR_APPID         huo shan ASR app id
  VOLC_ASR_ACCESS_TOKEN  huo shan ASR access token
  MINE_MODEL             optional, default claude-sonnet-4-6
  MINE_DRY              if set, list what WOULD be mined and exit (no ASR/LLM)
"""
import os, sys, json, subprocess, tempfile, urllib.request
from urllib.parse import quote

BASE = "https://jianshuo.dev/files/api"
TOKEN = os.environ["FILES_TOKEN"]
CLAUDE_KEY = os.environ.get("CLAUDE_API_KEY") or os.environ.get("ANTHROPIC_API_KEY", "")
MODEL = os.environ.get("MINE_MODEL", "claude-sonnet-4-6")
DRY = bool(os.environ.get("MINE_DRY"))
HERE = os.path.dirname(os.path.abspath(__file__))

SYSTEM = """你是王建硕，在写自己的微信公众号文章。下面给你一段你自己的口述录音转写，把它整理成一篇可发布的公众号文章。

语气 DNA（必须遵守）：
- 胸有成竹地下断言，不绕弯、不加「我觉得可能也许」的缓冲。
- 不讲故事、不铺垫，直接给结论再给理由；开头一句就立住，绝不用小白式提问钩子。
- 第一人称用「我」，绝不用「笔者」。称呼 AI / Claude 一律用「他」，不用「它」。
- 多用「我 / 他」起句，少用「这里会有…」这类无人称、物称句。
- 细节能列就用表格 / 列表，不在叙述句里堆细节。
- 保留口语词（吧 / 呢 / 啊 / 了）、自造词、家常比喻——这是你的声音，别改成书面语。
- 不加 AI 味连接词（首先 / 其次 / 综上所述 / 值得注意的是），不加 emoji。
- 默认 800–1000 字。中英文之间留一个空格（盘古之白）。
- 只用转写里出现的事实，绝不编造。不提任何公司具体名字，需要时用「我们公司」。

只输出一个 JSON 对象：{"title": "标题", "body": "正文 markdown"}，不要输出任何其它文字。"""


# Always go direct (no proxy) with a normal UA: Cloudflare 403s the default
# python-urllib UA, and a local clash/VPN proxy breaks jianshuo.dev. CI has no
# proxy, so this is correct everywhere.
_OPENER = urllib.request.build_opener(urllib.request.ProxyHandler({}))


def _req(method, url, data=None, headers=None):
    h = dict(headers or {})
    h.setdefault("User-Agent", "voicedrop-miner/1")
    req = urllib.request.Request(url, data=data, method=method, headers=h)
    with _OPENER.open(req, timeout=300) as r:
        return r.read()


def api_list():
    raw = _req("GET", f"{BASE}/list", headers={"Authorization": f"Bearer {TOKEN}"})
    return json.loads(raw).get("files", [])


def api_download(key, dest):
    raw = _req("GET", f"{BASE}/download/{quote(key)}",
               headers={"Authorization": f"Bearer {TOKEN}"})
    with open(dest, "wb") as f:
        f.write(raw)


def api_put(key, body_bytes, content_type):
    _req("PUT", f"{BASE}/upload/{quote(key)}", data=body_bytes,
         headers={"Authorization": f"Bearer {TOKEN}", "Content-Type": content_type})


def article_key_for(audio_key):
    # users/anon-x/VoiceDrop-Y.m4a -> users/anon-x/articles/VoiceDrop-Y.json
    # VoiceDrop-Y.m4a (flat)       -> articles/VoiceDrop-Y.json
    parts = audio_key.rsplit("/", 1)
    stem = parts[-1][:-4]  # strip .m4a
    prefix = parts[0] + "/" if len(parts) == 2 else ""
    return f"{prefix}articles/{stem}.json"


def transcribe(audio_path):
    out = audio_path + ".asr.json"
    subprocess.run([sys.executable, os.path.join(HERE, "volc_asr_stream.py"),
                    audio_path, out], check=True)
    res = json.load(open(out)).get("result", {})
    text = res.get("text") or "".join(u.get("text", "") for u in res.get("utterances", []))
    return text.strip()


def generate_article(transcript):
    payload = {
        "model": MODEL, "max_tokens": 4000, "system": SYSTEM,
        "messages": [{"role": "user", "content": f"口述转写：\n\n{transcript}"}],
    }
    raw = _req("POST", "https://api.anthropic.com/v1/messages",
               data=json.dumps(payload).encode(),
               headers={"x-api-key": CLAUDE_KEY, "anthropic-version": "2023-06-01",
                        "content-type": "application/json"})
    resp = json.loads(raw)
    text = "".join(b.get("text", "") for b in resp.get("content", [])
                   if b.get("type") == "text").strip()
    if text.startswith("```"):                       # strip code fences
        text = text.strip("`")
        text = text[4:].strip() if text.lower().startswith("json") else text
    try:
        obj = json.loads(text)
        return obj.get("title", "(no title)"), obj.get("body", "")
    except Exception:
        lines = [l for l in text.splitlines() if l.strip()]
        return (lines[0][:40] if lines else "(no title)"), text


def main():
    files = api_list()
    names = {f["name"] for f in files}
    uploaded = {f["name"]: f.get("uploaded", "") for f in files}
    audios = [f["name"] for f in files
              if f["name"].rsplit("/", 1)[-1].startswith("VoiceDrop-")
              and f["name"].endswith(".m4a")]
    todo = [a for a in audios if article_key_for(a) not in names]
    print(f"{len(audios)} audio total, {len(todo)} to mine")
    if DRY:
        for a in todo:
            print(f"  would mine: {a} -> {article_key_for(a)}")
        return
    if not CLAUDE_KEY:
        sys.exit("CLAUDE_API_KEY not set")

    done = 0
    for audio in todo:
        try:
            with tempfile.TemporaryDirectory() as td:
                local = os.path.join(td, os.path.basename(audio))
                api_download(audio, local)
                transcript = transcribe(local)
                if not transcript:
                    print(f"  skip (empty transcript): {audio}")
                    continue
                title, body = generate_article(transcript)
                art = {
                    "id": os.path.basename(audio)[:-4],
                    "title": title,
                    "createdAt": uploaded.get(audio, ""),
                    "sourceAudio": os.path.basename(audio),
                    "transcript": transcript,
                    "body": body,
                    "status": "ready",
                }
                api_put(article_key_for(audio),
                        json.dumps(art, ensure_ascii=False).encode(),
                        "application/json")
                done += 1
                print(f"  mined: {audio} -> {article_key_for(audio)}  [{title}]")
        except Exception as e:
            print(f"  FAILED {audio}: {e}", file=sys.stderr)
    print(f"done: {done} new article(s)")


if __name__ == "__main__":
    main()
