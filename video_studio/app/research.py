"""Source-grounded match extraction. Extracted values remain unverified until reviewed."""
import base64
import json
import re
from html.parser import HTMLParser
from pathlib import Path
from urllib.parse import urlparse

import httpx
from PIL import Image
from .config import settings


ALLOWED_HOSTS = {"crex.com", "www.crex.com", "cricbuzz.com", "www.cricbuzz.com",
                 "icc-cricket.com", "www.icc-cricket.com"}


class VisibleText(HTMLParser):
    def __init__(self):
        super().__init__()
        self.parts = []
        self.hidden = 0

    def handle_starttag(self, tag, attrs):
        if tag in {"script", "style", "noscript", "svg"}:
            self.hidden += 1

    def handle_endtag(self, tag):
        if tag in {"script", "style", "noscript", "svg"} and self.hidden:
            self.hidden -= 1

    def handle_data(self, data):
        if not self.hidden and data.strip():
            self.parts.append(data.strip())


async def _fetch_match_page(source_url: str) -> str:
    parsed = urlparse(source_url)
    if parsed.scheme != "https" or parsed.hostname not in ALLOWED_HOSTS:
        raise ValueError("Only HTTPS CREX, Cricbuzz or ICC match URLs are accepted.")
    async with httpx.AsyncClient(timeout=25, follow_redirects=True,
                                 headers={"User-Agent": "PreetSportsMatchResearch/1.0"}) as client:
        response = await client.get(source_url)
        response.raise_for_status()
        if urlparse(str(response.url)).hostname not in ALLOWED_HOSTS:
            raise ValueError("Match page redirected outside the permitted cricket domains.")
        parser = VisibleText()
        parser.feed(response.text[:3_000_000])
        return re.sub(r"\s+", " ", " ".join(parser.parts))[:24000]


async def _extract_screenshot_facts(paths: list[str]) -> dict:
    if not paths or not settings.gemini_api_key:
        return {}
    parts = [{"text": """Read the supplied cricket match screenshots. Return ONLY a JSON object.
Extract only values visibly present. Use null or empty arrays for missing values.
Schema: {"match_name":null,"teams":[],"match_format":null,"match_date":null,
"venue":null,"match_status":null,"weather":{},"venue_stats":{},"team_comparison":{},
"head_to_head":{},"recent_form":{},"players":[],"pitch_report":null,
"source_notes":[]}. Preserve exact numbers and labels. Do not calculate, infer or invent.
If two screenshots conflict, include both in source_notes and leave the disputed field null."""}]
    for raw_path in paths[:10]:
        path = Path(raw_path).resolve()
        if not path.is_file() or path.stat().st_size > 12 * 1024 * 1024:
            continue
        try:
            with Image.open(path) as image:
                image = image.convert("RGB")
                image.thumbnail((1600, 1600))
                import io
                buf = io.BytesIO()
                image.save(buf, format="JPEG", quality=82)
                parts.append({"inline_data": {"mime_type": "image/jpeg",
                                               "data": base64.b64encode(buf.getvalue()).decode("ascii")}})
        except Exception:
            continue
    if len(parts) == 1:
        return {}
    url = f"https://generativelanguage.googleapis.com/v1beta/models/{settings.gemini_model}:generateContent"
    async with httpx.AsyncClient(timeout=120) as client:
        response = await client.post(url, params={"key": settings.gemini_api_key},
            json={"contents": [{"parts": parts}],
                  "generationConfig": {"responseMimeType": "application/json"}})
        if response.is_error:
            raise RuntimeError(f"Gemini screenshot extraction failed ({response.status_code}): {response.text[:700]}")
        data = response.json()
    try:
        raw = data["candidates"][0]["content"]["parts"][0]["text"]
        parsed = json.loads(raw)
        return parsed if isinstance(parsed, dict) else {}
    except (KeyError, IndexError, TypeError, ValueError):
        raise RuntimeError("Gemini could not return structured facts from the uploaded screenshots.")


async def research_match(match_name: str, supplied_facts: dict,
                         source_url: str | None = None,
                         screenshot_paths: list[str] | None = None) -> dict:
    page_text = ""
    page_error = None
    if source_url:
        try:
            page_text = await _fetch_match_page(source_url)
        except Exception as exc:
            page_error = str(exc)[:500]
    screenshot_facts = await _extract_screenshot_facts(screenshot_paths or [])
    facts = dict(supplied_facts or {})
    # Screenshot extraction fills missing fields only; explicit user facts are retained.
    for key, value in screenshot_facts.items():
        if key not in facts or facts[key] in (None, "", [], {}):
            facts[key] = value
    if page_text:
        facts.setdefault("source_page_text", page_text)
    if page_error:
        facts["source_page_warning"] = page_error
    if not facts and not settings.cricket_data_api_url:
        return {"match_name": match_name, "facts": {}, "source": source_url,
                "verified": False, "verification_status": "no_extractable_facts",
                "warning": "No readable match facts were extracted. Review the URL or upload clear screenshots.", "page_error": page_error}
    if not facts and settings.cricket_data_api_url:
        headers = {}
        if settings.cricket_data_api_key:
            headers["Authorization"] = f"Bearer {settings.cricket_data_api_key}"
        async with httpx.AsyncClient(timeout=25) as client:
            response = await client.post(settings.cricket_data_api_url,
                json={"match_name": match_name, "source_url": source_url,
                      "page_text": page_text}, headers=headers)
            response.raise_for_status()
            data = response.json()
        if not isinstance(data, dict):
            raise ValueError("Cricket data provider must return a JSON object.")
        facts.update(data)
    return {"match_name": match_name, "facts": facts,
            "source": source_url or ("user_supplied" if supplied_facts else None),
            "verified": False,
            "verification_status": "source_extracted_needs_review",
            "warning": "Facts were extracted from the supplied page/screenshots. Confirm figures and date before publishing."}
