/**
 * Widget Utilities - Common patterns and utilities for widget development
 */
class WidgetUtils {
    /**
     * Create standardized widget header
     * @param {string} title 
     * @param {string} icon 
     * @param {Object} stats 
     * @returns {HTMLElement}
     */
    static createWidgetHeader(title, icon, stats = null) {
        const header = DOMUtils.createElement('div', { className: 'widget-header' });
        
        const titleSection = DOMUtils.createElement('div', { className: 'widget-title' });
        
        if (icon) {
            titleSection.appendChild(DOMUtils.createElement('i', { className: icon }));
        }
        
        titleSection.appendChild(DOMUtils.createElement('h1', { textContent: title }));
        header.appendChild(titleSection);
        
        if (stats) {
            const statsSection = DOMUtils.createElement('div', { className: 'widget-stats' });
            
            Object.entries(stats).forEach(([key, value]) => {
                const statItem = DOMUtils.createElement('div', { className: 'stat-item' });
                statItem.appendChild(DOMUtils.createElement('i', { className: value.icon || 'fas fa-circle' }));
                statItem.appendChild(DOMUtils.createElement('span', { 
                    className: value.className || '',
                    textContent: `${key}: ${value.count || value}` 
                }));
                statsSection.appendChild(statItem);
            });
            
            header.appendChild(statsSection);
        }
        
        return header;
    }
    
    /**
     * Create standardized toolbar for widgets
     * @param {Array} buttons - Array of button configurations
     * @returns {HTMLElement}
     */
    static createToolbar(buttons = []) {
        const toolbar = DOMUtils.createElement('div', { className: 'toolbar' });
        
        buttons.forEach(buttonConfig => {
            const button = DOMUtils.createElement('button', {
                className: `btn-${buttonConfig.type || 'primary'} ${buttonConfig.className || ''}`,
                textContent: buttonConfig.text
            });
            
            if (buttonConfig.icon) {
                button.innerHTML = `<i class="${buttonConfig.icon}"></i> ${buttonConfig.text}`;
            }
            
            if (buttonConfig.onClick) {
                button.addEventListener('click', buttonConfig.onClick);
            }
            
            if (buttonConfig.disabled) {
                button.classList.add('btn-disabled');
            }
            
            toolbar.appendChild(button);
        });
        
        return toolbar;
    }
    
    /**
     * Create standardized table with sorting and filtering
     * @param {Array} columns - Column definitions
     * @param {Array} data - Table data
     * @param {Object} options - Table options
     * @returns {HTMLElement}
     */
    static createDataTable(columns, data, options = {}) {
        const container = DOMUtils.createElement('div', { className: 'table-container' });
        const table = DOMUtils.createElement('table', { className: 'data-table' });
        
        // Create header
        const thead = DOMUtils.createElement('thead');
        const headerRow = DOMUtils.createElement('tr');
        
        columns.forEach(column => {
            const th = DOMUtils.createElement('th', {
                textContent: column.title || column.key,
                className: column.sortable ? 'sortable' : ''
            });
            
            if (column.sortable) {
                th.addEventListener('click', () => {
                    this.sortTable(table, column.key, column.type || 'string');
                });
            }
            
            headerRow.appendChild(th);
        });
        
        thead.appendChild(headerRow);
        table.appendChild(thead);
        
        // Create body
        const tbody = DOMUtils.createElement('tbody');
        this.populateTableBody(tbody, columns, data);
        table.appendChild(tbody);
        
        container.appendChild(table);
        
        // Add pagination if needed
        if (options.pagination && data.length > options.pagination.pageSize) {
            const pagination = this.createPagination(data.length, options.pagination);
            container.appendChild(pagination);
        }
        
        return container;
    }
    
    /**
     * Populate table body with data
     * @param {HTMLElement} tbody 
     * @param {Array} columns 
     * @param {Array} data 
     */
    static populateTableBody(tbody, columns, data) {
        DOMUtils.clearChildren(tbody);
        
        data.forEach(rowData => {
            const row = DOMUtils.createElement('tr');
            
            columns.forEach(column => {
                const cell = DOMUtils.createElement('td');
                let value = rowData[column.key];
                
                if (column.render) {
                    cell.innerHTML = column.render(value, rowData);
                } else {
                    cell.textContent = value || '';
                }
                
                if (column.className) {
                    cell.className = column.className;
                }
                
                row.appendChild(cell);
            });
            
            tbody.appendChild(row);
        });
    }
    
    /**
     * Sort table by column
     * @param {HTMLElement} table 
     * @param {string} key 
     * @param {string} type 
     */
    static sortTable(table, key, type = 'string') {
        const tbody = table.querySelector('tbody');
        const rows = Array.from(tbody.querySelectorAll('tr'));
        
        const sorted = rows.sort((a, b) => {
            const aValue = this.getCellValue(a, key);
            const bValue = this.getCellValue(b, key);
            
            switch (type) {
                case 'number':
                    return parseFloat(aValue) - parseFloat(bValue);
                case 'date':
                    return new Date(aValue) - new Date(bValue);
                default:
                    return aValue.localeCompare(bValue);
            }
        });
        
        DOMUtils.clearChildren(tbody);
        sorted.forEach(row => tbody.appendChild(row));
    }
    
