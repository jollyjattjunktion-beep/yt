"""Generate original, text-free cricket scene backgrounds with Gemini image generation."""
import asyncio
import base64
from pathlib import Path
import httpx
from .config import settings


async def generate_scene_backgrounds(match_name: str, scenes: list[dict], output_dir: Path) -> list[str]:
    if not settings.gemini_api_key:
        raise RuntimeError("Gemini API key is required for original cricket scene image generation.")
    output_dir.mkdir(parents=True, exist_ok=True)
    model = settings.gemini_image_model
    endpoint = f"https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent"
    outputs = []
    async with httpx.AsyncClient(timeout=httpx.Timeout(180, connect=25)) as client:
        for index, scene in enumerate(scenes[:12], start=1):
            title = str(scene.get("title", "Cricket analysis"))[:80]
            subtitle = str(scene.get("subtitle", ""))[:160]
            prompt = f"""Create one premium, cinematic cricket broadcast background image for Preet Sports.
Match context: {match_name}. Scene: {title}. Supporting context: {subtitle}.
Visual direction: photorealistic professional cricket photography, dramatic but natural stadium lighting,
deep navy and muted gold palette, rich realistic grass and pitch textures, crisp details, atmospheric depth,
professional sports documentary art direction. Use a new original composition, no recognizable real player,
no copyrighted team logo, no sponsor marks, no text, no numbers, no watermark, no UI.
Compose with clean negative space across the upper third and lower third for later readable typography.
Wide landscape 16:9 image, broadcast-quality, natural color grading, not plastic or cartoon-like."""
            response = await client.post(
                endpoint,
                params={"key": settings.gemini_api_key},
                json={
                    "contents": [{"parts": [{"text": prompt}]}],
                    "generationConfig": {
                        "responseModalities": ["TEXT", "IMAGE"],
                        "imageConfig": {"aspectRatio": "16:9"}
                    }
                }
            )
            if response.is_error:
                raise RuntimeError(
                    f"Gemini image generation failed for scene {index} ({response.status_code}): "
                    + response.text[:900]
                )
            data = response.json()
            image_bytes = None
            mime = "image/png"
            try:
                parts = data["candidates"][0]["content"]["parts"]
                for part in parts:
                    inline = part.get("inlineData") or part.get("inline_data")
                    if inline and inline.get("data"):
                        image_bytes = base64.b64decode(inline["data"])
                        mime = inline.get("mimeType") or inline.get("mime_type") or mime
                        break
            except (KeyError, IndexError, TypeError):
                pass
            if not image_bytes:
                raise RuntimeError(f"Gemini returned no image for scene {index}. Check image-model access and quota.")
            extension = ".jpg" if "jpeg" in mime else ".png"
            target = output_dir / f"ai-scene-{index:02d}{extension}"
            target.write_bytes(image_bytes)
            outputs.append(str(target))
            # Avoid burst traffic and stay within conservative free-tier request pacing.
            if index < min(12, len(scenes)):
                await asyncio.sleep(1.0)
    if len(outputs) < 8:
        raise RuntimeError(f"Only {len(outputs)} AI scene images were generated; at least 8 are required.")
    return outputs
