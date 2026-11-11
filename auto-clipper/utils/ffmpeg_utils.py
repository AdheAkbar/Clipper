# File: utils/ffmpeg_utils.py
import ffmpeg
import os
from pathlib import Path
from utils.logger import logger

def extract_audio(video_path: str, output_path: str) -> bool:
    """Ekstrak audio dari video menjadi format .wav untuk analisis."""
    try:
        logger.info(f"Mengekstrak audio dari {video_path} ke {output_path}...")
        stream = ffmpeg.input(video_path)
        stream = ffmpeg.output(stream, output_path, acodec='pcm_s16le', ac=1, ar='16k')
        ffmpeg.run(stream, overwrite_output=True, quiet=True)
        logger.info("Ekstraksi audio selesai.")
        return True
    except ffmpeg.Error as e:
        logger.error(f"Error ekstraksi audio: {e.stderr.decode() if e.stderr else str(e)}")
        return False

def get_video_info(video_path: str) -> dict:
    """Dapatkan resolusi, durasi, dan framerate video."""
    try:
        probe = ffmpeg.probe(video_path)
        video_stream = next((stream for stream in probe['streams'] if stream['codec_type'] == 'video'), None)
        if not video_stream:
            raise ValueError("Tidak ditemukan stream video")
            
        width = int(video_stream['width'])
        height = int(video_stream['height'])
        
        # Handle framerate (e.g., "30000/1001" or "30/1")
        fps_str = video_stream.get('r_frame_rate', '30/1')
        num, den = map(int, fps_str.split('/'))
        fps = num / den if den != 0 else 30.0
        
        duration = float(probe['format'].get('duration', 0.0))
        
        return {"width": width, "height": height, "fps": fps, "duration": duration}
    except Exception as e:
        logger.error(f"Gagal membaca info video: {e}")
        return {"width": 1280, "height": 720, "fps": 30.0, "duration": 0.0}
