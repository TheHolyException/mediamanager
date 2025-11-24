/**
 * DOM Utilities - Common DOM manipulation patterns used across widgets
 */
class DOMUtils {
    /**
     * Create an element with attributes and children
     * @param {string} tag - HTML tag name
     * @param {Object} attributes - Object containing attributes to set
     * @param {Array|string|HTMLElement} children - Children to append
     * @returns {HTMLElement}
     */
    static createElement(tag, attributes = {}, children = []) {
        const element = document.createElement(tag);
        
        Object.entries(attributes).forEach(([key, value]) => {
            if (key === 'className') {
                element.className = value;
            } else if (key === 'innerHTML') {
                element.innerHTML = value;
            } else if (key === 'textContent') {
                element.textContent = value;
            } else {
                element.setAttribute(key, value);
            }
        });
        
        if (children) {
            if (Array.isArray(children)) {
                children.forEach(child => {
                    if (typeof child === 'string') {
                        element.appendChild(document.createTextNode(child));
                    } else if (child instanceof HTMLElement) {
                        element.appendChild(child);
                    }
                });
            } else if (typeof children === 'string') {
                element.textContent = children;
            } else if (children instanceof HTMLElement) {
                element.appendChild(children);
            }
        }
        
        return element;
    }
    
    /**
     * Efficiently show/hide elements
     * @param {HTMLElement|jQuery} element 
     * @param {boolean} show 
     */
    static toggleVisibility(element, show) {
        const el = element.jquery ? element[0] : element;
        if (show) {
            el.classList.remove('force-hide');
            el.style.display = '';
        } else {
            el.classList.add('force-hide');
        }
    }
    
    /**
     * Safely remove all children from an element
     * @param {HTMLElement|jQuery} element 
     */
    static clearChildren(element) {
        const el = element.jquery ? element[0] : element;
        while (el.firstChild) {
            el.removeChild(el.firstChild);
        }
    }
    
    /**
     * Batch DOM updates to minimize reflows
     * @param {HTMLElement} element 
     * @param {Function} updateFunction 
     */
    static batchUpdate(element, updateFunction) {
        const originalDisplay = element.style.display;
        element.style.display = 'none';
        
        try {
            updateFunction();
        } finally {
            element.style.display = originalDisplay;
        }
    }
    
    /**
     * Create a loading spinner element
     * @param {string} size - 'small', 'medium', 'large'
     * @returns {HTMLElement}
     */
    static createSpinner(size = 'medium') {
        const sizeClass = `spinner-${size}`;
        return this.createElement('div', {
            className: `spinner ${sizeClass}`,
            innerHTML: '<i class="fa-solid fa-spinner fa-spin"></i>'
        });
    }
    
    /**
     * Create a status indicator
     * @param {string} status - 'success', 'error', 'warning', 'info'
     * @param {string} text 
     * @returns {HTMLElement}
     */
    static createStatusIndicator(status, text) {
        const iconMap = {
            success: 'fa-check-circle',
            error: 'fa-exclamation-circle',
            warning: 'fa-exclamation-triangle',
            info: 'fa-info-circle'
        };
        
        return this.createElement('div', {
            className: `status-indicator status-${status}`,
        }, [
            this.createElement('i', { className: `fas ${iconMap[status]}` }),
            this.createElement('span', { textContent: text })
        ]);
    }
}

/**
 * Form Utilities - Common form validation and handling patterns
 */
class FormUtils {
    /**
     * Validate required fields in a form
     * @param {HTMLElement|jQuery} form 
     * @returns {Object} {isValid: boolean, errors: Array}
     */
    static validateRequired(form) {
        const formEl = form.jquery ? form[0] : form;
        const requiredFields = formEl.querySelectorAll('[required]');
        const errors = [];
        
        requiredFields.forEach(field => {
            if (!field.value.trim()) {
                const label = formEl.querySelector(`label[for="${field.id}"]`)?.textContent || field.name;
                errors.push(`${label} is required`);
                field.classList.add('form-error-field');
            } else {
                field.classList.remove('form-error-field');
            }
        });
        
        return {
            isValid: errors.length === 0,
            errors
        };
    }
    
    /**
     * Create form field with label and error handling
     * @param {string} type - Input type
     * @param {string} name - Field name
     * @param {string} label - Label text
     * @param {Object} options - Additional options
     * @returns {HTMLElement}
     */
    static createField(type, name, label, options = {}) {
        const fieldContainer = DOMUtils.createElement('div', { className: 'form-group' });
        
        if (label) {
            const labelEl = DOMUtils.createElement('label', {
                className: 'form-label',
                textContent: label,
                for: name
            });
            fieldContainer.appendChild(labelEl);
        }
        
        const input = DOMUtils.createElement('input', {
            type,
            name,
            id: name,
            className: 'form-input',
            ...options
        });
        
        fieldContainer.appendChild(input);
        
        if (options.help) {
            const helpText = DOMUtils.createElement('div', {
                className: 'form-help',
                textContent: options.help
            });
            fieldContainer.appendChild(helpText);
        }
        
        return fieldContainer;
    }
    
    /**
     * Serialize form data to object
     * @param {HTMLElement|jQuery} form 
     * @returns {Object}
     */
    static serializeForm(form) {
        const formEl = form.jquery ? form[0] : form;
        const formData = new FormData(formEl);
        const data = {};
        
        for (const [key, value] of formData.entries()) {
            if (data[key]) {
                if (Array.isArray(data[key])) {
                    data[key].push(value);
                } else {
                    data[key] = [data[key], value];
                }
            } else {
                data[key] = value;
            }
        }
        
        return data;
    }
}

/**
 * API Utilities - Centralized API handling
 */
class APIUtils {
    /**
     * Make an API request with standardized error handling
     * @param {string} url 
     * @param {Object} options 
     * @returns {Promise}
     */
    static async request(url, options = {}) {
        const config = {
            method: 'GET',
            headers: {
                'Content-Type': 'application/json',
                ...options.headers
            },
            ...options
        };
        
        if (config.data && config.method !== 'GET') {
            config.body = JSON.stringify(config.data);
        }
        
        try {
            const response = await fetch(url, config);
            const data = await response.json();
            
            if (!response.ok) {
                throw new Error(data.error || `HTTP ${response.status}`);
            }
            
            return data;
        } catch (error) {
            console.error(`API request failed: ${url}`, error);
            throw error;
        }
    }
    
    /**
     * Handle API errors consistently
     * @param {Error} error 
     * @param {string} defaultMessage 
     */
    static handleError(error, defaultMessage = 'An error occurred') {
        const message = error.message || defaultMessage;
        
        if (window.yeti) {
            window.yeti.show({
                message,
                severity: 'nok',
                time: 5000
            });
        } else {
            console.error('API Error:', message);
        }
        
        return message;
    }
    
    /**
     * Handle API success consistently
     * @param {*} response 
     * @param {string} defaultMessage 
     */
    static handleSuccess(response, defaultMessage = 'Operation completed successfully') {
        const message = response?.message || defaultMessage;
        
        if (window.yeti) {
            window.yeti.show({
                message,
                severity: 'ok',
                time: 3000
            });
        }
        
        return message;
    }
}

// Export for use in other modules
window.DOMUtils = DOMUtils;
window.FormUtils = FormUtils;
window.APIUtils = APIUtils;