class SubscriptionsWidget extends BaseWidget {
    static instanceContextMenus = new Map();

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
        this.instanceId = 'subscriptions-' + Date.now() + '-' + Math.random().toString(36).substr(2, 9);
    }

    onDestroy() {
        // Clean up context menu when widget is destroyed
        const contextMenu = SubscriptionsWidget.instanceContextMenus.get(this.instanceId);
        if (contextMenu) {
            contextMenu.remove();
            SubscriptionsWidget.instanceContextMenus.delete(this.instanceId);
        }
    }

    createContent() {
        let widget = $(`
        <div class="widget subscriptions-widget scrollbar-on-hover custom-scrollbar" widget-name="SubscriptionsWidget" widget-id="${this.instanceId}">
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
                        <input type="text" class="sub-url" placeholder="https://aniworld.to/anime/..." required>
                        <div class="input-hint">Enter the full URL to the anime series</div>
                    </div>

                    <div class="form-group">
                        <label for="sub-language">
                            <i class="fa fa-language"></i>
                            Preferred Language
                        </label>
                        <select class="sub-language">
                            <option value="1">German (Dub)</option>
                            <option value="2">German (Sub)</option>
                            <option value="3">English (Dub)</option>
                            <option value="4">English (Sub)</option>
                            <option value="5">Japanese (Sub)</option>
                        </select>
                    </div>
                    
                    <div class="form-group">
                        <label for="sub-target-folder">
                            <i class="fa fa-hdd"></i>
                            Target Folder
                        </label>
                        <select class="sub-target-folder targetfolder">
                        </select>
                    </div>
                    
                    <div class="form-group">
                        <label for="sub-subfolder">
                            <i class="fa fa-folder"></i>
                            Subfolder
                        </label>
                        <div class="custom-dropdown">
                            <input type="text" class="sub-subfolder" placeholder="Anime/Series Name" list="sub-subfolder-list" style="width: 100%;">
                            <datalist class="sub-subfolder-list">
                            </datalist>
                        </div>
                        <div class="input-hint">Leave empty to auto-detect from title, or select from existing folders</div>
                    </div>
                    
                    <div class="form-group">
                        <label for="sub-excluded">
                            <i class="fa fa-ban"></i>
                            Excluded Seasons
                        </label>
                        <input type="text" class="sub-excluded" placeholder="1,3,5">
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
                            <input type="checkbox" class="auto-start">
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
                        <input type="text" class="edit-url" readonly disabled>
                        <div class="input-hint">URL cannot be changed after subscription</div>
                    </div>

                    <div class="form-group">
                        <label for="edit-language">
                            <i class="fa fa-language"></i>
                            Preferred Language
                        </label>
                        <select class="edit-language">
                            <option value="1">German (Dub)</option>
                            <option value="2">German (Sub)</option>
                            <option value="3">English (Dub)</option>
                            <option value="4">English (Sub)</option>
                            <option value="5">Japanese (Sub)</option>
                        </select>
                        <div class="input-hint">Changing language will affect future downloads</div>
                    </div>
                    
                    <div class="form-group">
                        <label for="edit-target-folder">
                            <i class="fa fa-hdd"></i>
                            Target Folder
                        </label>
                        <select class="edit-target-folder targetfolder">
                        </select>
                    </div>
                    
                    <div class="form-group">
                        <label for="edit-subfolder">
                            <i class="fa fa-folder"></i>
                            Subfolder
                        </label>
                        <div class="custom-dropdown">
                            <input type="text" class="edit-subfolder" placeholder="Anime/Series Name" list="edit-subfolder-list" style="width: 100%;">
                            <datalist class="edit-subfolder-list">
                            </datalist>
                        </div>
                        <div class="input-hint">Leave empty to auto-detect from title, or select from existing folders</div>
                    </div>
                    
                    <div class="form-group">
                        <label for="edit-excluded">
                            <i class="fa fa-ban"></i>
                            Excluded Seasons
                        </label>
                        <input type="text" class="edit-excluded" placeholder="1,3,5">
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

        // Create context menu container
        const contextMenu = $(`
            <div class="subscription-context-menu subscription-context-menu-${this.instanceId}" style="display: none;">
                <div class="context-menu-item" data-action="scan">
                    <i class="fa fa-rotate"></i>
                    <span>Scan for Episodes</span>
                </div>
                <div class="context-menu-item" data-action="download">
                    <i class="fa fa-download"></i>
                    <span>Download Now</span>
                </div>
                <div class="context-menu-item" data-action="pause">
                    <i class="fa fa-pause"></i>
                    <span>Pause</span>
                </div>
                <div class="context-menu-item" data-action="resume">
                    <i class="fa fa-play"></i>
                    <span>Resume</span>
                </div>
                <div class="context-menu-separator"></div>
                <div class="context-menu-item" data-action="settings">
                    <i class="fa fa-cog"></i>
                    <span>Settings</span>
                </div>
                <div class="context-menu-item" data-action="copyUrl">
                    <i class="fas fa-copy"></i>
                    <span>Copy URL</span>
                </div>
                <div class="context-menu-item" data-action="copyFolder">
                    <i class="fas fa-folder-open"></i>
                    <span>Copy Folder Path</span>
                </div>
                <div class="context-menu-separator"></div>
                <div class="context-menu-item danger" data-action="delete">
                    <i class="fa fa-trash"></i>
                    <span>Unsubscribe</span>
                </div>
            </div>
        `);
        
        $('body').append(contextMenu);
        
        // Store reference to context menu for this instance
        SubscriptionsWidget.instanceContextMenus.set(this.instanceId, contextMenu);
        
        // Hide context menu when clicking elsewhere
        $(document).on('click', function() {
            $('.subscription-context-menu').hide();
        });

        this.bindEvents(widget);
        this.populateTargetFolders(widget);
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
        widget.find('.sub-url').on('input', function() {
            self.validateForm(widget);
        });

        // Target folder change handlers
        widget.find('.sub-target-folder').on('change', function() {
            const selection = $(this).val();
            widget.find('.sub-subfolder').val('');
            self.fetchSubfolders(selection, widget.find('.sub-subfolder-list'));
        });
        
        widget.find('.edit-target-folder').on('change', function() {
            const selection = $(this).val();
            widget.find('.edit-subfolder').val('');
            self.fetchSubfolders(selection, widget.find('.edit-subfolder-list'));
        });
    }

    showAddForm(widget) {
        this.populateTargetFolders(widget);
        widget.find('.add-subscription-form').slideDown(300);
        widget.find('.sub-url').focus();
    }

    hideAddForm(widget) {
        widget.find('.add-subscription-form').slideUp(300);
        this.clearForm(widget);
    }

    showEditForm(widget, item) {
        SubscriptionsWidget.currentEditItem = item;
        
        this.populateTargetFolders(widget);
        
        // Parse the directory path to separate target folder and subfolder
        const directory = item.directory || '';
        let targetFolder = '';
        let subfolder = '';
        
        if (directory) {
            // Find the target folder that matches the beginning of the directory
            for (let folder of targetFolders) {
                if (directory.startsWith(folder.displayName + '/') || directory === folder.displayName) {
                    targetFolder = folder.identifier;
                    subfolder = directory.substring(folder.displayName.length + 1);
                    break;
                }
            }
            // If no match found, treat the whole thing as a subfolder
            if (!targetFolder && targetFolders.length > 0) {
                targetFolder = targetFolders[0].identifier;
                subfolder = directory;
            }
        }
        
        // Populate form with current values
        widget.find('.edit-url').val(item.url);
        widget.find('.edit-target-folder').val(targetFolder);
        widget.find('.edit-subfolder').val(subfolder);
        widget.find('.edit-language').val(item.languageId || 1);
        widget.find('.edit-excluded').val(item.excludedSeasons || '');
        
        // Fetch subfolders for the selected target folder
        if (targetFolder) {
            this.fetchSubfolders(targetFolder, widget.find('.edit-subfolder-list'));
        }
        
        widget.find('.edit-subscription-form').slideDown(300);
        widget.find('.edit-subfolder').focus();
    }

    hideEditForm(widget) {
        widget.find('.edit-subscription-form').slideUp(300);
        SubscriptionsWidget.currentEditItem = null;
    }

    clearForm(widget) {
        widget.find('.sub-url, .sub-subfolder, .sub-excluded').val('');
        widget.find('.sub-language').val('1');
        widget.find('.sub-target-folder').prop('selectedIndex', 0);
        widget.find('input[name="quality"][value="720p"]').prop('checked', true);
        widget.find('.auto-start').prop('checked', false);
    }

    validateForm(widget) {
        const url = widget.find('.sub-url').val().trim();
        const isValid = url && url.includes('aniworld.to');
        widget.find('.save-subscription-btn').prop('disabled', !isValid);
        return isValid;
    }

    updateSearchUI(widget) {
        const hasSearch = this.searchTerm.length > 0;
        widget.find('.clear-search').toggle(hasSearch);
    }

    populateTargetFolders(widget) {
        // Populate target folders in both forms
        const addTargetSelect = widget.find('.sub-target-folder');
        const editTargetSelect = widget.find('.edit-target-folder');
        
        // Clear existing options
        addTargetSelect.empty();
        editTargetSelect.empty();
        
        // Add options from global targetFolders
        for (let folder of targetFolders) {
            const option = $('<option>').text(folder.displayName).attr('value', folder.identifier);
            addTargetSelect.append(option.clone());
            editTargetSelect.append(option.clone());
        }
    }

    fetchSubfolders(targetPath, $datalistElement) {
        if (!targetPath) return;
        
        // Clear existing options
        $datalistElement.empty();
        
        // Find the target folder display name
        let targetFolderName = '';
        for (let folder of targetFolders) {
            if (folder.identifier === targetPath) {
                targetFolderName = folder.displayName;
                break;
            }
        }
        
        if (!targetFolderName) return;
        
        // Make REST API call using the display name (which is what the backend expects)
        $.ajax({
            url: `/api/subfolders/${encodeURIComponent(targetPath)}`,
            method: 'GET',
            success: function(subfolders) {
                // Check if subfolders is an array and has items
                if (Array.isArray(subfolders) && subfolders.length > 0) {
                    // Add each subfolder as an option to the datalist
                    for (let folder of subfolders) {
                        let option = $('<option>');
                        option.attr('value', folder);
                        $datalistElement.append(option);
                    }
                }
                // If no subfolders or not an array, just leave the datalist empty
            },
            error: function(xhr, status, error) {
                console.warn('Failed to fetch subfolders:', error);
                
                // Only show error popup for actual errors (not 204 No Content)
                if (xhr.status !== 204) {
                    console.error('Error fetching subfolders for target:', targetPath, error);
                }
            }
        });
    }

    saveSubscription(widget) {
        if (!this.validateForm(widget)) return;

        const targetFolderSelect = widget.find('.sub-target-folder').val();
        const subfolder = widget.find('.sub-subfolder').val().trim();
        
        const data = {
            url: widget.find('.sub-url').val().trim(),
            languageId: parseInt(widget.find('.sub-language').val()),
            directory: subfolder,
            excludedSeasons: widget.find('.sub-excluded').val().trim(),
            quality: widget.find('input[name="quality"]:checked').val(),
            autoStart: widget.find('.auto-start').prop('checked')
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

        const subfolder = widget.find('.edit-subfolder').val().trim();
        
        const data = {
            id: SubscriptionsWidget.currentEditItem.id,
            languageId: parseInt(widget.find('.edit-language').val()),
            directory: subfolder,
            excludedSeasons: widget.find('.edit-excluded').val().trim()
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
                            <span class="value episodes-count">${item.unloaded || 0}</span>
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
                    <div class="status-div">
                        <span class="status-badge status-${status}">${item.status || status}</span>
                    </div>
                </div>
            </div>
        `);

        // Add right-click context menu to the card
        card.css('cursor', 'context-menu')
            .on('contextmenu', (e) => {
                e.preventDefault();
                const widgetElement = card.closest('[widget-name="SubscriptionsWidget"]');
                const widgetId = widgetElement.attr('widget-id');
                this.showContextMenu(e, item, widgetId);
            });
        
        container.append(card);
        this.updateEmptyState(widget);
        this.updateStats(widget);
    }

    showContextMenu(event, item, widgetId) {
        // Find the specific context menu for this widget instance
        const contextMenu = widgetId ? 
            $(`.subscription-context-menu-${widgetId}`) : 
            $('.subscription-context-menu').first();
        
        if (contextMenu.length === 0) {
            return;
        }
        
        // Update menu item states based on subscription status
        const pauseItem = contextMenu.find('[data-action="pause"]');
        const resumeItem = contextMenu.find('[data-action="resume"]');
        
        // Show/hide pause/resume based on current state
        if (item.paused) {
            pauseItem.hide();
            resumeItem.show();
        } else {
            pauseItem.show();
            resumeItem.hide();
        }
        
        // Remove previous click handlers
        contextMenu.off('click', '.context-menu-item');
        
        // Add click handlers
        contextMenu.on('click', '.context-menu-item', (e) => {
            e.stopPropagation();
            
            const action = $(e.currentTarget).data('action');
            
            switch(action) {
                case 'scan':
                    this.scanSubscription(item);
                    break;
                case 'download':
                    this.downloadSubscription(item);
                    break;
                case 'pause':
                    this.pauseSubscription(item);
                    break;
                case 'resume':
                    this.resumeSubscription(item);
                    break;
                case 'settings':
                    this.showSettingsForItem(item);
                    break;
                case 'copyUrl':
                    navigator.clipboard.writeText(item.url).then(() => {
                        this.showNotification('URL copied to clipboard', 'success');
                    }).catch(err => {
                        console.error('Failed to copy URL: ', err);
                        // Fallback for older browsers
                        const textArea = document.createElement('textarea');
                        textArea.value = item.url;
                        document.body.appendChild(textArea);
                        textArea.select();
                        document.execCommand('copy');
                        document.body.removeChild(textArea);
                        this.showNotification('URL copied to clipboard', 'success');
                    });
                    break;
                case 'copyFolder':
                    const folderPath = item.directory || 'Auto';
                    navigator.clipboard.writeText(folderPath).then(() => {
                        this.showNotification('Folder path copied to clipboard', 'success');
                    }).catch(err => {
                        console.error('Failed to copy folder path: ', err);
                        // Fallback for older browsers
                        const textArea = document.createElement('textarea');
                        textArea.value = folderPath;
                        document.body.appendChild(textArea);
                        textArea.select();
                        document.execCommand('copy');
                        document.body.removeChild(textArea);
                        this.showNotification('Folder path copied to clipboard', 'success');
                    });
                    break;
                case 'delete':
                    this.confirmDelete(item);
                    break;
            }
            
            contextMenu.hide();
        });
        
        // Hide all other context menus first
        $('.subscription-context-menu').hide();
        
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
            $('.subscription-widget').first();
        
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

    scanSubscription(item) {
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
    }

    downloadSubscription(item) {
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
    }

    pauseSubscription(item) {
        ApiClient.pauseSubscription(item.id)
            .then(data => {
                this.showNotification('Subscription paused', 'info');
                item.paused = true;
            })
            .catch(error => {
                console.error('Error pausing subscription:', error);
                this.showNotification('Failed to pause: ' + error.message, 'error');
            });
    }

    resumeSubscription(item) {
        ApiClient.resumeSubscription(item.id)
            .then(data => {
                this.showNotification('Subscription resumed', 'info');
                item.paused = false;
            })
            .catch(error => {
                console.error('Error resuming subscription:', error);
                this.showNotification('Failed to resume: ' + error.message, 'error');
            });
    }

    showSettingsForItem(item) {
        const widget = $('[widget-name="SubscriptionsWidget"]');
        this.showEditForm(widget, item);
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