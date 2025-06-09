# Grid Dashboard Library

A powerful, flexible drag-and-drop dashboard library built with vanilla JavaScript and CSS Grid. Create responsive, customizable grid layouts with automatic cell sizing, widget management, and smooth interactions.

## 🚀 Features

### Core Functionality
- **CSS Auto Sizing**: Responsive grid cells that adapt to container dimensions
- **Drag & Drop**: Smooth widget repositioning with visual feedback
- **Auto Resize**: Grid automatically expands when widgets are moved beyond boundaries
- **Widget Management**: Easy widget creation, positioning, and removal
- **Responsive Design**: Adapts to any container size with minimum cell constraints

### Advanced Features
- **Visual Grid Overlay**: Optional grid lines for precise widget placement
- **Placeholder Preview**: Real-time drag preview with grid expansion
- **Event System**: Comprehensive event emission for custom integrations
- **Widget Palette**: Support for draggable widget creation from external sources
- **Overlap Control**: Configurable widget overlap prevention
- **Animation Support**: Smooth transitions and hover effects

## 📦 Installation

Include the required files in your project:

```html
<script src="gridWidget.js"></script>
<script src="gridDashboard.js"></script>
```

## 🔧 Quick Start

### Basic Setup

```html
<div id="dashboard-container"></div>
```

```javascript
// Create a 4x3 grid with CSS auto sizing
const dashboard = new GridDashboard('#dashboard-container', 4, 3, {
    useCSSAuto: true,
    gap: 10,
    editMode: true,
    showGridLines: true
});

// Create and add a widget
const widget = new GridWidget({
    title: 'My Widget',
    width: 2,
    height: 1,
    content: '<div style="padding: 20px;">Hello World!</div>'
});

dashboard.addWidget(widget, { x: 0, y: 0 });
```

## 📋 API Reference

### GridDashboard

#### Constructor
```javascript
new GridDashboard(container, width, height, options)
```

**Parameters:**
- `container` (string|Element): CSS selector or DOM element
- `width` (number): Initial grid width in cells
- `height` (number): Initial grid height in cells
- `options` (object): Configuration options

#### Options

| Option | Type | Default | Description |
|--------|------|---------|-------------|
| `useCSSAuto` | boolean | `true` | Enable CSS automatic cell sizing |
| `minCellSize` | number | `null` | Minimum cell size in pixels (CSS auto mode) |
| `maxCellSize` | number | `null` | Maximum cell size in pixels (CSS auto mode) |
| `cellSize` | number | `100` | Fixed cell size (legacy mode) |
| `gap` | number | `10` | Gap between cells in pixels |
| `editMode` | boolean | `true` | Enable widget editing and dragging |
| `showGridLines` | boolean | `false` | Show visual grid overlay |
| `autoResizeWidth` | boolean | `false` | Auto-expand grid width |
| `autoResizeHeight` | boolean | `false` | Auto-expand grid height |
| `allowOverlapping` | boolean | `false` | Allow widgets to overlap |
| `enableDragDrop` | boolean | `true` | Enable drag and drop functionality |

#### Methods

##### Widget Management
```javascript
// Add widget
dashboard.addWidget(widget, position?)

// Remove widget
dashboard.removeWidget(widgetId)

// Get widget
dashboard.getWidget(widgetId)

// Clear all widgets
dashboard.clearWidgets()
```

##### Grid Control
```javascript
// Set grid dimensions
dashboard.setGridDimensions(width, height)

// Get current dimensions
dashboard.getGridDimensions()

// Fit grid to content
dashboard.fitToContent()
```

##### CSS Auto Sizing
```javascript
// Enable/disable CSS auto mode
dashboard.setCSSAutoMode(enabled)

// Check if CSS auto mode is enabled
dashboard.getCSSAutoMode()

// Toggle CSS auto mode
dashboard.toggleCSSAutoMode()
```

##### Events
```javascript
// Listen for events
dashboard.container.addEventListener('gridDashboard:widgetAdded', (e) => {
    console.log('Widget added:', e.detail);
});

dashboard.container.addEventListener('gridDashboard:gridResized', (e) => {
    console.log('Grid resized:', e.detail);
});
```

