"""Optional YouTube upload. Requires the channel owner's OAuth consent."""
import json, pickle
from pathlib import Path
from google.auth.transport.requests import Request
from google_auth_oauthlib.flow import InstalledAppFlow
from googleapiclient.discovery import build
from googleapiclient.http import MediaFileUpload
from .config import settings

SCOPES = ["https://www.googleapis.com/auth/youtube.upload"]

def upload_video(video_path: str, metadata_path: str, privacy_status: str = "private") -> dict:
    if not settings.youtube_client_secrets_file.is_file():
        raise RuntimeError("YouTube OAuth client file is missing. Add client_secret.json locally; never commit it.")
    metadata=json.loads(Path(metadata_path).read_text(encoding="utf-8"))
    credentials=None
    if settings.youtube_token_file.is_file():
        with settings.youtube_token_file.open("rb") as token:
            credentials=pickle.load(token)
    if credentials and credentials.expired and credentials.refresh_token:
        credentials.refresh(Request())
    if not credentials or not credentials.valid:
        flow=InstalledAppFlow.from_client_secrets_file(str(settings.youtube_client_secrets_file),SCOPES)
        credentials=flow.run_local_server(port=0,open_browser=True)
    settings.youtube_token_file.parent.mkdir(parents=True,exist_ok=True)
    with settings.youtube_token_file.open("wb") as token:
        pickle.dump(credentials,token)
    service=build("youtube","v3",credentials=credentials)
    body={"snippet":{"title":metadata["title"][:100],
      "description":metadata["description"][:5000],
      "tags":metadata.get("tags",[])[:500],"categoryId":"17"},
      "status":{"privacyStatus":privacy_status,"selfDeclaredMadeForKids":False}}
    # YouTube categoryId 17 is Sports.
    request=service.videos().insert(part="snippet,status",body=body,
      media_body=MediaFileUpload(video_path,chunksize=-1,resumable=True))
    response=request.execute()
    return {"video_id":response.get("id"),"url":"https://www.youtube.com/watch?v="+response.get("id","")}
