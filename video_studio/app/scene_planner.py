"""Gemini scene planning with a deterministic, safe fallback."""
import json, httpx
from .config import settings

async def plan_scenes(match_name: str, script: str, research: dict, duration: float) -> list[dict]:
    if not settings.gemini_api_key:
        return fallback_scenes(match_name, research)
    prompt = f"""Create a sports-broadcast storyboard for a cricket pre-analysis video.
Return ONLY valid JSON array, 8 to 14 scenes. Each object: title, on_screen_text (max 18 words), visual_type (one of: opener, team_card, player_card, pitch_graphic, stat_graphic, tactics_board, matchup, prediction, outro), narration_anchor (short exact phrase from script), duration_weight (positive number).
Do not invent facts. Use only the supplied research. Use a polished navy/gold cricket TV graphics package, clean lower thirds, player photo placeholders when no licensed image URL exists, animated field diagrams and scorecard-style panels. No broadcast footage.
MATCH: {match_name}
DURATION_SECONDS: {int(duration)}
RESEARCH: {json.dumps(research, ensure_ascii=False)}
SCRIPT: {script}
"""
    url=f"https://generativelanguage.googleapis.com/v1beta/models/{settings.gemini_model}:generateContent"
    async with httpx.AsyncClient(timeout=120) as client:
        response=await client.post(
            url,
            params={"key":settings.gemini_api_key},
            json={
                "contents":[{"parts":[{"text":prompt}]}],
                "generationConfig":{"responseMimeType":"application/json"}
            }
        )
        if response.is_error:
            return fallback_scenes(match_name, research)
        try:
            raw=response.json()["candidates"][0]["content"]["parts"][0]["text"]
            data=json.loads(raw)
            if isinstance(data,list) and 8 <= len(data) <= 12:
                allowed={"opener","team_card","player_card","pitch_graphic","stat_graphic","tactics_board","matchup","prediction","outro"}
                clean=[]
                for item in data:
                    if not isinstance(item,dict): continue
                    clean.append({"title":str(item.get("title","MATCH ANALYSIS"))[:60],"subtitle":str(item.get("on_screen_text",""))[:140],"type":item.get("visual_type") if item.get("visual_type") in allowed else "stat_graphic","anchor":str(item.get("narration_anchor",""))[:180],"weight":max(.1,float(item.get("duration_weight",1)))})
                if len(clean)>=8: return clean[:12]
        except Exception: pass
    return fallback_scenes(match_name, research)

def fallback_scenes(match_name: str, research: dict) -> list[dict]:
    facts=research.get("facts") or {}
    teams=facts.get("teams") or []
    team_line=" vs ".join(t.get("name","") for t in teams if isinstance(t,dict) and t.get("name")) or "Team details awaiting confirmation"
    players=facts.get("players") or []
    player_line=", ".join(p.get("name","") for p in players[:5] if isinstance(p,dict) and p.get("name")) or "Verified player information pending"
    pitch=(facts.get("pitch_report") or {}).get("summary","No verified pitch report supplied")
    h2h=(facts.get("head_to_head") or {}).get("summary","No verified head-to-head data supplied")
    return [
      {"title":"MATCH PREVIEW","subtitle":match_name,"type":"opener","anchor":"","weight":1.1},
      {"title":"THE MATCH-UP","subtitle":team_line,"type":"team_card","anchor":"","weight":1.1},
      {"title":"PLAYERS TO WATCH","subtitle":player_line,"type":"player_card","anchor":"","weight":1.2},
      {"title":"PITCH & CONDITIONS","subtitle":pitch,"type":"pitch_graphic","anchor":"","weight":1.0},
      {"title":"HEAD-TO-HEAD","subtitle":h2h,"type":"stat_graphic","anchor":"","weight":.9},
      {"title":"TACTICAL BATTLE","subtitle":"Powerplay · Middle overs · Death overs","type":"tactics_board","anchor":"","weight":1.4},
      {"title":"KEY MATCH-UP","subtitle":"Batting plans vs bowling options","type":"matchup","anchor":"","weight":1.2},
      {"title":"OUR MATCH READ","subtitle":"Balanced prediction based on available evidence","type":"prediction","anchor":"","weight":1.0},
      {"title":"PREET SPORTS","subtitle":"Cricket analysis. Match smarter.","type":"outro","anchor":"","weight":.8}
    ]
