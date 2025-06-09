/**
 * CustomHTMLWidgetPalette - A widget palette that works with custom HTML elements
 * Uses widget-type attributes to identify draggable widget templates
 */
class CustomHTMLWidgetPalette {
    constructor(container, options = {}) {
        this.container = typeof container === 'string' ? document.querySelector(container) : container;
        
        if (!this.container) {
            throw new Error('CustomHTMLWidgetPalette: Container element not found');
        }
        
        this.options = {
            widgetSelector: '[widget-type]', // Selector for draggable items
            draggedClass: 'palette-item-dragging',
            hoverClass: 'palette-item-hover',
            disabledClass: 'palette-item-disabled',
            enableHoverEffects: true,
            enableClickToAdd: true,
            allowCustomData: true,
            autoSetDraggable: true,
            enableCustomDragFeedback: true, // Enable custom drag feedback
            ...options
        };
        
        this.widgetTemplates = new Map();
        this.dashboards = new Set(); // Support multiple dashboards
        this.draggedElement = null;
        this.dragFeedback = null;
        this.isInitialized = false;
        
        this.init();
    }
    
    init() {
        if (this.isInitialized) return;
        
        this.setupContainer();
        this.setupEventListeners();
        this.setupDragDropItems();
        this.isInitialized = true;
        
        this.emit('initialized');
    }
    
    setupContainer() {
        this.container.classList.add('custom-widget-palette');
        
        // Add CSS if not already present
        if (!document.getElementById('custom-html-palette-styles')) {
            const style = document.createElement('style');
            style.id = 'custom-html-palette-styles';
            style.textContent = this.getCSS();
            document.head.appendChild(style);
        }
    }
    
    setupEventListeners() {
        // Handle drag events
        this.container.addEventListener('dragstart', this.handleDragStart.bind(this));
        this.container.addEventListener('dragend', this.handleDragEnd.bind(this));
        
        // Handle drag feedback events
        if (this.options.enableCustomDragFeedback) {
            this.container.addEventListener('drag', this.handleDrag.bind(this));
        }
        
        // Handle click events for quick adding
        if (this.options.enableClickToAdd) {
            this.container.addEventListener('click', this.handleClick.bind(this));
        }
        
        // Handle hover effects
        if (this.options.enableHoverEffects) {
            this.container.addEventListener('mouseenter', this.handleMouseEnter.bind(this), true);
            this.container.addEventListener('mouseleave', this.handleMouseLeave.bind(this), true);
        }
        
        // Handle mutations to detect new widget items
        if ('MutationObserver' in window) {
            this.observer = new MutationObserver(this.handleMutations.bind(this));
            this.observer.observe(this.container, {
                childList: true,
                subtree: true,
                attributes: true,
                attributeFilter: ['widget-type']
            });
        }
    }
    
    setupDragDropItems() {
        const items = this.container.querySelectorAll(this.options.widgetSelector);
        items.forEach(item => this.setupDragDropItem(item));
    }
    
    setupDragDropItem(item) {
        const widgetType = item.getAttribute('widget-type');
        if (!widgetType) return;
        
        // Set draggable if auto-setting is enabled
        if (this.options.autoSetDraggable && !item.hasAttribute('draggable')) {
            item.draggable = true;
        }
        
        // Add CSS class for styling
        item.classList.add('custom-palette-item');
        
        // Store widget type data
        item.dataset.widgetType = widgetType;
        
        // Add data attributes for additional configuration
        if (this.options.allowCustomData) {
            this.extractCustomData(item);
        }
        
        this.emit('itemSetup', { item, widgetType });
    }
    
    extractCustomData(item) {
        // Extract data-* attributes for widget configuration
        const customData = {};
        
        Array.from(item.attributes).forEach(attr => {
            if (attr.name.startsWith('data-widget-')) {
                const key = attr.name.replace('data-widget-', '').replace(/-([a-z])/g, (g) => g[1].toUpperCase());
                customData[key] = attr.value;
            }
        });
        
        if (Object.keys(customData).length > 0) {
            item._customWidgetData = customData;
        }
    }
    
    handleMutations(mutations) {
        mutations.forEach(mutation => {
            if (mutation.type === 'childList') {
                // Check for new nodes with widget-type attribute
                mutation.addedNodes.forEach(node => {
                    if (node.nodeType === Node.ELEMENT_NODE) {
                        if (node.matches && node.matches(this.options.widgetSelector)) {
                            this.setupDragDropItem(node);
                        }
                        // Check children too
                        const children = node.querySelectorAll && node.querySelectorAll(this.options.widgetSelector);
                        if (children) {
                            children.forEach(child => this.setupDragDropItem(child));
                        }
                    }
                });
            } else if (mutation.type === 'attributes' && mutation.attributeName === 'widget-type') {
                this.setupDragDropItem(mutation.target);
            }
        });
    }
    
