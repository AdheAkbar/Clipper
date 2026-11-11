# File: core/clip_exporter.py
import os
import ffmpeg
from pathlib import Path
from utils.logger import logger

class ClipExporter:
    """Memotong dan menyimpan clip menggunakan FFmpeg."""
    
    def __init__(self, output_dir: str):
        self.output_dir = Path(output_dir)
        self.output_dir.mkdir(parents=True, exist_ok=True)
        
    def export_clip(self, video_path: str, clip: dict, idx: int, reencode: bool = False, enable_fade: bool = False):
        """Export 1 klip ke file output."""
        output_file = self.output_dir / f"clip_{idx+1:02d}_skor{clip['score']}_t{clip['start']}.mp4"
        
        logger.info(f"Mengekspor: {output_file.name} [{clip['start']}s -> {clip['end']}s]")
        
        try:
            stream = ffmpeg.input(video_path, ss=clip['start'], to=clip['end'])
            
            if reencode:
                if enable_fade:
                    fade_dur = 0.5
                    v_stream = stream.video.filter('fade', type='in', start_time=clip['start'], duration=fade_dur) \
                                           .filter('fade', type='out', start_time=clip['end']-fade_dur, duration=fade_dur)
                    a_stream = stream.audio.filter('afade', type='in', start_time=clip['start'], duration=fade_dur) \
                                           .filter('afade', type='out', start_time=clip['end']-fade_dur, duration=fade_dur)
                    output = ffmpeg.output(v_stream, a_stream, str(output_file), vcodec='libx264', acodec='aac')
                else:
                    output = ffmpeg.output(stream, str(output_file), vcodec='libx264', acodec='aac')
            else:
                # Fast copy
                output = ffmpeg.output(stream, str(output_file), c='copy')
                
            ffmpeg.run(output, overwrite_output=True, quiet=True)
            return True
            
        except Exception as e:
            logger.error(f"Gagal ekstrak klip: {e}")
            return False
