# File: gui/settings_dialog.py
from PyQt6.QtWidgets import QDialog, QVBoxLayout, QFormLayout, QLineEdit, QSpinBox, QDoubleSpinBox, QDialogButtonBox
import os

class SettingsDialog(QDialog):
    def __init__(self, config, parent=None):
        super().__init__(parent)
        self.setWindowTitle("Pengaturan Auto Clipper")
        self.config = config
        
        layout = QVBoxLayout()
        form = QFormLayout()
        
        # API Key
        self.api_input = QLineEdit(os.getenv("GEMINI_API_KEY", ""))
        self.api_input.setPlaceholderText("Masukkan Gemini API Key")
        form.addRow("Gemini API Key:", self.api_input)
        
        # Clip Count
        self.clip_count = QSpinBox()
        self.clip_count.setRange(1, 20)
        self.clip_count.setValue(self.config.get("default_clip_count", 5))
        form.addRow("Jumlah Clip:", self.clip_count)
        
        # Audio Weight
        self.w_audio = QDoubleSpinBox()
        self.w_audio.setRange(0.0, 1.0)
        self.w_audio.setSingleStep(0.1)
        self.w_audio.setValue(self.config.get("weight_audio", 0.3))
        form.addRow("Bobot Audio:", self.w_audio)
        
        # Visual Weight
        self.w_visual = QDoubleSpinBox()
        self.w_visual.setRange(0.0, 1.0)
        self.w_visual.setSingleStep(0.1)
        self.w_visual.setValue(self.config.get("weight_visual", 0.3))
        form.addRow("Bobot Visual:", self.w_visual)
        
        # AI Weight
        self.w_ai = QDoubleSpinBox()
        self.w_ai.setRange(0.0, 1.0)
        self.w_ai.setSingleStep(0.1)
        self.w_ai.setValue(self.config.get("weight_ai", 0.4))
        form.addRow("Bobot AI/Teks:", self.w_ai)
        
        layout.addLayout(form)
        
        btn_box = QDialogButtonBox(QDialogButtonBox.StandardButton.Ok | QDialogButtonBox.StandardButton.Cancel)
        btn_box.accepted.connect(self.accept)
        btn_box.rejected.connect(self.reject)
        layout.addWidget(btn_box)
        
        self.setLayout(layout)

    def get_updated_config(self):
        self.config["default_clip_count"] = self.clip_count.value()
        self.config["weight_audio"] = self.w_audio.value()
        self.config["weight_visual"] = self.w_visual.value()
        self.config["weight_ai"] = self.w_ai.value()
        return self.api_input.text(), self.config
