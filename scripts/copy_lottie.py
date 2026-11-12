"""
copy_lottie.py - copies profilepageani.lottie to assets folder
Run from project root: python copy_lottie.py
"""
import shutil, os

src  = r"app\src\main\res\profilepageani.lottie"
dst  = r"app\src\main\assets\profilepageani.lottie"

os.makedirs(os.path.dirname(dst), exist_ok=True)
shutil.copy2(src, dst)
print(f"Copied {src} → {dst}")
