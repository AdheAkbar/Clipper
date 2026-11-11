# File: core/score_merger.py
import numpy as np
from utils.logger import logger

class ScoreMerger:
    """Menggabungkan skor Audio, Visual, dan AI menjadi SATU timeline tunggal."""
    
    def __init__(self, w_audio=0.3, w_visual=0.3, w_ai=0.4):
        self.w_audio = w_audio
        self.w_visual = w_visual
        self.w_ai = w_ai

    def merge(self, audio_scores, visual_scores, ai_scores, duration):
        """Gabungkan berdasar timeline per detik."""
        logger.info("Menggabungkan dan menganalisis skor akhir...")
        timeline = []
        
        for t in range(int(duration)):
            # Cari nilai rata-rata tiap domain dalam detik ke-t
            a_sc = np.mean([s['score'] for s in audio_scores if int(s['time']) == t]) if audio_scores else 0.0
            v_sc = np.mean([s['score'] for s in visual_scores if int(s['time']) == t]) if visual_scores else 0.0
            
            # AI score berlaku untuk rentang
            ai_sc = 0.0
            ai_reason = "Tidak ada"
            for s in ai_scores:
                if s['start'] <= t <= s['end']:
                    ai_sc = s['score']
                    ai_reason = s.get('reason', 'No reason')
                    break
                    
            if np.isnan(a_sc): a_sc = 0.0
            if np.isnan(v_sc): v_sc = 0.0
            
            final = (a_sc * self.w_audio) + (v_sc * self.w_visual) + (ai_sc * self.w_ai)
            
            timeline.append({
                "time": t,
                "score": final,
                "a_sc": a_sc, "v_sc": v_sc, "ai_sc": ai_sc,
                "reason": ai_reason
            })
            
        return timeline
        
    def find_peaks(self, timeline, num_clips=5, min_dur=10, max_dur=60):
        """Mencari momen peak berdasarkan skor akhir."""
        logger.info(f"Mencari {num_clips} klip terbaik...")
        
        # Sort detik berdasarkan skor tertinggi (Peak analysis sederhana)
        sorted_timeline = sorted(timeline, key=lambda x: x['score'], reverse=True)
        
        clips = []
        for point in sorted_timeline:
            if len(clips) >= num_clips:
                break
                
            t = point['time']
            # Cek overlap dengan clip yang sudah ada
            overlap = any((c['start'] <= t <= c['end']) for c in clips)
            if overlap:
                continue
                
            start = max(0, t - (min_dur // 2))
            end = start + min_dur
            
            clips.append({
                "start": start,
                "end": end,
                "score": round(point['score'], 2),
                "reason": point['reason'],
                "scores": f"A:{point['a_sc']:.2f}|V:{point['v_sc']:.2f}|AI:{point['ai_sc']:.2f}"
            })
            
        return sorted(clips, key=lambda x: x['start'])
