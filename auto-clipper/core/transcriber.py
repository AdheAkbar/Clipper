# File: core/transcriber.py
import whisper
from utils.logger import logger

class Transcriber:
    """Menggunakan model Whisper lokal untuk Speech-to-Text."""
    
    def __init__(self, model_name: str = "base"):
        self.model_name = model_name
        self.model = None

    def load_model(self):
        """Memuat model Whisper."""
        if self.model is None:
            logger.info(f"Memuat model Whisper '{self.model_name}' (Bisa memakan waktu untuk download pertama)...")
            self.model = whisper.load_model(self.model_name)
            logger.info("Model Whisper berhasil dimuat.")

    def transcribe(self, audio_path: str) -> list[dict]:
        """Hasilkan transkrip berisi segmen teks ber-timestamp."""
        try:
            self.load_model()
            logger.info(f"Mulai transkripsi: {audio_path}")
            result = self.model.transcribe(audio_path)
            
            segments = []
            for seg in result.get('segments', []):
                segments.append({
                    "start": seg['start'],
                    "end": seg['end'],
                    "text": seg['text'].strip()
                })
                
            logger.info(f"Transkripsi selesai. Jumlah segmen: {len(segments)}")
            return segments
            
        except Exception as e:
            logger.error(f"Error saat transkripsi Whisper: {e}")
            return []
