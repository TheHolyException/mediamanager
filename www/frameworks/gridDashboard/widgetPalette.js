/**
 * WidgetPalette - A draggable widget palette for creating new dashboard widgets
 * Provides a container from which users can drag widgets into the dashboard
 */
class WidgetPalette {
    constructor(container, options = {}) {
        this.container = typeof container === 'string' ? document.querySelector(container) : container;
        
        if (!this.container) {
            throw new Error('WidgetPalette: Container element not found');
        }
        
        this.options = {
            title: 'Widget Palette',
            layout: 'grid', // 'grid', 'list', 'tabs'
            collapsible: true,
            searchable: true,
            categories: true,
            itemsPerRow: 3,
            showPreview: true,
            allowCustomWidgets: true,
            ...options
        };
        
        this.widgetTemplates = new Map();
        this.categories = new Map();
        this.isCollapsed = false;
        this.currentFilter = '';
        this.currentCategory = 'all';
        
        this.init();
        this.loadDefaultTemplates();
    }
    
    init() {
        this.setupContainer();
        this.setupStyles();
        this.createHeader();
        this.createSearchBar();
        this.createCategoryFilter();
        this.createPaletteContent();
        this.setupEventListeners();
    }
    
    setupContainer() {
        this.container.classList.add('widget-palette');
        this.container.innerHTML = '';
    }
    
    setupStyles() {
        if (!document.getElementById('widget-palette-styles')) {
            const style = document.createElement('style');
            style.id = 'widget-palette-styles';
            style.textContent = this.getCSS();
            document.head.appendChild(style);
        }
    }
    
    createHeader() {
        this.header = document.createElement('div');
        this.header.className = 'palette-header';
        
        const title = document.createElement('h3');
        title.className = 'palette-title';
        title.textContent = this.options.title;
        this.header.appendChild(title);
        
        if (this.options.collapsible) {
            const toggleBtn = document.createElement('button');
            toggleBtn.className = 'palette-toggle';
            toggleBtn.innerHTML = '−';
            toggleBtn.title = 'Collapse/Expand';
            toggleBtn.addEventListener('click', () => this.toggle());
            this.header.appendChild(toggleBtn);
        }
        
        this.container.appendChild(this.header);
    }
    
    createSearchBar() {
        if (!this.options.searchable) return;
        
        this.searchContainer = document.createElement('div');
        this.searchContainer.className = 'palette-search';
        
        const searchInput = document.createElement('input');
        searchInput.type = 'text';
        searchInput.placeholder = 'Search widgets...';
        searchInput.className = 'palette-search-input';
        searchInput.addEventListener('input', (e) => this.filterWidgets(e.target.value));
        
        this.searchContainer.appendChild(searchInput);
        this.container.appendChild(this.searchContainer);
    }
    
    createCategoryFilter() {
        if (!this.options.categories) return;
        
        this.categoryContainer = document.createElement('div');
        this.categoryContainer.className = 'palette-categories';
        this.container.appendChild(this.categoryContainer);
        this.updateCategoryFilter();
    }
    
    createPaletteContent() {
        this.content = document.createElement('div');
        this.content.className = 'palette-content';
        this.content.classList.add(`layout-${this.options.layout}`);
        
        if (this.options.layout === 'grid') {
            this.content.style.gridTemplateColumns = `repeat(${this.options.itemsPerRow}, 1fr)`;
        }
        
        this.container.appendChild(this.content);
    }
    
    setupEventListeners() {
        // Handle drag start from palette items
        this.container.addEventListener('dragstart', this.handleDragStart.bind(this));
        this.container.addEventListener('dragend', this.handleDragEnd.bind(this));
    }
    
