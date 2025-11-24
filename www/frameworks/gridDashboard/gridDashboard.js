/**
 * GridDashboard - A flexible drag and drop dashboard library
 * Provides a grid-based layout system with draggable widgets
 */
class GridDashboard {
    constructor(container, width, height, options = {}) {
        this.container = typeof container === 'string' ? document.querySelector(container) : container;
        
        if (!width || !height) {
            throw new Error('GridDashboard: width and height are required parameters');
        }
        
        this.gridWidth = width;
        this.gridHeight = height;
        this.initialWidth = width;
        this.initialHeight = height;
        this.options = {
            cellWidth: 2, // Cell width in pixels, or "auto" for responsive
            cellHeight: 2, // Cell height in pixels, or "auto" for responsive
            gap: 10,
            autoResize: true,
            enableDragDrop: true,
            animationDuration: 300,
            allowOverlapping: false,
            autoResizeWidth: false,
            autoResizeHeight: false,
            autoShrink: false,
            editMode: true,
            showGridLines: false,
            ...options
        };
        
        this.widgets = new Map();
        this.layout = [];
        this.isDragging = false;
        this.draggedWidget = null;
        this.placeholder = null;
        this.gridOverlay = null;
        this.registeredPalettes = new Map();
        this.widgetTemplates = new Map();
        this.dragDropListenersSetup = false;
        
        this.init();
    }
    
    
    
    init() {
        this.setupContainer();
        this.setupStyles();
        this.setupEventListeners();
        this.setupGridOverlay();
        
        if (this.options.autoResize) {
            this.setupResizeObserver();
        }
    }
    
    setupContainer() {
        if (!this.container) {
            throw new Error('GridDashboard: Container element not found');
        }
        
        this.container.classList.add('grid-dashboard');
        this.container.style.position = 'relative';
        this.container.style.display = 'grid';
        
        this.updateContainerDimensions();
    }
    
    isAutoMode() {
        return this.options.cellWidth === 'auto' || this.options.cellHeight === 'auto';
    }
    
    updateContainerDimensions() {
        if (this.isAutoMode()) {
            // Use CSS auto values for flexible cell sizing
            const columnTemplate = this.options.cellWidth === 'auto' ? `repeat(${this.gridWidth}, 1fr)` : `repeat(${this.gridWidth}, ${this.options.cellWidth}px)`;
            const rowTemplate = this.options.cellHeight === 'auto' ? `repeat(${this.gridHeight}, 1fr)` : `repeat(${this.gridHeight}, ${this.options.cellHeight}px)`;
            
            this.container.style.gridTemplateColumns = columnTemplate;
            this.container.style.gridTemplateRows = rowTemplate;
            this.container.style.gap = `${this.options.gap}px`;
            
            // Let container size be determined by its parent/CSS
            this.container.style.width = 'auto';
            this.container.style.height = 'auto';
        } else {
            // Use fixed pixel values with independent width and height
            const cellWidth = this.options.cellWidth;
            const cellHeight = this.options.cellHeight;
            
            this.container.style.gridTemplateColumns = `repeat(${this.gridWidth}, ${cellWidth}px)`;
            this.container.style.gridTemplateRows = `repeat(${this.gridHeight}, ${cellHeight}px)`;
            this.container.style.gap = `${this.options.gap}px`;
            this.container.style.width = `${this.gridWidth * cellWidth + (this.gridWidth - 1) * this.options.gap}px`;
            this.container.style.height = `${this.gridHeight * cellHeight + (this.gridHeight - 1) * this.options.gap}px`;
        }
    }
    
    setupStyles() {
        // Check if external CSS is loaded, if not, warn user
        if (!document.getElementById('grid-dashboard-styles') && !this.isExternalCSSLoaded()) {
            console.warn('GridDashboard: External CSS file (gridDashboard.css) not found. Please include it in your HTML:');
            console.warn('<link rel="stylesheet" href="path/to/gridDashboard.css">');
        }
        
        // Set CSS custom property for animation duration
        if (this.container) {
            this.container.style.setProperty('--grid-animation-duration', `${this.options.animationDuration}ms`);
        }
    }
    
    isExternalCSSLoaded() {
        // Check if the CSS file is loaded by looking for one of our CSS classes
        const testElement = document.createElement('div');
        testElement.className = 'grid-dashboard';
        testElement.style.visibility = 'hidden';
        testElement.style.position = 'absolute';
        document.body.appendChild(testElement);
        
        const styles = window.getComputedStyle(testElement);
        const isLoaded = styles.userSelect === 'none';
        
        document.body.removeChild(testElement);
        return isLoaded;
    }
    
    setupEventListeners() {
        if (this.options.enableDragDrop) {
            this.setupDragDropListeners();
        }
        
        window.addEventListener('resize', this.debounce(this.handleResize.bind(this), 100));
    }
    
