// DOM Elements - with safe access
const uploadArea = document.getElementById('upload-area');
const fileInput = document.getElementById('file-input');
const uploadSection = document.getElementById('upload-section');
const cropSection = document.getElementById('crop-section');
const previewSection = document.getElementById('preview-section');
const loadingSection = document.getElementById('loading-section');
const resultSection = document.getElementById('result-section');
const errorSection = document.getElementById('error-section');
const cropImage = document.getElementById('crop-image');
const cropBox = document.getElementById('crop-box');
const cropWrapper = document.getElementById('crop-wrapper');
const cropDoneBtn = document.getElementById('crop-done-btn');
const cropAutoBtn = document.getElementById('crop-auto-btn');
const cropCancelBtn = document.getElementById('crop-cancel-btn');
const adjustCropBtn = document.getElementById('adjust-crop-btn');
const resultImage = document.getElementById('result-image');
const generateBtn = document.getElementById('generate-btn');
const downloadBtn = document.getElementById('download-btn');
const changePhotoBtn = document.getElementById('change-photo-btn');
const newPhotoBtn = document.getElementById('new-photo-btn');
const tryAgainBtn = document.getElementById('try-again-btn');
const errorMessage = document.getElementById('error-message');
const btnText = generateBtn ? generateBtn.querySelector('.btn-text') : null;
const btnSpinner = generateBtn ? generateBtn.querySelector('.spinner') : null;

let currentFile = null;
let generatedImage = null;
let originalImageData = null;
let savedCropData = null; // Store crop data when Done is clicked
let cropState = {
    x: 0,
    y: 0,
    isDragging: false,
    startX: 0,
    startY: 0,
    boxWidth: 0,
    boxHeight: 0
};

// Show section helper
function showSection(section) {
    [uploadSection, cropSection, previewSection, loadingSection, resultSection, errorSection].forEach(s => {
        if (s) s.classList.add('hidden');
    });
    if (section) section.classList.remove('hidden');
}

// Initialize crop box size
function initCropBox() {
    if (!cropWrapper || !cropBox) return;
    
    const wrapperWidth = cropWrapper.clientWidth;
    const wrapperHeight = cropWrapper.clientHeight;
    const ratio = 35 / 45;
    
    // Calculate max box that fits in wrapper at 90%
    let boxHeight = wrapperHeight * 0.5;
    let boxWidth = boxHeight * ratio;
    
    // If width exceeds wrapper, scale down by width
    if (boxWidth > wrapperWidth * 0.9) {
        boxWidth = wrapperWidth * 0.9;
        boxHeight = boxWidth / ratio;
    }
    
    cropState.boxWidth = Math.round(boxWidth);
    cropState.boxHeight = Math.round(boxHeight);
    cropState.x = Math.round((wrapperWidth - cropState.boxWidth) / 2);
    cropState.y = Math.round((wrapperHeight - cropState.boxHeight) / 2);
    
    updateCropBox();
}

function updateCropBox() {
    if (!cropBox) return;
    cropBox.style.width = `${cropState.boxWidth}px`;
    cropBox.style.height = `${cropState.boxHeight}px`;
    cropBox.style.left = `${cropState.x}px`;
    cropBox.style.top = `${cropState.y}px`;
}

// Interaction state
let activeHandle = null; // which handle is being dragged
const RATIO = 35 / 45; // passport ratio width/height
const MIN_SIZE = 80; // minimum crop dimension

// Mouse/touch down on crop box or handle
function handlePointerDown(e) {
    if (!cropWrapper) return;
    
    const target = e.target;
    
    // Check if a resize handle was clicked
    if (target.classList.contains('crop-handle')) {
        activeHandle = target.dataset.handle;
        cropState.isDragging = false;
    } else {
        // Dragging the box itself
        activeHandle = null;
        cropState.isDragging = true;
        if (cropBox) cropBox.style.cursor = 'grabbing';
    }
    
    cropState.startX = e.clientX || (e.touches && e.touches[0] ? e.touches[0].clientX : 0);
    cropState.startY = e.clientY || (e.touches && e.touches[0] ? e.touches[0].clientY : 0);
    
    // Save starting box state
    cropState.startBoxX = cropState.x;
    cropState.startBoxY = cropState.y;
    cropState.startBoxW = cropState.boxWidth;
    cropState.startBoxH = cropState.boxHeight;
}

