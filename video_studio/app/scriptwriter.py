import asyncio, json, re, httpx
from .config import settings

RETRYABLE_STATUS = {429, 500, 502, 503, 504}
MAX_REQUEST_ATTEMPTS = 4

def word_count(text: str) -> int:
    return len(re.findall(r"\b[\w'-]+\b", text))

def _retry_delay(attempt: int, retry_after: str | None = None) -> float:
    """Bounded exponential backoff; honour a numeric Retry-After when supplied."""
    if retry_after:
        try:
            return min(20.0, max(1.0, float(retry_after)))
        except (TypeError, ValueError):
            pass
    return min(15.0, 2.0 ** attempt)

async def _generate_content(client: httpx.AsyncClient, url: str, payload: dict) -> dict:
    last_error = "Gemini request failed."
    for attempt in range(MAX_REQUEST_ATTEMPTS):
        try:
            response = await client.post(
                url,
                params={"key": settings.gemini_api_key},
                json=payload,
            )
        except (httpx.TimeoutException, httpx.NetworkError, httpx.RemoteProtocolError) as exc:
            last_error = f"Network error contacting Gemini: {type(exc).__name__}: {exc}"
            if attempt == MAX_REQUEST_ATTEMPTS - 1:
                raise RuntimeError(
                    f"{last_error}. The production job can be retried after connectivity is restored."
                ) from exc
            await asyncio.sleep(_retry_delay(attempt))
            continue
        except httpx.RequestError as exc:
            raise RuntimeError(f"Gemini request could not be sent: {exc}") from exc

        if not response.is_error:
            try:
                return response.json()
            except ValueError as exc:
                raise RuntimeError("Gemini returned an invalid JSON response.") from exc

        status = response.status_code
        detail = response.text[:700]
        if status == 503:
            last_error = "Gemini is temporarily overloaded (HTTP 503)."
        elif status == 429:
            last_error = "Gemini rate limit or quota reached (HTTP 429). Check API usage and limits."
        elif status == 404:
            raise RuntimeError(
                f"Gemini model '{settings.gemini_model}' was not found (HTTP 404). "
                "Check the configured model ID and API access. No automatic model switch was made. "
                f"Details: {detail}"
            )
        elif status in RETRYABLE_STATUS:
            last_error = f"Gemini temporarily unavailable (HTTP {status})."
        else:
            raise RuntimeError(f"Gemini script error {status}: {detail}")

        if status not in RETRYABLE_STATUS or attempt == MAX_REQUEST_ATTEMPTS - 1:
            raise RuntimeError(
                f"{last_error} Tried {attempt + 1}/{MAX_REQUEST_ATTEMPTS} requests. "
                f"Please retry the production job. Details: {detail}"
            )

        await asyncio.sleep(_retry_delay(attempt, response.headers.get("Retry-After")))

    raise RuntimeError(last_error)

async def generate_script(match_name: str, research: dict, minutes: int, language: str) -> str:
    if not settings.gemini_api_key:
        raise RuntimeError("Gemini API key is required. Add it in the Studio Settings panel before generating.")
    facts = research.get("facts") or {}
    prompt = f"""You are the lead cricket analyst and presenter for PREET SPORTS.
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
RESEARCH JSON: {json.dumps(research, ensure_ascii=False)}
"""
    url = f"https://generativelanguage.googleapis.com/v1beta/models/{settings.gemini_model}:generateContent"
    async with httpx.AsyncClient(timeout=httpx.Timeout(150.0, connect=20.0)) as client:
        for length_attempt in range(2):
            data = await _generate_content(
                client,
                url,
                {"contents": [{"parts": [{"text": prompt}]}]},
            )
            try:
                script = data["candidates"][0]["content"]["parts"][0]["text"].strip()
            except (KeyError, IndexError, TypeError, AttributeError):
                feedback = data.get("promptFeedback") or {}
                block_reason = feedback.get("blockReason")
                if block_reason:
                    raise RuntimeError(f"Gemini could not generate the script: {block_reason}.")
                raise RuntimeError("Gemini returned no script text. Check the prompt and model response.")
            count = word_count(script)
            minimum = minutes * 105
            if count >= minimum:
                return script
            if length_attempt == 0:
                prompt += (
                    f"\nIMPORTANT RETRY: Your previous response had {count} words, below the "
                    f"{minimum}-word minimum. Expand all sections with meaningful analysis and "
                    "return at least the required spoken word count. Do not add unsupported facts."
                )
    raise RuntimeError(
        "Gemini repeatedly returned a script that is too short. Try again or reduce the requested duration."
    )