    addWidgetTemplate(id, template) {
        this.widgetTemplates.set(id, {
            id,
            name: template.name || id,
            description: template.description || '',
            icon: template.icon || '📦',
            category: template.category || 'general',
            preview: template.preview || null,
            factory: template.factory || (() => new GridWidget(template.defaultOptions)),
            defaultOptions: template.defaultOptions || {},
            ...template
        });
        
        // Add category if it doesn't exist
        if (!this.categories.has(template.category || 'general')) {
            this.addCategory(template.category || 'general', {
                name: template.category || 'General',
                icon: template.categoryIcon || '📁'
            });
        }
        
        this.renderPalette();
    }
    
    removeWidgetTemplate(id) {
        this.widgetTemplates.delete(id);
        this.renderPalette();
    }
    
    addCategory(id, category) {
        this.categories.set(id, {
            id,
            name: category.name || id,
            icon: category.icon || '📁',
            description: category.description || ''
        });
        this.updateCategoryFilter();
    }
    
    updateCategoryFilter() {
        if (!this.categoryContainer) return;
        
        this.categoryContainer.innerHTML = '';
        
        // Add "All" category
        const allBtn = document.createElement('button');
        allBtn.className = 'category-btn';
        allBtn.classList.toggle('active', this.currentCategory === 'all');
        allBtn.innerHTML = '📋 All';
        allBtn.addEventListener('click', () => this.filterByCategory('all'));
        this.categoryContainer.appendChild(allBtn);
        
        // Add other categories
        for (const [id, category] of this.categories) {
            const btn = document.createElement('button');
            btn.className = 'category-btn';
            btn.classList.toggle('active', this.currentCategory === id);
            btn.innerHTML = `${category.icon} ${category.name}`;
            btn.title = category.description;
            btn.addEventListener('click', () => this.filterByCategory(id));
            this.categoryContainer.appendChild(btn);
        }
    }
    
    renderPalette() {
        if (!this.content) return;
        
        this.content.innerHTML = '';
        
        const filteredTemplates = this.getFilteredTemplates();
        
        if (filteredTemplates.length === 0) {
            const emptyState = document.createElement('div');
            emptyState.className = 'palette-empty';
            emptyState.innerHTML = `
                <div class="empty-icon">🔍</div>
                <div class="empty-text">No widgets found</div>
                <div class="empty-subtext">Try adjusting your search or category filter</div>
            `;
            this.content.appendChild(emptyState);
            return;
        }
        
        filteredTemplates.forEach(template => {
            const item = this.createPaletteItem(template);
            this.content.appendChild(item);
        });
    }
    
    createPaletteItem(template) {
        const item = document.createElement('div');
        item.className = 'palette-item';
        item.draggable = true;
        item.dataset.templateId = template.id;
        
        // Create icon
        const icon = document.createElement('div');
        icon.className = 'palette-item-icon';
        icon.innerHTML = template.icon;
        item.appendChild(icon);
        
        // Create content
        const content = document.createElement('div');
        content.className = 'palette-item-content';
        
        const name = document.createElement('div');
        name.className = 'palette-item-name';
        name.textContent = template.name;
        content.appendChild(name);
        
        if (template.description) {
            const desc = document.createElement('div');
            desc.className = 'palette-item-description';
            desc.textContent = template.description;
            content.appendChild(desc);
        }
        
        item.appendChild(content);
        
        // Add preview if available
        if (this.options.showPreview && template.preview) {
            const preview = document.createElement('div');
            preview.className = 'palette-item-preview';
            if (typeof template.preview === 'string') {
                preview.innerHTML = template.preview;
            } else if (template.preview instanceof HTMLElement) {
                preview.appendChild(template.preview.cloneNode(true));
            }
            item.appendChild(preview);
        }
        
        // Add category badge
        const categoryBadge = document.createElement('div');
        categoryBadge.className = 'palette-item-category';
        const category = this.categories.get(template.category) || { name: template.category };
        categoryBadge.textContent = category.name;
        item.appendChild(categoryBadge);
        
        return item;
    }
    
