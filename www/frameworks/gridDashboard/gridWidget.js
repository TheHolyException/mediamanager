/**
 * GridWidget - Base class for dashboard widgets
 * Provides a foundation for creating draggable widgets
 */
class GridWidget {
    constructor(options = {}) {
        this.id = options.id || null;
        this.type = options.type || 'grid';
        this.title = options.title || 'Widget';
        this.size = {
            width: options.size?.width || options.width || 2,
            height: options.size?.height || options.height || 2
        };
        this.position = {
            x: options.position?.x || options.x || 0,
            y: options.position?.y || options.y || 0
        };
        this.content = options.content || '';
        this.actions = options.actions || [];
        this.resizable = options.resizable !== false;
        this.removable = options.removable !== false;
        this.element = null;
        this.dashboard = null;
    }
    
    render() {
        const widget = document.createElement('div');
        widget.className = 'grid-widget';
        widget.dataset.widgetId = this.id;
        
        // Create header
        const header = this.createHeader();
        widget.appendChild(header);
        
        // Create content area
        const content = this.createContent();
        widget.appendChild(content);
        
        // Add resize handle if resizable
        if (this.resizable) {
            const resizeHandle = document.createElement('div');
            resizeHandle.className = 'grid-widget-resize-handle';
            resizeHandle.innerHTML = '⤡';
            resizeHandle.title = 'Resize';
            resizeHandle.addEventListener('mousedown', this.handleResizeStart.bind(this));
            widget.appendChild(resizeHandle);
        }
        
        this.element = widget;
        this.onRender();
        
        return widget;
    }
    
    createHeader() {
        const header = document.createElement('div');
        header.className = 'grid-widget-header';
        
        const title = document.createElement('div');
        title.className = 'grid-widget-title';
        title.textContent = this.title;
        header.appendChild(title);
        
        const actions = document.createElement('div');
        actions.className = 'grid-widget-actions';
        
        // Add custom actions
        this.actions.forEach(action => {
            const button = document.createElement('button');
            button.className = 'grid-widget-action';
            button.innerHTML = action.icon || '⚙️';
            button.title = action.tooltip || '';
            button.addEventListener('click', (e) => {
                e.stopPropagation();
                if (action.handler) {
                    action.handler.call(this, e);
                }
            });
            actions.appendChild(button);
        });
        
        // Resize handle will be added to the widget content, not header
        
        // Add remove button if removable
        if (this.removable) {
            const removeBtn = document.createElement('button');
            removeBtn.className = 'grid-widget-action remove-handle';
            removeBtn.innerHTML = '✕';
            removeBtn.title = 'Remove';
            removeBtn.addEventListener('click', (e) => {
                e.stopPropagation();
                this.remove();
            });
            actions.appendChild(removeBtn);
        }
        
        header.appendChild(actions);
        return header;
    }
    
    createContent() {
        const content = document.createElement('div');
        content.className = 'grid-widget-content';
        
        if (typeof this.content === 'string') {
            content.innerHTML = this.content;
        } else if (this.content instanceof HTMLElement) {
            content.appendChild(this.content);
        } else if (typeof this.content === 'function') {
            const result = this.content.call(this);
            if (typeof result === 'string') {
                content.innerHTML = result;
            } else if (result instanceof HTMLElement) {
                content.appendChild(result);
            }
        }
        
        return content;
    }
    
    handleResizeStart(e) {
        e.preventDefault();
        e.stopPropagation();
        
        const startX = e.clientX;
        const startY = e.clientY;
        const startWidth = this.size.width;
        const startHeight = this.size.height;
        
        const handleResize = (e) => {
            const deltaX = e.clientX - startX;
            const deltaY = e.clientY - startY;
            
            // Calculate new size based on grid cell size
            const cellWidth = this.element.offsetWidth / this.size.width;
            const cellHeight = this.element.offsetHeight / this.size.height;
            
            let newWidth = Math.max(1, startWidth + Math.round(deltaX / cellWidth));
            let newHeight = Math.max(1, startHeight + Math.round(deltaY / cellHeight));
            
            // Check grid boundaries if auto-resize is disabled
            if (this.dashboard) {
                const maxWidth = this.dashboard.options.autoResizeWidth ? 
                    Infinity : 
                    this.dashboard.gridWidth - this.position.x;
                const maxHeight = this.dashboard.options.autoResizeHeight ? 
                    Infinity : 
                    this.dashboard.gridHeight - this.position.y;
                
                newWidth = Math.min(newWidth, maxWidth);
                newHeight = Math.min(newHeight, maxHeight);
            }
            
            this.resize(newWidth, newHeight);
        };
        
        const handleResizeEnd = () => {
            document.removeEventListener('mousemove', handleResize);
            document.removeEventListener('mouseup', handleResizeEnd);
            
            // Always unlock the resize handle when mouse is released
            const resizeHandle = this.element?.querySelector('.grid-widget-resize-handle');
            if (resizeHandle) {
                resizeHandle.classList.remove('constrained');
                resizeHandle.title = 'Resize';
            }
            
            this.onResizeEnd();
        };
        
        document.addEventListener('mousemove', handleResize);
        document.addEventListener('mouseup', handleResizeEnd);
        
        this.onResizeStart();
    }
    
