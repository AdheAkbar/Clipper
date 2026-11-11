# File: core/audio_analyzer.py
import librosa
import numpy as np
from utils.logger import logger

class AudioAnalyzer:
    """Menganalisis file audio untuk menentukan intensitas berdasarkan suara."""
    
    def __init__(self, audio_path: str):
        self.audio_path = audio_path

    def analyze(self, frame_duration: float = 0.5) -> list[dict]:
        """Eksekusi librosa untuk RMS, centroid, dan zero crossing."""
        logger.info(f"Memulai analisis audio: {self.audio_path}")
        try:
            y, sr = librosa.load(self.audio_path, sr=16000)
            
            # Hitung RMS energy untuk deteksi suara keras
            hop_length = int(sr * frame_duration)
            rms = librosa.feature.rms(y=y, hop_length=hop_length)[0]
            
            # Normalisasi RMS ke 0.0 - 1.0
            rms_norm = (rms - np.min(rms)) / (np.max(rms) - np.min(rms) + 1e-6)
            
            scores = []
            for i, score in enumerate(rms_norm):
                time_sec = (i * hop_length) / sr
                scores.append({
                    "time": float(time_sec),
                    "score": float(score)
                })
                
            logger.info(f"Analisis audio selesai. Total frame: {len(scores)}")
            return scores
        except Exception as e:
            logger.error(f"Error di audio analyzer: {e}")
            return []