    setupResizeObserver() {
        if ('ResizeObserver' in window) {
            this.resizeObserver = new ResizeObserver(entries => {
                // Debounce resize events to prevent excessive recalculations
                if (this.resizeTimeout) {
                    clearTimeout(this.resizeTimeout);
                }
                this.resizeTimeout = setTimeout(() => {
                    this.handleResize();
                }, 50);
            });
            this.resizeObserver.observe(this.container);
            
            // Also observe parent container when using CSS auto sizing
            if (this.options.useCSSAuto && this.container.parentElement) {
                this.resizeObserver.observe(this.container.parentElement);
            }
        }
    }
    
    setupGridOverlay() {
        this.gridOverlay = document.createElement('div');
        this.gridOverlay.className = 'grid-dashboard-overlay';
        this.container.appendChild(this.gridOverlay);
        this.updateGridOverlay();
    }
    
    updateGridOverlay() {
        if (!this.gridOverlay) return;
        
        // Clear existing grid cells
        this.gridOverlay.innerHTML = '';
        
        // Show/hide overlay based on options and edit mode
        const shouldShow = this.options.showGridLines && this.options.editMode;
        this.gridOverlay.style.display = shouldShow ? 'grid' : 'none';
        
        if (!shouldShow) return;
        
        // Set up grid layout to match container
        this.gridOverlay.style.position = 'absolute';
        this.gridOverlay.style.top = '0';
        this.gridOverlay.style.left = '0';
        this.gridOverlay.style.width = '100%';
        this.gridOverlay.style.height = '100%';
        this.gridOverlay.style.pointerEvents = 'none';
        this.gridOverlay.style.zIndex = '0';
        
        if (this.isAutoMode()) {
            // Use CSS auto values for overlay to match container
            const columnTemplate = this.options.cellWidth === 'auto' ? `repeat(${this.gridWidth}, 1fr)` : `repeat(${this.gridWidth}, ${this.options.cellWidth}px)`;
            const rowTemplate = this.options.cellHeight === 'auto' ? `repeat(${this.gridHeight}, 1fr)` : `repeat(${this.gridHeight}, ${this.options.cellHeight}px)`;
            
            this.gridOverlay.style.gridTemplateColumns = columnTemplate;
            this.gridOverlay.style.gridTemplateRows = rowTemplate;
        } else {
            // Use fixed pixel values with independent width and height
            this.gridOverlay.style.gridTemplateColumns = `repeat(${this.gridWidth}, ${this.options.cellWidth}px)`;
            this.gridOverlay.style.gridTemplateRows = `repeat(${this.gridHeight}, ${this.options.cellHeight}px)`;
        }
        this.gridOverlay.style.gap = `${this.options.gap}px`;
        
        // Create grid cells
        for (let y = 0; y < this.gridHeight; y++) {
            for (let x = 0; x < this.gridWidth; x++) {
                const cell = document.createElement('div');
                cell.className = 'grid-cell-placeholder';
                cell.dataset.x = x;
                cell.dataset.y = y;
                this.gridOverlay.appendChild(cell);
            }
        }
    }
    
    updateGridOverlayForSize(width, height) {
        if (!this.gridOverlay) return;
        
        // Clear existing grid cells
        this.gridOverlay.innerHTML = '';
        
        // Show/hide overlay based on options and edit mode
        const shouldShow = this.options.showGridLines && this.options.editMode;
        this.gridOverlay.style.display = shouldShow ? 'grid' : 'none';
        
        if (!shouldShow) return;
        
        // Set up grid layout with specified dimensions
        this.gridOverlay.style.position = 'absolute';
        this.gridOverlay.style.top = '0';
        this.gridOverlay.style.left = '0';
        this.gridOverlay.style.width = '100%';
        this.gridOverlay.style.height = '100%';
        this.gridOverlay.style.pointerEvents = 'none';
        this.gridOverlay.style.zIndex = '0';
        
        if (this.isAutoMode()) {
            // Use CSS auto values for overlay to match container
            const columnTemplate = this.options.cellWidth === 'auto' ? `repeat(${width}, 1fr)` : `repeat(${width}, ${this.options.cellWidth}px)`;
            const rowTemplate = this.options.cellHeight === 'auto' ? `repeat(${height}, 1fr)` : `repeat(${height}, ${this.options.cellHeight}px)`;
            
            this.gridOverlay.style.gridTemplateColumns = columnTemplate;
            this.gridOverlay.style.gridTemplateRows = rowTemplate;
        } else {
            // Use fixed pixel values with independent width and height
            this.gridOverlay.style.gridTemplateColumns = `repeat(${width}, ${this.options.cellWidth}px)`;
            this.gridOverlay.style.gridTemplateRows = `repeat(${height}, ${this.options.cellHeight}px)`;
        }
        this.gridOverlay.style.gap = `${this.options.gap}px`;
        
        // Create grid cells with specified dimensions
        for (let y = 0; y < height; y++) {
            for (let x = 0; x < width; x++) {
                const cell = document.createElement('div');
                cell.className = 'grid-cell-placeholder';
                cell.dataset.x = x;
                cell.dataset.y = y;
                this.gridOverlay.appendChild(cell);
            }
        }
    }
    