    resize(width, height) {
        this.size.width = width;
        this.size.height = height;
        
        if (this.dashboard) {
            this.dashboard.resizeWidget(this.id, this.size);
        }
        
        this.onResize();
    }
    
    move(x, y) {
        this.position.x = x;
        this.position.y = y;
        
        if (this.dashboard) {
            this.dashboard.moveWidget(this.id, this.position);
        }
        
        this.onMove();
    }
    
    remove() {
        if (this.dashboard) {
            this.dashboard.removeWidget(this.id);
        }
        
        this.onRemove();
    }
    
    update(options = {}) {
        if (options.title !== undefined) {
            this.title = options.title;
            const titleElement = this.element?.querySelector('.grid-widget-title');
            if (titleElement) {
                titleElement.textContent = this.title;
            }
        }
        
        if (options.content !== undefined) {
            this.content = options.content;
            const contentElement = this.element?.querySelector('.grid-widget-content');
            if (contentElement) {
                contentElement.innerHTML = '';
                const newContent = this.createContent();
                contentElement.appendChild(...newContent.childNodes);
            }
        }
        
        this.onUpdate(options);
    }
    
    getData() {
        return {
            id: this.id,
            type: this.type,
            title: this.title,
            size: { ...this.size },
            position: { ...this.position },
            content: this.content,
            actions: this.actions,
            resizable: this.resizable,
            removable: this.removable
        };
    }
    
    setData(data) {
        Object.assign(this, data);
        if (this.element) {
            this.element.remove();
            this.render();
        }
    }
    
    // Event handlers (override in subclasses)
    onRender() {
        this.updateResizeHandleState();
    }
    onUpdate(options) {}
    onMove() {
        this.updateResizeHandleState();
    }
    onResize() {
        this.updateResizeHandleState();
    }
    
    updateResizeHandleState() {
        const resizeHandle = this.element?.querySelector('.grid-widget-resize-handle');
        if (resizeHandle && this.dashboard) {
            const atWidthBoundary = !this.dashboard.options.autoResizeWidth && 
                                  (this.position.x + this.size.width >= this.dashboard.gridWidth);
            const atHeightBoundary = !this.dashboard.options.autoResizeHeight && 
                                   (this.position.y + this.size.height >= this.dashboard.gridHeight);
            
            if (atWidthBoundary || atHeightBoundary) {
                resizeHandle.classList.add('constrained');
                resizeHandle.title = 'Resize constrained by grid boundaries';
            } else {
                resizeHandle.classList.remove('constrained');
                resizeHandle.title = 'Resize';
            }
        }
    }
    
    onResizeStart() {}
    onResizeEnd() {}
    onRemove() {}
    
    // Utility methods
    querySelector(selector) {
        return this.element?.querySelector(selector);
    }
    
    querySelectorAll(selector) {
        return this.element?.querySelectorAll(selector) || [];
    }
    
    addClass(className) {
        this.element?.classList.add(className);
    }
    
    removeClass(className) {
        this.element?.classList.remove(className);
    }
    
    toggleClass(className) {
        this.element?.classList.toggle(className);
    }
    
    addEventListener(event, handler) {
        this.element?.addEventListener(event, handler);
    }
    
    removeEventListener(event, handler) {
        this.element?.removeEventListener(event, handler);
    }
    
