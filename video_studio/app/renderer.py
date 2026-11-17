import asyncio, json, re, shutil, subprocess
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont
from .config import settings
from .gemini_tts import synthesize

def safe_name(value: str) -> str:
    return re.sub(r"[^a-zA-Z0-9-]+", "-", value).strip("-").lower()[:70] or "match"

def font(size: int):
    candidates=["./assets/fonts/DejaVuSans-Bold.ttf","/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf","C:/Windows/Fonts/arialbd.ttf"]
    for candidate in candidates:
        try: return ImageFont.truetype(candidate,size)
        except OSError: pass
    return ImageFont.load_default()

def make_card(path: Path,size: tuple[int,int],title: str,subtitle: str,index: int):
    w,h=size; img=Image.new("RGB",size,(7,20,36)); d=ImageDraw.Draw(img)
    # Original illustrated night-stadium: floodlights, tiered stands and a central pitch.
    for y in range(h):
        t=y/max(1,h); d.line((0,y,w,y),fill=(7,int(19+24*t),int(35+26*t)))
    horizon=int(h*.70)
    d.polygon([(0,horizon),(int(w*.18),int(h*.48)),(int(w*.82),int(h*.48)),(w,horizon),(w,h),(0,h)],fill=(12,42,54))
    for tier in range(4):
        yy=int(h*(.48+tier*.045))
        d.arc((int(w*.08),yy,int(w*.92),int(h*.94)),180,360,fill=(48+5*tier,78+4*tier,95+3*tier),width=max(2,int(w*.003)))
    # floodlight towers
    for x in (int(w*.12),int(w*.88)):
        d.line((x,int(h*.18),x,int(h*.55)),fill=(110,139,151),width=max(2,int(w*.004)))
        for n in range(5):
            d.ellipse((x-int(w*.025)+n*int(w*.01),int(h*.15),x-int(w*.02)+n*int(w*.01),int(h*.16)),fill=(255,225,160))
    d.ellipse((int(w*.30),int(h*.57),int(w*.70),int(h*.91)),fill=(25,88,64),outline=(82,140,101),width=max(2,int(w*.004)))
    d.line((int(w*.50),int(h*.60),int(w*.50),int(h*.88)),fill=(184,190,151),width=max(2,int(w*.002)))
    d.rectangle((int(w*.05),int(h*.08),int(w*.95),int(h*.09)),fill=(232,177,76))
    d.rounded_rectangle((int(w*.055),int(h*.12),int(w*.945),int(h*.91)),radius=max(12,int(w*.02)),fill=(5,18,31),outline=(50,78,95),width=max(2,int(w*.002)))
    d.text((int(w*.08),int(h*.15)),"PREET SPORTS",font=font(max(22,int(w*.032))),fill=(245,195,102))
    d.text((int(w*.08),int(h*.30)),f"{index:02d}",font=font(max(24,int(w*.04))),fill=(135,174,194))
    max_chars=25 if h>w else 34; words=title.split(); lines=[]; line=""
    for word in words:
        if len(line+" "+word)>max_chars and line: lines.append(line); line=word
        else: line=(line+" "+word).strip()
    if line: lines.append(line)
    y=int(h*.40)
    for part in lines[:4]:
        d.text((int(w*.08),y),part,font=font(max(25,int(w*.048))),fill="white",stroke_width=1,stroke_fill=(0,0,0)); y+=int(h*.07)
    sub=subtitle[:90]
    d.text((int(w*.08),int(h*.78)),sub,font=font(max(15,int(w*.019))),fill=(212,224,232))
    d.rectangle((int(w*.06),int(h*.87),int(w*.94),int(h*.875)),fill=(232,177,76))
    img.save(path,quality=93)

def media_duration(path: Path) -> float:
    probe=shutil.which("ffprobe")
    if not probe: raise RuntimeError("ffprobe not found. Install FFmpeg and add it to PATH.")
    proc=subprocess.run([probe,"-v","error","-show_entries","format=duration","-of","default=noprint_wrappers=1:nokey=1",str(path)],capture_output=True,text=True)
    if proc.returncode: raise RuntimeError("Could not read media duration: "+proc.stderr[-1000:])
    return max(1.0,float(proc.stdout.strip()))

def run_ffmpeg(args: list[str]):
    proc=subprocess.run(args,capture_output=True,text=True)
    if proc.returncode: raise RuntimeError("FFmpeg failed: "+proc.stderr[-2500:])

def fmt_srt(seconds: float):
    ms=int(seconds*1000); h,ms=divmod(ms,3600000); m,ms=divmod(ms,60000); s,ms=divmod(ms,1000)
    return f"{h:02}:{m:02}:{s:02},{ms:03}"

def make_srt(script: str,duration: float,path: Path):
    chunks=re.split(r"(?<=[.!?])\s+",script.strip())
    chunks=[x.strip() for x in chunks if x.strip()]
    total=max(1,sum(len(x.split()) for x in chunks)); cursor=0.0; blocks=[]
    for i,chunk in enumerate(chunks,1):
        span=duration*len(chunk.split())/total
        blocks.append(f"{i}\n{fmt_srt(cursor)} --> {fmt_srt(cursor+span)}\n{chunk}\n")
        cursor+=span
    path.write_text("\n".join(blocks),encoding="utf-8")

