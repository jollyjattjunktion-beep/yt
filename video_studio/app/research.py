"""Pluggable cricket-data adapter. Never scrape or invent facts silently."""
import httpx
from .config import settings

async def research_match(match_name: str, supplied_facts: dict) -> dict:
    if supplied_facts:
        return {"match_name": match_name, "facts": supplied_facts,
                "source": "user_supplied", "verified": False}
    if not settings.cricket_data_api_url:
        return {"match_name": match_name, "facts": {},
                "source": None, "verified": False,
                "warning": "No cricket-data provider configured. Script must avoid specific stats, squads, venue or pitch claims."}
    headers = {}
    if settings.cricket_data_api_key:
        headers["Authorization"] = f"Bearer {settings.cricket_data_api_key}"
    async with httpx.AsyncClient(timeout=25) as client:
        response = await client.post(settings.cricket_data_api_url,
            json={"match_name": match_name}, headers=headers)
        response.raise_for_status()
        data = response.json()
    if not isinstance(data, dict):
        raise ValueError("Cricket data provider must return a JSON object.")
    return {"match_name": match_name, "facts": data,
            "source": settings.cricket_data_api_url, "verified": True}
