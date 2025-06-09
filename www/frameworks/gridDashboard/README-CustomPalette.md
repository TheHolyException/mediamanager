# Custom HTML Widget Palette

The `CustomHTMLWidgetPalette` allows you to create widget palettes using your own custom HTML elements with complete control over styling and layout.

## Basic Usage

```javascript
// Create a custom HTML palette
const customPalette = new CustomHTMLWidgetPalette('.my-palette-container', {
    widgetSelector: '[widget-type]',
    enableClickToAdd: true,
    enableHoverEffects: true
});

// Register with a dashboard
customPalette.registerDashboard(dashboard);
```

## HTML Structure

Create HTML elements with the `widget-type` attribute to make them draggable:

```html
<div class="my-custom-widget" 
     widget-type="my-widget-type"
     data-widget-title="My Widget"
     data-widget-width="3"
     data-widget-height="2"
     data-icon="🎨"
     data-title="Custom Widget">
    
    <div class="widget-icon">🎨</div>
    <div class="widget-name">Custom Widget</div>
    <div class="widget-description">A beautifully styled widget</div>
</div>
```

## Key Attributes

### Required
- `widget-type`: Identifies the widget template to use

### Optional Data Attributes
- `data-widget-*`: Widget configuration (title, width, height, etc.)
- `data-icon`: Icon for drag image
- `data-title`: Title for drag image
- `data-click-to-add`: Enable/disable click to add (default: true)
- `data-drag-image`: CSS selector for custom drag image element
- `data-drag-image-html`: Custom HTML for drag image
- `data-drag-image-style`: JSON string with custom drag image styles

### Drag Configuration
- `data-drag-*`: Additional data to include in drag transfer

## Widget Template Registration

Register widget templates to define how widgets are created:

```javascript
customPalette.registerWidgetTemplate('my-widget-type', {
    name: 'My Custom Widget',
    description: 'A custom widget with special features',
    factory: (options) => new CustomWidget({
        title: options.title || 'Default Title',
        width: options.width || 2,
        height: options.height || 2,
        content: '<p>Custom widget content</p>',
        headerConfig: {
            backgroundColor: '#4a90e2',
            icon: '🎨'
        }
    })
});
```

## Styling Examples

### Card Style
```html
<div class="card-widget" 
     widget-type="dashboard-card"
     data-widget-width="4"
     data-widget-height="3">
    <div class="card-header">
        <div class="card-icon">📊</div>
        <div class="card-title">Analytics</div>
    </div>
    <div class="card-content">Advanced analytics dashboard</div>
</div>
```

### Compact Style
```html
<div class="compact-widget" 
     widget-type="notification-widget"
     data-widget-width="2"
     data-widget-height="2">
    <div class="widget-icon">🔔</div>
    <div class="widget-info">
        <div class="widget-name">Notifications</div>
        <div class="widget-desc">Real-time alerts</div>
    </div>
</div>
```

### Grid Style
```html
<div class="grid-layout">
    <div class="grid-item" 
         widget-type="weather-widget"
         data-widget-width="2"
         data-widget-height="2">
        <div class="item-icon">🌤️</div>
        <div class="item-name">Weather</div>
    </div>
    <!-- More grid items... -->
</div>
```

## API Methods

### Core Methods
```javascript
// Register widget template
customPalette.registerWidgetTemplate(widgetType, template);

// Register with dashboard
customPalette.registerDashboard(dashboard);

// Enable/disable items
customPalette.enableItem('[widget-type="my-type"]');
customPalette.disableItem('[widget-type="disabled-type"]');

// Refresh palette
customPalette.refresh();

// Add widget programmatically
customPalette.addWidgetToDashboard(dashboard, 'widget-type', customData);
```

### Event Listeners
```javascript
// Listen for palette events
customPalette.container.addEventListener('customWidgetPalette:dragStart', (e) => {
    console.log('Drag started:', e.detail);
});

customPalette.container.addEventListener('customWidgetPalette:itemClicked', (e) => {
    console.log('Item clicked:', e.detail);
});

customPalette.container.addEventListener('customWidgetPalette:widgetAdded', (e) => {
    console.log('Widget added:', e.detail);
});
```

## Configuration Options

```javascript
const options = {
    widgetSelector: '[widget-type]',     // Selector for draggable items
    draggedClass: 'palette-item-dragging',  // CSS class while dragging
    hoverClass: 'palette-item-hover',       // CSS class on hover
    disabledClass: 'palette-item-disabled', // CSS class for disabled items
    enableHoverEffects: true,               // Enable hover animations
    enableClickToAdd: true,                 // Enable click to add widgets
    allowCustomData: true,                  // Extract data-widget-* attributes
    autoSetDraggable: true                  // Automatically set draggable=true
};
```

## Advanced Features

### Custom Drag Images
```html
<div widget-type="custom-widget"
     data-drag-image-html='<div class="custom-drag"><span>🎨</span><span>Dragging...</span></div>'
     data-drag-image-style='{"background": "#ff6b6b", "color": "white"}'>
    <!-- Widget content -->
</div>
```

### Multiple Dashboards
```javascript
// Register with multiple dashboards
customPalette.registerDashboard(dashboard1);
customPalette.registerDashboard(dashboard2);
```

### Dynamic Content
```javascript
// The palette automatically detects new widgets added to the DOM
// with MutationObserver, so you can dynamically add HTML elements
// and they'll become draggable automatically
```

## Integration with Standard Palette

Both palettes can work together on the same dashboard:

```javascript
// Create both palettes
const standardPalette = new WidgetPalette('#standard-palette');
const customPalette = new CustomHTMLWidgetPalette('#custom-palette');

// Register both with the same dashboard
dashboard.registerWidgetPalette('standard', standardPalette);
customPalette.registerDashboard(dashboard);
```

## Examples

See `custom-html-palette-demo.html` for a complete working example showcasing:
- Different widget styling approaches
- Custom drag effects
- Integration with standard palette
- Real-time dashboard interaction

The demo includes card-style, compact-style, and grid-style widget layouts to demonstrate the flexibility of the custom HTML approach.