/**
 * Grid Dashboard Library
 * Main entry point for the grid dashboard system
 */

// Import all grid components
// Note: In a browser environment, these would be included via script tags

/**
 * GridDashboardFactory - Factory for creating dashboard instances
 */
class GridDashboardFactory {
    static create(container, width, height, options = {}) {
        return new GridDashboard(container, width, height, options);
    }
    
    static createWidget(type, options = {}) {
        switch (type.toLowerCase()) {
            case 'text':
                return new TextWidget(options);
            case 'chart':
                return new ChartWidget(options);
            case 'image':
                return new ImageWidget(options);
            case 'grid':
            default:
                return new GridWidget(options);
        }
    }
    
    static createWidgetFromData(data) {
        return this.createWidget(data.type || 'grid', data);
    }
    
    static getAvailableWidgetTypes() {
        return ['text', 'chart', 'image', 'custom'];
    }
}

/**
 * GridDashboardManager - Manages multiple dashboard instances
 */
class GridDashboardManager {
    constructor() {
        this.dashboards = new Map();
        this.widgetTemplates = new Map();
    }
    
    createDashboard(id, container, width, height, options = {}) {
        if (this.dashboards.has(id)) {
            throw new Error(`Dashboard with id "${id}" already exists`);
        }
        
        const dashboard = GridDashboardFactory.create(container, width, height, options);
        this.dashboards.set(id, dashboard);
        
        return dashboard;
    }
    
    getDashboard(id) {
        return this.dashboards.get(id);
    }
    
    removeDashboard(id) {
        const dashboard = this.dashboards.get(id);
        if (dashboard) {
            dashboard.destroy();
            this.dashboards.delete(id);
        }
    }
    
    registerWidgetTemplate(name, template) {
        this.widgetTemplates.set(name, template);
    }
    
    createWidgetFromTemplate(templateName, options = {}) {
        const template = this.widgetTemplates.get(templateName);
        if (!template) {
            throw new Error(`Widget template "${templateName}" not found`);
        }
        
        return GridDashboardFactory.createWidget(template.type, {
            ...template.defaultOptions,
            ...options
        });
    }
    
    getRegisteredTemplates() {
        return Array.from(this.widgetTemplates.keys());
    }
    
    exportDashboardLayout(dashboardId) {
        const dashboard = this.getDashboard(dashboardId);
        if (!dashboard) {
            throw new Error(`Dashboard "${dashboardId}" not found`);
        }
        
        return {
            id: dashboardId,
            options: dashboard.options,
            gridDimensions: dashboard.getGridDimensions(),
            widgets: Array.from(dashboard.widgets.values()).map(widget => widget.getData())
        };
    }
    
    importDashboardLayout(dashboardId, layoutData) {
        const dashboard = this.getDashboard(dashboardId);
        if (!dashboard) {
            throw new Error(`Dashboard "${dashboardId}" not found`);
        }
        
        // Clear existing widgets
        dashboard.widgets.clear();
        dashboard.container.innerHTML = '';
        
        // Apply grid dimensions if provided
        if (layoutData.gridDimensions) {
            dashboard.setGridDimensions(layoutData.gridDimensions.width, layoutData.gridDimensions.height);
        }
        
        // Recreate widgets from layout data
        if (layoutData.widgets) {
            layoutData.widgets.forEach(widgetData => {
                const widget = GridDashboardFactory.createWidgetFromData(widgetData);
                dashboard.addWidget(widget, widgetData.position);
            });
        }
    }
}

/**
 * Utility functions
 */