def research_scenes(research: dict,match_name: str):
    facts=research.get("facts") or {}
    teams=facts.get("teams") or []
    team_names=[t.get("name","") for t in teams if isinstance(t,dict) and t.get("name")]
    team_line=" vs ".join(team_names) if team_names else "Team details not verified yet"
    players=facts.get("players") or []
    player_names=[p.get("name","") for p in players if isinstance(p,dict) and p.get("name")]
    player_line=", ".join(player_names[:4]) if player_names else "Player profiles pending verified data"
    pitch=facts.get("pitch_report") or {}
    pitch_line=pitch.get("summary","No verified pitch report supplied")
    h2h=facts.get("head_to_head") or {}
    h2h_line=h2h.get("summary","No verified head-to-head record supplied")
    prediction=facts.get("prediction") or {}
    prediction_line=prediction.get("summary","Tactical preview only; no unsupported winner claim")
    return [("MATCH PREVIEW",match_name),("TEAM INTRODUCTIONS",team_line),
      ("PLAYER CARDS",player_line),("PITCH REPORT",pitch_line),
      ("HEAD TO HEAD",h2h_line),("MATCH PREDICTION",prediction_line),
      ("PREET SPORTS","Roman Hinglish cricket analysis")]

async def render_video(script: str,match_name: str,job_dir: Path,voice: str|None=None,research: dict|None=None,scenes: list[dict]|None=None,tts_provider: str|None=None):
    if not shutil.which(settings.ffmpeg_bin) and not Path(settings.ffmpeg_bin).exists():
        raise RuntimeError("FFmpeg not found. Install FFmpeg and add its bin folder to PATH.")
    job_dir.mkdir(parents=True,exist_ok=True)
    audio=job_dir/"voiceover.wav"; await synthesize(script,audio,provider=tts_provider,voice=voice)
    total=media_duration(audio); scenes=scenes or research_scenes(research or {},match_name)
    srt=job_dir/"subtitles.srt"; make_srt(script,total,srt)
    outputs=[]
    for size,suffix in [((1920,1080),"16x9"),((1080,1920),"9x16")]:
        cards=job_dir/suffix; cards.mkdir(exist_ok=True); concat=job_dir/f"{suffix}.txt"
        weights=[max(.1,float(s.get("weight",1))) for s in scenes]
        weight_sum=sum(weights); durations=[total*x/weight_sum for x in weights]
        with concat.open("w",encoding="utf-8") as f:
            for i,scene in enumerate(scenes):
                title=scene.get("title","MATCH ANALYSIS")
                sub=scene.get("subtitle","")
                p=cards/f"scene-{i:02d}.jpg"; make_card(p,size,title,sub,i+1)
                f.write(f"file '{p.resolve().as_posix()}'\nduration {durations[i]}\n")
            f.write(f"file '{(cards/f'scene-{len(scenes)-1:02d}.jpg').resolve().as_posix()}'\n")
        out=job_dir/f"preet-sports-{suffix}.mp4"
        run_ffmpeg([settings.ffmpeg_bin,"-y","-threads","2","-f","concat","-safe","0","-i",str(concat),"-i",str(audio),
          "-vf","fps=30,format=yuv420p","-c:v","libx264","-preset","veryfast","-threads","2",
          "-c:a","aac","-b:a","192k","-shortest","-movflags","+faststart",str(out)])
        if not out.is_file() or out.stat().st_size<10000: raise RuntimeError(f"Render quality check failed for {suffix}.")
        probe=shutil.which("ffprobe")
        check=subprocess.run([probe,"-v","error","-show_entries","stream=codec_type,width,height","-of","json",str(out)],capture_output=True,text=True) if probe else None
        if not check or check.returncode: raise RuntimeError(f"ffprobe validation failed for {suffix}.")
        streams=json.loads(check.stdout).get("streams",[])
        if not any(x.get("codec_type")=="video" for x in streams) or not any(x.get("codec_type")=="audio" for x in streams):
            raise RuntimeError(f"Output is missing an audio or video stream: {suffix}.")
        outputs.append(out)
    (job_dir/"script.txt").write_text(script,encoding="utf-8")
    thumb=job_dir/"thumbnail.jpg"
    make_card(thumb,(1280,720),"MATCH PREVIEW",match_name,1)
    metadata={"title":f"{match_name} | Match Preview | Preet Sports","description":f"Roman Hinglish cricket analysis: {match_name}. Independent commentary and analysis.\n\n#Cricket #PreetSports","tags":["cricket","match preview","preet sports","cricket analysis"]}
    meta=job_dir/"metadata.json"; meta.write_text(json.dumps(metadata,indent=2),encoding="utf-8")
    manifest={"match":match_name,"audio":str(audio),"videos":[str(x) for x in outputs],"subtitles":str(srt),"thumbnail":str(thumb),"metadata":str(meta),"duration_seconds":total}
    (job_dir/"output_manifest.json").write_text(json.dumps(manifest,indent=2),encoding="utf-8")
    return {"audio":str(audio),"videos":[str(x) for x in outputs],"script":str(job_dir/"script.txt"),
      "subtitles":str(srt),"thumbnail":str(thumb),"metadata":str(meta),"manifest":str(job_dir/"output_manifest.json")}