    /**
     * Get cell value for sorting
     * @param {HTMLElement} row 
     * @param {string} key 
     * @returns {string}
     */
    static getCellValue(row, key) {
        const cell = row.querySelector(`[data-key="${key}"]`);
        return cell ? cell.textContent.trim() : '';
    }
    
    /**
     * Create pagination controls
     * @param {number} totalItems 
     * @param {Object} paginationOptions 
     * @returns {HTMLElement}
     */
    static createPagination(totalItems, paginationOptions) {
        const { pageSize, currentPage = 1 } = paginationOptions;
        const totalPages = Math.ceil(totalItems / pageSize);
        
        const pagination = DOMUtils.createElement('div', { className: 'pagination' });
        
        // Previous button
        const prevBtn = DOMUtils.createElement('button', {
            className: currentPage === 1 ? 'btn-secondary btn-disabled' : 'btn-secondary',
            textContent: 'Previous'
        });
        
        if (currentPage > 1) {
            prevBtn.addEventListener('click', () => this.changePage(currentPage - 1));
        }
        
        pagination.appendChild(prevBtn);
        
        // Page numbers
        for (let i = 1; i <= totalPages; i++) {
            const pageBtn = DOMUtils.createElement('button', {
                className: i === currentPage ? 'btn-primary' : 'btn-secondary',
                textContent: i.toString()
            });
            
            pageBtn.addEventListener('click', () => this.changePage(i));
            pagination.appendChild(pageBtn);
        }
        
        // Next button
        const nextBtn = DOMUtils.createElement('button', {
            className: currentPage === totalPages ? 'btn-secondary btn-disabled' : 'btn-secondary',
            textContent: 'Next'
        });
        
        if (currentPage < totalPages) {
            nextBtn.addEventListener('click', () => this.changePage(currentPage + 1));
        }
        
        pagination.appendChild(nextBtn);
        
        return pagination;
    }
    
    /**
     * Handle page change
     * @param {number} page 
     */
    static changePage(page) {
        // This would trigger a re-render of the table with new page data
        console.log('Page changed to:', page);
    }
    
    /**
     * Create a progress bar
     * @param {number} percentage 
     * @param {string} label 
     * @returns {HTMLElement}
     */
    static createProgressBar(percentage, label = '') {
        const container = DOMUtils.createElement('div', { className: 'progress-container' });
        
        if (label) {
            container.appendChild(DOMUtils.createElement('div', {
                className: 'progress-label',
                textContent: label
            }));
        }
        
        const progressBar = DOMUtils.createElement('div', { className: 'progress-bar' });
        const progressFill = DOMUtils.createElement('div', {
            className: 'progress-fill',
            style: `width: ${Math.min(100, Math.max(0, percentage))}%`
        });
        
        progressBar.appendChild(progressFill);
        container.appendChild(progressBar);
        
        return container;
    }
}

/**
 * Data Utilities - Common data manipulation patterns
 */
class DataUtils {
    /**
     * Deep clone an object
     * @param {*} obj 
     * @returns {*}
     */
    static deepClone(obj) {
        if (obj === null || typeof obj !== 'object') return obj;
        if (obj instanceof Date) return new Date(obj.getTime());
        if (obj instanceof Array) return obj.map(item => this.deepClone(item));
        if (typeof obj === 'object') {
            const cloned = {};
            Object.keys(obj).forEach(key => {
                cloned[key] = this.deepClone(obj[key]);
            });
            return cloned;
        }
    }
    
    /**
     * Debounce function calls
     * @param {Function} func 
     * @param {number} wait 
     * @returns {Function}
     */
    static debounce(func, wait) {
        let timeout;
        return function executedFunction(...args) {
            const later = () => {
                clearTimeout(timeout);
                func.apply(this, args);
            };
            clearTimeout(timeout);
            timeout = setTimeout(later, wait);
        };
    }
    
    /**
     * Format file size
     * @param {number} bytes 
     * @returns {string}
     */
    static formatFileSize(bytes) {
        if (bytes === 0) return '0 Bytes';
        const k = 1024;
        const sizes = ['Bytes', 'KB', 'MB', 'GB', 'TB'];
        const i = Math.floor(Math.log(bytes) / Math.log(k));
        return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
    }
    
    /**
     * Format duration in seconds to human readable format
     * @param {number} seconds 
     * @returns {string}
     */
    static formatDuration(seconds) {
        const hours = Math.floor(seconds / 3600);
        const minutes = Math.floor((seconds % 3600) / 60);
        const remainingSeconds = seconds % 60;
        
        if (hours > 0) {
            return `${hours}:${minutes.toString().padStart(2, '0')}:${remainingSeconds.toString().padStart(2, '0')}`;
        } else {
            return `${minutes}:${remainingSeconds.toString().padStart(2, '0')}`;
        }
    }
}

// Export utilities
window.WidgetUtils = WidgetUtils;
window.DataUtils = DataUtils;