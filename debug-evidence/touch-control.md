# 后台模拟触摸对照

2026-10-07，7420400，两个模块启用。Scene配置不变；未设置Window/Surface刷新率提示。此实验脚本独立于模块，仅用于验证显示调度。

先打开DEBUG本地4K120控制器，再在手机后台运行以下shell循环。示例坐标适用于本机1272×2800竖屏的空白区域；使用前根据实际界面选择位置。只在Bili拥有焦点时注入，最长180秒；移除运行标记即可提前停止。

```sh
base=/data/local/tmp/processing
touch "$base/touch-keepalive.run"
started=$(date +%s)
while [ -f "$base/touch-keepalive.run" ]; do
    now=$(date +%s)
    [ $((now-started)) -ge 180 ] && break
    focus=$(dumpsys window displays | grep mCurrentFocus)
    case "$focus" in
        *tv.danmaku.bili/*)
            input touchscreen swipe 636 1600 637 1601 180
            ;;
    esac
    sleep 0.10
done
rm -f "$base/touch-keepalive.run"
```

将脚本放在processing目录，以`nohup sh SCRIPT > /data/local/tmp/processing/touch-keepalive.log 2>&1 < /dev/null &`启动。停止命令：`rm -f /data/local/tmp/processing/touch-keepalive.run`。没有开机启动或持久服务。实验期间没有触发点赞/投币/关注/评论，界面和原生进度均复核。

只注入MOVE而不建立按下/抬起序列时，本地源仍约60.35fps。短触摸序列时，本地源单Surface actual-present三次窗口为120.703、117.862、115.994Hz，中位间隔约8.284ms；同一播放器解码/output估算接近120，app refreshRate为120。脚本停止后的独立对照回到60.358Hz，中位间隔约16.568ms。

目标BV1P5411Q7Cj的20秒后台输入实验中，system renderFrameRate三次均为120.00001。请求qn120仍仅获得可播放1080P HEVC、声明30.303fps，原生打开URL指纹808836c3a7b4f2d4；decode/output估算约29–32fps。停止后4秒，physical mode1仍约120Hz而renderFrameRate回到60。此结果验证输入能解除当前显示调度限制，但不能证明目标已播放4K120。

输入命令、焦点查询有执行成本；没有测量长期功耗，也没有把此脚本作为生产修复。本轮测试结束后运行标记、PID文件和后台循环均已退出，脚本保留供复现。
