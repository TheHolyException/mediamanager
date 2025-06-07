class SubscriptionsWidget extends BaseWidget {
    constructor(name = "Subscriptions") {
        super(name);
        this.searchTerm = '';
        this.filterStatus = 'all';
        this.sortBy = 'title';
        this.sortOrder = 'asc';
    }
    
    // Static property to store current edit item across instances
    static currentEditItem = null;

    render() {
        let widget = $(`
        <div class="widget subscriptions-widget scrollbar-on-hover custom-scrollbar" widget-name="SubscriptionsWidget">
            <div class="widget-header">
                <div class="widget-title">
                    <i class="fas fa-bell"></i>
                    <h1 class="widget-handle">Subscriptions</h1>
                </div>
                <div class="widget-stats">
                    <span class="stat-item">
                        <i class="fa fa-play-circle"></i>
                        <span class="active-count">0</span> Active
                    </span>
                    <span class="stat-item">
                        <i class="fa fa-clock"></i>
                        <span class="pending-count">0</span> Pending
                    </span>
                </div>
            </div>

            <div class="toolbar">
                <div class="search-section">
                    <div class="search-box">
                        <i class="fa fa-search"></i>
                        <input type="text" placeholder="Search subscriptions..." class="search-input">
                        <button class="clear-search" style="display: none;"><i class="fa fa-times"></i></button>
                    </div>
                </div>
                
                <div class="filter-section">
                    <select class="status-filter">
                        <option value="all">All Status</option>
                        <option value="active">Active</option>
                        <option value="paused">Paused</option>
                        <option value="error">Errors</option>
                    </select>
                    
                    <select class="sort-select">
                        <option value="title">Sort by Title</option>
                        <option value="lastScan">Last Scan</option>
                        <option value="unloaded">Unloaded Episodes</option>
                        <option value="dateAdded">Date Added</option>
                    </select>
                    
                    <button class="sort-order-btn" data-order="asc" title="Sort ascending">
                        <i class="fa fa-sort-alpha-down"></i>
                    </button>
                </div>

                <button class="add-subscription-btn primary-btn">
                    <i class="fa fa-plus"></i>
                    Add Subscription
                </button>
            </div>

            <div class="add-subscription-form" style="display: none;">
                <div class="form-header">
                    <h3><i class="fa fa-plus-circle"></i> New Subscription</h3>
                    <button class="close-form-btn"><i class="fa fa-times"></i></button>
                </div>
                
                <div class="form-grid">
                    <div class="form-group">
                        <label for="sub-url">
                            <i class="fa fa-link"></i>
                            Anime URL *
                        </label>
                        <input type="text" id="sub-url" placeholder="https://aniworld.to/anime/..." required>
                        <div class="input-hint">Enter the full URL to the anime series</div>
                    </div>
                    
                    <div class="form-group">
                        <label for="sub-folder">
                            <i class="fa fa-folder"></i>
                            Download Folder
                        </label>
                        <input type="text" id="sub-folder" placeholder="Anime/Series Name">
                        <div class="input-hint">Leave empty to auto-detect from title</div>
                    </div>
                    
                    <div class="form-group">
                        <label for="sub-language">
                            <i class="fa fa-language"></i>
                            Preferred Language
                        </label>
                        <select id="sub-language">
                            <option value="1">German (Dub)</option>
                            <option value="2">German (Sub)</option>
                            <option value="3">English (Dub)</option>
                            <option value="4">English (Sub)</option>
                            <option value="5">Japanese (Sub)</option>
                        </select>
                    </div>
                    
                    <div class="form-group">
                        <label for="sub-excluded">
                            <i class="fa fa-ban"></i>
                            Excluded Seasons
                        </label>
                        <input type="text" id="sub-excluded" placeholder="1,3,5">
                        <div class="input-hint">Comma-separated season numbers to skip</div>
                    </div>
                    
                    <div class="form-group quality-group">
                        <label>
                            <i class="fa fa-video"></i>
                            Preferred Quality
                        </label>
                        <div class="quality-options">
                            <label class="quality-option">
                                <input type="radio" name="quality" value="720p" checked>
                                <span>720p</span>
                            </label>
                            <label class="quality-option">
                                <input type="radio" name="quality" value="1080p">
                                <span>1080p</span>
                            </label>
                            <label class="quality-option">
                                <input type="radio" name="quality" value="any">
                                <span>Any</span>
                            </label>
                        </div>
                    </div>
                    
                    <div class="form-group">
                        <label class="checkbox-label">
                            <input type="checkbox" id="auto-start">
                            <span class="checkmark"></span>
                            Start downloading immediately
                        </label>
                    </div>
                </div>
                
                <div class="form-actions">
                    <button class="cancel-btn">Cancel</button>
                    <button class="save-subscription-btn primary-btn">
                        <i class="fa fa-save"></i>
                        Add Subscription
                    </button>
                </div>
            </div>

            <div class="edit-subscription-form" style="display: none;">
                <div class="form-header">
                    <h3><i class="fa fa-edit"></i> Edit Subscription</h3>
                    <button class="close-edit-form-btn"><i class="fa fa-times"></i></button>
                </div>
                
                <div class="form-grid">
                    <div class="form-group">
                        <label for="edit-url">
                            <i class="fa fa-link"></i>
                            Anime URL
                        </label>
                        <input type="text" id="edit-url" readonly disabled>
                        <div class="input-hint">URL cannot be changed after subscription</div>
                    </div>
                    
                    <div class="form-group">
                        <label for="edit-folder">
                            <i class="fa fa-folder"></i>
                            Download Folder
                        </label>
                        <input type="text" id="edit-folder" placeholder="Anime/Series Name">
                        <div class="input-hint">Leave empty to auto-detect from title</div>
                    </div>
                    
                    <div class="form-group">
                        <label for="edit-language">
                            <i class="fa fa-language"></i>
                            Preferred Language
                        </label>
                        <select id="edit-language">
                            <option value="1">German (Dub)</option>
                            <option value="2">German (Sub)</option>
                            <option value="3">English (Dub)</option>
                            <option value="4">English (Sub)</option>
                            <option value="5">Japanese (Sub)</option>
                        </select>
                        <div class="input-hint">Changing language will affect future downloads</div>
                    </div>
                    
                    <div class="form-group">
                        <label for="edit-excluded">
                            <i class="fa fa-ban"></i>
                            Excluded Seasons
                        </label>
                        <input type="text" id="edit-excluded" placeholder="1,3,5">
                        <div class="input-hint">Comma-separated season numbers to skip downloading</div>
                    </div>
                </div>
                
                <div class="form-actions">
                    <button class="cancel-edit-btn">Cancel</button>
                    <button class="save-edit-btn primary-btn">
                        <i class="fa fddda-save"></i>
                        Save Changes
                    </button>
                </div>
            </div>

            <div class="subscriptions-container">
                <div class="empty-state" style="display: none;">
                    <i class="fa fa-inbox"></i>
                    <h3>No Subscriptions</h3>
                    <p>Add your first anime subscription to get started</p>
                    <button class="add-first-btn primary-btn">
                        <i class="fa fa-plus"></i>
                        Add Subscription
                    </button>
                </div>
                
                <div class="subscriptions-grid"></div>
            </div>
        </div>
        `);

        this.bindEvents(widget);
        this.requestData();
        return widget.get(0);
    }

    bindEvents(widget) {
        const self = this;

        // Add subscription button
        widget.find('.add-subscription-btn, .add-first-btn').click(function() {
            self.showAddForm(widget);
        });

        // Close form
        widget.find('.close-form-btn, .cancel-btn').click(function() {
            self.hideAddForm(widget);
        });

        // Close edit form
        widget.find('.close-edit-form-btn, .cancel-edit-btn').click(function() {
            self.hideEditForm(widget);
        });

        // Search functionality
        widget.find('.search-input').on('input', function() {
            self.searchTerm = $(this).val().toLowerCase();
            self.updateSearchUI(widget);
            self.filterAndSort(widget);
        });

        widget.find('.clear-search').click(function() {
            widget.find('.search-input').val('');
            self.searchTerm = '';
            self.updateSearchUI(widget);
            self.filterAndSort(widget);
        });

        // Filter and sort
        widget.find('.status-filter').change(function() {
            self.filterStatus = $(this).val();
            self.filterAndSort(widget);
        });

        widget.find('.sort-select').change(function() {
            self.sortBy = $(this).val();
            self.filterAndSort(widget);
        });

        widget.find('.sort-order-btn').click(function() {
            const btn = $(this);
            self.sortOrder = self.sortOrder === 'asc' ? 'desc' : 'asc';
            btn.attr('data-order', self.sortOrder);
            btn.find('i').removeClass().addClass(
                self.sortOrder === 'asc' ? 'fa fa-sort-alpha-down' : 'fa fa-sort-alpha-up'
            );
            self.filterAndSort(widget);
        });

        // Save subscription
        widget.find('.save-subscription-btn').click(function() {
            self.saveSubscription(widget);
        });

        // Save edit
        widget.find('.save-edit-btn').click(function() {
            console.log('Save edit button clicked');
            self.saveEdit(widget);
        });

        // Form validation
        widget.find('#sub-url').on('input', function() {
            self.validateForm(widget);
        });
    }

    showAddForm(widget) {
        widget.find('.add-subscription-form').slideDown(300);
        widget.find('#sub-url').focus();
    }

    hideAddForm(widget) {
        widget.find('.add-subscription-form').slideUp(300);
        this.clearForm(widget);
    }

    showEditForm(widget, item) {
        console.log('showEditForm called with item:', item);
        SubscriptionsWidget.currentEditItem = item;
        
        // Populate form with current values
        widget.find('#edit-url').val(item.url);
        widget.find('#edit-folder').val(item.directory || '');
        widget.find('#edit-language').val(item.languageId || 1);
        widget.find('#edit-excluded').val(item.excludedSeasons || '');
        
        console.log('Form populated with values:', {
            url: item.url,
            directory: item.directory,
            languageId: item.languageId,
            excludedSeasons: item.excludedSeasons
        });
        
        widget.find('.edit-subscription-form').slideDown(300);
        widget.find('#edit-folder').focus();
    }

    hideEditForm(widget) {
        widget.find('.edit-subscription-form').slideUp(300);
        SubscriptionsWidget.currentEditItem = null;
    }

    clearForm(widget) {
        widget.find('#sub-url, #sub-folder, #sub-excluded').val('');
        widget.find('#sub-language').val('1');
        widget.find('input[name="quality"][value="720p"]').prop('checked', true);
        widget.find('#auto-start').prop('checked', false);
    }

    validateForm(widget) {
        const url = widget.find('#sub-url').val().trim();
        const isValid = url && url.includes('aniworld.to');
        widget.find('.save-subscription-btn').prop('disabled', !isValid);
        return isValid;
    }

    updateSearchUI(widget) {
        const hasSearch = this.searchTerm.length > 0;
        widget.find('.clear-search').toggle(hasSearch);
    }

    saveSubscription(widget) {
        if (!this.validateForm(widget)) return;

        let directory = widget.find('#sub-folder').val().trim();
        // Remove leading slashes to prevent them being converted to underscores
        if (directory.startsWith('/') || directory.startsWith('\\')) {
            directory = directory.substring(1);
        }
        
        const data = {
            url: widget.find('#sub-url').val().trim(),
            languageId: parseInt(widget.find('#sub-language').val()),
            directory: directory,
            excludedSeasons: widget.find('#sub-excluded').val().trim(),
            quality: widget.find('input[name="quality"]:checked').val(),
            autoStart: widget.find('#auto-start').prop('checked')
        };

        sendPacket('subscribe', 'autoloader', data);
        this.hideAddForm(widget);
        
        // Show success feedback
        this.showNotification('Subscription added successfully!', 'success');
    }

    saveEdit(widget) {
        console.log('saveEdit called');
        
        if (!SubscriptionsWidget.currentEditItem) {
            console.error('No currentEditItem found');
            return;
        }
        
        console.log('Current edit item:', SubscriptionsWidget.currentEditItem);

        let directory = widget.find('#edit-folder').val().trim();
        // Remove leading slashes to prevent them being converted to underscores
        if (directory.startsWith('/') || directory.startsWith('\\')) {
            directory = directory.substring(1);
        }
        
        const data = {
            id: SubscriptionsWidget.currentEditItem.id,
            languageId: parseInt(widget.find('#edit-language').val()),
            directory: directory,
            excludedSeasons: widget.find('#edit-excluded').val().trim()
        };
        
        console.log('Sending modification data:', data);

        // Check WebSocket connection
        if (!ws || ws.readyState !== WebSocket.OPEN) {
            console.error('WebSocket connection not ready:', ws ? ws.readyState : 'ws is null');
            this.showNotification('Connection not ready. Please try again.', 'error');
            return;
        }
        
        try {
            sendPacket('modify', 'autoloader', data);
            console.log('Packet sent successfully');
            this.hideEditForm(widget);
            
            // Show success feedback
            this.showNotification('Subscription updated successfully!', 'success');
        } catch (error) {
            console.error('Error sending packet:', error);
            this.showNotification('Failed to update subscription: ' + error.message, 'error');
        }
    }

    addSubscriptionCard(item) {
        const widget = $('[widget-name="SubscriptionsWidget"]').first();
        this.addSubscriptionCardToWidget(widget, item);
    }

    addSubscriptionCardToWidget(widget, item) {
        const container = widget.find('.subscriptions-grid');
        
        // Remove existing card
        container.find(`[data-id="${item.id}"]`).remove();

        const status = this.getSubscriptionStatus(item);
        const lastScan = this.formatLastScan(item.lastScan);
        
        const card = $(`
            <div class="subscription-card" data-id="${item.id}" data-status="${status}">
                <div class="card-header">
                    <div class="title-section">
                        <h3 class="anime-title" title="${item.title}">${item.title}</h3>
                        <span class="status-badge status-${status}">${status}</span>
                    </div>
                    <div class="card-actions">
                        <button class="action-btn scan-btn" title="Scan for new episodes">
                            <i class="fa fa-search"></i>
                        </button>
                        <button class="action-btn download-btn" title="Download now">
                            <i class="fa fa-download"></i>
                        </button>
                        <button class="action-btn pause-btn" title="Pause/Resume">
                            <i class="fa ${status === 'paused' ? 'fa-play' : 'fa-pause'}"></i>
                        </button>
                        <button class="action-btn settings-btn" title="Settings">
                            <i class="fa fa-cog"></i>
                        </button>
                        <button class="action-btn delete-btn" title="Unsubscribe">
                            <i class="fa fa-trash"></i>
                        </button>
                    </div>
                </div>
                
                <div class="card-content">
                    <div class="info-grid">
                        <div class="info-item">
                            <i class="fa fa-folder"></i>
                            <span class="label">Folder:</span>
                            <span class="value folder-path" title="${item.directory || 'Auto'}">${item.directory || 'Auto'}</span>
                        </div>
                        <div class="info-item">
                            <i class="fa fa-download"></i>
                            <span class="label">Unloaded:</span>
                            <span class="value episodes-count">${item.unloaded || 0} episodes</span>
                        </div>
                        <div class="info-item">
                            <i class="fa fa-clock"></i>
                            <span class="label">Last Scan:</span>
                            <span class="value">${lastScan}</span>
                        </div>
                        <div class="info-item">
                            <i class="fa fa-link"></i>
                            <span class="label">Source:</span>
                            <a href="${item.url}" target="_blank" class="value link">Aniworld</a>
                        </div>
                    </div>
                    
                    ${item.excludedSeasons ? `
                    <div class="excluded-seasons">
                        <i class="fa fa-ban"></i>
                        <span>Excluded seasons: ${item.excludedSeasons}</span>
                    </div>
                    ` : ''}
                </div>
                
                <div class="card-footer">
                    <div class="progress-section">
                        <div class="progress-bar">
                            <div class="progress-fill" style="width: ${this.calculateProgress(item)}%"></div>
                        </div>
                        <span class="progress-text">${this.getProgressText(item)}</span>
                    </div>
                </div>
            </div>
        `);

        // Bind card events
        this.bindCardEvents(card, item);
        
        container.append(card);
        this.updateEmptyState(widget);
        this.updateStats(widget);
    }

    bindCardEvents(card, item) {
        card.find('.scan-btn').click(() => {
            const scanBtn = card.find('.scan-btn');
            const originalIcon = scanBtn.find('i').attr('class');
            
            // Show loading state
            scanBtn.prop('disabled', true);
            scanBtn.find('i').attr('class', 'fa fa-spinner fa-spin');
            
            sendPacket("scan", "autoloader", { id: item.id });
            this.showNotification('Scanning for new episodes...', 'info');
            
            // Reset button after 3 seconds
            setTimeout(() => {
                scanBtn.prop('disabled', false);
                scanBtn.find('i').attr('class', originalIcon);
            }, 3000);
        });

        card.find('.download-btn').click(() => {
            sendPacket("runDownload", "autoloader", { id: item.id });
            this.showNotification('Download started', 'success');
        });

        card.find('.pause-btn').click(() => {
            const pauseBtn = card.find('.pause-btn');
            const action = item.paused ? 'resume' : 'pause';
            console.log('Pause button clicked. Current paused state:', item.paused, 'Action:', action);
            
            // Optimistically update the button icon
            const newIcon = item.paused ? 'fa-pause' : 'fa-play';
            pauseBtn.find('i').attr('class', `fa ${newIcon}`);
            
            // Update the item's paused state locally for immediate feedback
            item.paused = !item.paused;
            
            sendPacket(action, "autoloader", { id: item.id });
            
            const actionText = action === 'pause' ? 'paused' : 'resumed';
            this.showNotification(`Subscription ${actionText}`, 'info');
        });

        card.find('.delete-btn').click(() => {
            this.confirmDelete(item);
        });

        card.find('.settings-btn').click(() => {
            const widget = $('[widget-name="SubscriptionsWidget"]');
            // Create a temporary instance to call the method, or call it statically
            const instance = new SubscriptionsWidget();
            instance.showEditForm(widget, item);
        });
    }

    confirmDelete(item) {
        if (confirm(`Are you sure you want to unsubscribe from "${item.title}"?`)) {
            sendPacket("unsubscribe", "autoloader", { id: item.id });
            this.showNotification('Subscription removed', 'info');
        }
    }

    filterAndSort(widget) {
        const container = widget.find('.subscriptions-grid');
        const cards = container.find('.subscription-card').get();
        
        // Filter
        const filteredCards = cards.filter(card => {
            const $card = $(card);
            const title = $card.find('.anime-title').text().toLowerCase();
            const status = $card.attr('data-status');
            
            const matchesSearch = !this.searchTerm || title.includes(this.searchTerm);
            const matchesFilter = this.filterStatus === 'all' || status === this.filterStatus;
            
            return matchesSearch && matchesFilter;
        });

        // Sort
        filteredCards.sort((a, b) => {
            const $a = $(a), $b = $(b);
            let valueA, valueB;
            
            switch(this.sortBy) {
                case 'title':
                    valueA = $a.find('.anime-title').text();
                    valueB = $b.find('.anime-title').text();
                    break;
                case 'unloaded':
                    valueA = parseInt($a.find('.episodes-count').text()) || 0;
                    valueB = parseInt($b.find('.episodes-count').text()) || 0;
                    break;
                default:
                    valueA = $a.attr('data-' + this.sortBy) || '';
                    valueB = $b.attr('data-' + this.sortBy) || '';
            }
            
            const comparison = valueA < valueB ? -1 : valueA > valueB ? 1 : 0;
            return this.sortOrder === 'asc' ? comparison : -comparison;
        });

        // Hide all cards and show filtered/sorted ones
        container.find('.subscription-card').hide();
        filteredCards.forEach(card => $(card).show());
        
        this.updateEmptyState(widget);
    }

    getSubscriptionStatus(item) {
        if (item.error) return 'error';
        if (item.paused) return 'paused';
        if (item.unloaded > 0) return 'pending';
        return 'active';
    }

    formatLastScan(timestamp) {
        if (!timestamp) return 'Never';
        const date = new Date(timestamp);
        const now = new Date();
        const diffMs = now - date;
        const diffMins = Math.floor(diffMs / 60000);
        
        if (diffMins < 1) return 'Just now';
        if (diffMins < 60) return `${diffMins}m ago`;
        if (diffMins < 1440) return `${Math.floor(diffMins / 60)}h ago`;
        return date.toLocaleDateString();
    }

    calculateProgress(item) {
        // Mock progress calculation - replace with actual logic
        return Math.max(0, 100 - (item.unloaded || 0) * 10);
    }

    getProgressText(item) {
        const unloaded = item.unloaded || 0;
        return unloaded > 0 ? `${unloaded} episodes pending` : 'Up to date';
    }

    updateEmptyState(widget) {
        const container = widget.find('.subscriptions-grid');
        const visibleCards = container.find('.subscription-card:visible').length;
        widget.find('.empty-state').toggle(visibleCards === 0);
    }

    updateStats(widget) {
        const cards = widget.find('.subscription-card');
        const activeCount = cards.filter('[data-status="active"]').length;
        const pendingCount = cards.filter('[data-status="pending"]').length;
        
        widget.find('.active-count').text(activeCount);
        widget.find('.pending-count').text(pendingCount);
    }

    showNotification(message, type = 'info') {
        // Use existing notification system or create a simple one
        console.log(`${type.toUpperCase()}: ${message}`);
    }

    requestData() {
        sendPacket('getData', 'autoloader');
    }

    static onWSResponse(cmd, content) {
        // Find all subscription widgets (could be in stream view and/or grid view)
        const widgets = $('[widget-name="SubscriptionsWidget"]');
        if (!widgets.length) return;
        
        const instance = new SubscriptionsWidget();
        
        // Process each widget separately to avoid double counting
        widgets.each(function() {
            const widget = $(this);
            
            switch (cmd) {
                case "syn":
                    // Clear existing cards first to avoid duplicates
                    widget.find('.subscription-card').remove();
                    content.items?.forEach(item => {
                        instance.addSubscriptionCardToWidget(widget, item);
                    });
                    break;
                case "del":
                    widget.find(`[data-id="${content.id}"]`).fadeOut(300, function() {
                        $(this).remove();
                        instance.updateEmptyState(widget);
                        instance.updateStats(widget);
                    });
                    break;
                case "update":
                    instance.addSubscriptionCardToWidget(widget, content);
                    instance.showNotification('Subscription updated', 'success');
                    break;
            }
        });
    }
}