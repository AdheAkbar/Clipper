# File: gui/preview_widget.py
from PyQt6.QtWidgets import QWidget, QVBoxLayout, QLabel
from PyQt6.QtCore import Qt

class PreviewWidget(QWidget):
    """
    Mockup Preview Widget. Untuk produksi penuh, gunakan QtMultimedia atau libvlc.
    """
    def __init__(self, parent=None):
        super().__init__(parent)
        layout = QVBoxLayout()
        
        self.lbl_title = QLabel("🎬 Preview Clip")
        self.lbl_title.setAlignment(Qt.AlignmentFlag.AlignCenter)
        self.lbl_title.setStyleSheet("font-size: 16px; font-weight: bold; background: #000; color: #fff;")
        self.lbl_title.setMinimumHeight(200)
        
        self.lbl_info = QLabel("Pilih clip hasil untuk diputar")
        self.lbl_info.setWordWrap(True)
        
        layout.addWidget(self.lbl_title)
        layout.addWidget(self.lbl_info)
        self.setLayout(layout)

    def load_clip_info(self, clip):
        self.lbl_title.setText(f"▶ Memutar Clip...\nStart: {clip['start']}s | End: {clip['end']}s\nScore: {clip['score']}")
        self.lbl_info.setText(f"Alasan AI: {clip['reason']}\nDetail: {clip['scores']}")