    addWidget(widget, position = null) {
        if (!(widget instanceof GridWidget)) {
            throw new Error('GridDashboard: Widget must be an instance of GridWidget');
        }
        
        const widgetId = widget.id || this.generateId();
        widget.id = widgetId;
        widget.dashboard = this;
        
        if (position) {
            widget.position = { ...position };
        } else {
            widget.position = this.findAvailablePosition(widget.size);
        }
        
        this.widgets.set(widgetId, widget);
        this.renderWidget(widget);
        this.updateLayout();
        
        return widgetId;
    }
    
    removeWidget(widgetId) {
        const widget = this.widgets.get(widgetId);
        if (widget) {
            if (widget.element && widget.element.parentNode) {
                widget.element.remove();
            }
            widget.dashboard = null;
        }
        this.widgets.delete(widgetId);
        this.updateLayout();
    }
    
    moveWidget(widgetId, newPosition) {
        const widget = this.widgets.get(widgetId);
        if (widget) {
            widget.position = { ...newPosition };
            this.updateWidgetPosition(widget);
            this.updateLayout();
        }
    }
    
    resizeWidget(widgetId, newSize) {
        const widget = this.widgets.get(widgetId);
        if (widget) {
            // Validate new size against grid boundaries
            let validatedSize = { ...newSize };
            
            // Check if resize would exceed grid boundaries
            const wouldExceedWidth = widget.position.x + newSize.width > this.gridWidth;
            const wouldExceedHeight = widget.position.y + newSize.height > this.gridHeight;
            
            // If auto-resize is disabled, constrain to grid boundaries
            if (wouldExceedWidth && !this.options.autoResizeWidth) {
                validatedSize.width = this.gridWidth - widget.position.x;
            }
            
            if (wouldExceedHeight && !this.options.autoResizeHeight) {
                validatedSize.height = this.gridHeight - widget.position.y;
            }
            
            widget.size = validatedSize;
            this.updateWidgetSize(widget);
            this.updateLayout();
        }
    }
    
    renderWidget(widget) {
        const element = widget.render();
        element.classList.add('grid-widget');
        element.dataset.widgetId = widget.id;
        
        this.updateWidgetPosition(widget);
        this.updateWidgetSize(widget);
        
        // Set z-index for overlapping widgets
        if (this.options.allowOverlapping) {
            element.style.zIndex = this.widgets.size + 1;
            element.addEventListener('mousedown', () => {
                // Bring to front when clicked
                element.style.zIndex = Math.max(1000, ...Array.from(this.widgets.values()).map(w => parseInt(w.element.style.zIndex) || 0)) + 1;
            });
        }
        
        // Apply edit mode settings
        this.updateWidgetEditMode(element, widget);
        
        if (this.options.enableDragDrop) {
            element.addEventListener('dragstart', this.handleDragStart.bind(this));
            element.addEventListener('dragend', this.handleDragEnd.bind(this));
        }
        
        this.container.appendChild(element);
        widget.element = element;
    }
    
    updateWidgetPosition(widget) {
        if (widget.element) {
            widget.element.style.gridColumnStart = widget.position.x + 1;
            widget.element.style.gridRowStart = widget.position.y + 1;
        }
    }
    
    updateWidgetSize(widget) {
        if (widget.element) {
            widget.element.style.gridColumnEnd = `span ${widget.size.width}`;
            widget.element.style.gridRowEnd = `span ${widget.size.height}`;
        }
    }
    
    findAvailablePosition(size) {
        // If overlapping is allowed, just place at origin or find any valid position within bounds
        if (this.options.allowOverlapping) {
            return { x: 0, y: 0 };
        }
        
        const occupiedCells = new Set();
        
        // Mark all occupied cells
        for (const [id, widget] of this.widgets) {
            for (let x = widget.position.x; x < widget.position.x + widget.size.width; x++) {
                for (let y = widget.position.y; y < widget.position.y + widget.size.height; y++) {
                    occupiedCells.add(`${x},${y}`);
                }
            }
        }
        
        // Find first available position
        for (let y = 0; y <= this.gridHeight - size.height; y++) {
            for (let x = 0; x <= this.gridWidth - size.width; x++) {
                let canPlace = true;
                
                for (let dx = 0; dx < size.width && canPlace; dx++) {
                    for (let dy = 0; dy < size.height && canPlace; dy++) {
                        if (occupiedCells.has(`${x + dx},${y + dy}`)) {
                            canPlace = false;
                        }
                    }
                }
                
                if (canPlace) {
                    return { x, y };
                }
            }
        }
        
        return { x: 0, y: 0 };
    }
    
    handleDragStart(e) {
        this.isDragging = true;
        this.draggedWidget = this.widgets.get(e.target.dataset.widgetId);
        
        e.target.classList.add('dragging');
        e.dataTransfer.effectAllowed = 'move';
        e.dataTransfer.setData('text/plain', e.target.dataset.widgetId);
        
        this.createPlaceholder();
    }
    
