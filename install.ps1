$ErrorActionPreference = "Stop"
$repoUrl = "https://github.com/Jakeschincariol/youtube-agent-skill.git"
$temp = Join-Path $env:TEMP ("youtube-agent-skill-" + [guid]::NewGuid().ToString("N"))
$skillsDir = Join-Path (Get-Location) ".claude\skills"
$voiceDir = Join-Path (Get-Location) ".claude\youtube"

if (-not (Get-Command git -ErrorAction SilentlyContinue)) { throw "Git is required. Install Git for Windows, then rerun this script." }
New-Item -ItemType Directory -Force -Path $skillsDir | Out-Null
New-Item -ItemType Directory -Force -Path $voiceDir | Out-Null

try {
  git clone --depth 1 $repoUrl $temp
  if ($LASTEXITCODE -ne 0) { throw "Could not clone the upstream YouTube Agent repository." }
  $skillFolders = Get-ChildItem (Join-Path $temp "skills") -Directory -Filter "yt-*"
  if (-not $skillFolders) { throw "No upstream yt-* skill folders were found." }
  foreach ($folder in $skillFolders) {
    $destination = Join-Path $skillsDir $folder.Name
    if (Test-Path $destination) { Remove-Item -Recurse -Force $destination }
    Copy-Item -Recurse $folder.FullName $destination
    Write-Host "Installed $($folder.Name)"
  }
  Copy-Item (Join-Path (Get-Location) "templates\voice.md") (Join-Path $voiceDir "voice.md") -Force
  Write-Host "Installed Preet Sports voice profile."
  Write-Host "Done. Open this folder in Claude Code and try /yt-script."
}
finally {
  if (Test-Path $temp) { Remove-Item -Recurse -Force $temp }
}