    handleDragStart(e) {
        const item = e.target.closest(this.options.widgetSelector);
        if (!item) return;
        
        const widgetType = item.getAttribute('widget-type');
        if (!widgetType) return;
        
        this.draggedElement = item;
        item.classList.add(this.options.draggedClass);
        
        // Prepare drag data
        const dragData = {
            templateId: widgetType,
            source: 'custom-html-palette',
            customData: item._customWidgetData || {}
        };
        
        // Add any additional data from data-drag-* attributes
        Array.from(item.attributes).forEach(attr => {
            if (attr.name.startsWith('data-drag-')) {
                const key = attr.name.replace('data-drag-', '');
                dragData[key] = attr.value;
            }
        });
        
        e.dataTransfer.effectAllowed = 'copy';
        e.dataTransfer.setData('application/widget-template', JSON.stringify(dragData));
        
        // Create custom drag feedback if enabled
        if (this.options.enableCustomDragFeedback) {
            this.createDragFeedback(item, widgetType);
        }
        
        this.emit('dragStart', { item, widgetType, dragData });
    }
    
    handleDragEnd(e) {
        const item = e.target.closest(this.options.widgetSelector);
        if (item) {
            item.classList.remove(this.options.draggedClass);
        }
        
        // Remove drag feedback
        if (this.options.enableCustomDragFeedback) {
            this.removeDragFeedback();
        }
        
        this.draggedElement = null;
        this.emit('dragEnd', { item });
    }
    
    handleDrag(e) {
        // Update drag feedback position
        if (this.dragFeedback && e.clientX && e.clientY) {
            this.dragFeedback.style.left = e.clientX + 'px';
            this.dragFeedback.style.top = e.clientY + 'px';
        }
    }
    
    handleClick(e) {
        const item = e.target.closest(this.options.widgetSelector);
        if (!item || item.classList.contains(this.options.disabledClass)) return;
        
        const widgetType = item.getAttribute('widget-type');
        if (!widgetType) return;
        
        // Don't trigger if it's a drag operation or if clicking on interactive elements
        if (e.defaultPrevented || e.target.matches('button, input, select, textarea, a')) return;
        
        // Check for click-to-add configuration
        const clickToAdd = item.getAttribute('data-click-to-add');
        if (clickToAdd === 'false') return;
        
        // Add to the first registered dashboard
        const dashboard = Array.from(this.dashboards)[0];
        if (dashboard) {
            this.addWidgetToDashboard(dashboard, widgetType, item._customWidgetData || {});
        }
        
        this.emit('itemClicked', { item, widgetType });
    }
    
    handleMouseEnter(e) {
        const item = e.target.closest(this.options.widgetSelector);
        if (item && !item.classList.contains(this.options.disabledClass)) {
            item.classList.add(this.options.hoverClass);
        }
    }
    
    handleMouseLeave(e) {
        const item = e.target.closest(this.options.widgetSelector);
        if (item) {
            item.classList.remove(this.options.hoverClass);
        }
    }
    
    createDragFeedback(item, widgetType) {
        // Get custom drag image content or create default
        const dragImageHtml = item.getAttribute('data-drag-image-html');
        const icon = item.getAttribute('data-icon') || 
                    item.querySelector('.icon')?.textContent || 
                    this.getDefaultIcon(widgetType);
        const title = item.getAttribute('data-title') || 
                     item.querySelector('.title, .name')?.textContent || 
                     item.getAttribute('data-widget-title') ||
                     widgetType;
        const color = item.getAttribute('data-drag-color') || this.getDefaultColor(widgetType);
        
        // Create feedback element
        this.dragFeedback = document.createElement('div');
        
        if (dragImageHtml) {
            // Use custom HTML
            this.dragFeedback.innerHTML = dragImageHtml;
        } else {
            // Use default format
            this.dragFeedback.innerHTML = `${icon} ${title}`;
        }
        
        // Style the feedback element
        this.dragFeedback.style.cssText = `
            position: fixed;
            padding: 8px 12px;
            background: ${color};
            color: white;
            border-radius: 6px;
            box-shadow: 0 4px 12px rgba(0,0,0,0.3);
            font-size: 14px;
            font-weight: bold;
            pointer-events: none;
            z-index: 10000;
            transform: translate(-50%, -50%);
            white-space: nowrap;
        `;
        
        document.body.appendChild(this.dragFeedback);
    }
    
    removeDragFeedback() {
        if (this.dragFeedback && this.dragFeedback.parentNode) {
            this.dragFeedback.parentNode.removeChild(this.dragFeedback);
            this.dragFeedback = null;
        }
    }
    
