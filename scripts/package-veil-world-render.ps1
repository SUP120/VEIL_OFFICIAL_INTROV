$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$templates = Join-Path $root 'world/deploy/render'
$pack = Join-Path $root 'app/src/veilWorld/assets/veil_world.pck'
$output = Join-Path $root 'world/build/render-upload'
New-Item -ItemType Directory -Force -Path $output | Out-Null
$files = @(
    (Join-Path $templates 'render.yaml')
    (Join-Path $templates 'Dockerfile')
    $pack
)
$archive = Join-Path $output 'veil-world-render.zip'
Compress-Archive -LiteralPath $files -DestinationPath $archive -Force
Get-Item -LiteralPath $archive | Select-Object FullName, Length