function handlePointerMove(e) {
    if (!cropWrapper) return;
    if (!cropState.isDragging && !activeHandle) return;
    e.preventDefault();
    
    const clientX = e.clientX || (e.touches && e.touches[0] ? e.touches[0].clientX : 0);
    const clientY = e.clientY || (e.touches && e.touches[0] ? e.touches[0].clientY : 0);
    
    const dx = clientX - cropState.startX;
    const dy = clientY - cropState.startY;
    
    const wrapW = cropWrapper.clientWidth;
    const wrapH = cropWrapper.clientHeight;
    
    if (cropState.isDragging && !activeHandle) {
        // Move the box
        const maxX = wrapW - cropState.boxWidth;
        const maxY = wrapH - cropState.boxHeight;
        cropState.x = Math.max(0, Math.min(cropState.startBoxX + dx, maxX));
        cropState.y = Math.max(0, Math.min(cropState.startBoxY + dy, maxY));
    } else if (activeHandle) {
        // Resize based on which handle
        let newX = cropState.startBoxX;
        let newY = cropState.startBoxY;
        let newW = cropState.startBoxW;
        let newH = cropState.startBoxH;
        
        switch (activeHandle) {
            case 'br':
                // Bottom-right: increase width, height follows ratio
                newW = Math.max(MIN_SIZE, cropState.startBoxW + dx);
                newH = newW / RATIO;
                break;
            case 'bl':
                // Bottom-left: move left edge, width changes, height follows
                newW = Math.max(MIN_SIZE, cropState.startBoxW - dx);
                newH = newW / RATIO;
                newX = cropState.startBoxX + cropState.startBoxW - newW;
                break;
            case 'tr':
                // Top-right: increase width, move top, height follows
                newW = Math.max(MIN_SIZE, cropState.startBoxW + dx);
                newH = newW / RATIO;
                newY = cropState.startBoxY + cropState.startBoxH - newH;
                break;
            case 'tl':
                // Top-left: move both edges
                newW = Math.max(MIN_SIZE, cropState.startBoxW - dx);
                newH = newW / RATIO;
                newX = cropState.startBoxX + cropState.startBoxW - newW;
                newY = cropState.startBoxY + cropState.startBoxH - newH;
                break;
            case 'r':
                newW = Math.max(MIN_SIZE, cropState.startBoxW + dx);
                newH = newW / RATIO;
                break;
            case 'l':
                newW = Math.max(MIN_SIZE, cropState.startBoxW - dx);
                newH = newW / RATIO;
                newX = cropState.startBoxX + cropState.startBoxW - newW;
                break;
            case 'b':
                newH = Math.max(MIN_SIZE, cropState.startBoxH + dy);
                newW = newH * RATIO;
                break;
            case 't':
                newH = Math.max(MIN_SIZE, cropState.startBoxH - dy);
                newW = newH * RATIO;
                newY = cropState.startBoxY + cropState.startBoxH - newH;
                break;
        }
        
        // Bounds check
        if (newX < 0) { newW += newX; newH = newW / RATIO; newX = 0; }
        if (newY < 0) { newH += newY; newW = newH * RATIO; newY = 0; }
        if (newX + newW > wrapW) { newW = wrapW - newX; newH = newW / RATIO; }
        if (newY + newH > wrapH) { newH = wrapH - newY; newW = newH * RATIO; }
        
        // Minimum size check
        if (newW >= MIN_SIZE && newH >= MIN_SIZE) {
            cropState.x = newX;
            cropState.y = newY;
            cropState.boxWidth = newW;
            cropState.boxHeight = newH;
        }
    }
    
    updateCropBox();
}

function handlePointerEnd() {
    cropState.isDragging = false;
    activeHandle = null;
    if (cropBox) cropBox.style.cursor = 'grab';
}

// Get crop coordinates relative to original image
function getCropData() {
    if (!cropWrapper || !cropImage || !cropImage.naturalWidth) {
        return { x: 0, y: 0, width: 0, height: 0 };
    }
    
    const scaleX = cropImage.naturalWidth / cropWrapper.clientWidth;
    const scaleY = cropImage.naturalHeight / cropWrapper.clientHeight;
    
    return {
        x: Math.round(cropState.x * scaleX),
        y: Math.round(cropState.y * scaleY),
        width: Math.round(cropState.boxWidth * scaleX),
        height: Math.round(cropState.boxHeight * scaleY)
    };
}