    getDefaultIcon(widgetType) {
        const iconMap = {
            'text-widget': '📝',
            'chart-widget': '📊',
            'image-widget': '🖼️',
            'calendar-widget': '📅',
            'weather-widget': '🌤️',
            'todo-widget': '✅',
            'notes-widget': '📝'
        };
        return iconMap[widgetType] || '📦';
    }
    
    getDefaultColor(widgetType) {
        const colorMap = {
            'text-widget': '#4CAF50',
            'chart-widget': '#2196F3',
            'image-widget': '#FF9800',
            'calendar-widget': '#9C27B0',
            'weather-widget': '#00BCD4',
            'todo-widget': '#F44336',
            'notes-widget': '#4CAF50'
        };
        return colorMap[widgetType] || '#666';
    }
    
    createDefaultDragImage(item, widgetType) {
        const dragImage = document.createElement('div');
        dragImage.className = 'custom-palette-drag-image';
        
        // Try to extract icon and title from the item
        const icon = item.querySelector('[data-icon]')?.textContent || 
                    item.querySelector('.icon')?.textContent || 
                    item.getAttribute('data-icon') || '📦';
        
        const title = item.querySelector('[data-title]')?.textContent || 
                     item.querySelector('.title, .name')?.textContent || 
                     item.getAttribute('data-title') || 
                     widgetType;
        
        dragImage.innerHTML = `
            <div class="drag-icon">${icon}</div>
            <div class="drag-title">${title}</div>
        `;
        
        return dragImage;
    }
    
    styleDragImage(dragImage, originalItem) {
        Object.assign(dragImage.style, {
            position: 'absolute',
            top: '-9999px',
            left: '-9999px',
            background: 'white',
            border: '2px solid #4a90e2',
            borderRadius: '8px',
            padding: '10px',
            boxShadow: '0 4px 12px rgba(0,0,0,0.2)',
            fontSize: '14px',
            display: 'flex',
            alignItems: 'center',
            gap: '8px',
            zIndex: '9999',
            pointerEvents: 'none',
            minWidth: '120px'
        });
        
        // Apply custom drag image styles if specified
        const customStyles = originalItem.getAttribute('data-drag-image-style');
        if (customStyles) {
            try {
                const styles = JSON.parse(customStyles);
                Object.assign(dragImage.style, styles);
            } catch (e) {
                console.warn('Invalid drag image style JSON:', customStyles);
            }
        }
    }
    
    // Public API methods
    
    registerWidgetTemplate(widgetType, templateOrWidget) {
        let templateConfig;
        
        // Check if the second parameter is a widget instance
        if (templateOrWidget && typeof templateOrWidget.render === 'function') {
            // It's a widget instance - create a template from it
            const widget = templateOrWidget;
            templateConfig = {
                id: widgetType,
                name: widget.title || widgetType,
                description: widget.type || 'Custom widget',
                factory: (options) => {
                    // Clone the widget with new options
                    const WidgetClass = widget.constructor;
                    const newWidget = new WidgetClass({
                        title: widget.title,
                        content: widget.content,
                        width: widget.size?.width || widget.width,
                        height: widget.size?.height || widget.height,
                        type: widget.type,
                        ...options
                    });
                    
                    // Copy custom methods from the original widget
                    if (widget.onRender && widget.onRender !== WidgetClass.prototype.onRender) {
                        newWidget.onRender = widget.onRender;
                    }
                    
                    // Copy any other custom methods or properties
                    for (const key in widget) {
                        if (typeof widget[key] === 'function' && 
                            key !== 'constructor' && 
                            key !== 'render' &&
                            !newWidget.hasOwnProperty(key)) {
                            newWidget[key] = widget[key];
                        }
                    }
                    
                    return newWidget;
                },
                defaultOptions: {
                    title: widget.title,
                    content: widget.content,
                    width: widget.size?.width || widget.width,
                    height: widget.size?.height || widget.height,
                    type: widget.type
                }
            };
        } else {
            // It's a template configuration object - use as is
            templateConfig = {
                id: widgetType,
                name: templateOrWidget.name || widgetType,
                description: templateOrWidget.description || '',
                factory: templateOrWidget.factory || (() => new GridWidget(templateOrWidget.defaultOptions)),
                defaultOptions: templateOrWidget.defaultOptions || {},
                ...templateOrWidget
            };
        }
        
        this.widgetTemplates.set(widgetType, templateConfig);
        this.emit('templateRegistered', { widgetType, template: templateConfig });
    }
    
    unregisterWidgetTemplate(widgetType) {
        const removed = this.widgetTemplates.delete(widgetType);
        if (removed) {
            this.emit('templateUnregistered', { widgetType });
        }
        return removed;
    }
    
    getWidgetTemplate(widgetType) {
        return this.widgetTemplates.get(widgetType);
    }
    