    handleDragEnd(e) {
        this.isDragging = false;
        e.target.classList.remove('dragging');
        
        if (this.placeholder) {
            this.placeholder.remove();
            this.placeholder = null;
        }
        
        // Update layout to ensure proper grid sizing after drag (allows auto-resize to work)
        this.updateLayout();
        
        this.draggedWidget = null;
    }
    
    handleDragOver(e) {
        e.preventDefault();
        
        // Check if this is a palette widget drag
        const paletteData = e.dataTransfer.types.includes('application/widget-template');
        if (paletteData) {
            e.dataTransfer.dropEffect = 'copy';
            return;
        }
        
        // Handle existing widget move
        e.dataTransfer.dropEffect = 'move';
        if (this.isDragging && this.placeholder) {
            const dropPosition = this.getDropPosition(e);
            if (dropPosition) {
                this.updatePlaceholderPosition(dropPosition);
            }
        }
    }
    
    handleDrop(e) {
        e.preventDefault();
        
        // Check if this is a widget template from palette
        const paletteData = e.dataTransfer.getData('application/widget-template');
        if (paletteData) {
            this.handlePaletteDrop(e, JSON.parse(paletteData));
            return;
        }
        
        // Handle existing widget move
        const widgetId = e.dataTransfer.getData('text/plain');
        const widget = this.widgets.get(widgetId);
        
        if (widget) {
            const dropPosition = this.getDropPosition(e);
            if (dropPosition && this.isValidPosition(dropPosition, widget.size, widgetId)) {
                this.moveWidget(widgetId, dropPosition);
            }
        }
    }
    
    handleDragEnter(e) {
        e.preventDefault();
    }
    
    handleDragLeave(e) {
        e.preventDefault();
    }
    
    handlePaletteDrop(e, paletteData) {
        let dropPosition = this.getDropPosition(e);
        if (!dropPosition) return;
        
        // Get the widget template from registered palettes
        const template = this.getWidgetTemplate(paletteData.templateId);
        if (!template) {
            console.warn(`Widget template "${paletteData.templateId}" not found`);
            return;
        }
        
        // Create new widget instance
        let widget;
        try {
            if (typeof template.factory === 'function') {
                widget = template.factory();
            } else {
                // Fallback to basic GridWidget
                widget = new GridWidget(template.defaultOptions || {});
            }
        } catch (error) {
            console.error('Error creating widget from template:', error);
            // Try fallback
            try {
                widget = new GridWidget({
                    title: template.name || 'Widget',
                    width: 2,
                    height: 2,
                    content: `<p>${template.description || 'Basic widget'}</p>`
                });
            } catch (fallbackError) {
                console.error('Fallback widget creation also failed:', fallbackError);
                return;
            }
        }
        
        // Ensure widget has required properties
        if (!widget.size) {
            widget.size = { width: 2, height: 2 };
        }
        
        // Check if position is valid
        if (!this.isValidPosition(dropPosition, widget.size)) {
            // Try to find a nearby valid position
            const newPosition = this.findNearestValidPosition(dropPosition, widget.size);
            if (newPosition) {
                dropPosition = newPosition;
            }
        }
        
        // Add widget to dashboard
        const widgetId = this.addWidget(widget, dropPosition);
        
        // Emit event for widget creation
        this.emit('widgetCreated', { 
            widgetId, 
            widget, 
            template: paletteData.templateId,
            position: dropPosition 
        });
        
        return widgetId;
    }
    
    getDropPosition(e) {
        const rect = this.container.getBoundingClientRect();
        const x = e.clientX - rect.left;
        const y = e.clientY - rect.top;
        
        let cellWidth, cellHeight;
        
        if (this.isAutoMode()) {
            // Calculate actual cell size from CSS grid for auto dimensions
            if (this.options.cellWidth === 'auto') {
                cellWidth = (rect.width - (this.gridWidth - 1) * this.options.gap) / this.gridWidth;
            } else {
                cellWidth = this.options.cellWidth;
            }
            
            if (this.options.cellHeight === 'auto') {
                cellHeight = (rect.height - (this.gridHeight - 1) * this.options.gap) / this.gridHeight;
            } else {
                cellHeight = this.options.cellHeight;
            }
        } else {
            // Use fixed cell size from options
            cellWidth = this.options.cellWidth;
            cellHeight = this.options.cellHeight;
        }
        
        const gridX = Math.floor(x / (cellWidth + this.options.gap));
        const gridY = Math.floor(y / (cellHeight + this.options.gap));
        
        // Allow positions outside current grid bounds if auto-resize is enabled
        const maxX = this.options.autoResizeWidth ? Math.max(0, gridX) : Math.min(gridX, this.gridWidth - 1);
        const maxY = this.options.autoResizeHeight ? Math.max(0, gridY) : Math.min(gridY, this.gridHeight - 1);
        
        return {
            x: Math.max(0, maxX),
            y: Math.max(0, maxY)
        };
    }
    
