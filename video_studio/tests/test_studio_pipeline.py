from app.schemas import MatchRequest
from app.scene_planner import fallback_scenes

def test_duration_is_8_to_12_minutes():
    assert MatchRequest(match_name="India vs Australia", duration_minutes=8).duration_minutes == 8
    assert MatchRequest(match_name="India vs Australia", duration_minutes=12).duration_minutes == 12

def test_duration_rejects_old_short_preview():
    import pytest
    with pytest.raises(Exception):
        MatchRequest(match_name="India vs Australia", duration_minutes=7)

def test_fallback_scene_plan_is_sports_broadcast():
    scenes=fallback_scenes("India vs Australia", {"facts":{}})
    assert len(scenes) >= 8
    assert scenes[0]["type"] == "opener"
    assert scenes[-1]["type"] == "outro"