    registerDashboard(dashboard) {
        this.dashboards.add(dashboard);
        
        // Register this palette with the dashboard
        if (dashboard.registerWidgetPalette) {
            dashboard.registerWidgetPalette(`custom-html-palette-${Date.now()}`, this);
        }
        
        this.emit('dashboardRegistered', { dashboard });
    }
    
    unregisterDashboard(dashboard) {
        const removed = this.dashboards.delete(dashboard);
        this.emit('dashboardUnregistered', { dashboard });
        return removed;
    }
    
    addWidgetToDashboard(dashboard, widgetType, customData = {}) {
        const template = this.getWidgetTemplate(widgetType);
        if (!template) {
            console.warn(`Widget template "${widgetType}" not found`);
            return null;
        }
        
        try {
            // Create widget instance
            const widget = template.factory({ ...template.defaultOptions, ...customData });
            
            // Add to dashboard
            const widgetId = dashboard.addWidget(widget);
            
            this.emit('widgetAdded', { dashboard, widget, widgetId, widgetType, customData });
            
            return widgetId;
        } catch (error) {
            console.error('Error adding widget to dashboard:', error);
            return null;
        }
    }
    
    enableItem(selector) {
        const items = typeof selector === 'string' ? 
            this.container.querySelectorAll(selector) : [selector];
        
        items.forEach(item => {
            item.classList.remove(this.options.disabledClass);
            if (this.options.autoSetDraggable) {
                item.draggable = true;
            }
        });
    }
    
    disableItem(selector) {
        const items = typeof selector === 'string' ? 
            this.container.querySelectorAll(selector) : [selector];
        
        items.forEach(item => {
            item.classList.add(this.options.disabledClass);
            item.draggable = false;
        });
    }
    
    refresh() {
        this.setupDragDropItems();
        this.emit('refreshed');
    }
    
    destroy() {
        if (this.observer) {
            this.observer.disconnect();
        }
        
        // Remove event listeners
        this.container.removeEventListener('dragstart', this.handleDragStart);
        this.container.removeEventListener('dragend', this.handleDragEnd);
        this.container.removeEventListener('drag', this.handleDrag);
        this.container.removeEventListener('click', this.handleClick);
        this.container.removeEventListener('mouseenter', this.handleMouseEnter, true);
        this.container.removeEventListener('mouseleave', this.handleMouseLeave, true);
        
        // Clean up drag feedback
        this.removeDragFeedback();
        
        // Clear data
        this.widgetTemplates.clear();
        this.dashboards.clear();
        
        // Remove CSS classes
        this.container.classList.remove('custom-widget-palette');
        
        this.emit('destroyed');
    }
    
    emit(eventName, data = {}) {
        const event = new CustomEvent(`customWidgetPalette:${eventName}`, { detail: data });
        this.container.dispatchEvent(event);
    }
    
    getCSS() {
        return `
            .custom-widget-palette {
                user-select: none;
            }
            
            .custom-palette-item {
                cursor: grab;
                transition: all 0.2s ease;
                position: relative;
            }
            
            .custom-palette-item.${this.options.hoverClass} {
                transform: translateY(-2px);
                box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
            }
            
            .custom-palette-item.${this.options.draggedClass} {
                cursor: grabbing;
                opacity: 0.6;
                transform: scale(0.95);
            }
            
            .custom-palette-item.${this.options.disabledClass} {
                opacity: 0.5;
                cursor: not-allowed;
                pointer-events: none;
            }
            
            .custom-palette-drag-image {
                pointer-events: none;
                white-space: nowrap;
            }
            
            .custom-palette-drag-image .drag-icon {
                font-size: 16px;
                flex-shrink: 0;
            }
            
            .custom-palette-drag-image .drag-title {
                font-weight: 600;
                color: #333;
                overflow: hidden;
                text-overflow: ellipsis;
            }
            
            /* Hover effects for interactive elements inside palette items */
            .custom-palette-item button,
            .custom-palette-item .interactive {
                pointer-events: auto;
            }
            
            .custom-palette-item[draggable="true"] {
                cursor: grab;
            }
            
            .custom-palette-item[draggable="false"] {
                cursor: default;
            }
            
            /* Animation for click feedback */
            .custom-palette-item:active {
                transform: scale(0.98);
            }
            
            /* Focus styles for accessibility */
            .custom-palette-item:focus {
                outline: 2px solid #4a90e2;
                outline-offset: 2px;
            }
        `;
    }
}

// Export for use
if (typeof module !== 'undefined' && module.exports) {
    module.exports = CustomHTMLWidgetPalette;
} else if (typeof window !== 'undefined') {
    window.CustomHTMLWidgetPalette = CustomHTMLWidgetPalette;
}