import json, re, httpx
from .config import settings

def word_count(text: str) -> int:
    return len(re.findall(r"\b[\w'-]+\b",text))

async def generate_script(match_name: str, research: dict, minutes: int, language: str) -> str:
    if not settings.gemini_api_key:
        raise RuntimeError("Gemini API key is required. Add it in the Studio Settings panel before generating.")
    facts=research.get("facts") or {}
    prompt=f"""You are the lead cricket analyst and presenter for PREET SPORTS.
Create a complete, ready-to-record {minutes}-minute cricket match preview.
LANGUAGE: Roman Hinglish only. Write Hindi words in Latin letters. Never use Devanagari.
VOICE: Natural Indian male sports presenter; warm, confident, energetic, conversational.
LENGTH: Aim for {minutes*125} to {minutes*140} spoken words. Hard minimum {minutes*105} words.
STRUCTURE:
1. A strong 15-second hook and greeting.
2. Match context: teams, format, fixture and stakes when confirmed.
3. Team A analysis: likely approach, strengths and vulnerabilities supported by data.
4. Team B analysis: same depth.
5. Player matchups/cards: mention only players and roles actually present in the research.
6. Pitch and venue report only when a sourced report is present.
7. Head-to-head only when historical figures are present and dated.
8. Tactical turning points: powerplay, middle overs, death overs, bowling matchups.
9. Balanced match prediction with reasons and uncertainty; never invent a probability.
10. Natural Preet Sports CTA.
STYLE: Real spoken commentary with varied sentence lengths, natural transitions and pauses created by punctuation. Avoid repetitive filler and generic claims. No headings, bullets, markdown, emojis or production notes. Return only the spoken script.
FACT SAFETY: Only state details explicitly supported in the research JSON. If squads, playing XI, injuries, pitch, venue, stats or H2H are absent, say they are not confirmed or skip the section. Never make up a player, stat, toss, condition or result.
MATCH: {match_name}
RESEARCH JSON: {json.dumps(research,ensure_ascii=False)}
"""
    url=f"https://generativelanguage.googleapis.com/v1beta/models/{settings.gemini_model}:generateContent"
    async with httpx.AsyncClient(timeout=150) as client:
        for attempt in range(2):
            response=await client.post(url,params={"key":settings.gemini_api_key},
              json={"contents":[{"parts":[{"text":prompt}]}],"generationConfig":{"temperature":0.55}})
            if response.is_error: raise RuntimeError(f"Gemini script error {response.status_code}: {response.text[:1000]}")
            data=response.json()
            try: script=data["candidates"][0]["content"]["parts"][0]["text"].strip()
            except (KeyError,IndexError,TypeError): raise RuntimeError("Gemini returned no script text.")
            count=word_count(script)
            minimum=minutes*105
            if count>=minimum: return script
            prompt += f"\nIMPORTANT RETRY: Your previous response had {count} words, below the {minimum}-word minimum. Expand all sections with meaningful analysis and return at least {minimum} spoken words. Do not add unsupported facts."
    raise RuntimeError("Gemini repeatedly returned a script that is too short. Try again or reduce the requested duration.")
