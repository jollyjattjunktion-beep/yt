import json
import httpx
from .config import settings

def fallback_script(match_name: str, research: dict, minutes: int) -> str:
    facts = research.get("facts") or {}
    supplied = json.dumps(facts, ensure_ascii=False) if facts else "No verified match statistics, squads, venue or pitch data were supplied."
    return f"""Sat Sri Akal dosto! Welcome to Preet Sports.
Aaj hum baat karne wale hain {match_name} ke pre-match मुकाबले ki.
Is preview mein hum dono teams ke approach, important match situations aur game ke possible turning points ko samjhenge.
Available verified information: {supplied}
Jahan confirmed information available nahi hai, wahan hum koi player statistic, playing eleven ya pitch report assume nahi karenge.
Cricket mein powerplay ka use, middle overs mein strike rotation aur death overs ki execution match ka direction badal sakti hai.
Toss important ho sakta hai, lekin decision ground conditions, team combination aur match format ko dekhkar hi samajhna chahiye.
Dosto, final prediction se pehle confirmed playing eleven aur latest team updates zaroor check karein.
Aapke hisaab se is match mein kis team ka advantage rahega? Apni prediction comments mein batayein.
Preet Sports ko subscribe karein. Milte hain match ke baad. Dhanyavaad!"""

async def generate_script(match_name: str, research: dict, minutes: int, language: str) -> str:
    if not settings.gemini_api_key:
        return fallback_script(match_name, research, minutes)
    prompt = f"""Write an original {minutes}-minute pre-match cricket analysis narration for Preet Sports.
Language: {language}; use natural Roman Hinglish, not Devanagari.
Sound like a warm, energetic Indian male cricket presenter. No generic filler.
Use only facts present in this research JSON. Do not invent squads, stats, injuries, venue,
pitch conditions, toss results or recent form. If a detail is missing, say it is not confirmed
or omit it. Include a hook, match context, team analysis, key tactical factors, player battles
only when supported by named data, prediction with caveats, and concise CTA.
Return only spoken narration, no markdown or stage directions.
MATCH: {match_name}
RESEARCH JSON: {json.dumps(research, ensure_ascii=False)}"""
    url = f"https://generativelanguage.googleapis.com/v1beta/models/{settings.gemini_model}:generateContent"
    async with httpx.AsyncClient(timeout=90) as client:
        response = await client.post(url, params={"key": settings.gemini_api_key},
            json={"contents":[{"parts":[{"text":prompt}]}],
                  "generationConfig":{"temperature":0.65}})
        response.raise_for_status()
        data = response.json()
    return data["candidates"][0]["content"]["parts"][0]["text"].strip()
