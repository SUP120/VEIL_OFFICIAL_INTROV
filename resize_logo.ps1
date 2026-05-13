Add-Type -AssemblyName System.Drawing

$src = "d:\#2 2026\APPS DEVELOPMENT\Inrovert Matching Game\to_read\logoVEIL.png"
$baseDir = "d:\#2 2026\APPS DEVELOPMENT\Inrovert Matching Game\app\src\main\res"

$img = [System.Drawing.Image]::FromFile($src)

$sizes = @{
    "mipmap-mdpi"    = 48
    "mipmap-hdpi"    = 72
    "mipmap-xhdpi"   = 96
    "mipmap-xxhdpi"  = 144
    "mipmap-xxxhdpi" = 192
}

foreach ($folder in $sizes.Keys) {
    $size = $sizes[$folder]
    $outDir = Join-Path $baseDir $folder

    Remove-Item (Join-Path $outDir "ic_launcher.webp") -ErrorAction SilentlyContinue
    Remove-Item (Join-Path $outDir "ic_launcher_round.webp") -ErrorAction SilentlyContinue

    $bmp = New-Object System.Drawing.Bitmap($size, $size)
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $g.SmoothingMode    = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $g.DrawImage($img, 0, 0, $size, $size)
    $g.Dispose()

    $outPath  = Join-Path $outDir "ic_launcher.png"
    $outPath2 = Join-Path $outDir "ic_launcher_round.png"
    $bmp.Save($outPath,  [System.Drawing.Imaging.ImageFormat]::Png)
    $bmp.Save($outPath2, [System.Drawing.Imaging.ImageFormat]::Png)
    $bmp.Dispose()

    Write-Host "Saved ${size}x${size} -> $folder"
}

$img.Dispose()
Write-Host "All icons generated successfully!"
