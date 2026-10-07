# Local HFR control reproduction

Use FFmpeg with libx265. Construct red and blue 3840×2160 inputs at60fps for12s;interleave by PTS into120fps. A local font supplied frame numbers;fonts/media stay ignored. Relevant filter:

```text
[0:v]settb=1/120,setpts=2*N[r];
[1:v]settb=1/120,setpts=2*N+1[b];
[r][b]interleave=nb_inputs=2,drawtext=fontfile=FONT:text='%{n}':x=80:y=80:fontsize=120:fontcolor=white[v]
```

Inputs: `color=c=red:s=3840x2160:r=60:d=12` and `color=c=blue:s=3840x2160:r=60:d=12`. Output arguments: `-map "[v]" -r 120 -fps_mode cfr -c:v libx265 -preset ultrafast -crf 26 -pix_fmt yuv420p -x265-params pools=8:frame-threads=2:keyint=120:bframes=0 -t 12`. Encoder/font versions change bytes;verify decoded content independently of the historical hash.

Decode every frame with showinfo and a2×2 center crop to RGB24. Count frame indices/PTS and red-vs-blue dominance. Required:1440frames,720each,1439alternations,positive PTS deltas around1/120s. Container fps alone is insufficient.

Push source to /data/local/tmp/processing/. The host UID cannot read that location directly here;copy only this control into the existing Bili cache as hfr-control.mp4,set app ownership/permissions and restorecon. This is the documented artifact-path exception,not a production input path.

With DEBUG part-fix enabled:

```text
am start -n tv.danmaku.bili/.MainActivityV2 --ez bili_hfr_control true --ez bili_hfr_hardware true
```

The explicit intent opens a temporary dialog/host IjkMediaPlayer and consumes the extra. Normal UGC requests/resources stay native. Completion,pause,destroy,dismiss,surface destruction or20s timeout releases the player. Release contains no intent entry. Input is trusted local test media.

Select exact BLAST SurfaceView layer from dumpsys SurfaceFlinger --list. Query --latency "LAYER" at4s and8s. Discard0/sentinel timestamps,deduplicate column2,and calculate median interval and(N-1)/elapsed. Repeat without input and with8s sustained touch on the blank dialog. Hold source,decoder settings andScene configuration fixed. Choreographer/PixelCopy callbacks are not displayed-frame counts.

AOSP definitions: [FrameTracker.cpp](https://android.googlesource.com/platform/frameworks/native/+/refs/heads/main/services/surfaceflinger/FrameTracker.cpp). OEM ring length differs;row-count subtraction is not a drop counter. This tests host decoder/surface behavior,not original BV source cadence.
