FROM python:3.9-slim

WORKDIR /app

# Install system dependencies for Pillow
RUN apt-get update && apt-get install -y \
    libjpeg-dev \
    libpng-dev \
    zlib1g-dev \
    && rm -rf /var/lib/apt/lists/*

# Copy requirements and install Python dependencies
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Copy application code
COPY . .

# Expose port (Render uses PORT env var)
EXPOSE 5000

# Run the application with gunicorn (production server)
CMD ["gunicorn", "app:app", "--bind", "0.0.0.0:${PORT:-5000}", "--workers", "1"]
