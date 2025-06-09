/**
 * CustomWidget - A specialized widget with custom header/handle functionality
 * Extends GridWidget with enhanced header customization and special handling
 */
class CustomWidget extends GridWidget {
    constructor(options = {}) {
        super({
            type: 'custom',
            title: 'Custom Widget',
            width: 3,
            height: 3,
            ...options
        });
        
        // Custom header configuration
        this.headerConfig = {
            backgroundColor: options.headerConfig?.backgroundColor || '#4a90e2',
            textColor: options.headerConfig?.textColor || '#ffffff',
            icon: options.headerConfig?.icon || '⚡',
            showGradient: options.headerConfig?.showGradient !== false,
            height: options.headerConfig?.height || 'auto',
            borderRadius: options.headerConfig?.borderRadius || '8px 8px 0 0',
            ...options.headerConfig
        };
        
        // Custom handle configuration
        this.handleConfig = {
            type: options.handleConfig?.type || 'dots', // 'dots', 'lines', 'grip', 'custom'
            position: options.handleConfig?.position || 'left', // 'left', 'center', 'right'
            color: options.handleConfig?.color || 'rgba(255, 255, 255, 0.7)',
            hoverColor: options.handleConfig?.hoverColor || 'rgba(255, 255, 255, 1)',
            size: options.handleConfig?.size || 'medium', // 'small', 'medium', 'large'
            customIcon: options.handleConfig?.customIcon || null,
            ...options.handleConfig
        };
        
        // Enhanced functionality
        this.customActions = options.customActions || [];
        this.statusIndicator = options.statusIndicator || null;
        this.badge = options.badge || null;
    }
    
