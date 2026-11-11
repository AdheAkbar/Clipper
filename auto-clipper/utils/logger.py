# File: utils/logger.py
import logging
import os
from pathlib import Path

def setup_logger(name: str) -> logging.Logger:
    """Setup dan kembalikan instance logger yang menulis ke file dan konsol."""
    log_dir = Path("./logs")
    log_dir.mkdir(exist_ok=True)
    
    logger = logging.getLogger(name)
    logger.setLevel(logging.DEBUG)
    
    # Hindari menambah handler berkali-kali jika logger sudah ada
    if not logger.handlers:
        # File handler
        fh = logging.FileHandler(log_dir / "app.log", encoding="utf-8")
        fh.setLevel(logging.DEBUG)
        
        # Console handler
        ch = logging.StreamHandler()
        ch.setLevel(logging.INFO)
        
        # Formatter
        formatter = logging.Formatter('%(asctime)s - %(name)s - %(levelname)s - %(message)s')
        fh.setFormatter(formatter)
        ch.setFormatter(formatter)
        
        logger.addHandler(fh)
        logger.addHandler(ch)
        
    return logger

logger = setup_logger("AutoClipper")
