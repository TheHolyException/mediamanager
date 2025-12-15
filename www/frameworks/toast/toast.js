/**
 * Toast Notification System
 * A lightweight, dark-themed notification system for MediaManager
 */
class Toast {
    constructor() {
        this.container = null;
        this.notificationCount = 0;
    }

    /**
     * Display a toast notification
     * @param {string|object} messageOrOptions - Message string or options object
     * @param {string} severity - Severity level: 'success', 'error', 'warning', 'info' (or legacy: 'ok', 'nok', 'warn')
     * @param {number} duration - Duration in milliseconds (0 = persistent)
     */
    show(messageOrOptions, severity = 'info', duration = 5000) {
        let options;

        // Handle both API signatures
        if (typeof messageOrOptions === 'string') {
            options = {
                message: messageOrOptions,
                severity: this._normalizeSeverity(severity),
                duration: duration
            };
        } else {
            options = {
                title: messageOrOptions.title || '',
                message: messageOrOptions.message || '',
                severity: this._normalizeSeverity(messageOrOptions.severity || 'info'),
                duration: messageOrOptions.duration !== undefined ? messageOrOptions.duration : 5000
            };
        }

        // Ensure container exists
        if (!this.container) {
            this._createContainer();
        }

        // Create notification element
        const notification = this._createNotification(options);

        // Add to container
        this.container.appendChild(notification);

        // Trigger fade-in animation
        setTimeout(() => {
            notification.classList.add('toast-fade-in');
        }, 10);

        // Auto-dismiss if duration > 0
        if (options.duration > 0) {
            setTimeout(() => {
                this._dismissNotification(notification);
            }, options.duration);
        }
    }

    /**
     * Normalize severity values (handle legacy 'ok', 'nok' values)
     */
    _normalizeSeverity(severity) {
        const severityMap = {
            'ok': 'success',
            'nok': 'error',
            'warn': 'warning'
        };
        return severityMap[severity] || severity;
    }

    /**
     * Create the toast container (called once)
     */
    _createContainer() {
        this.container = document.createElement('div');
        this.container.className = 'toast-container';
        document.body.appendChild(this.container);
    }

    /**
     * Create a notification element
     */
    _createNotification(options) {
        const id = `toast-${++this.notificationCount}`;
        const notification = document.createElement('div');
        notification.className = `toast toast-${options.severity}`;
        notification.id = id;

        // Build notification HTML
        notification.innerHTML = `
            <div class="toast-icon">
                ${this._getIcon(options.severity)}
            </div>
            <div class="toast-content">
                ${options.title ? `<div class="toast-title">${this._escapeHtml(options.title)}</div>` : ''}
                <div class="toast-message">${this._escapeHtml(options.message)}</div>
            </div>
            <button class="toast-close" aria-label="Close notification">
                <i class="fas fa-times"></i>
            </button>
        `;

        // Add close button handler
        const closeButton = notification.querySelector('.toast-close');
        closeButton.addEventListener('click', () => {
            this._dismissNotification(notification);
        });

        return notification;
    }

    /**
     * Get FontAwesome icon for severity
     */
    _getIcon(severity) {
        const icons = {
            'success': '<i class="fas fa-circle-check"></i>',
            'error': '<i class="fas fa-circle-exclamation"></i>',
            'warning': '<i class="fas fa-triangle-exclamation"></i>',
            'info': '<i class="fas fa-circle-info"></i>'
        };
        return icons[severity] || icons['info'];
    }

    /**
     * Dismiss a notification with fade-out animation
     */
    _dismissNotification(notification) {
        notification.classList.remove('toast-fade-in');
        notification.classList.add('toast-fade-out');

        // Remove from DOM after animation completes
        setTimeout(() => {
            if (notification.parentNode) {
                notification.parentNode.removeChild(notification);
            }

            // Clean up container if empty
            if (this.container && this.container.children.length === 0) {
                this.container.remove();
                this.container = null;
            }
        }, 500); // Match CSS transition duration
    }

    /**
     * Escape HTML to prevent XSS
     */
    _escapeHtml(text) {
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }
}
