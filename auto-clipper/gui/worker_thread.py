# File: gui/worker_thread.py
from PyQt6.QtCore import QThread, pyqtSignal
from utils.ffmpeg_utils import extract_audio, get_video_info
from core.audio_analyzer import AudioAnalyzer
from core.visual_analyzer import VisualAnalyzer
from core.transcriber import Transcriber
from core.ai_scorer import AIScorer
from core.score_merger import ScoreMerger
from core.clip_exporter import ClipExporter
from utils.file_utils import get_cache_dir
import os

class AnalyzeWorker(QThread):
    progress = pyqtSignal(int, str)
    finished = pyqtSignal(list, list) # timeline, clips
    error = pyqtSignal(str)

    def __init__(self, video_path, config):
        super().__init__()
        self.video_path = video_path
        self.config = config

    def run(self):
        try:
            self.progress.emit(5, "Membaca info video...")
            info = get_video_info(self.video_path)
            
            cache_dir = get_cache_dir()
            audio_cache = os.path.join(cache_dir, "temp_audio.wav")
            
            self.progress.emit(10, "Mengekstrak audio via FFmpeg...")
            if not extract_audio(self.video_path, audio_cache):
                self.error.emit("Gagal mengekstrak audio!")
                return
                
            self.progress.emit(25, "Menganalisis Visual...")
            v_analyzer = VisualAnalyzer(self.video_path)
            v_scores = v_analyzer.analyze(interval_sec=1.0)
            
            self.progress.emit(45, "Menganalisis Audio...")
            a_analyzer = AudioAnalyzer(audio_cache)
            a_scores = a_analyzer.analyze(frame_duration=1.0)
            
            self.progress.emit(60, "Transkripsi AI (Whisper)...")
            transcriber = Transcriber(self.config.get("whisper_model", "base"))
            segments = transcriber.transcribe(audio_cache)
            
            self.progress.emit(80, "AI Scoring (Gemini API)...")
            ai_scorer = AIScorer()
            ai_scores = ai_scorer.score_transcript(segments)
            
            self.progress.emit(90, "Menggabungkan skor & mencari clip terbaik...")
            merger = ScoreMerger(
                w_audio=self.config.get("weight_audio", 0.3),
                w_visual=self.config.get("weight_visual", 0.3),
                w_ai=self.config.get("weight_ai", 0.4)
            )
            timeline = merger.merge(a_scores, v_scores, ai_scores, info['duration'])
            clips = merger.find_peaks(
                timeline, 
                num_clips=self.config.get("default_clip_count", 5),
                min_dur=self.config.get("min_duration", 10),
                max_dur=self.config.get("max_duration", 60)
            )
            
            self.progress.emit(100, "Selesai!")
            self.finished.emit(timeline, clips)
            
        except Exception as e:
            self.error.emit(str(e))
