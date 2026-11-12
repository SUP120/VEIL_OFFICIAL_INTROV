# Project Maintenance & Migration Scripts

This folder contains utility scripts used during project asset, encoding, and emoji migrations:

- `copy_lottie.py`: Utility to sync Lottie animation files into Android assets.
- `deep_fix_mojibake.py`: Fixed UTF-8 character encoding and mojibake issues in source files.
- `find_kotlin_errors.py`: Helper parser for extracting compilation issues from build logs.
- `fix_and_clean_emojis.py` / `fix_emojis.py`: Cleaned and normalized Unicode emoji sequences in strings.
- `fix_encoding.py` / `fix_veilhome_encoding.py`: Restored clean UTF-8 source encodings.
- `fix_screen.py` / `fix_veil_profile.py`: Scripted refactoring helpers.
- `fix_drawable_names.bat`: Renamed resource assets to match Android naming conventions (`a-z0-9_`).
- `resize_logo.ps1`: Resized app logos for mipmap densities.
