# File: main.py
import sys
import json
import os
from pathlib import Path
from dotenv import load_dotenv
from PyQt6.QtWidgets import QApplication
from utils.logger import logger
from gui.main_window import MainWindow

def load_config() -> dict:
    """Muat konfigurasi dari config.json."""
    config_path = Path("config.json")
    if config_path.exists():
        with open(config_path, "r") as f:
            return json.load(f)
    return {}

def main():
    logger.info("Memulai aplikasi AUTO VIDEO CLIPPER...")
    
    # Load env vars (GEMINI_API_KEY)
    load_dotenv()
    
    # Load setup config
    config = load_config()
    
    # Init QApplication (PyQt6)
    app = QApplication(sys.argv)
    
    # Setup style / dark mode
    app.setStyle("Fusion")
    
    window = MainWindow(config)
    window.show()
    
    sys.exit(app.exec())

if __name__ == "__main__":
    main()
