"""Free Colab SDXL Turbo image provider for Preet Sports."""
import base64
from pathlib import Path
import httpx
from .config import settings


def _prompt(match_name: str, scene: dict) -> str:
    title = str(scene.get("title", "Cricket analysis"))[:80]
    subtitle = str(scene.get("subtitle", ""))[:160]
    return f"""Create one original cinematic cricket broadcast background for Preet Sports.
Match: {match_name}. Scene: {title}. Context: {subtitle}.
Photorealistic cricket stadium photography, natural lighting, navy and muted gold broadcast palette,
realistic pitch and grass, professional sports documentary, no recognizable real players, no team logos,
no sponsor marks, no text, no numbers, no watermark. Wide landscape 16:9, negative space for titles."""


async def _colab_images(match_name: str, scenes: list[dict], output_dir: Path) -> list[str]:
    base = (settings.colab_image_worker_url or "").rstrip("/")
    token = settings.colab_worker_token
    if not base or not token:
        raise RuntimeError("Free Colab image worker is not connected. Configure COLAB_IMAGE_WORKER_URL and COLAB_WORKER_TOKEN.")
    output_dir.mkdir(parents=True, exist_ok=True)
    prompts = [_prompt(match_name, scene) for scene in scenes[:12]]
    if len(prompts) < 8:
        raise RuntimeError(f"At least 8 scenes are required; received {len(prompts)}.")
    async with httpx.AsyncClient(timeout=httpx.Timeout(settings.colab_image_timeout_seconds, connect=20)) as client:
        response = await client.post(
            base + "/generate",
            headers={"Authorization": f"Bearer {token}"},
            json={"prompts": prompts, "width": 1024, "height": 576, "steps": 4},
        )
        if response.is_error:
            raise RuntimeError(f"Free Colab worker returned {response.status_code}: {response.text[:600]}")
        data = response.json()
    images = data.get("images", [])
    if len(images) < 8:
        raise RuntimeError(f"Colab worker returned {len(images)} images; at least 8 are required.")
    outputs = []
    for index, item in enumerate(images[:12], 1):
        raw = base64.b64decode(item["data"], validate=True)
        if len(raw) > 15 * 1024 * 1024:
            raise RuntimeError(f"Colab image {index} exceeds 15 MB.")
        is_png = raw.startswith(b"\x89PNG\r\n\x1a\n")
        is_jpeg = raw.startswith(b"\xff\xd8")
        if not (is_png or is_jpeg):
            raise RuntimeError(f"Colab image {index} is not a valid PNG/JPEG.")
        extension = ".png" if is_png else ".jpg"
        path = output_dir / f"ai-scene-{index:02d}{extension}"
        path.write_bytes(raw)
        outputs.append(str(path))
    return outputs


async def generate_scene_backgrounds(match_name: str, scenes: list[dict], output_dir: Path) -> list[str]:
    # No paid image API fallback: fail clearly if the free Colab worker is offline.
    return await _colab_images(match_name, scenes, output_dir)