    isValidPosition(position, size, excludeWidgetId = null) {
        // Check if position is within current grid bounds or if auto-resize will handle it
        const wouldExceedWidth = position.x + size.width > this.gridWidth;
        const wouldExceedHeight = position.y + size.height > this.gridHeight;
        
        // If would exceed bounds and auto-resize is not enabled, reject
        if (wouldExceedWidth && !this.options.autoResizeWidth) {
            return false;
        }
        if (wouldExceedHeight && !this.options.autoResizeHeight) {
            return false;
        }
        
        // Ensure position is not negative
        if (position.x < 0 || position.y < 0) {
            return false;
        }
        
        // If overlapping is allowed, position is valid
        if (this.options.allowOverlapping) {
            return true;
        }
        
        // Check for collisions with existing widgets
        const occupiedCells = new Set();
        
        for (const [id, widget] of this.widgets) {
            if (id === excludeWidgetId) continue;
            
            for (let x = widget.position.x; x < widget.position.x + widget.size.width; x++) {
                for (let y = widget.position.y; y < widget.position.y + widget.size.height; y++) {
                    occupiedCells.add(`${x},${y}`);
                }
            }
        }
        
        for (let x = position.x; x < position.x + size.width; x++) {
            for (let y = position.y; y < position.y + size.height; y++) {
                if (occupiedCells.has(`${x},${y}`)) {
                    return false;
                }
            }
        }
        
        return true;
    }
    
    createPlaceholder() {
        this.placeholder = document.createElement('div');
        this.placeholder.className = 'grid-widget-placeholder';
        this.container.appendChild(this.placeholder);
    }
    
    updatePlaceholderPosition(position) {
        if (this.placeholder && this.draggedWidget) {
            // Check if we need to temporarily expand the grid for preview
            const wouldExceedWidth = position.x + this.draggedWidget.size.width > this.gridWidth;
            const wouldExceedHeight = position.y + this.draggedWidget.size.height > this.gridHeight;
            
            if ((wouldExceedWidth && this.options.autoResizeWidth) || (wouldExceedHeight && this.options.autoResizeHeight)) {
                // Temporarily expand container for placeholder preview
                const tempWidth = Math.max(this.gridWidth, position.x + this.draggedWidget.size.width);
                const tempHeight = Math.max(this.gridHeight, position.y + this.draggedWidget.size.height);
                
                if (this.isAutoMode()) {
                    // Use CSS auto values for temporary expansion
                    const columnTemplate = this.options.cellWidth === 'auto' ? `repeat(${tempWidth}, 1fr)` : `repeat(${tempWidth}, ${this.options.cellWidth}px)`;
                    const rowTemplate = this.options.cellHeight === 'auto' ? `repeat(${tempHeight}, 1fr)` : `repeat(${tempHeight}, ${this.options.cellHeight}px)`;
                    
                    this.container.style.gridTemplateColumns = columnTemplate;
                    this.container.style.gridTemplateRows = rowTemplate;
                    // Let container size be determined by content in auto mode
                    this.container.style.width = 'auto';
                    this.container.style.height = 'auto';
                } else {
                    // Use fixed pixel values with independent width and height
                    this.container.style.gridTemplateColumns = `repeat(${tempWidth}, ${this.options.cellWidth}px)`;
                    this.container.style.gridTemplateRows = `repeat(${tempHeight}, ${this.options.cellHeight}px)`;
                    this.container.style.width = `${tempWidth * this.options.cellWidth + (tempWidth - 1) * this.options.gap}px`;
                    this.container.style.height = `${tempHeight * this.options.cellHeight + (tempHeight - 1) * this.options.gap}px`;
                }
                
                // Update grid overlay to match the temporarily expanded grid
                this.updateGridOverlayForSize(tempWidth, tempHeight);
            }
            
            this.placeholder.style.gridColumnStart = position.x + 1;
            this.placeholder.style.gridRowStart = position.y + 1;
            this.placeholder.style.gridColumnEnd = `span ${this.draggedWidget.size.width}`;
            this.placeholder.style.gridRowEnd = `span ${this.draggedWidget.size.height}`;
            
            const isValid = this.isValidPosition(position, this.draggedWidget.size, this.draggedWidget.id);
            this.placeholder.style.display = isValid ? 'block' : 'none';
            
            // Add expanding class if this would expand the grid
            if (isValid && ((wouldExceedWidth && this.options.autoResizeWidth) || (wouldExceedHeight && this.options.autoResizeHeight))) {
                this.placeholder.classList.add('expanding');
            } else {
                this.placeholder.classList.remove('expanding');
            }
        }
    }
    
    updateLayout() {
        this.layout = Array.from(this.widgets.values()).map(widget => ({
            id: widget.id,
            type: widget.type,
            title: widget.title,
            position: { ...widget.position },
            size: { ...widget.size }
        }));
        
        this.autoResizeGrid();
        this.emit('layoutChanged', this.layout);
    }
    