### GridWidget

#### Constructor
```javascript
new GridWidget(options)
```

#### Options

| Option | Type | Default | Description |
|--------|------|---------|-------------|
| `title` | string | `'Widget'` | Widget header title |
| `width` | number | `1` | Widget width in cells |
| `height` | number | `1` | Widget height in cells |
| `content` | string | `''` | Widget HTML content |
| `headerConfig` | object | `{}` | Header styling configuration |

#### Methods
```javascript
// Update widget content
widget.updateContent(newContent)

// Resize widget
widget.resize(width, height)

// Get widget position
widget.getPosition()

// Set widget position
widget.setPosition(x, y)
```

## 🎨 CSS Auto Sizing

The library's CSS auto sizing feature provides responsive grid cells that adapt to container dimensions:

### How It Works
- Uses CSS Grid `minmax()` for responsive cell sizing
- Cells automatically scale with container resize
- Maintains minimum size for usability
- No JavaScript calculations needed for performance

### Configuration Examples

```javascript
// Responsive with constraints
const dashboard = new GridDashboard('#container', 4, 3, {
    useCSSAuto: true,
    minCellSize: 100,
    maxCellSize: 300,
    gap: 15
});

// Responsive with defaults (minmax(80px, 1fr))
const dashboard = new GridDashboard('#container', 4, 3, {
    useCSSAuto: true
});

// Fixed size (legacy mode)
const dashboard = new GridDashboard('#container', 4, 3, {
    useCSSAuto: false,
    cellSize: 120
});
```

### CSS Grid Templates Generated

| Configuration | Column Template | Row Template |
|---------------|----------------|--------------|
| Default CSS Auto | `repeat(4, minmax(80px, 1fr))` | `repeat(3, minmax(80px, 1fr))` |
| With Constraints | `repeat(4, minmax(100px, 300px))` | `repeat(3, minmax(100px, 300px))` |
| Fixed Size | `repeat(4, 120px)` | `repeat(3, 120px)` |

## 🎯 Examples

### Basic Dashboard
```javascript
const dashboard = new GridDashboard('#dashboard', 3, 2, {
    useCSSAuto: true,
    gap: 10,
    showGridLines: true
});

const widgets = [
    new GridWidget({
        title: 'Statistics',
        width: 2,
        height: 1,
        content: '<div class="stats">Dashboard Stats</div>'
    }),
    new GridWidget({
        title: 'Chart',
        width: 1,
        height: 2,
        content: '<canvas id="chart"></canvas>'
    })
];

widgets.forEach(widget => dashboard.addWidget(widget));
```

### Responsive Dashboard
```javascript
const dashboard = new GridDashboard('#responsive-dashboard', 4, 3, {
    useCSSAuto: true,
    minCellSize: 120,
    maxCellSize: 250,
    autoResizeWidth: true,
    autoResizeHeight: true
});

// Dashboard automatically adapts to container size changes
window.addEventListener('resize', () => {
    // No manual handling needed - CSS auto sizing handles it
});
```

### Custom Widget with Events
```javascript
const widget = new GridWidget({
    title: 'Interactive Widget',
    width: 2,
    height: 2,
    content: `
        <div style="padding: 20px;">
            <button onclick="handleClick()">Click Me</button>
            <div id="output"></div>
        </div>
    `
});

dashboard.addWidget(widget);

function handleClick() {
    document.getElementById('output').textContent = 'Button clicked!';
}
```

## 🎮 Events

The library emits custom events for integration with your application:

| Event | Description | Detail Properties |
|-------|-------------|-------------------|
| `gridDashboard:widgetAdded` | Widget added to grid | `widget`, `position` |
| `gridDashboard:widgetRemoved` | Widget removed from grid | `widgetId` |
| `gridDashboard:widgetMoved` | Widget position changed | `widget`, `oldPosition`, `newPosition` |
| `gridDashboard:gridResized` | Grid dimensions changed | `width`, `height` |
| `gridDashboard:layoutChanged` | Grid layout updated | `widgets` |
| `gridDashboard:cellSizeChanged` | Cell size changed | `cellSize` |

