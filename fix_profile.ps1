# Removes the leaked old ProfileTab body (lines 1090-1523) from VeilHomeScreen.kt
$file = "app\src\main\java\com\ns\findyourintroverfriend\home\VeilHomeScreen.kt"
$lines = [System.IO.File]::ReadAllLines($file)
Write-Host "Total lines before: $($lines.Length)"

# Keep lines 1..1088 (0-indexed: 0..1087) and 1524.. (0-indexed: 1524..)
# Line numbers above are 1-indexed from the view tool
$keep = @()
$keep += $lines[0..1087]        # lines 1-1088 (0-indexed 0..1087)
$keep += $lines[1523..($lines.Length - 1)]  # line 1524 onwards (0-indexed 1523..)

[System.IO.File]::WriteAllLines($file, $keep)
Write-Host "Total lines after:  $($keep.Length)"
Write-Host "Done. Leaked old ProfileTab body removed."