const GridUtils = {
    /**
     * Generate a unique ID for widgets
     */
    generateId(prefix = 'widget') {
        return `${prefix}-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`;
    },
    
    /**
     * Validate widget configuration
     */
    validateWidget(widget) {
        if (!widget.size || !widget.size.width || !widget.size.height) {
            return { valid: false, error: 'Widget must have valid size (width and height)' };
        }
        
        if (widget.size.width < 1 || widget.size.height < 1) {
            return { valid: false, error: 'Widget size must be at least 1x1' };
        }
        
        return { valid: true };
    },
    
    /**
     * Calculate optimal grid size based on widgets
     */
    calculateOptimalGridSize(widgets) {
        let maxX = 0;
        let maxY = 0;
        
        widgets.forEach(widget => {
            const endX = widget.position.x + widget.size.width;
            const endY = widget.position.y + widget.size.height;
            
            maxX = Math.max(maxX, endX);
            maxY = Math.max(maxY, endY);
        });
        
        return { width: maxX, height: maxY };
    },
    
    /**
     * Check for widget overlaps
     */
    findOverlaps(widgets) {
        const overlaps = [];
        
        for (let i = 0; i < widgets.length; i++) {
            for (let j = i + 1; j < widgets.length; j++) {
                const widget1 = widgets[i];
                const widget2 = widgets[j];
                
                if (this.doWidgetsOverlap(widget1, widget2)) {
                    overlaps.push([widget1.id, widget2.id]);
                }
            }
        }
        
        return overlaps;
    },
    
    /**
     * Check if two widgets overlap
     */
    doWidgetsOverlap(widget1, widget2) {
        const w1 = {
            left: widget1.position.x,
            right: widget1.position.x + widget1.size.width,
            top: widget1.position.y,
            bottom: widget1.position.y + widget1.size.height
        };
        
        const w2 = {
            left: widget2.position.x,
            right: widget2.position.x + widget2.size.width,
            top: widget2.position.y,
            bottom: widget2.position.y + widget2.size.height
        };
        
        return !(w1.right <= w2.left || w2.right <= w1.left || w1.bottom <= w2.top || w2.bottom <= w1.top);
    },
    
    /**
     * Auto-arrange widgets to avoid overlaps
     */
    autoArrangeWidgets(widgets, gridWidth = 12, gridHeight = 12) {
        const arranged = [...widgets];
        const grid = {};
        
        // Sort by area (largest first) to prioritize placement
        arranged.sort((a, b) => (b.size.width * b.size.height) - (a.size.width * a.size.height));
        
        arranged.forEach(widget => {
            const position = this.findAvailablePosition(grid, widget.size, gridWidth, gridHeight);
            widget.position = position;
            this.markGridCells(grid, widget);
        });
        
        return arranged;
    },
    
    /**
     * Find available position in grid
     */
    findAvailablePosition(grid, size, gridWidth, gridHeight) {
        for (let y = 0; y <= gridHeight - size.height; y++) {
            for (let x = 0; x <= gridWidth - size.width; x++) {
                if (this.canPlaceWidget(grid, { x, y }, size)) {
                    return { x, y };
                }
            }
        }
        return { x: 0, y: 0 };
    },
    
    /**
     * Check if widget can be placed at position
     */
    canPlaceWidget(grid, position, size) {
        for (let x = position.x; x < position.x + size.width; x++) {
            for (let y = position.y; y < position.y + size.height; y++) {
                if (grid[`${x},${y}`]) {
                    return false;
                }
            }
        }
        return true;
    },
    
    /**
     * Mark grid cells as occupied
     */
    markGridCells(grid, widget) {
        for (let x = widget.position.x; x < widget.position.x + widget.size.width; x++) {
            for (let y = widget.position.y; y < widget.position.y + widget.size.height; y++) {
                grid[`${x},${y}`] = widget.id;
            }
        }
    }
};

/**
 * Global manager instance
 */
const GridManager = new GridDashboardManager();

// Register default widget templates
GridManager.registerWidgetTemplate('basic-text', {
    type: 'text',
    defaultOptions: {
        title: 'Text Widget',
        width: 2,
        height: 2,
        text: 'Enter your text here...'
    }
});

GridManager.registerWidgetTemplate('basic-chart', {
    type: 'chart',
    defaultOptions: {
        title: 'Chart Widget',
        width: 4,
        height: 3,
        chartType: 'line'
    }
});

GridManager.registerWidgetTemplate('basic-image', {
    type: 'image',
    defaultOptions: {
        title: 'Image Widget',
        width: 3,
        height: 3,
        objectFit: 'cover'
    }
});

// Export for use
if (typeof module !== 'undefined' && module.exports) {
    // Node.js environment
    module.exports = {
        GridDashboard,
        GridWidget,
        TextWidget,
        ChartWidget,
        ImageWidget,
        GridDashboardFactory,
        GridDashboardManager,
        GridUtils,
        GridManager
    };
} else {
    // Browser environment
    window.GridDashboard = {
        Dashboard: GridDashboard,
        Widget: GridWidget,
        TextWidget,
        ChartWidget,
        ImageWidget,
        Factory: GridDashboardFactory,
        Manager: GridDashboardManager,
        Utils: GridUtils,
        manager: GridManager
    };
}