# Use Ubuntu 22.04 - High compatibility for AI models
FROM ubuntu:22.04

# Avoid interactive prompts during build
ENV DEBIAN_FRONTEND=noninteractive

# Install Python and system-level libraries
RUN apt-get update && apt-get install -y \
    python3.10 \
    python3-pip \
    libgl1 \
    libglib2.0-0 \
    libjpeg-dev \
    libpng-dev \
    zlib1g-dev \
    libgomp1 \
    && rm -rf /var/lib/apt/lists/*

# Set the working directory
WORKDIR /app

# Create a non-root user for Hugging Face security
RUN useradd -m -u 1000 user
ENV HOME=/home/user \
    PATH=/home/user/.local/bin:$PATH \
    U2NET_HOME=/app/.u2net

# Copy only requirements first to leverage Docker cache
COPY requirements.txt .
RUN pip3 install --no-cache-dir -r requirements.txt

# Copy the rest of the app and set ownership
COPY --chown=user . .

# Create necessary directories and set full permissions
RUN mkdir -p temp .u2net && chown -R user:user /app && chmod -R 777 /app

# Switch to the non-root user
USER user

# Expose port 7860 (Hugging Face default)
EXPOSE 7860

# Run the app with gunicorn
CMD ["gunicorn", "app:app", "--bind", "0.0.0.0:7860", "--workers", "1", "--timeout", "300"]