    autoResizeGrid() {
        if (!this.options.autoResizeWidth && !this.options.autoResizeHeight) {
            return;
        }
        
        let newWidth = this.gridWidth;
        let newHeight = this.gridHeight;
        
        // Calculate required dimensions based on widgets
        let requiredWidth = 0;
        let requiredHeight = 0;
        
        for (const [id, widget] of this.widgets) {
            requiredWidth = Math.max(requiredWidth, widget.position.x + widget.size.width);
            requiredHeight = Math.max(requiredHeight, widget.position.y + widget.size.height);
        }
        
        // Auto-resize width
        if (this.options.autoResizeWidth) {
            const minWidth = this.options.autoShrink ? Math.max(1, requiredWidth) : Math.max(this.initialWidth, requiredWidth);
            newWidth = Math.max(minWidth, requiredWidth);
        }
        
        // Auto-resize height
        if (this.options.autoResizeHeight) {
            const minHeight = this.options.autoShrink ? Math.max(1, requiredHeight) : Math.max(this.initialHeight, requiredHeight);
            newHeight = Math.max(minHeight, requiredHeight);
        }
        
        // Update grid dimensions if they changed
        let updated = false;
        if (this.options.autoResizeWidth && newWidth !== this.gridWidth) {
            this.gridWidth = newWidth;
            updated = true;
        }
        
        if (this.options.autoResizeHeight && newHeight !== this.gridHeight) {
            this.gridHeight = newHeight;
            updated = true;
        }
        
        // Update container if dimensions changed
        if (updated) {
            this.updateContainerSize();
            this.emit('gridResized', { width: this.gridWidth, height: this.gridHeight });
        }
    }
    
    updateContainerSize() {
        this.updateContainerDimensions();
        this.updateGridOverlay();
    }
    
    handleResize() {
        // Trigger relayout if needed
        this.emit('resize');
    }
    
    generateId() {
        return 'widget-' + Math.random().toString(36).substr(2, 9);
    }
    
    debounce(func, wait) {
        let timeout;
        return function executedFunction(...args) {
            const later = () => {
                clearTimeout(timeout);
                func(...args);
            };
            clearTimeout(timeout);
            timeout = setTimeout(later, wait);
        };
    }
    
    emit(eventName, data) {
        const event = new CustomEvent(`gridDashboard:${eventName}`, { detail: data });
        this.container.dispatchEvent(event);
    }
    
    
    getLayout() {
        return this.layout;
    }
    
    setLayout(layout) {
        layout.forEach(item => {
            const widget = this.widgets.get(item.id);
            if (widget) {
                widget.position = { ...item.position };
                widget.size = { ...item.size };
                this.updateWidgetPosition(widget);
                this.updateWidgetSize(widget);
            }
        });
        this.updateLayout();
    }
    
    loadFromLayout(layout) {
        // Clear existing widgets
        this.clearAll();
        
        // Create widgets from layout data
        layout.forEach(item => {
            let widget;
            
            // First try to get widget from registered templates
            const template = this.getWidgetTemplate(item.type);
            if (template && typeof template.factory === 'function') {
                try {
                    widget = template.factory();
                    // Override with saved properties
                    if (item.id) widget.id = item.id;
                    if (item.title) widget.title = item.title;
                    if (item.position) widget.position = { ...item.position };
                    if (item.size) widget.size = { ...item.size };
                    
                    // Apply any additional saved properties
                    Object.keys(item).forEach(key => {
                        if (!['id', 'title', 'position', 'size', 'type'].includes(key)) {
                            widget[key] = item[key];
                        }
                    });
                } catch (error) {
                    console.warn(`Failed to create widget from template "${item.type}":`, error);
                    widget = null;
                }
            }
            
            // Fallback to built-in widget types if template not found
            if (!widget) {
                switch (item.type) {
                    case 'text':
                        widget = new TextWidget({
                            id: item.id,
                            title: item.title,
                            text: item.text || 'Text Widget',
                            textAlign: item.textAlign || 'left',
                            fontSize: item.fontSize || '16px'
                        });
                        break;
                    case 'chart':
                        widget = new ChartWidget({
                            id: item.id,
                            title: item.title,
                            chartType: item.chartType || 'line',
                            chartData: item.chartData || [],
                            chartOptions: item.chartOptions || {}
                        });
                        break;
                    case 'image':
                        widget = new ImageWidget({
                            id: item.id,
                            title: item.title,
                            src: item.src || '',
                            alt: item.alt || '',
                            objectFit: item.objectFit || 'cover'
                        });
                        break;
                    case 'grid':
                    default:
                        widget = new GridWidget({
                            id: item.id,
                            type: item.type || 'grid',
                            title: item.title || 'Widget',
                            content: item.content || '',
                            actions: item.actions || [],
                            resizable: item.resizable !== false,
                            removable: item.removable !== false
                        });
                        break;
                }
            }
            
            if (widget) {
                // Ensure position and size are set
                widget.position = { ...item.position };
                widget.size = { ...item.size };
                
                // Add widget to dashboard
                this.widgets.set(widget.id, widget);
                widget.dashboard = this;
                this.renderWidget(widget);
            } else {
                console.warn(`Could not create widget of type "${item.type}"`);
            }
        });
        
        // Update layout and resize grid
        this.updateLayout();
        
        // Ensure edit mode is properly applied to all loaded widgets
        this.updateEditMode();
        
        // Emit event
        this.emit('layoutLoaded', { layout, widgetCount: layout.length });
    }
    