### Event Handling Example
```javascript
dashboard.container.addEventListener('gridDashboard:widgetMoved', (e) => {
    const { widget, oldPosition, newPosition } = e.detail;
    console.log(`Widget ${widget.id} moved from (${oldPosition.x}, ${oldPosition.y}) to (${newPosition.x}, ${newPosition.y})`);
});
```

## 🎨 Styling

### CSS Variables
The library supports CSS custom properties for easy theming:

```css
:root {
    --surface-primary: #ffffff;
    --surface-secondary: #f8f9fa;
    --border-primary: #e1e5e9;
    --accent-primary: #00d4ff;
    --border-radius: 8px;
    --text-primary: #333333;
}
```

### Widget Styling
```css
.grid-widget {
    /* Custom widget styles */
    border: 2px solid var(--accent-primary);
    background: linear-gradient(135deg, #fff 0%, #f8f9fa 100%);
}

.grid-widget-header {
    /* Custom header styles */
    background: var(--accent-primary);
    color: white;
}
```

## 🔧 Advanced Usage

### Widget Palette Integration
```javascript
// Create draggable widget palette
class WidgetPalette {
    constructor(dashboard) {
        this.dashboard = dashboard;
        this.setupPalette();
    }
    
    setupPalette() {
        const palette = document.getElementById('widget-palette');
        const widgets = palette.querySelectorAll('.palette-widget');
        
        widgets.forEach(element => {
            element.draggable = true;
            element.addEventListener('dragstart', (e) => {
                e.dataTransfer.setData('application/json', JSON.stringify({
                    type: 'palette-widget',
                    widgetType: element.dataset.widgetType
                }));
            });
        });
    }
}
```

### Custom Widget Types
```javascript
class ChartWidget extends GridWidget {
    constructor(chartData, options = {}) {
        super({
            title: 'Chart',
            width: 2,
            height: 2,
            content: '<canvas class="chart-canvas"></canvas>',
            ...options
        });
        
        this.chartData = chartData;
        this.initChart();
    }
    
    initChart() {
        // Custom chart initialization
        const canvas = this.element.querySelector('.chart-canvas');
        // ... chart rendering logic
    }
}
```

## 📱 Responsive Design

The library is designed to work seamlessly across different screen sizes:

### Mobile Considerations
```javascript
const isMobile = window.innerWidth < 768;

const dashboard = new GridDashboard('#dashboard', 
    isMobile ? 2 : 4,  // Fewer columns on mobile
    isMobile ? 4 : 3,  // More rows on mobile
    {
        useCSSAuto: true,
        minCellSize: isMobile ? 120 : 80,
        gap: isMobile ? 8 : 10,
        editMode: !isMobile  // Disable editing on mobile
    }
);
```

### Container Queries Support
```css
.dashboard-container {
    container-type: inline-size;
}

@container (max-width: 600px) {
    .grid-widget {
        font-size: 14px;
    }
    
    .grid-widget-header {
        padding: 8px 12px;
    }
}
```

## 🐛 Troubleshooting

### Common Issues

**Grid cells not resizing:**
- Ensure `useCSSAuto: true` is set
- Check that container has defined dimensions
- Verify CSS doesn't override grid templates

**Widgets overlapping:**
- Set `allowOverlapping: false`
- Check widget positions and sizes
- Ensure grid dimensions accommodate all widgets

**Drag and drop not working:**
- Verify `enableDragDrop: true`
- Check that `editMode: true`
- Ensure widgets have proper event handlers

**Performance issues:**
- Use CSS auto sizing instead of JavaScript calculations
- Limit the number of concurrent widgets
- Optimize widget content and DOM structure

## 🤝 Contributing

We welcome contributions! Please follow these guidelines:

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Add tests for new functionality
5. Submit a pull request

## 📄 License

This project is licensed under the MIT License - see the LICENSE file for details.

## 🔗 Links

- [Demo Pages](./demo.html)
- [API Documentation](./api-docs.md)
- [Examples](./examples/)
- [GitHub Repository](#)

---

**Built with ❤️ using vanilla JavaScript and CSS Grid**