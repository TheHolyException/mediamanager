class SubscriptionsWidget extends BaseWidget {
    constructor(options = {}) {
        super({
            type: 'subscriptions',
            width: 2,
            height: 2,
            ...options
        });

        this.searchTerm = '';
        this.filterStatus = 'all';
        this.sortBy = 'title';
        this.sortOrder = 'asc';
    }

    createContent() {
        let widget = $(`
        <div class="widget subscriptions-widget scrollbar-on-hover custom-scrollbar" widget-name="SubscriptionsWidget">
            <div class="widget-header">
                <div class="widget-title">
                    <i class="fas fa-bell"></i>
                    <h1 class="widget-handle">Subscriptions</h1>
                </div>
                <div class="widget-stats">
                    <div class="autoloader-status-info" style="display: none;">
                        <i class="fa fa-exclamation-triangle"></i>
                        <span>Autoloader Disabled</span>
                    </div>
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
                        <option value="scanning">Scanning</option>
                        <option value="downloading">Downloading</option>
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
                    <button class="cancel-edit-btn cancel-btn">Cancel</button>
                    <button class="save-edit-btn primary-btn">
                        <i class="fa fa-save"></i>
                        Save Changes
                    </button>
                </div>
            </div>

            <div class="subscriptions-container scrollable-content">
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
        widget.find('.add-subscription-btn').click(function() {
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
        SubscriptionsWidget.currentEditItem = item;
        
        // Populate form with current values
        widget.find('#edit-url').val(item.url);
        widget.find('#edit-folder').val(item.directory || '');
        widget.find('#edit-language').val(item.languageId || 1);
        widget.find('#edit-excluded').val(item.excludedSeasons || '');
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

        ApiClient.addSubscription(data)
            .then(subscription => {
                this.hideAddForm(widget);
                this.showNotification('Subscription added successfully!', 'success');
            })
            .catch(error => {
                console.error('Error adding subscription:', error);
                this.showNotification('Failed to add subscription: ' + error.message, 'error');
            });
    }

    saveEdit(widget) {
        if (!SubscriptionsWidget.currentEditItem) {
            console.error('No currentEditItem found');
            return;
        }

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

        ApiClient.updateSubscription(SubscriptionsWidget.currentEditItem.id, data)
            .then(subscription => {
                this.hideEditForm(widget);
                this.showNotification('Subscription updated successfully!', 'success');
            })
            .catch(error => {
                console.error('Error updating subscription:', error);
                this.showNotification('Failed to update subscription: ' + error.message, 'error');
            });
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
                    <div class="card-actions">
                        <button class="action-btn scan-btn" title="Scan for new episodes">
                            <i class="fa fa-rotate"></i>
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
                    <div class="status-div">
                        <span class="status-badge status-${status}">${item.status || status}</span>
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
            
            ApiClient.scanSubscription(item.id)
                .then(data => {
                    this.showNotification('Scanning for new episodes...', 'info');
                    if (data.unloadedEpisodes !== undefined) {
                        this.showNotification(`Scan completed. Found ${data.unloadedEpisodes} unloaded episodes.`, 'success');
                    }
                })
                .catch(error => {
                    console.error('Error scanning subscription:', error);
                    this.showNotification('Failed to scan: ' + error.message, 'error');
                });
            
            // Reset button after 3 seconds
            setTimeout(() => {
                scanBtn.prop('disabled', false);
                scanBtn.find('i').attr('class', originalIcon);
            }, 3000);
        });

        card.find('.download-btn').click(() => {
            ApiClient.downloadSubscription(item.id)
                .then(data => {
                    this.showNotification('Download started', 'success');
                })
                .catch(error => {
                    console.error('Error starting download:', error);
                    // Extract just the error message without the status code prefix
                    let errorMessage = error.message;
                    if (errorMessage.includes(': ')) {
                        errorMessage = errorMessage.split(': ').slice(1).join(': ');
                    }
                    this.showNotification('Failed to start download: ' + errorMessage, 'error');
                });
        });

        card.find('.pause-btn').click(() => {
            const pauseBtn = card.find('.pause-btn');
            const action = item.paused ? 'resume' : 'pause';
            
            // Optimistically update the button icon
            const newIcon = item.paused ? 'fa-pause' : 'fa-play';
            pauseBtn.find('i').attr('class', `fa ${newIcon}`);
            
            // Update the item's paused state locally for immediate feedback
            item.paused = !item.paused;
            
            const apiCall = action === 'pause' ? 
                ApiClient.pauseSubscription(item.id) : 
                ApiClient.resumeSubscription(item.id);
            
            apiCall
                .then(data => {
                    const actionText = action === 'pause' ? 'paused' : 'resumed';
                    this.showNotification(`Subscription ${actionText}`, 'info');
                })
                .catch(error => {
                    console.error(`Error ${action}ing subscription:`, error);
                    this.showNotification(`Failed to ${action}: ` + error.message, 'error');
                    // Revert the local state change on error
                    item.paused = !item.paused;
                });
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
            ApiClient.deleteSubscription(item.id)
                .then(() => {
                    this.showNotification('Subscription removed', 'info');
                })
                .catch(error => {
                    console.error('Error unsubscribing:', error);
                    this.showNotification('Failed to unsubscribe: ' + error.message, 'error');
                });
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
        // Use the status field from the backend if available, otherwise fall back to legacy logic
        if (item.status) {
            return item.status.toLowerCase();
        }
        
        // Legacy logic for backward compatibility
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
        // Empty state functionality removed
        // The widget will show an empty grid when no subscriptions are available
    }

    updateStats(widget) {
        const cards = widget.find('.subscription-card');
        const activeCount = cards.filter('[data-status="active"]').length;
        const pendingCount = cards.filter('[data-status="pending"]').length;
        
        widget.find('.active-count').text(activeCount);
        widget.find('.pending-count').text(pendingCount);
    }

    showNotification(message, type = 'info') {
        // Map notification types to yeti severity levels
        const severityMap = {
            'success': 'ok',
            'info': 'info', 
            'error': 'nok',
            'warning': 'warn'
        };
        
        // Check if yeti is available globally
        if (typeof window.yeti !== 'undefined') {
            window.yeti.show({
                message: message,
                severity: severityMap[type] || 'info',
                time: 5000
            });
        } else if (typeof yeti !== 'undefined') {
            yeti.show({
                message: message,
                severity: severityMap[type] || 'info',
                time: 5000
            });
        } else {
            // Fallback to console logging if yeti is not available
            console.log(`${type.toUpperCase()}: ${message}`);
        }
    }

    /**
     * Gets alternate providers for a specific episode
     * @param {number} animeId - The subscription/anime ID
     * @param {number} seasonId - The season ID
     * @param {number} episodeId - The episode ID
     * @returns {Promise} Promise that resolves to the providers data
     */
    getAlternateProviders(animeId, seasonId, episodeId) {
        return ApiClient.getAlternateProviders(animeId, seasonId, episodeId)
            .catch(error => {
                console.error('Error getting alternate providers:', error);
                this.showNotification('Failed to get alternate providers: ' + error.message, 'error');
                throw error;
            });
    }

    requestData() {
        // Check autoloader status first
        this.checkAutoloaderStatus();
        
        ApiClient.getSubscriptions()
            .then(data => {
                // Process the data
                const widgets = $('[widget-name=\"SubscriptionsWidget\"]');
                widgets.each((index, widgetElement) => {
                    const widget = $(widgetElement);
                    // Clear existing cards first to avoid duplicates
                    widget.find('.subscription-card').remove();
                    data.forEach(item => {
                        this.addSubscriptionCardToWidget(widget, item);
                    });
                });
            })
            .catch(error => {
                console.error('Error fetching subscriptions:', error);
                this.showNotification('Failed to load subscriptions: ' + error.message, 'error');
            });
    }

    checkAutoloaderStatus() {
        fetch('/api/autoloader/status')
            .then(response => {
                if (!response.ok) {
                    // If API fails, show the info box (assume disabled)
                    this.updateAutoloaderStatusInfo(false);
                    return;
                }
                return response.json();
            })
            .then(data => {
                if (data) {
                    this.updateAutoloaderStatusInfo(data.enabled);
                }
            })
            .catch(error => {
                console.warn('Error checking autoloader status:', error);
                // On error, show the info box (safer to assume disabled)
                this.updateAutoloaderStatusInfo(false);
            });
    }

    updateAutoloaderStatusInfo(enabled) {
        const widgets = $('[widget-name="SubscriptionsWidget"]');
        widgets.each((index, widgetElement) => {
            const widget = $(widgetElement);
            const statusInfo = widget.find('.autoloader-status-info');
            
            if (enabled) {
                statusInfo.hide();
            } else {
                statusInfo.show();
            }
        });
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
                case "data-changed":
                    // Handle data-changed notifications from REST API
                    if (content.dataType === "subscriptions") {
                        // Refetch the subscription data
                        instance.requestData();
                    }
                    break;
            }
        });
    }
}