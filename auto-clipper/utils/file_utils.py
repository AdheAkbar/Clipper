# File: utils/file_utils.py
import os
from pathlib import Path
from utils.logger import logger

def validate_video_file(file_path: str) -> bool:
    """Validasi apakah file ada dan ekstensinya didukung."""
    path = Path(file_path)
    if not path.exists() or not path.is_file():
        logger.error(f"File tidak ditemukan: {file_path}")
        return False
        
    valid_extensions = {".mp4", ".mkv", ".mov", ".avi", ".webm"}
    if path.suffix.lower() not in valid_extensions:
        logger.error(f"Format file tidak didukung: {path.suffix}")
        return False
        
    return True

def ensure_directory(dir_path: str) -> Path:
    """Pastikan direktori ada, jika belum buat baru."""
    path = Path(dir_path)
    path.mkdir(parents=True, exist_ok=True)
    return path

def get_cache_dir() -> Path:
    """Dapatkan folder untuk menyimpan file sementara (audio terpisah, dll)."""
    return ensure_directory("./.cache")