    clearAll() {
        // Remove all widgets from DOM and clear the widgets map
        for (const [id, widget] of this.widgets) {
            if (widget.element && widget.element.parentNode) {
                widget.element.remove();
            }
            widget.dashboard = null;
        }
        this.widgets.clear();
        this.layout = [];
        this.updateLayout();
    }
    
    getGridDimensions() {
        return {
            width: this.gridWidth,
            height: this.gridHeight
        };
    }
    
    setGridDimensions(width, height) {
        this.gridWidth = width;
        this.gridHeight = height;
        this.updateContainerSize();
    }
    
    getRequiredDimensions() {
        let maxWidth = 0;
        let maxHeight = 0;
        
        for (const [id, widget] of this.widgets) {
            maxWidth = Math.max(maxWidth, widget.position.x + widget.size.width);
            maxHeight = Math.max(maxHeight, widget.position.y + widget.size.height);
        }
        
        return {
            width: maxWidth,
            height: maxHeight
        };
    }
    
    fitToContent() {
        const required = this.getRequiredDimensions();
        this.setGridDimensions(Math.max(1, required.width), Math.max(1, required.height));
    }
    
    setEditMode(enabled) {
        this.options.editMode = enabled;
        this.updateEditMode();
        this.emit('editModeChanged', { editMode: enabled });
    }
    
    getEditMode() {
        return this.options.editMode;
    }
    
    toggleEditMode() {
        this.setEditMode(!this.options.editMode);
    }
    
    updateEditMode() {
        // Update all existing widgets
        this.container.querySelectorAll('.grid-widget').forEach(element => {
            const widgetId = element.dataset.widgetId;
            const widget = this.widgets.get(widgetId);
            if (widget) {
                this.updateWidgetEditMode(element, widget);
            }
        });
        
        // Update container class
        if (this.options.editMode) {
            this.container.classList.add('edit-mode');
            this.container.classList.remove('view-mode');
        } else {
            this.container.classList.remove('edit-mode');
            this.container.classList.add('view-mode');
        }
        
        // Update grid overlay visibility
        this.updateGridOverlay();
    }
    
    setShowGridLines(enabled) {
        this.options.showGridLines = enabled;
        this.updateGridOverlay();
        this.emit('gridLinesChanged', { showGridLines: enabled });
    }
    
    getShowGridLines() {
        return this.options.showGridLines;
    }
    
    toggleGridLines() {
        this.setShowGridLines(!this.options.showGridLines);
    }
    
    setEnableDragDrop(enabled) {
        this.options.enableDragDrop = enabled;
        this.updateDragDropMode();
        this.emit('dragDropModeChanged', { enableDragDrop: enabled });
    }
    
    getEnableDragDrop() {
        return this.options.enableDragDrop;
    }
    
    toggleDragDrop() {
        this.setEnableDragDrop(!this.options.enableDragDrop);
    }
    
    updateDragDropMode() {
        // Update event listeners
        if (this.options.enableDragDrop) {
            this.setupDragDropListeners();
        } else {
            this.removeDragDropListeners();
        }
        
        // Update all existing widgets
        for (const [id, widget] of this.widgets) {
            const element = widget.element;
            if (element) {
                this.updateWidgetEditMode(element, widget);
                
                // Add or remove drag event listeners based on enableDragDrop state
                if (this.options.enableDragDrop) {
                    // Remove existing listeners first to avoid duplicates
                    element.removeEventListener('dragstart', this.handleDragStart);
                    element.removeEventListener('dragend', this.handleDragEnd);
                    // Add new listeners
                    element.addEventListener('dragstart', this.handleDragStart.bind(this));
                    element.addEventListener('dragend', this.handleDragEnd.bind(this));
                } else {
                    // Remove drag event listeners
                    element.removeEventListener('dragstart', this.handleDragStart);
                    element.removeEventListener('dragend', this.handleDragEnd);
                }
            }
        }
    }
    
    setupDragDropListeners() {
        if (!this.dragDropListenersSetup) {
            this.container.addEventListener('dragover', this.handleDragOver.bind(this));
            this.container.addEventListener('drop', this.handleDrop.bind(this));
            this.container.addEventListener('dragenter', this.handleDragEnter.bind(this));
            this.container.addEventListener('dragleave', this.handleDragLeave.bind(this));
            this.dragDropListenersSetup = true;
        }
    }
    
    removeDragDropListeners() {
        if (this.dragDropListenersSetup) {
            this.container.removeEventListener('dragover', this.handleDragOver.bind(this));
            this.container.removeEventListener('drop', this.handleDrop.bind(this));
            this.container.removeEventListener('dragenter', this.handleDragEnter.bind(this));
            this.container.removeEventListener('dragleave', this.handleDragLeave.bind(this));
            this.dragDropListenersSetup = false;
        }
    }
    
