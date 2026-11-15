"""Durable local job store. SQLite keeps jobs across application restarts."""
import json, sqlite3
from datetime import datetime, timezone
from pathlib import Path
from .config import settings

DB_PATH = settings.data_dir / "studio.sqlite3"

def connect():
    DB_PATH.parent.mkdir(parents=True, exist_ok=True)
    db=sqlite3.connect(DB_PATH, timeout=30)
    db.row_factory=sqlite3.Row
    db.execute("""CREATE TABLE IF NOT EXISTS jobs (
      id TEXT PRIMARY KEY, match_name TEXT NOT NULL, payload TEXT NOT NULL,
      status TEXT NOT NULL, progress INTEGER NOT NULL DEFAULT 0,
      stage TEXT NOT NULL DEFAULT 'queued', result TEXT, error TEXT,
      created_at TEXT NOT NULL, updated_at TEXT NOT NULL
    )""")
    db.commit()
    return db

def create_job(job_id, req):
    now=datetime.now(timezone.utc).isoformat()
    with connect() as db:
        db.execute("INSERT INTO jobs(id,match_name,payload,status,progress,stage,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?)",
          (job_id,req.match_name,req.model_dump_json(),"queued",0,"Waiting for local worker",now,now))

def get_job(job_id):
    with connect() as db:
        row=db.execute("SELECT * FROM jobs WHERE id=?",(job_id,)).fetchone()
    if not row: return None
    item=dict(row)
    item["result"]=json.loads(item["result"]) if item["result"] else None
    return item

def next_job():
    with connect() as db:
        db.execute("BEGIN IMMEDIATE")
        row=db.execute("SELECT id,payload FROM jobs WHERE status='queued' ORDER BY created_at LIMIT 1").fetchone()
        if not row: return None
        now=datetime.now(timezone.utc).isoformat()
        db.execute("UPDATE jobs SET status='running',stage='Starting production',progress=2,updated_at=? WHERE id=?",(now,row["id"]))
        return {"id":row["id"],"payload":row["payload"]}

def update_job(job_id, status=None, stage=None, progress=None, result=None, error=None):
    fields=[]; values=[]
    for key,val in (("status",status),("stage",stage),("progress",progress),("result",json.dumps(result,ensure_ascii=False) if result is not None else None),("error",error)):
        if val is not None: fields.append(f"{key}=?"); values.append(val)
    fields.append("updated_at=?"); values.append(datetime.now(timezone.utc).isoformat()); values.append(job_id)
    with connect() as db: db.execute(f"UPDATE jobs SET {','.join(fields)} WHERE id=?",values)

def recover_interrupted():
    now=datetime.now(timezone.utc).isoformat()
    with connect() as db:
        db.execute("UPDATE jobs SET status='queued',stage='Recovered after restart',updated_at=? WHERE status='running'",(now,))