    createHeader() {
        const header = document.createElement('div');
        header.className = 'grid-widget-header custom-widget-header';
        
        // Apply custom header styling
        this.applyHeaderStyling(header);
        
        // Create drag handle
        const dragHandle = this.createDragHandle();
        header.appendChild(dragHandle);
        
        // Create icon if specified
        if (this.headerConfig.icon) {
            const icon = document.createElement('div');
            icon.className = 'custom-widget-icon';
            icon.innerHTML = this.headerConfig.icon;
            header.appendChild(icon);
        }
        
        // Create title with enhanced styling
        const titleContainer = document.createElement('div');
        titleContainer.className = 'custom-widget-title-container';
        
        const title = document.createElement('div');
        title.className = 'grid-widget-title custom-widget-title';
        title.textContent = this.title;
        titleContainer.appendChild(title);
        
        // Add badge if specified
        if (this.badge) {
            const badge = this.createBadge();
            titleContainer.appendChild(badge);
        }
        
        header.appendChild(titleContainer);
        
        // Create status indicator if specified
        if (this.statusIndicator) {
            const status = this.createStatusIndicator();
            header.appendChild(status);
        }
        
        // Create actions container
        const actions = document.createElement('div');
        actions.className = 'grid-widget-actions custom-widget-actions';
        
        // Add custom actions
        this.customActions.forEach(action => {
            const button = this.createActionButton(action);
            actions.appendChild(button);
        });
        
        // Add default actions
        this.actions.forEach(action => {
            const button = document.createElement('button');
            button.className = 'grid-widget-action custom-widget-action';
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
        
        // Add remove button if removable
        if (this.removable) {
            const removeBtn = document.createElement('button');
            removeBtn.className = 'grid-widget-action remove-handle custom-remove-handle';
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
    
    createDragHandle() {
        const handle = document.createElement('div');
        handle.className = 'custom-drag-handle';
        
        // Apply handle positioning
        handle.classList.add(`handle-${this.handleConfig.position}`);
        handle.classList.add(`handle-${this.handleConfig.size}`);
        
        // Create handle content based on type
        let handleContent = '';
        switch (this.handleConfig.type) {
            case 'dots':
                handleContent = '<div class="handle-dots">⋮⋮</div>';
                break;
            case 'lines':
                handleContent = '<div class="handle-lines">≡</div>';
                break;
            case 'grip':
                handleContent = '<div class="handle-grip">⣿</div>';
                break;
            case 'custom':
                handleContent = this.handleConfig.customIcon || '⋮⋮';
                break;
            default:
                handleContent = '<div class="handle-dots">⋮⋮</div>';
        }
        
        handle.innerHTML = handleContent;
        
        // Apply handle styling
        this.applyHandleStyling(handle);
        
        return handle;
    }
    
    createBadge() {
        const badge = document.createElement('div');
        badge.className = 'custom-widget-badge';
        badge.textContent = this.badge.text || '';
        badge.style.backgroundColor = this.badge.color || '#ff6b6b';
        badge.style.color = this.badge.textColor || '#ffffff';
        return badge;
    }
    
    createStatusIndicator() {
        const indicator = document.createElement('div');
        indicator.className = 'custom-widget-status';
        indicator.classList.add(`status-${this.statusIndicator.type || 'info'}`);
        indicator.title = this.statusIndicator.tooltip || '';
        
        const dot = document.createElement('div');
        dot.className = 'status-dot';
        indicator.appendChild(dot);
        
        return indicator;
    }
    
    createActionButton(action) {
        const button = document.createElement('button');
        button.className = 'grid-widget-action custom-widget-action';
        
        if (action.type) {
            button.classList.add(`action-${action.type}`);
        }
        
        button.innerHTML = action.icon || '⚙️';
        button.title = action.tooltip || '';
        
        if (action.style) {
            Object.assign(button.style, action.style);
        }
        
        button.addEventListener('click', (e) => {
            e.stopPropagation();
            if (action.handler) {
                action.handler.call(this, e);
            }
        });
        
        return button;
    }
    
    applyHeaderStyling(header) {
        const config = this.headerConfig;
        
        // Base styling
        header.style.backgroundColor = config.backgroundColor;
        header.style.color = config.textColor;
        header.style.borderRadius = config.borderRadius;
        
        if (config.height !== 'auto') {
            header.style.minHeight = config.height;
        }
        
        // Apply gradient if enabled
        if (config.showGradient) {
            const lightColor = this.lightenColor(config.backgroundColor, 20);
            header.style.background = `linear-gradient(135deg, ${config.backgroundColor} 0%, ${lightColor} 100%)`;
        }
        
        // Add custom CSS classes
        if (config.customClass) {
            header.classList.add(config.customClass);
        }
    }
    
    applyHandleStyling(handle) {
        const config = this.handleConfig;
        
        handle.style.color = config.color;
        
        // Add hover effect
        handle.addEventListener('mouseenter', () => {
            handle.style.color = config.hoverColor;
        });
        
        handle.addEventListener('mouseleave', () => {
            handle.style.color = config.color;
        });
    }
    
    lightenColor(color, percent) {
        // Simple color lightening function
        const num = parseInt(color.replace("#", ""), 16);
        const amt = Math.round(2.55 * percent);
        const R = (num >> 16) + amt;
        const G = (num >> 8 & 0x00FF) + amt;
        const B = (num & 0x0000FF) + amt;
        return "#" + (0x1000000 + (R < 255 ? R < 1 ? 0 : R : 255) * 0x10000 +
            (G < 255 ? G < 1 ? 0 : G : 255) * 0x100 +
            (B < 255 ? B < 1 ? 0 : B : 255)).toString(16).slice(1);
    }
    
    updateStatus(type, tooltip) {
        this.statusIndicator = { type, tooltip };
        const statusElement = this.querySelector('.custom-widget-status');
        if (statusElement) {
            statusElement.className = `custom-widget-status status-${type}`;
            statusElement.title = tooltip || '';
        }
    }
    
    updateBadge(text, color, textColor) {
        this.badge = { text, color, textColor };
        const badgeElement = this.querySelector('.custom-widget-badge');
        if (badgeElement) {
            badgeElement.textContent = text || '';
            badgeElement.style.backgroundColor = color || '#ff6b6b';
            badgeElement.style.color = textColor || '#ffffff';
        }
    }
    
    addCustomAction(action) {
        this.customActions.push(action);
        const actionsContainer = this.querySelector('.custom-widget-actions');
        if (actionsContainer) {
            const button = this.createActionButton(action);
            // Insert before existing actions
            const firstDefaultAction = actionsContainer.querySelector('.grid-widget-action:not(.custom-widget-action)');
            if (firstDefaultAction) {
                actionsContainer.insertBefore(button, firstDefaultAction);
            } else {
                actionsContainer.appendChild(button);
            }
        }
    }
    
    removeCustomAction(index) {
        if (index >= 0 && index < this.customActions.length) {
            this.customActions.splice(index, 1);
            // Re-render actions
            const actionsContainer = this.querySelector('.custom-widget-actions');
            if (actionsContainer) {
                // Remove all custom action buttons and re-create them
                const customButtons = actionsContainer.querySelectorAll('.custom-widget-action');
                customButtons.forEach(btn => btn.remove());
                
                // Re-add custom actions
                this.customActions.forEach(action => {
                    const button = this.createActionButton(action);
                    actionsContainer.insertBefore(button, actionsContainer.firstChild);
                });
            }
        }
    }
    
    getData() {
        return {
            ...super.getData(),
            headerConfig: this.headerConfig,
            handleConfig: this.handleConfig,
            customActions: this.customActions,
            statusIndicator: this.statusIndicator,
            badge: this.badge
        };
    }
    
    // Add CSS for custom styling
    static getCSS() {
        return `
            .custom-widget-header {
                position: relative;
                display: flex;
                align-items: center;
                gap: 8px;
                padding: 12px 16px;
                font-weight: 600;
                min-height: 49px;
                box-sizing: border-box;
                border-bottom: 1px solid rgba(255, 255, 255, 0.2);
                overflow: hidden;
            }
            
            .custom-drag-handle {
                cursor: move;
                user-select: none;
                font-size: 16px;
                font-weight: bold;
                transition: color 0.2s ease, transform 0.2s ease;
                display: flex;
                align-items: center;
                justify-content: center;
                width: 20px;
                height: 20px;
            }
            
            .custom-drag-handle:hover {
                transform: scale(1.1);
            }
            
            .custom-drag-handle.handle-center {
                order: 1;
            }
            
            .custom-drag-handle.handle-right {
                order: 3;
                margin-left: auto;
            }
            
            .custom-drag-handle.handle-small {
                font-size: 12px;
                width: 16px;
                height: 16px;
            }
            
            .custom-drag-handle.handle-large {
                font-size: 20px;
                width: 24px;
                height: 24px;
            }
            
            .handle-dots, .handle-lines, .handle-grip {
                display: flex;
                align-items: center;
                justify-content: center;
                width: 100%;
                height: 100%;
            }
            
            .custom-widget-icon {
                font-size: 18px;
                display: flex;
                align-items: center;
                justify-content: center;
                width: 24px;
                height: 24px;
                margin-right: 4px;
            }
            
            .custom-widget-title-container {
                flex: 1;
                display: flex;
                align-items: center;
                gap: 8px;
                overflow: hidden;
            }
            
            .custom-widget-title {
                flex: 1;
                overflow: hidden;
                text-overflow: ellipsis;
                white-space: nowrap;
                font-weight: 600;
                text-shadow: 0 1px 2px rgba(0, 0, 0, 0.1);
            }
            
            .custom-widget-badge {
                background: #ff6b6b;
                color: white;
                border-radius: 12px;
                padding: 2px 8px;
                font-size: 11px;
                font-weight: 600;
                text-transform: uppercase;
                letter-spacing: 0.5px;
                box-shadow: 0 1px 3px rgba(0, 0, 0, 0.2);
            }
            
            .custom-widget-status {
                display: flex;
                align-items: center;
                justify-content: center;
                width: 12px;
                height: 12px;
                margin: 0 4px;
            }
            
            .status-dot {
                width: 8px;
                height: 8px;
                border-radius: 50%;
                background: currentColor;
                animation: pulse 2s infinite;
            }
            
            .status-success .status-dot {
                background: #28a745;
            }
            
            .status-warning .status-dot {
                background: #ffc107;
            }
            
            .status-error .status-dot {
                background: #dc3545;
            }
            
            .status-info .status-dot {
                background: #17a2b8;
            }
            
            .custom-widget-actions {
                display: flex;
                gap: 6px;
                align-items: center;
            }
            
            .custom-widget-action {
                background: rgba(255, 255, 255, 0.1);
                border: none;
                cursor: pointer;
                padding: 6px 8px;
                border-radius: 6px;
                transition: all 0.2s ease;
                color: inherit;
                font-size: 14px;
                line-height: 1;
                min-width: 24px;
                height: 24px;
                display: flex;
                align-items: center;
                justify-content: center;
                backdrop-filter: blur(10px);
            }
            
            .custom-widget-action:hover {
                background: rgba(255, 255, 255, 0.2);
                transform: translateY(-1px);
                box-shadow: 0 2px 4px rgba(0, 0, 0, 0.2);
            }
            
            .custom-widget-action.action-primary {
                background: rgba(0, 123, 255, 0.8);
            }
            
            .custom-widget-action.action-success {
                background: rgba(40, 167, 69, 0.8);
            }
            
            .custom-widget-action.action-warning {
                background: rgba(255, 193, 7, 0.8);
            }
            
            .custom-widget-action.action-danger {
                background: rgba(220, 53, 69, 0.8);
            }
            
            .custom-remove-handle {
                background: rgba(220, 53, 69, 0.8) !important;
            }
            
            .custom-remove-handle:hover {
                background: rgba(220, 53, 69, 1) !important;
                transform: translateY(-1px) scale(1.05);
            }
            
            @keyframes pulse {
                0% {
                    opacity: 1;
                }
                50% {
                    opacity: 0.5;
                }
                100% {
                    opacity: 1;
                }
            }
            
            /* Dark theme support */
            .grid-dashboard.dark-theme .custom-widget-header {
                border-bottom-color: rgba(255, 255, 255, 0.1);
            }
            
            .grid-dashboard.dark-theme .custom-widget-action {
                background: rgba(255, 255, 255, 0.05);
            }
            
            .grid-dashboard.dark-theme .custom-widget-action:hover {
                background: rgba(255, 255, 255, 0.1);
            }
            
            /* Responsive design */
            @media (max-width: 768px) {
                .custom-widget-header {
                    padding: 8px 12px;
                    gap: 6px;
                }
                
                .custom-widget-icon {
                    font-size: 16px;
                    width: 20px;
                    height: 20px;
                }
                
                .custom-widget-badge {
                    font-size: 10px;
                    padding: 1px 6px;
                }
                
                .custom-widget-action {
                    min-width: 20px;
                    height: 20px;
                    font-size: 12px;
                }
            }
        `;
    }
}

// Inject CSS when the class is loaded
if (typeof document !== 'undefined') {
    const existingStyle = document.getElementById('custom-widget-styles');
    if (!existingStyle) {
        const style = document.createElement('style');
        style.id = 'custom-widget-styles';
        style.textContent = CustomWidget.getCSS();
        document.head.appendChild(style);
    }
}

// Export for use
if (typeof module !== 'undefined' && module.exports) {
    module.exports = CustomWidget;
} else if (typeof window !== 'undefined') {
    window.CustomWidget = CustomWidget;
    
    // Register with GridManager if available
    if (window.GridDashboard && window.GridDashboard.manager) {
        window.GridDashboard.manager.registerWidgetTemplate('custom-enhanced', {
            type: 'custom',
            defaultOptions: {
                title: 'Enhanced Widget',
                width: 3,
                height: 3,
                headerConfig: {
                    backgroundColor: '#4a90e2',
                    textColor: '#ffffff',
                    icon: '⚡',
                    showGradient: true
                },
                handleConfig: {
                    type: 'dots',
                    position: 'left',
                    size: 'medium'
                },
                statusIndicator: {
                    type: 'success',
                    tooltip: 'Widget is active'
                },
                badge: {
                    text: 'NEW',
                    color: '#ff6b6b'
                }
            }
        });
    }
}