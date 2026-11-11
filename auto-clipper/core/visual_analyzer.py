# File: core/visual_analyzer.py
import cv2
import numpy as np
from utils.logger import logger

class VisualAnalyzer:
    """Menganalisis video per frame untuk deteksi perubahan visual dan motion."""
    
    def __init__(self, video_path: str):
        self.video_path = video_path

    def analyze(self, interval_sec: float = 0.5) -> list[dict]:
        """Gunakan OpenCV untuk membaca video dan mendeteksi motion & perubahan."""
        logger.info(f"Memulai analisis visual: {self.video_path}")
        cap = cv2.VideoCapture(self.video_path)
        
        if not cap.isOpened():
            logger.error("Gagal membuka video untuk analisis visual.")
            return []
            
        fps = cap.get(cv2.CAP_PROP_FPS)
        frame_skip = int(fps * interval_sec)
        if frame_skip <= 0: frame_skip = 1
        
        scores = []
        prev_gray = None
        current_frame = 0
        
        try:
            while cap.isOpened():
                ret, frame = cap.read()
                if not ret:
                    break
                    
                if current_frame % frame_skip == 0:
                    # Konversi ke Grayscale untuk analisis motion/difference
                    gray = cv2.cvtColor(frame, cv2.COLOR_BGR2GRAY)
                    
                    score = 0.0
                    if prev_gray is not None:
                        # Simple frame difference scoring
                        diff = cv2.absdiff(gray, prev_gray)
                        score = float(np.mean(diff) / 255.0)
                        
                    time_sec = current_frame / fps
                    scores.append({
                        "time": float(time_sec),
                        "score": score
                    })
                    prev_gray = gray
                    
                current_frame += 1
                
        except Exception as e:
            logger.error(f"Error di visual analyzer: {e}")
        finally:
            cap.release()
            
        # Normalize
        if scores:
            max_score = max([s['score'] for s in scores]) or 1.0
            for s in scores:
                s['score'] = s['score'] / max_score
                
        logger.info("Analisis visual selesai.")
        return scores
