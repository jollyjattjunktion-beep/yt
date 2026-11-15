from app import job_store
from app.schemas import MatchRequest

def test_job_persists_and_can_be_retrieved(tmp_path, monkeypatch):
    monkeypatch.setattr(job_store,"DB_PATH",tmp_path/"studio.sqlite3")
    req=MatchRequest(match_name="India vs Australia 1st ODI")
    job_store.create_job("abc123",req)
    item=job_store.get_job("abc123")
    assert item["status"]=="queued"
    assert item["match_name"]=="India vs Australia 1st ODI"
    queued=job_store.next_job()
    assert queued["id"]=="abc123"
    item=job_store.get_job("abc123")
    assert item["status"]=="running"
    job_store.update_job("abc123",status="completed",progress=100,result={"videos":["a.mp4","b.mp4"]})
    item=job_store.get_job("abc123")
    assert item["progress"]==100
    assert item["result"]["videos"]==["a.mp4","b.mp4"]