    getFilteredTemplates() {
        let templates = Array.from(this.widgetTemplates.values());
        
        // Filter by category
        if (this.currentCategory !== 'all') {
            templates = templates.filter(t => t.category === this.currentCategory);
        }
        
        // Filter by search
        if (this.currentFilter) {
            const filter = this.currentFilter.toLowerCase();
            templates = templates.filter(t => 
                t.name.toLowerCase().includes(filter) ||
                t.description.toLowerCase().includes(filter) ||
                t.category.toLowerCase().includes(filter)
            );
        }
        
        return templates;
    }
    
    filterWidgets(searchTerm) {
        this.currentFilter = searchTerm;
        this.renderPalette();
    }
    
    filterByCategory(categoryId) {
        this.currentCategory = categoryId;
        this.updateCategoryFilter();
        this.renderPalette();
    }
    
    toggle() {
        this.isCollapsed = !this.isCollapsed;
        this.container.classList.toggle('collapsed', this.isCollapsed);
        
        const toggleBtn = this.header.querySelector('.palette-toggle');
        if (toggleBtn) {
            toggleBtn.innerHTML = this.isCollapsed ? '+' : '−';
        }
    }
    
    handleDragStart(e) {
        const paletteItem = e.target.closest('.palette-item');
        if (!paletteItem) return;
        
        const templateId = paletteItem.dataset.templateId;
        const template = this.widgetTemplates.get(templateId);
        
        if (!template) return;
        
        e.dataTransfer.effectAllowed = 'copy';
        e.dataTransfer.setData('application/widget-template', JSON.stringify({
            templateId,
            source: 'palette'
        }));
        
        paletteItem.classList.add('dragging');
        
        // Create drag image
        this.createDragImage(e, template);
        
        // Emit event
        this.emit('dragStart', { template, element: paletteItem });
    }
    
    handleDragEnd(e) {
        const paletteItem = e.target.closest('.palette-item');
        if (paletteItem) {
            paletteItem.classList.remove('dragging');
        }
        
        // Emit event
        this.emit('dragEnd', { element: paletteItem });
    }
    
    createDragImage(e, template) {
        // Create a visual representation for dragging
        const dragImage = document.createElement('div');
        dragImage.className = 'palette-drag-image';
        dragImage.innerHTML = `
            <div class="drag-icon">${template.icon}</div>
            <div class="drag-name">${template.name}</div>
        `;
        
        // Style the drag image
        Object.assign(dragImage.style, {
            position: 'absolute',
            top: '-9999px',
            background: '#fff',
            border: '2px solid #4a90e2',
            borderRadius: '8px',
            padding: '10px',
            boxShadow: '0 4px 12px rgba(0,0,0,0.2)',
            fontSize: '14px',
            display: 'flex',
            alignItems: 'center',
            gap: '8px',
            zIndex: '9999'
        });
        
        document.body.appendChild(dragImage);
        
        // Set as drag image
        e.dataTransfer.setDragImage(dragImage, 50, 25);
        
        // Clean up after a short delay
        setTimeout(() => {
            if (dragImage.parentNode) {
                dragImage.parentNode.removeChild(dragImage);
            }
        }, 100);
    }
    
