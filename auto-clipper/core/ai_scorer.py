# File: core/ai_scorer.py
import os
import json
import time
import google.generativeai as genai
from utils.logger import logger

class AIScorer:
    """Mengirim transkrip ke Gemini API untuk dinilai secara otomatis."""
    
    def __init__(self):
        self.api_key = os.getenv("GEMINI_API_KEY")
        if self.api_key and self.api_key != "isi_api_key_gemini_disini":
            genai.configure(api_key=self.api_key)
            self.model = genai.GenerativeModel("gemini-1.5-flash")
            self.enabled = True
        else:
            logger.warning("API Key Gemini tidak dikonfigurasi. Skor AI dinonaktifkan.")
            self.enabled = False

    def chunk_transcript(self, segments: list[dict], chunk_duration: float = 30.0) -> list[dict]:
        chunks = []
        current_chunk = []
        current_start = 0.0
        
        for seg in segments:
            if not current_chunk:
                current_start = seg['start']
                
            current_chunk.append(seg['text'])
            
            if seg['end'] - current_start >= chunk_duration:
                chunks.append({
                    "start": current_start,
                    "end": seg['end'],
                    "text": " ".join(current_chunk)
                })
                current_chunk = []
                
        # Sisa
        if current_chunk and segments:
            chunks.append({
                "start": current_start,
                "end": segments[-1]['end'],
                "text": " ".join(current_chunk)
            })
            
        return chunks

    def score_transcript(self, segments: list[dict]) -> list[dict]:
        if not self.enabled or not segments:
            return [{"start": 0.0, "end": 0.0, "score": 0.5, "reason": "AI Dinonaktifkan"}]
            
        chunks = self.chunk_transcript(segments)
        ai_scores = []
        
        prompt_sys = (
            "Kamu adalah analis video. Beri skor 0.0-1.0 untuk cuplikan ini berdasarkan seberapa "
            "menarik/seru/penting momennya. Balas HANYA dengan JSON array: "
            '[{"score": 0.85, "reason": "Momen epik singkat"}]'
        )
        
        for idx, chunk in enumerate(chunks):
            logger.info(f"Mengirim chunk {idx+1}/{len(chunks)} ke Gemini API...")
            try:
                # Hindari rate limit
                time.sleep(1)
                
                response = self.model.generate_content(
                    f"{prompt_sys}\n\nTranskrip ({chunk['start']}-{chunk['end']}s): {chunk['text']}"
                )
                
                # Coba parse respon
                text = response.text.replace("```json", "").replace("```", "").strip()
                result = json.loads(text)
                
                if isinstance(result, list) and len(result) > 0:
                    score = float(result[0].get('score', 0.5))
                    reason = result[0].get('reason', 'Teranalisis')
                else:
                    score = 0.5
                    reason = "Format parsial"
                    
                ai_scores.append({
                    "start": float(chunk['start']),
                    "end": float(chunk['end']),
                    "score": score,
                    "reason": reason
                })
                
            except Exception as e:
                logger.error(f"Error memanggil Gemini API pada chunk {idx}: {e}")
                ai_scores.append({
                    "start": float(chunk['start']),
                    "end": float(chunk['end']),
                    "score": 0.5,
                    "reason": "API Error"
                })
                
        return ai_scores
