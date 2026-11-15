import asyncio, json, re, shutil, subprocess
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont
import edge_tts
from .config import settings

def safe_name(value: str) -> str:
    return re.sub(r"[^a-zA-Z0-9-]+", "-", value).strip("-").lower()[:70] or "match"

def font(size: int):
    candidates = ["./assets/fonts/DejaVuSans-Bold.ttf", "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf",
                  "C:/Windows/Fonts/arialbd.ttf"]
    for candidate in candidates:
        try: return ImageFont.truetype(candidate, size)
        except OSError: pass
    return ImageFont.load_default()

def make_card(path: Path, size: tuple[int,int], title: str, subtitle: str, index: int):
    w,h=size
    img=Image.new("RGB", size, (9,22,39))
    d=ImageDraw.Draw(img)
    # Original, generated gradient/rings; no unlicensed broadcast footage.
    for y in range(h):
        c=int(18 + 30*y/h)
        d.line((0,y,w,y), fill=(8,c, min(65,c+20)))
    d.rectangle((int(w*.05),int(h*.08),int(w*.95),int(h*.09)), fill=(232,177,76))
    d.text((int(w*.07),int(h*.13)), "PREET SPORTS", font=font(max(22,int(w*.035))), fill=(245,195,102))
    d.text((int(w*.07),int(h*.34)), f"{index:02d}", font=font(max(24,int(w*.045))), fill=(120,155,183))
    # Wrap title by measured character width.
    max_chars=26 if h>w else 42
    words=title.split(); lines=[]; line=""
    for word in words:
        if len(line+" "+word)>max_chars and line: lines.append(line); line=word
        else: line=(line+" "+word).strip()
    if line: lines.append(line)
    y=int(h*.43)
    for part in lines[:4]:
        d.text((int(w*.07),y),part,font=font(max(26,int(w*.052))),fill="white",stroke_width=1,stroke_fill=(0,0,0))
        y+=int(h*.075)
    d.text((int(w*.07),int(h*.84)),subtitle[:100],font=font(max(15,int(w*.023))),fill=(210,220,230))
    d.rectangle((int(w*.05),int(h*.93),int(w*.95),int(h*.935)),fill=(232,177,76))
    img.save(path, quality=92)

def media_duration(path: Path) -> float:
    probe = shutil.which("ffprobe")
    if not probe:
        raise RuntimeError("ffprobe not found. Install FFmpeg including ffprobe.")
    proc = subprocess.run([probe, "-v", "error", "-show_entries", "format=duration",
                           "-of", "default=noprint_wrappers=1:nokey=1", str(path)],
                          capture_output=True, text=True)
    if proc.returncode:
        raise RuntimeError("Could not read voiceover duration: " + proc.stderr[-1000:])
    return max(10.0, float(proc.stdout.strip()))

def run_ffmpeg(args: list[str]):
    proc=subprocess.run(args,capture_output=True,text=True)
    if proc.returncode:
        raise RuntimeError("FFmpeg failed: "+proc.stderr[-2500:])

async def render_video(script: str, match_name: str, job_dir: Path, voice: str | None = None):
    if not shutil.which(settings.ffmpeg_bin) and not Path(settings.ffmpeg_bin).exists():
        raise RuntimeError("FFmpeg not found. Install FFmpeg and set FFMPEG_BIN.")
    job_dir.mkdir(parents=True,exist_ok=True)
    audio=job_dir/"voiceover.mp3"
    tts=edge_tts.Communicate(script, voice or settings.tts_voice, rate=settings.tts_rate)
    await tts.save(str(audio))
    scenes=[
      ("MATCH PREVIEW",match_name),("TEAM ANALYSIS","Strengths, combinations and match approach"),
      ("TACTICAL BATTLE","Powerplay, middle overs and death overs"),
      ("KEY FACTORS","Conditions and confirmed team updates"),
      ("PREET SPORTS","Your match. Your analysis. Your cricket.")
    ]
    outputs=[]
    for label,size,suffix in [("landscape",(1920,1080),"16x9"),("portrait",(1080,1920),"9x16")]:
        cards=job_dir/suffix; cards.mkdir(exist_ok=True)
        concat=job_dir/f"{suffix}.txt"
        total_duration = media_duration(audio)
        weights = [0.14, 0.23, 0.23, 0.22, 0.18]
        durations = [total_duration * weight for weight in weights]
        with concat.open("w",encoding="utf-8") as f:
            for i,(title,sub) in enumerate(scenes):
                p=cards/f"scene-{i:02d}.jpg"
                make_card(p,size,title,sub,i+1)
                f.write(f"file '{p.resolve().as_posix()}'\n")
                f.write(f"duration {durations[i]}\n")
            f.write(f"file '{(cards/'scene-04.jpg').resolve().as_posix()}'\n")
        out=job_dir/f"preet-sports-{suffix}.mp4"
        run_ffmpeg([settings.ffmpeg_bin,"-y","-f","concat","-safe","0","-i",str(concat),
                    "-i",str(audio),"-vf","fps=30,format=yuv420p","-c:v","libx264",
                    "-preset","veryfast","-c:a","aac","-b:a","192k","-shortest","-movflags","+faststart",str(out)])
        outputs.append(out)
    (job_dir/"script.txt").write_text(script,encoding="utf-8")
    (job_dir/"metadata.json").write_text(json.dumps({"title":f"{match_name} | Pre-Match Analysis | Preet Sports",
      "description":f"Pre-match cricket analysis: {match_name}. Independent commentary and analysis.\n\n#Cricket #PreetSports",
      "tags":["cricket","match preview","preet sports","cricket analysis"]},indent=2),encoding="utf-8")
    return {"audio":str(audio),"videos":[str(x) for x in outputs],
            "script":str(job_dir/"script.txt"),"metadata":str(job_dir/"metadata.json")}