    loadDefaultTemplates() {
        // Basic widget types
        this.addWidgetTemplate('text-widget', {
            name: 'Text Widget',
            description: 'Simple text display widget',
            icon: '📝',
            category: 'basic',
            preview: '<div style="padding:5px;font-size:12px;">Sample text</div>',
            factory: (options) => new TextWidget({
                title: 'Text Widget',
                text: 'Hello World!',
                width: 2,
                height: 2,
                ...options
            })
        });
        
        this.addWidgetTemplate('chart-widget', {
            name: 'Chart Widget',
            description: 'Data visualization widget',
            icon: '📊',
            category: 'data',
            preview: '<div style="padding:5px;font-size:10px;">📈 Chart</div>',
            factory: (options) => new ChartWidget({
                title: 'Chart Widget',
                width: 4,
                height: 3,
                ...options
            })
        });
        
        this.addWidgetTemplate('image-widget', {
            name: 'Image Widget',
            description: 'Image display widget',
            icon: '🖼️',
            category: 'media',
            preview: '<div style="padding:5px;font-size:10px;">🖼️ Image</div>',
            factory: (options) => new ImageWidget({
                title: 'Image Widget',
                width: 3,
                height: 3,
                ...options
            })
        });
        
        this.addWidgetTemplate('custom-widget', {
            name: 'Custom Widget',
            description: 'Enhanced widget with custom styling',
            icon: '⚡',
            category: 'advanced',
            preview: '<div style="padding:5px;font-size:10px;background:#4a90e2;color:white;border-radius:3px;">Custom</div>',
            factory: (options) => new CustomWidget({
                title: 'Custom Widget',
                width: 3,
                height: 2,
                headerConfig: {
                    backgroundColor: '#4a90e2',
                    textColor: '#ffffff',
                    icon: '⚡'
                },
                ...options
            })
        });
        
        // Add default categories
        this.addCategory('basic', { name: 'Basic', icon: '📋' });
        this.addCategory('data', { name: 'Data', icon: '📊' });
        this.addCategory('media', { name: 'Media', icon: '🎨' });
        this.addCategory('advanced', { name: 'Advanced', icon: '⚙️' });
    }
    
    emit(eventName, data) {
        const event = new CustomEvent(`widgetPalette:${eventName}`, { detail: data });
        this.container.dispatchEvent(event);
    }
    
