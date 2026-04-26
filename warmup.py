"""
Warmup script to download rembg AI model in advance.
Run this before starting the server to avoid first-request delays.
"""

from PIL import Image
import io
from rembg import remove

print("Downloading rembg AI model (~180MB)...")
print("This will take 1-2 minutes depending on your internet speed.")
print()

# Create a small dummy image to trigger model download
dummy = Image.new('RGB', (100, 100), color='red')
dummy_bytes = io.BytesIO()
dummy.save(dummy_bytes, format='PNG')
dummy_bytes.seek(0)
dummy_img = Image.open(dummy_bytes)

# This triggers model download
print("Loading AI model...")
try:
    result = remove(dummy_img)
    print()
    print("✅ Model downloaded successfully!")
    print("You can now start the server with: python app.py")
except Exception as e:
    print(f"❌ Error: {e}")
    print("Please check your internet connection and try again.")