// File validation
function validateFile(file) {
    const validTypes = ['image/jpeg', 'image/png', 'image/webp'];
    const maxSize = 5 * 1024 * 1024;
    
    if (!validTypes.includes(file.type)) {
        return 'Invalid file type. Please upload JPG, PNG, or WEBP.';
    }
    if (file.size > maxSize) {
        return 'File too large. Maximum size is 5MB.';
    }
    return null;
}

// Handle file selection
function handleFile(file) {
    const error = validateFile(file);
    if (error) {
        showError(error);
        return;
    }
    
    currentFile = file;
    
    const reader = new FileReader();
    reader.onload = (e) => {
        originalImageData = e.target.result;
        
        if (cropImage) {
            cropImage.src = originalImageData;
            
            cropImage.onload = () => {
                if (cropWrapper && cropBox) {
                    showSection(cropSection);
                    // Init after section is visible so wrapper has dimensions
                    setTimeout(initCropBox, 50);
                } else {
                    showSection(previewSection);
                }
            };
            
            cropImage.onerror = () => {
                showSection(previewSection);
            };
        } else {
            showSection(previewSection);
        }
    };
    reader.readAsDataURL(file);
}

// Show error
function showError(message) {
    if (errorMessage) errorMessage.textContent = message;
    showSection(errorSection);
}

// Upload area events
if (uploadArea && fileInput) {
    uploadArea.addEventListener('click', () => fileInput.click());
    
    uploadArea.addEventListener('dragover', (e) => {
        e.preventDefault();
        uploadArea.classList.add('dragover');
    });
    
    uploadArea.addEventListener('dragleave', () => {
        uploadArea.classList.remove('dragover');
    });
    
    uploadArea.addEventListener('drop', (e) => {
        e.preventDefault();
        uploadArea.classList.remove('dragover');
        if (e.dataTransfer.files.length > 0) {
            handleFile(e.dataTransfer.files[0]);
        }
    });
    
    fileInput.addEventListener('change', (e) => {
        if (e.target.files.length > 0) {
            handleFile(e.target.files[0]);
        }
    });
}

// Crop events - combined move and resize
if (cropBox) {
    cropBox.addEventListener('mousedown', handlePointerDown);
    cropBox.addEventListener('touchstart', handlePointerDown, { passive: false });
}
document.addEventListener('mousemove', handlePointerMove);
document.addEventListener('touchmove', handlePointerMove, { passive: false });
document.addEventListener('mouseup', handlePointerEnd);
document.addEventListener('touchend', handlePointerEnd);

// Crop done - save crop data, draw preview and go to preview
if (cropDoneBtn) {
    cropDoneBtn.addEventListener('click', () => {
        // Save crop data NOW while crop section is still visible
        savedCropData = getCropData();
        console.log('Saved crop data:', savedCropData);
        
        const canvas = document.getElementById('crop-preview-canvas');
        if (!canvas || !cropImage) {
            showSection(previewSection);
            return;
        }
        
        const ctx = canvas.getContext('2d');
        const img = new Image();
        
        img.onload = () => {
            canvas.width = 413;
            canvas.height = 531;
            ctx.fillStyle = '#fff';
            ctx.fillRect(0, 0, canvas.width, canvas.height);
            ctx.drawImage(img, savedCropData.x, savedCropData.y, savedCropData.width, savedCropData.height, 0, 0, canvas.width, canvas.height);
            showSection(previewSection);
        };
        img.onerror = () => {
            showSection(previewSection);
        };
        img.src = originalImageData;
    });
}

// Auto detect
if (cropAutoBtn) {
    cropAutoBtn.addEventListener('click', () => {
        console.log('Auto Detect clicked');
        // Small delay to ensure DOM is ready
        setTimeout(() => {
            initCropBox();
        }, 50);
    });
}

// Cancel crop
if (cropCancelBtn) {
    cropCancelBtn.addEventListener('click', () => {
        currentFile = null;
        if (fileInput) fileInput.value = '';
        showSection(uploadSection);
    });
}

// Adjust crop from preview
if (adjustCropBtn) {
    adjustCropBtn.addEventListener('click', () => {
        showSection(cropSection);
    });
}

