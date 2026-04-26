#!/usr/bin/env python3
"""
Download U2-Net TFLite model for background removal
"""

import urllib.request
import os

# Model URLs
U2NET_MODEL_URL = "https://github.com/xuebinqi/U-2-Net/releases/download/v1.0/u2net.tflite"
U2NETP_MODEL_URL = "https://github.com/xuebinqi/U-2-Net/releases/download/v1.0/u2netp.tflite"

# Target directory
ASSETS_DIR = os.path.join(os.path.dirname(__file__), "app", "src", "main", "assets")

def download_model(url, output_path):
    """Download model from URL"""
    print(f"Downloading from: {url}")
    print(f"Saving to: {output_path}")
    
    try:
        urllib.request.urlretrieve(url, output_path)
        print("Download completed successfully!")
        
        # Show file size
        size_mb = os.path.getsize(output_path) / (1024 * 1024)
        print(f"File size: {size_mb:.2f} MB")
        
    except Exception as e:
        print(f"Download failed: {e}")

if __name__ == "__main__":
    # Create assets directory if it doesn't exist
    os.makedirs(ASSETS_DIR, exist_ok=True)
    
    # Choose model (uncomment the one you want)
    MODEL_URL = U2NETP_MODEL_URL  # Smaller, faster (4.7MB)
    # MODEL_URL = U2NET_MODEL_URL  # Larger, more accurate (176MB)
    
    OUTPUT_PATH = os.path.join(ASSETS_DIR, "u2net.tflite")
    
    download_model(MODEL_URL, OUTPUT_PATH)
