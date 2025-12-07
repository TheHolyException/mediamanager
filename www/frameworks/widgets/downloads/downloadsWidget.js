class DownloadsWidget extends BaseWidget {
    static indexes = new Map();
    static instanceContextMenus = new Map();

    constructor(options = {}) {
        super({
            type: 'downloads',
            width: 2,
            height: 2,
            ...options
        });
        this.instanceId = 'downloads-' + Date.now() + '-' + Math.random().toString(36).substr(2, 9);
    }

    onInit() {
        // Store reference to widget content for instance-scoped operations
        this.widgetElement = $('.embeded-widget [widget-name="DownloadsWidget"]').last();
        if (this.widgetElement.length === 0) {
            // For grid view, find the widget element differently
            this.widgetElement = $('[widget-name="DownloadsWidget"]').last();
        }
        
        // Request initial data when widget is initialized
        sendPacket("syn", "default");
    }

    onDestroy() {
        // Clean up context menu when widget is destroyed
        const contextMenu = DownloadsWidget.instanceContextMenus.get(this.instanceId);
        if (contextMenu) {
            contextMenu.remove();
            DownloadsWidget.instanceContextMenus.delete(this.instanceId);
        }
    }

    createContent() {
        let widgetContent = $(`
        <div class="widget scrollbar-on-hover custom-scrollbar" widget-name="DownloadsWidget" widget-id="${this.instanceId}">
            <div class="widget-header">
                <div class="widget-title">
                    <i class="fas fa-download"></i>
                    <h1 class="widget-handle">Download Manager</h1>
                </div>
            </div>
            <div class="downloads-content">
                <div class="downloads-header">
                    <div class="queue-stats">
                        <div class="stat-card">
                            <div class="stat-icon"><i class="fas fa-download"></i></div>
                            <div class="stat-info">
                                <span class="stat-value total-downloads">0</span>
                                <span class="stat-label">Total</span>
                            </div>
                        </div>
                        <div class="stat-card">
                            <div class="stat-icon"><i class="fas fa-bars-progress"></i></div>
                            <div class="stat-info">
                                <span class="stat-value active-downloads-count">0</span>
                                <span class="stat-label">Active</span>
                            </div>
                        </div>
                        <div class="stat-card">
                            <div class="stat-icon"><i class="fas fa-check-circle"></i></div>
                            <div class="stat-info">
                                <span class="stat-value completed-downloads">0</span>
                                <span class="stat-label">Completed</span>
                            </div>
                        </div>
                        <div class="stat-card">
                            <div class="stat-icon"><i class="fas fa-exclamation-triangle"></i></div>
                            <div class="stat-info">
                                <span class="stat-value failed-downloads-count">0</span>
                                <span class="stat-label">Failed</span>
                            </div>
                        </div>
                    </div>
                    <nav class="queue-action-bar">
                        <button class="action-btn add-sources-btn">
                            <i class="fa fa-plus"></i>
                            <span>Add Downloads</span>
                        </button>
                        <button class="action-btn commit-sources-btn">
                            <i class="fas fa-paper-plane"></i>
                            <span>Start Queue</span>
                        </button>
                        <button class="action-btn retry-all-btn">
                            <i class="fa-solid fa-rotate-right"></i>
                            <span>Retry Failed</span>
                        </button>
                        <button class="action-btn retry-validationerror-btn">
                            <i class="fa-solid fa-rotate-right"></i>
                            <span>Retry all with validation error</span>
                        </button>
                        <button class="action-btn delete-all-btn">
                            <i class="fa fa-trash"></i>
                            <span>Clear All</span>
                        </button>
                        <button class="action-btn delete-completed-btn">
                            <i class="fa fa-check"></i>
                            <span>Remove Completed</span>
                        </button>
                    </nav>
                </div>
                <div class="table-container scrollable-content">
                    <table class="queue-table">
                        <thead>
                            <tr>
                                <th col="state">
                                    <i class="fas fa-info-circle"></i>
                                    <span>Status</span>
                                </th>
                                <th col="url">
                                    <i class="fas fa-link"></i>
                                    <span>Source URL</span>
                                </th>
                                <th col="target">
                                    <i class="fas fa-folder"></i>
                                    <span>Target Path</span>
                                </th>
                            </tr>
                        </thead>
                        <tbody class="queue-body">
                        </tbody>
                    </table>
                </div>
            </div>
        </div>
        `);

        widgetContent.find('.add-sources-btn').click(function () {
            // Simple function call with timeout fallback
            setTimeout(function () {
                if (typeof window.openAddSourcePopup === 'function') {
                    window.openAddSourcePopup();
                } else if (typeof openAddSourcePopup === 'function') {
                    openAddSourcePopup();
                } else {
                    console.error('openAddSourcePopup function not available');
                    console.log('Available window functions:', Object.keys(window).filter(key => typeof window[key] === 'function' && key.toLowerCase().includes('popup')));
                    alert('Add source dialog is not ready yet. Please try again in a moment.');
                }
            }, 100);
        });

        widgetContent.find('.commit-sources-btn').click(function () {
            DownloadsWidget.commit();
        });

        widgetContent.find('.retry-all-btn').click(function () {
            // Retry all failed and retry items
            for (let [uuid, data] of DownloadsWidget.indexes) {
                if (data.state.includes('Error') || data.state.includes('Retry scheduled')) {
                    if (!(data.state === "new" || data.state.includes("Downloading"))) {
                        data.state = "new";
                        addDownloadsAPI([data]);
                    }
                }
            }
        });

        widgetContent.find('.retry-validationerror-btn').click(function () {
            // Retry all validation error items
            for (let [uuid, data] of DownloadsWidget.indexes) {
                if (data.state.startsWith('Validation Error:')) {
                    if (!(data.state === "new" || data.state.includes("Downloading"))) {
                        data.state = "new";
                        data.options.skipValidation = "true";
                        addDownloadsAPI([data]);
                    }
                }
            }
        });

        widgetContent.find('.delete-all-btn').click(function () {
            deleteAllDownloadsAPI();
        });

        widgetContent.find('.delete-completed-btn').click(function () {
            // Delete all completed items
            const completedItems = [];
            for (let [uuid, data] of DownloadsWidget.indexes) {
                if (data.state.includes('Completed')) {
                    if (data.state != "new") {
                        deleteDownloadAPI(uuid);
                    }
                    completedItems.push(uuid);
                }
            }
            
            completedItems.forEach(uuid => {
                DownloadsWidget.indexes.delete(uuid);
                $(`[uuid="${uuid}"]`).remove();
            });
            
            DownloadsWidget.updateStatistics();
        });
        
        // Create context menu container
        const contextMenu = $(`
            <div class="download-context-menu download-context-menu-${this.instanceId}" style="display: none;">
                <div class="context-menu-item" data-action="retry">
                    <i class="fa fa-rotate-right"></i>
                    <span>Retry Download</span>
                </div>
                <div class="context-menu-item" data-action="retrySkipValidation">
                    <i class="fa-solid fa-forward"></i>
                    <span>Retry (Skip Validation)</span>
                </div>
                <div class="context-menu-item" data-action="retryOtherStream">
                    <i class="fa-solid fa-code-branch"></i>
                    <span>Retry with Other Stream</span>
                </div>
                <div class="context-menu-item" data-action="viewLog">
                    <i class="fas fa-file-text"></i>
                    <span>View Log</span>
                </div>
                <div class="context-menu-item" data-action="viewDetailedLog">
                    <i class="fas fa-file-alt"></i>
                    <span>View Detailed Log</span>
                </div>
                <div class="context-menu-separator"></div>
                <div class="context-menu-item" data-action="copyUrl">
                    <i class="fas fa-copy"></i>
                    <span>Copy URL</span>
                </div>
                <div class="context-menu-item" data-action="copyTarget">
                    <i class="fas fa-folder-open"></i>
                    <span>Copy Target Path</span>
                </div>
                <div class="context-menu-separator"></div>
                <div class="context-menu-item danger" data-action="delete">
                    <i class="fa fa-trash"></i>
                    <span>Delete</span>
                </div>
            </div>
        `);
        
        $('body').append(contextMenu);
        
        // Store reference to context menu for this instance
        DownloadsWidget.instanceContextMenus.set(this.instanceId, contextMenu);
        
        // Hide context menu when clicking elsewhere
        $(document).on('click', function() {
            $('.download-context-menu').hide();
        });
        
        return widgetContent.get(0);
    }

    static commit() {
        let commitPacket = [];
        for (let [uuid, data] of DownloadsWidget.indexes) {
            // We only want to put new entries
            if (data.state != "new") continue;
            commitPacket.push(data);
        }

        addDownloadsAPI(commitPacket);
    }
    
    static showContextMenu(event, uuid, widgetId) {
        // Find the specific context menu for this widget instance
        const contextMenu = widgetId ? 
            $(`.download-context-menu-${widgetId}`) : 
            $('.download-context-menu').first();
        const data = DownloadsWidget.indexes.get(uuid);
        
        if (!data || contextMenu.length === 0) {
            return;
        }
        
        // Reset all items to enabled state first
        contextMenu.find('.context-menu-item').removeClass('disabled');
        
        // Update menu item states based on download state
        const retryItem = contextMenu.find('[data-action="retry"]');
        const retrySkipValidationItem = contextMenu.find('[data-action="retrySkipValidation"]');
        const retryOtherStreamItem = contextMenu.find('[data-action="retryOtherStream"]');
        const viewLogItem = contextMenu.find('[data-action="viewLog"]');
        
        // Disable retry option if not retryable
        const canRetry = !(data.state === "new" || data.state.includes("Downloading"));
        if (!canRetry) {
            retryItem.addClass('disabled');
        }
        
        // Disable retry with skip validation if not a validation error
        if (!data.state.startsWith('Validation Error:')) {
            retrySkipValidationItem.addClass('disabled');
        }
        
        // Disable retry with other stream if not applicable
        if (!(data.autoloaderData != undefined && data.state.startsWith('Error'))) {
            retryOtherStreamItem.addClass('disabled');
        }
        
        
        // Remove previous click handlers
        contextMenu.off('click', '.context-menu-item');
        
        // Add click handlers
        contextMenu.on('click', '.context-menu-item', function(e) {
            e.stopPropagation();
            
            // Don't do anything if the item is disabled
            if ($(this).hasClass('disabled')) {
                return;
            }
            
            const action = $(this).data('action');
            
            switch(action) {
                case 'retry':
                    if (canRetry) {
                        data.state = "new";
                        addDownloadsAPI([data]);
                    }
                    break;
                case 'retrySkipValidation':
                    if (canRetry) {
                        data.state = "new";
                        data.options.skipValidation = "true";
                        addDownloadsAPI([data]);
                    }
                    break;
                case 'retryOtherStream':
                    SelectStreamPopup.request(data);
                    break;
                case 'viewLog':
                    window.open('/api/view-log/' + uuid, '_blank');
                    break;
                case 'viewDetailedLog':
                    window.open('/api/view-log/' + uuid + '?detailed=true', '_blank');
                    break;
                case 'copyUrl':
                    navigator.clipboard.writeText(data.url).then(() => {
                        // You could add a toast notification here if desired
                        console.log('URL copied to clipboard');
                    }).catch(err => {
                        console.error('Failed to copy URL: ', err);
                        // Fallback for older browsers
                        const textArea = document.createElement('textarea');
                        textArea.value = data.url;
                        document.body.appendChild(textArea);
                        textArea.select();
                        document.execCommand('copy');
                        document.body.removeChild(textArea);
                    });
                    break;
                case 'copyTarget':
                    const targetPath = data.target + (data.target.endsWith('/') ? '' : '/') + (data.title || '?');
                    navigator.clipboard.writeText(targetPath).then(() => {
                        console.log('Target path copied to clipboard');
                    }).catch(err => {
                        console.error('Failed to copy target path: ', err);
                        // Fallback for older browsers
                        const textArea = document.createElement('textarea');
                        textArea.value = targetPath;
                        document.body.appendChild(textArea);
                        textArea.select();
                        document.execCommand('copy');
                        document.body.removeChild(textArea);
                    });
                    break;
                case 'delete':
                    if (data.state != "new") {
                        deleteDownloadAPI(uuid);
                    }
                    DownloadsWidget.indexes.delete(uuid);
                    $(`[uuid="${uuid}"]`).remove();
                    DownloadsWidget.updateStatistics();
                    break;
            }
            
            contextMenu.hide();
        });
        
        // Hide all other context menus first
        $('.download-context-menu').hide();
        
        // Show menu temporarily to measure its actual dimensions
        contextMenu.css({
            position: 'fixed',
            left: '-9999px',
            top: '-9999px',
            display: 'block',
            visibility: 'hidden'
        });
        
        // Get actual menu dimensions
        const menuWidth = contextMenu.outerWidth();
        const menuHeight = contextMenu.outerHeight();
        const windowWidth = $(window).width();
        const windowHeight = $(window).height();
        
        // Get widget boundaries
        const widgetElement = widgetId ? 
            $(`[widget-id="${widgetId}"]`) : 
            $('[widget-name="DownloadsWidget"]').first();
        
        // Use clientX/clientY instead of pageX/pageY to avoid scroll offset issues
        let x = event.clientX;
        let y = event.clientY;
        
        // Get widget boundaries in viewport coordinates
        const widgetRect = widgetElement[0].getBoundingClientRect();
        const widgetLeft = widgetRect.left;
        const widgetTop = widgetRect.top;
        const widgetRight = widgetRect.right;
        const widgetBottom = widgetRect.bottom;
        
        // Determine effective boundaries (smaller of widget or window)
        const effectiveRight = Math.min(windowWidth, widgetRight);
        const effectiveBottom = Math.min(windowHeight, widgetBottom);
        const effectiveLeft = Math.max(0, widgetLeft);
        const effectiveTop = Math.max(0, widgetTop);
        
        // Adjust horizontal position
        if (x + menuWidth > effectiveRight) {
            x = effectiveRight - menuWidth - 5; // 5px padding from edge
        }
        if (x < effectiveLeft) {
            x = effectiveLeft + 5; // 5px padding from edge
        }
        
        // Adjust vertical position - prioritize showing the full menu
        if (y + menuHeight > effectiveBottom) {
            // Try to position above the click point first
            const aboveY = y - menuHeight - 10;
            if (aboveY >= effectiveTop) {
                y = aboveY;
            } else {
                // If not enough space above, position at the bottom edge
                y = effectiveBottom - menuHeight - 5;
            }
        }
        if (y < effectiveTop) {
            y = effectiveTop + 5;
        }
        
        // Force all the necessary styles with ultra-high z-index
        contextMenu[0].style.cssText = `
            position: fixed !important;
            left: ${x}px !important;
            top: ${y}px !important;
            display: block !important;
            visibility: visible !important;
            z-index: 999999 !important;
            background: rgba(40, 44, 52, 0.98) !important;
            border: 1px solid rgba(255, 255, 255, 0.1) !important;
            border-radius: 4px !important;
            box-shadow: 0 8px 32px rgba(0, 0, 0, 0.4) !important;
            min-width: 200px !important;
            padding: 8px 0 !important;
            font-size: 14px !important;
        `;
    }

    static addDownloaderItem(item) {
        let index = DownloadsWidget.indexes.get(item.uuid);
        let tableBodies = $('.queue-body');
        let row = undefined;

        if (index == undefined) {//its a new item and can be aded to all existing tables at once
            row = DownloadsWidget.createNewRow(item);
            DownloadsWidget.setStatusAndTooltip(row, item);
            tableBodies.append(row);
        }
        else {//its an existing item and needs to be added to each table seperatly to update already existing rows
            let dirPath = item.target;
            let subPath = item.title;
            if (!dirPath.endsWith('/'))
                dirPath += '/';
            if (!subPath)
                subPath = '?';

            // Cache DOM queries for better performance
            const tableBodyElements = document.querySelectorAll('.queue-body');
            
            for (const tableBodyElement of tableBodyElements) {
                let existingRow = tableBodyElement.querySelector(`[uuid="${item.uuid}"]`);

                if (!existingRow) {
                    const newRowJQuery = DownloadsWidget.createNewRow(item);
                    const newRow = newRowJQuery.get(0); // Convert jQuery object to native DOM element
                    tableBodyElement.appendChild(newRow);
                    existingRow = newRow;
                } else {
                    DownloadsWidget.updateExistingRow(existingRow, item, dirPath + subPath);
                }

                DownloadsWidget.setStatusAndTooltip($(existingRow), item);
            }
        }


        DownloadsWidget.indexes.set(item.uuid, item);
        DownloadsWidget.updateStatistics();
    }

    static updateExistingRow(row, item, targetPath) {
        const statusColumn = row.querySelector('[col="state"]');
        const statusIndicator = statusColumn.querySelector('.status-indicator');
        const statusText = statusColumn.querySelector('.status-text');
        
        // Update status text
        statusText.textContent = item.state;
        
        // Update status indicator symbols
        const existingSymbols = statusIndicator.querySelectorAll('.error-symbol, .warning-symbol');
        existingSymbols.forEach(symbol => symbol.remove());
        
        if (item.hadServerError) {
            const errorIcon = DOMUtils.createElement('i', {
                className: 'fas fa-exclamation-circle error-symbol',
                title: 'Download had errors'
            });
            statusIndicator.appendChild(errorIcon);
        } else if (item.hadWarning) {
            const warningIcon = DOMUtils.createElement('i', {
                className: 'fas fa-exclamation-triangle warning-symbol',
                title: 'Download had warnings'
            });
            statusIndicator.appendChild(warningIcon);
        }

        const targetColumn = row.querySelector('[col="target"]');
        targetColumn.textContent = targetPath;
    }

    static updateStatistics() {
        const items = Array.from(DownloadsWidget.indexes.values());
        const total = items.length;
        const active = items.filter(item => item.state.includes('Downloading') || item.state.includes('Committed')).length;
        const completed = items.filter(item => item.state.includes('Completed')).length;
        const failed = items.filter(item => item.state.includes('Error')).length;

        // Update all download widget instances
        $('[widget-name="DownloadsWidget"]').each(function() {
            const widget = $(this);
            widget.find('.total-downloads').text(total);
            widget.find('.active-downloads-count').text(active);
            widget.find('.completed-downloads').text(completed);
            widget.find('.failed-downloads-count').text(failed);
        });
    }

    static setStatusAndTooltip(row, item) {
        let tooltip = "Status:\n" + item.state + "\n";
        tooltip += "Options:\n";
        for (let key in item.options) {
            tooltip += "\t" + key + ": " + item.options[key] + "\n"
        }
        row.attr('title', tooltip);

        row.attr('class', DownloadsWidget.getStatusClass(item.state));
    }

    static createNewRow(item) {
        //Row
        let row = $('<tr>')
            .attr('uuid', item.uuid)
            .css('cursor', 'context-menu')
            .on('contextmenu', function(e) {
                e.preventDefault();
                // Find the widget instance this row belongs to
                const widgetElement = $(this).closest('[widget-name="DownloadsWidget"]');
                const widgetId = widgetElement.attr('widget-id');
                DownloadsWidget.showContextMenu(e, item.uuid, widgetId);
            });
        //===============================================================================
        //==============================Column - State===============================
        let statusText = item.state.split('\n')[0];
        let statusIndicator = $('<div>').addClass('status-indicator');
        
        // Add warning/error symbols
        if (item.hadServerError) {
            statusIndicator.append($('<i>').addClass('fas fa-exclamation-circle error-symbol').attr('title', 'Download had errors'));
        } else if (item.hadWarning) {
            statusIndicator.append($('<i>').addClass('fas fa-exclamation-triangle warning-symbol').attr('title', 'Download had warnings'));
        }
        
        let state = $('<td>')
            .attr('col', 'state')
            .addClass('status-cell')
            .click(() => navigator.clipboard.writeText(item.state))
            .append(
                $('<div>').addClass('status-content').append(
                    statusIndicator,
                    $('<span>').addClass('status-text').text(statusText)
                )
            );
        //===============================================================================
        //==============================Column - URL===============================
        let url = $('<td>')
            .attr('col', 'url')
            .addClass('url-cell')
            .append(
                $('<div>').addClass('url-content').append(
                    $('<i>').addClass('fas fa-link url-icon'),
                    $('<a>').addClass('url-link')
                        .text(item.url.length > 50 ? item.url.substring(0, 50) + '...' : item.url)
                        .attr('href', item.url)
                        .attr('title', item.url)
                        .attr('target', '_blank')
                )
            );
        //===============================================================================
        //==============================Column - Target===============================
        let dirPath = item.target;
        let subPath = item.title;
        if (!dirPath.endsWith('/'))
            dirPath += '/';
        if (!subPath)
            subPath = '?';

        let target = $('<td>')
            .attr('col', 'target')
            .addClass('target-cell')
            .append(
                $('<div>').addClass('target-content').append(
                    $('<i>').addClass('fas fa-folder target-icon'),
                    $('<span>').addClass('target-path').text(dirPath + subPath).attr('title', dirPath + subPath)
                )
            );
        //===============================================================================
        row.append(state, url, target);
        return row;
    }

    static getStatusClass(statusMessage) {
        if (statusMessage.includes("Validation Error")) {
            return 'validation'
        }

        if (statusMessage.includes("rror")) {
            return 'failed'
        }

        if (statusMessage.includes("Committed")) {
            return 'committed'
        }

        if (statusMessage.includes("Downloading")) {
            return 'downlading'
        }

        if (statusMessage.includes("Completed")) {
            return 'success'
        }

        if (statusMessage.includes("Retry scheduled for")) {
            return 'retry'
        }
    }

    static generateUUID() {
        // Fallback UUID generation for compatibility
        if (crypto && crypto.randomUUID) {
            return crypto.randomUUID();
        } else {
            // Fallback implementation
            return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, function(c) {
                const r = Math.random() * 16 | 0;
                const v = c === 'x' ? r : (r & 0x3 | 0x8);
                return v.toString(16);
            });
        }
    }

    static addNewElement(urls, settings, targetSelection, subfolder) {
        if (!urls) {
            console.error("No URLs provided to addNewElement");
            return;
        }
        let urlArray = urls.split(";");

        for (let urlElement of urlArray) {
            let obj = {
                uuid: DownloadsWidget.generateUUID(),
                state: "new",
                created: new Date().getTime(),
                url: urlElement,
                options: settings,
                target: targetSelection + "/" + subfolder
            }
            DownloadsWidget.addDownloaderItem(obj);
        }
    }


    static onWSResponse(cmd, content) {
        switch (cmd) {
            case "syn": // Acknowledge data sync
                for (let i = 0; i < content.data.length; i++) {
                    let entry = content.data[i];
                    DownloadsWidget.addDownloaderItem(entry);
                }
                break;
            case "del":
                for (let uuid of content.list) {
                    DownloadsWidget.indexes.delete(uuid);
                    $('.queue-table [uuid="' + uuid + '"]').remove();
                }
                DownloadsWidget.updateStatistics();
                break;
        }
    }

}