# File: gui/main_window.py
import os
import json
from PyQt6.QtWidgets import (QMainWindow, QWidget, QVBoxLayout, QHBoxLayout, 
                             QPushButton, QLabel, QFileDialog, QProgressBar, QMessageBox, QListWidget)
from PyQt6.QtCore import Qt
from utils.logger import logger
from gui.worker_thread import AnalyzeWorker
from gui.timeline_widget import TimelineWidget
from gui.preview_widget import PreviewWidget
from gui.settings_dialog import SettingsDialog
from core.clip_exporter import ClipExporter

class MainWindow(QMainWindow):
    def __init__(self, config):
        super().__init__()
        self.config = config
        self.video_path = None
        self.clips = []
        
        self.setWindowTitle("AUTO VIDEO CLIPPER - V1.0")
        self.resize(800, 600)
        self.init_ui()

    def init_ui(self):
        main_widget = QWidget()
        layout = QVBoxLayout()
        
        # Header / Drag Drop region
        self.lbl_file = QLabel("Drag & Drop Video atau klik Browse...")
        self.lbl_file.setAlignment(Qt.AlignmentFlag.AlignCenter)
        self.lbl_file.setStyleSheet("border: 2px dashed #aaa; padding: 20px; font-size: 14px;")
        
        btn_browse = QPushButton("Buka Video (.mp4, .mkv)")
        btn_browse.clicked.connect(self.browse_video)
        
        btn_settings = QPushButton("⚙️ Settings")
        btn_settings.clicked.connect(self.open_settings)
        
        top_layout = QHBoxLayout()
        top_layout.addWidget(btn_browse)
        top_layout.addWidget(btn_settings)
        layout.addLayout(top_layout)
        layout.addWidget(self.lbl_file)
        
        # Analyze Button
        self.btn_analyze = QPushButton("🚀 ANALYZE & CLIP")
        self.btn_analyze.setStyleSheet("background-color: #28a745; color: white; font-weight: bold; padding: 10px;")
        self.btn_analyze.clicked.connect(self.start_analysis)
        self.btn_analyze.setEnabled(False)
        layout.addWidget(self.btn_analyze)
        
        # Progress & Status
        self.progress_bar = QProgressBar()
        self.progress_bar.setValue(0)
        self.lbl_status = QLabel("Status: Menunggu file video")
        layout.addWidget(self.progress_bar)
        layout.addWidget(self.lbl_status)
        
        # Timeline
        layout.addWidget(QLabel("── Timeline Skor ──"))
        self.timeline_widget = TimelineWidget()
        layout.addWidget(self.timeline_widget)
        
        # Results
        layout.addWidget(QLabel("── Hasil Clip (Pilih untuk preview) ──"))
        
        bottom_layout = QHBoxLayout()
        self.list_clips = QListWidget()
        self.list_clips.itemClicked.connect(self.on_clip_selected)
        
        self.preview = PreviewWidget()
        
        bottom_layout.addWidget(self.list_clips, 1)
        bottom_layout.addWidget(self.preview, 1)
        layout.addLayout(bottom_layout)
        
        # Export Actions
        self.btn_export = QPushButton("💾 Export Semua Clip")
        self.btn_export.clicked.connect(self.export_clips)
        self.btn_export.setEnabled(False)
        layout.addWidget(self.btn_export)
        
        main_widget.setLayout(layout)
        self.setCentralWidget(main_widget)

    def open_settings(self):
        dlg = SettingsDialog(self.config, self)
        if dlg.exec():
            api_key, new_config = dlg.get_updated_config()
            self.config = new_config
            if api_key:
                os.environ["GEMINI_API_KEY"] = api_key
            
            with open("config.json", "w") as f:
                json.dump(self.config, f, indent=2)
            QMessageBox.information(self, "Tersimpan", "Pengaturan berhasil disimpan.")

    def browse_video(self):
        fname, _ = QFileDialog.getOpenFileName(self, "Buka Video", "", "Video Files (*.mp4 *.mkv *.mov *.avi *.webm)")
        if fname:
            self.video_path = fname
            self.lbl_file.setText(f"File: {os.path.basename(fname)}")
            self.btn_analyze.setEnabled(True)

    def start_analysis(self):
        if not self.video_path: return
        
        self.btn_analyze.setEnabled(False)
        self.list_clips.clear()
        self.clips = []
        
        self.worker = AnalyzeWorker(self.video_path, self.config)
        self.worker.progress.connect(self.update_progress)
        self.worker.finished.connect(self.on_analysis_finished)
        self.worker.error.connect(self.on_error)
        self.worker.start()

    def update_progress(self, val, msg):
        self.progress_bar.setValue(val)
        self.lbl_status.setText(f"Status: {msg}")

    def on_analysis_finished(self, timeline, clips):
        self.timeline_widget.set_data(timeline)
        self.clips = clips
        
        for idx, c in enumerate(clips):
            self.list_clips.addItem(f"Clip {idx+1} | {c['start']}s - {c['end']}s | Skor: {c['score']}")
            
        self.btn_analyze.setEnabled(True)
        self.btn_export.setEnabled(True)
        self.lbl_status.setText("Selesai! Silakan preview atau export klip.")
        QMessageBox.information(self, "Selesai", f"Ditemukan {len(clips)} momen terbaik!")

    def on_error(self, err_msg):
        logger.error(f"Analysis error: {err_msg}")
        QMessageBox.critical(self, "Error", f"Terjadi kesalahan:\n{err_msg}")
        self.update_progress(0, "Error terjadi")
        self.btn_analyze.setEnabled(True)

    def on_clip_selected(self, item):
        idx = self.list_clips.row(item)
        clip = self.clips[idx]
        self.preview.load_clip_info(clip)

    def export_clips(self):
        if not self.clips or not self.video_path: return
        
        output_dir = self.config.get("output_folder", "./output")
        exporter = ClipExporter(output_dir)
        
        self.progress_bar.setValue(0)
        self.lbl_status.setText("Status: Mengekspor clip...")
        
        success_count = 0
        for idx, clip in enumerate(self.clips):
            success = exporter.export_clip(self.video_path, clip, idx, reencode=self.config.get("reencode", False), enable_fade=self.config.get("fade_enabled", True))
            if success: success_count += 1
            self.progress_bar.setValue(int(((idx+1)/len(self.clips))*100))
            
        QMessageBox.information(self, "Export Selesai", f"Berhasil mengekspor {success_count}/{len(self.clips)} clip ke {output_dir}")
        self.lbl_status.setText("Status: Export selesai.")
