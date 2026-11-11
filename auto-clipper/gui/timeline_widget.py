# File: gui/timeline_widget.py
from PyQt6.QtWidgets import QWidget
from PyQt6.QtGui import QPainter, QColor, QPen
from PyQt6.QtCore import Qt

class TimelineWidget(QWidget):
    def __init__(self, parent=None):
        super().__init__(parent)
        self.setMinimumHeight(100)
        self.timeline_data = []

    def set_data(self, timeline_data):
        self.timeline_data = timeline_data
        self.update()

    def paintEvent(self, event):
        painter = QPainter(self)
        painter.setRenderHint(QPainter.RenderHint.Antialiasing)
        
        # Background
        painter.fillRect(self.rect(), QColor("#1e1e1e"))
        
        if not self.timeline_data:
            painter.setPen(QPen(Qt.GlobalColor.white))
            painter.drawText(self.rect(), Qt.AlignmentFlag.AlignCenter, "Timeline Kosong")
            return
            
        width = self.width()
        height = self.height()
        
        total_time = len(self.timeline_data)
        if total_time == 0: return
        
        # Draw bars
        for idx, point in enumerate(self.timeline_data):
            x = int((idx / total_time) * width)
            bar_height = int(point['score'] * height)
            y = height - bar_height
            
            # Warnai berdasar skor
            if point['score'] > 0.8:
                color = QColor("#00ff00") # Hijau
            elif point['score'] > 0.5:
                color = QColor("#ffff00") # Kuning
            else:
                color = QColor("#555555") # Abu-abu
                
            painter.fillRect(x, y, max(1, int(width/total_time)+1), bar_height, color)