// Generate passport photo
async function generatePassportPhoto() {
    if (!currentFile) return;
    
    if (generateBtn) generateBtn.disabled = true;
    if (btnText) btnText.textContent = 'Processing...';
    if (btnSpinner) btnSpinner.classList.remove('hidden');
    showSection(loadingSection);
    
    const loadingSubText = loadingSection ? loadingSection.querySelector('.sub-text') : null;
    const originalSubText = loadingSubText ? loadingSubText.textContent : '';
    const firstTimeMsg = 'Downloading AI model (~180MB)... This may take 1-2 minutes on first use';
    
    const msgTimeout = setTimeout(() => {
        if (loadingSection && !loadingSection.classList.contains('hidden') && loadingSubText) {
            loadingSubText.textContent = firstTimeMsg;
        }
    }, 3000);
    
    const bgColorEl = document.querySelector('input[name="bg-color"]:checked');
    const bgColor = bgColorEl ? bgColorEl.value : 'white';
    
    const formData = new FormData();
    formData.append('image', currentFile);
    formData.append('bg_color', bgColor);
    
    // Use saved crop coordinates (saved when Done was clicked)
    const cropData = savedCropData || getCropData();
    console.log('Sending crop data:', cropData);
    formData.append('crop_x', cropData.x);
    formData.append('crop_y', cropData.y);
    formData.append('crop_width', cropData.width);
    formData.append('crop_height', cropData.height);
    
    const controller = new AbortController();
    const timeoutId = setTimeout(() => controller.abort(), 120000);
    
    try {
        const response = await fetch('/upload', {
            method: 'POST',
            body: formData,
            signal: controller.signal
        });
        
        clearTimeout(timeoutId);
        clearTimeout(msgTimeout);
        
        const data = await response.json();
        
        if (!response.ok) {
            throw new Error(data.error || 'Processing failed');
        }
        
        generatedImage = data.image;
        if (resultImage) resultImage.src = generatedImage;
        showSection(resultSection);
        
    } catch (error) {
        clearTimeout(timeoutId);
        clearTimeout(msgTimeout);
        if (loadingSubText) loadingSubText.textContent = originalSubText;
        
        if (error.name === 'AbortError') {
            showError('Request timed out. The first processing may take 1-2 minutes. Please try again.');
        } else {
            showError(error.message || 'Failed to process image. Please try again.');
        }
    } finally {
        if (generateBtn) generateBtn.disabled = false;
        if (btnText) btnText.textContent = 'Generate Passport Photo';
        if (btnSpinner) btnSpinner.classList.add('hidden');
    }
}

if (generateBtn) {
    generateBtn.addEventListener('click', generatePassportPhoto);
}

// Download button
if (downloadBtn) {
    downloadBtn.addEventListener('click', async () => {
        if (!generatedImage) return;
        
        try {
            const response = await fetch('/download', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ image: generatedImage })
            });
            
            if (!response.ok) throw new Error('Download failed');
            
            const blob = await response.blob();
            const url = window.URL.createObjectURL(blob);
            const a = document.createElement('a');
            a.href = url;
            a.download = 'passport_photo_4x6.jpg';
            document.body.appendChild(a);
            a.click();
            document.body.removeChild(a);
            window.URL.revokeObjectURL(url);
            
        } catch (error) {
            const link = document.createElement('a');
            link.href = generatedImage;
            link.download = 'passport_photo_4x6.jpg';
            link.click();
        }
    });
}

// Navigation buttons
if (changePhotoBtn) {
    changePhotoBtn.addEventListener('click', () => {
        currentFile = null;
        if (fileInput) fileInput.value = '';
        showSection(uploadSection);
    });
}

if (newPhotoBtn) {
    newPhotoBtn.addEventListener('click', () => {
        currentFile = null;
        generatedImage = null;
        if (fileInput) fileInput.value = '';
        if (resultImage) resultImage.src = '';
        showSection(uploadSection);
    });
}

if (tryAgainBtn) {
    tryAgainBtn.addEventListener('click', () => {
        showSection(uploadSection);
    });
}

// Keyboard shortcuts
document.addEventListener('keydown', (e) => {
    if (e.key === 'Escape') {
        if (resultSection && !resultSection.classList.contains('hidden')) {
            if (newPhotoBtn) newPhotoBtn.click();
        } else if (previewSection && !previewSection.classList.contains('hidden')) {
            if (changePhotoBtn) changePhotoBtn.click();
        }
    }
});