    emit(eventName, data) {
        if (this.element) {
            const event = new CustomEvent(`widget:${eventName}`, { detail: { widget: this, data } });
            this.element.dispatchEvent(event);
        }
    }
}

/**
 * TextWidget - A simple text display widget
 */
class TextWidget extends GridWidget {
    constructor(options = {}) {
        super({
            type: 'text',
            title: 'Text Widget',
            width: 2,
            height: 2,
            ...options
        });
        
        this.text = options.text || 'Hello World!';
        this.textAlign = options.textAlign || 'left';
        this.fontSize = options.fontSize || '16px';
    }
    
    createContent() {
        const content = document.createElement('div');
        content.className = 'grid-widget-content';
        content.style.textAlign = this.textAlign;
        content.style.fontSize = this.fontSize;
        content.style.display = 'flex';
        content.style.alignItems = 'center';
        content.style.justifyContent = this.textAlign === 'center' ? 'center' : 'flex-start';
        content.textContent = this.text;
        
        return content;
    }
    
    setText(text) {
        this.text = text;
        const content = this.querySelector('.grid-widget-content');
        if (content) {
            content.textContent = text;
        }
    }
    
    getData() {
        return {
            ...super.getData(),
            text: this.text,
            textAlign: this.textAlign,
            fontSize: this.fontSize
        };
    }
}

/**
 * ChartWidget - A widget for displaying charts
 */
class ChartWidget extends GridWidget {
    constructor(options = {}) {
        super({
            type: 'chart',
            title: 'Chart Widget',
            width: 4,
            height: 3,
            ...options
        });
        
        this.chartType = options.chartType || 'line';
        this.chartData = options.chartData || [];
        this.chartOptions = options.chartOptions || {};
    }
    
    createContent() {
        const content = document.createElement('div');
        content.className = 'grid-widget-content';
        
        const canvas = document.createElement('canvas');
        canvas.style.width = '100%';
        canvas.style.height = '100%';
        content.appendChild(canvas);
        
        return content;
    }
    
    onRender() {
        // Chart rendering would be implemented here
        // This is a placeholder for chart library integration
        const canvas = this.querySelector('canvas');
        if (canvas) {
            const ctx = canvas.getContext('2d');
            ctx.fillStyle = '#f0f0f0';
            ctx.fillRect(0, 0, canvas.width, canvas.height);
            ctx.fillStyle = '#333';
            ctx.font = '16px Arial';
            ctx.textAlign = 'center';
            ctx.fillText('Chart Placeholder', canvas.width / 2, canvas.height / 2);
        }
    }
    
    getData() {
        return {
            ...super.getData(),
            chartType: this.chartType,
            chartData: this.chartData,
            chartOptions: this.chartOptions
        };
    }
}

/**
 * ImageWidget - A widget for displaying images
 */
class ImageWidget extends GridWidget {
    constructor(options = {}) {
        super({
            type: 'image',
            title: 'Image Widget',
            width: 3,
            height: 3,
            ...options
        });
        
        this.src = options.src || '';
        this.alt = options.alt || '';
        this.objectFit = options.objectFit || 'cover';
    }
    
    createContent() {
        const content = document.createElement('div');
        content.className = 'grid-widget-content';
        content.style.padding = '0';
        content.style.display = 'flex';
        content.style.alignItems = 'center';
        content.style.justifyContent = 'center';
        
        if (this.src) {
            const img = document.createElement('img');
            img.src = this.src;
            img.alt = this.alt;
            img.style.width = '100%';
            img.style.height = '100%';
            img.style.objectFit = this.objectFit;
            content.appendChild(img);
        } else {
            content.innerHTML = '<div style="color: #999; text-align: center;">No image</div>';
        }
        
        return content;
    }
    
    setImage(src, alt = '') {
        this.src = src;
        this.alt = alt;
        
        const content = this.querySelector('.grid-widget-content');
        if (content) {
            content.innerHTML = '';
            if (src) {
                const img = document.createElement('img');
                img.src = src;
                img.alt = alt;
                img.style.width = '100%';
                img.style.height = '100%';
                img.style.objectFit = this.objectFit;
                content.appendChild(img);
            } else {
                content.innerHTML = '<div style="color: #999; text-align: center;">No image</div>';
            }
        }
    }
    
    getData() {
        return {
            ...super.getData(),
            src: this.src,
            alt: this.alt,
            objectFit: this.objectFit
        };
    }
}