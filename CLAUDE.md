每次开始工作前先读 STATE.md，了解现状

# 测试规则
任何改动之前，先跑测试确认 pass：
```
cd ~/code/jianshuo.dev/agent && npm test
```
改动完成后也要跑一遍，确保没有回归。测试文件在 `agent/test/`，覆盖 article store、API 路由、tools、loop。
当作了大型的更改以后，及时更新STATE.md，把需要以后的Agent注意的内容写进去
PUSH的操作谨慎，当我要求的时候再PUSH，以为PUSH一次就会产生一次TESTFLIGHT的BUILD，每天太多次（20次）会被AppStore限制。
因为用xcodegen，当产生新的文件的时候帮我跑一下