    updateWidgetEditMode(element, widget) {
        // Update draggable attribute
        element.draggable = this.options.editMode && this.options.enableDragDrop;
        
        // Update widget class
        if (this.options.editMode) {
            element.classList.add('editable');
            element.classList.remove('readonly');
        } else {
            element.classList.remove('editable');
            element.classList.add('readonly');
        }
        
        // Show/hide resize handle
        const resizeHandle = element.querySelector('.grid-widget-resize-handle');
        if (resizeHandle) {
            resizeHandle.style.display = this.options.editMode ? 'flex' : 'none';
        }
        
        // Show/hide remove handle
        const removeHandle = element.querySelector('.remove-handle');
        if (removeHandle) {
            removeHandle.style.display = this.options.editMode ? 'flex' : 'none';
        }
    }
    
    registerWidgetPalette(id, palette) {
        if (!palette || typeof palette !== 'object') {
            throw new Error('Palette must be a valid palette object');
        }
        
        this.registeredPalettes.set(id, palette);
        
        // Copy palette templates to dashboard
        for (const [templateId, template] of palette.widgetTemplates) {
            this.widgetTemplates.set(templateId, template);
        }
    }
    
    unregisterWidgetPalette(id) {
        const palette = this.registeredPalettes.get(id);
        if (palette) {
            // Remove templates from this palette
            for (const templateId of palette.widgetTemplates.keys()) {
                this.widgetTemplates.delete(templateId);
            }
            this.registeredPalettes.delete(id);
        }
    }
    
    getWidgetTemplate(templateId) {
        return this.widgetTemplates.get(templateId);
    }
    
    addWidgetTemplate(id, template) {
        this.widgetTemplates.set(id, template);
    }
    
    removeWidgetTemplate(id) {
        this.widgetTemplates.delete(id);
    }
    
    findNearestValidPosition(targetPosition, size) {
        const maxDistance = Math.max(this.gridWidth, this.gridHeight);
        
        for (let distance = 0; distance <= maxDistance; distance++) {
            // Check positions in expanding rings around the target
            for (let dx = -distance; dx <= distance; dx++) {
                for (let dy = -distance; dy <= distance; dy++) {
                    // Only check positions on the perimeter of the current ring
                    if (Math.abs(dx) !== distance && Math.abs(dy) !== distance) {
                        continue;
                    }
                    
                    const checkPos = {
                        x: Math.max(0, targetPosition.x + dx),
                        y: Math.max(0, targetPosition.y + dy)
                    };
                    
                    if (this.isValidPosition(checkPos, size)) {
                        return checkPos;
                    }
                }
            }
        }
        
        return null;
    }
    
    
    destroy() {
        if (this.resizeObserver) {
            this.resizeObserver.disconnect();
        }
        
        if (this.resizeTimeout) {
            clearTimeout(this.resizeTimeout);
        }
        
        this.widgets.clear();
        this.registeredPalettes.clear();
        this.widgetTemplates.clear();
        this.container.innerHTML = '';
        this.container.classList.remove('grid-dashboard');
        
        // Reset container styles
        this.container.style.position = '';
        this.container.style.display = '';
        this.container.style.gridTemplateColumns = '';
        this.container.style.gridTemplateRows = '';
        this.container.style.gap = '';
        this.container.style.width = '';
        this.container.style.height = '';
        this.container.style.minWidth = '';
        this.container.style.minHeight = '';
    }
    
    getAutoMode() {
        return this.isAutoMode();
    }
    
    setAutoMode(enabled) {
        if (enabled) {
            this.options.cellWidth = 'auto';
            this.options.cellHeight = 'auto';
        } else {
            // Set to default numeric values if currently auto
            if (this.options.cellWidth === 'auto') this.options.cellWidth = 100;
            if (this.options.cellHeight === 'auto') this.options.cellHeight = 100;
        }
        this.updateContainerDimensions();
        this.updateGridOverlay();
        this.emit('autoModeChanged', { autoMode: enabled, cellWidth: this.options.cellWidth, cellHeight: this.options.cellHeight });
    }
    
    toggleAutoMode() {
        this.setAutoMode(!this.isAutoMode());
    }
    
    setCellWidth(width) {
        this.options.cellWidth = width; // Can be number or "auto"
        this.updateContainerDimensions();
        this.updateGridOverlay();
        this.emit('cellWidthChanged', { cellWidth: width });
    }
    
    getCellWidth() {
        return this.options.cellWidth;
    }
    
    setCellHeight(height) {
        this.options.cellHeight = height; // Can be number or "auto"
        this.updateContainerDimensions();
        this.updateGridOverlay();
        this.emit('cellHeightChanged', { cellHeight: height });
    }
    
    getCellHeight() {
        return this.options.cellHeight;
    }
    
    setCellDimensions(width, height) {
        this.options.cellWidth = width; // Can be number or "auto"
        this.options.cellHeight = height; // Can be number or "auto"
        this.updateContainerDimensions();
        this.updateGridOverlay();
        this.emit('cellDimensionsChanged', { cellWidth: width, cellHeight: height });
    }
    
    getCellDimensions() {
        return {
            width: this.options.cellWidth,
            height: this.options.cellHeight
        };
    }
}