    getCSS() {
        return `
            .widget-palette {
                background: #ffffff;
                border: 1px solid #e1e5e9;
                border-radius: 8px;
                box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
                overflow: hidden;
                transition: all 0.3s ease;
                user-select: none;
            }
            
            .widget-palette.collapsed .palette-search,
            .widget-palette.collapsed .palette-categories,
            .widget-palette.collapsed .palette-content {
                display: none;
            }
            
            .palette-header {
                background: #f8f9fa;
                border-bottom: 1px solid #e1e5e9;
                padding: 12px 16px;
                display: flex;
                justify-content: space-between;
                align-items: center;
            }
            
            .palette-title {
                margin: 0;
                font-size: 16px;
                font-weight: 600;
                color: #333;
            }
            
            .palette-toggle {
                background: none;
                border: none;
                font-size: 18px;
                font-weight: bold;
                cursor: pointer;
                color: #666;
                width: 24px;
                height: 24px;
                display: flex;
                align-items: center;
                justify-content: center;
                border-radius: 4px;
                transition: all 0.2s ease;
            }
            
            .palette-toggle:hover {
                background: #e9ecef;
                color: #333;
            }
            
            .palette-search {
                padding: 12px 16px;
                border-bottom: 1px solid #e1e5e9;
            }
            
            .palette-search-input {
                width: 100%;
                padding: 8px 12px;
                border: 1px solid #e1e5e9;
                border-radius: 6px;
                font-size: 14px;
                outline: none;
                transition: border-color 0.2s ease;
            }
            
            .palette-search-input:focus {
                border-color: #4a90e2;
                box-shadow: 0 0 0 2px rgba(74, 144, 226, 0.1);
            }
            
            .palette-categories {
                padding: 8px 12px;
                border-bottom: 1px solid #e1e5e9;
                display: flex;
                flex-wrap: wrap;
                gap: 6px;
            }
            
            .category-btn {
                background: #f8f9fa;
                border: 1px solid #e1e5e9;
                border-radius: 16px;
                padding: 4px 12px;
                font-size: 12px;
                cursor: pointer;
                transition: all 0.2s ease;
                white-space: nowrap;
            }
            
            .category-btn:hover {
                background: #e9ecef;
                border-color: #adb5bd;
            }
            
            .category-btn.active {
                background: #4a90e2;
                border-color: #4a90e2;
                color: white;
            }
            
            .palette-content {
                padding: 16px;
                max-height: 400px;
                overflow-y: auto;
            }
            
            .palette-content.layout-grid {
                display: grid;
                gap: 12px;
            }
            
            .palette-content.layout-list {
                display: flex;
                flex-direction: column;
                gap: 8px;
            }
            
            .palette-item {
                background: #ffffff;
                border: 1px solid #e1e5e9;
                border-radius: 8px;
                padding: 12px;
                cursor: grab;
                transition: all 0.2s ease;
                position: relative;
                overflow: hidden;
            }
            
            .palette-item:hover {
                border-color: #4a90e2;
                box-shadow: 0 2px 8px rgba(74, 144, 226, 0.1);
                transform: translateY(-1px);
            }
            
            .palette-item:active,
            .palette-item.dragging {
                cursor: grabbing;
                transform: scale(0.98);
                opacity: 0.8;
            }
            
            .palette-item-icon {
                font-size: 24px;
                text-align: center;
                margin-bottom: 8px;
            }
            
            .palette-item-content {
                text-align: center;
            }
            
            .palette-item-name {
                font-weight: 600;
                font-size: 14px;
                color: #333;
                margin-bottom: 4px;
            }
            
            .palette-item-description {
                font-size: 12px;
                color: #666;
                line-height: 1.3;
                margin-bottom: 8px;
            }
            
            .palette-item-preview {
                background: #f8f9fa;
                border: 1px solid #e1e5e9;
                border-radius: 4px;
                margin: 8px 0;
                min-height: 40px;
                display: flex;
                align-items: center;
                justify-content: center;
            }
            
            .palette-item-category {
                position: absolute;
                top: 6px;
                right: 6px;
                background: rgba(74, 144, 226, 0.1);
                color: #4a90e2;
                font-size: 10px;
                padding: 2px 6px;
                border-radius: 10px;
                font-weight: 500;
            }
            
            .palette-empty {
                text-align: center;
                padding: 40px 20px;
                color: #666;
            }
            
            .empty-icon {
                font-size: 48px;
                margin-bottom: 16px;
                opacity: 0.5;
            }
            
            .empty-text {
                font-size: 16px;
                font-weight: 600;
                margin-bottom: 8px;
            }
            
            .empty-subtext {
                font-size: 14px;
                opacity: 0.7;
            }
            
            .palette-drag-image {
                pointer-events: none;
            }
            
            .drag-icon {
                font-size: 16px;
            }
            
            .drag-name {
                font-weight: 600;
                color: #333;
            }
            
            /* Dark theme support */
            .widget-palette.dark-theme {
                background: #2c3e50;
                border-color: #34495e;
                color: #ecf0f1;
            }
            
            .dark-theme .palette-header {
                background: #34495e;
                border-color: #4a6478;
            }
            
            .dark-theme .palette-title {
                color: #ecf0f1;
            }
            
            .dark-theme .palette-item {
                background: #34495e;
                border-color: #4a6478;
                color: #ecf0f1;
            }
            
            .dark-theme .palette-item:hover {
                border-color: #3498db;
            }
            
            /* Responsive design */
            @media (max-width: 768px) {
                .palette-content.layout-grid {
                    grid-template-columns: repeat(2, 1fr);
                }
                
                .palette-item {
                    padding: 8px;
                }
                
                .palette-item-icon {
                    font-size: 20px;
                }
                
                .palette-item-name {
                    font-size: 12px;
                }
                
                .palette-item-description {
                    font-size: 11px;
                }
            }
        `;
    }
}

// Export for use
if (typeof module !== 'undefined' && module.exports) {
    module.exports = WidgetPalette;
} else if (typeof window !== 'undefined') {
    window.WidgetPalette = WidgetPalette;
}