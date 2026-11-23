"""AI scene image provider: authenticated free Colab worker, with clear fallback behavior."""
import asyncio, base64, re
from pathlib import Path
import httpx
from .config import settings


def _prompt(match_name: str, scene: dict) -> str:
    title=str(scene.get("title","Cricket analysis"))[:80]
    subtitle=str(scene.get("subtitle",""))[:160]
    return f"""Create one original cinematic cricket broadcast background for Preet Sports.
Match: {match_name}. Scene: {title}. Context: {subtitle}.
Photorealistic cricket stadium photography, natural lighting, navy and muted gold broadcast palette,
realistic pitch and grass, professional sports documentary, no recognizable real players, no team logos,
no sponsor marks, no text, no numbers, no watermark. Wide landscape 16:9, negative space for titles."""


async def _colab_images(match_name: str, scenes: list[dict], output_dir: Path) -> list[str]:
    base=(settings.colab_image_worker_url or "").rstrip("/")
    token=settings.colab_worker_token
    if not base or not token:
        raise RuntimeError("Colab image worker is not connected. Set COLAB_IMAGE_WORKER_URL and COLAB_WORKER_TOKEN.")
    output_dir.mkdir(parents=True,exist_ok=True)
    prompts=[_prompt(match_name,s) for s in scenes[:12]]
    async with httpx.AsyncClient(timeout=httpx.Timeout(settings.colab_image_timeout_seconds,connect=20)) as client:
        response=await client.post(base+"/generate",headers={"Authorization":f"Bearer {token}"},
          json={"prompts":prompts,"width":1024,"height":576,"steps":4})
        if response.is_error:
            raise RuntimeError(f"Colab worker returned {response.status_code}: {response.text[:600]}")
        data=response.json()
    images=data.get("images",[])
    if len(images)<8:
        raise RuntimeError(f"Colab worker returned {len(images)} images; at least 8 are required.")
    outputs=[]
    for i,item in enumerate(images[:12],1):
        raw=base64.b64decode(item["data"],validate=True)
        if len(raw)>15*1024*1024: raise RuntimeError(f"Colab image {i} exceeds 15 MB.")
        if not raw.startswith(b"\\x89PNG\\r\\n\\x1a\\n") and not raw.startswith(b"\\xff\\xd8"):
            raise RuntimeError(f"Colab image {i} is not a valid PNG/JPEG.")
        ext=".png" if raw.startswith(b"\\x89PNG") else ".jpg"
        path=output_dir/f"ai-scene-{i:02d}{ext}"
        path.write_bytes(raw); outputs.append(str(path))
    return outputs


async def generate_scene_backgrounds(match_name: str, scenes: list[dict], output_dir: Path) -> list[str]:
    if settings.colab_image_worker_url:
        return await _colab_images(match_name,scenes,output_dir)
    raise RuntimeError("No free image worker connected. Start the Preet Sports Colab notebook and configure COLAB_IMAGE_WORKER_URL and COLAB_WORKER_TOKEN in Render.")
