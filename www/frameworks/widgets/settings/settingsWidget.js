class SettingsWidget extends BaseWidget {
    static currentSettings = {};
    static isLoading = false;

    constructor(name = "Settings") {
        super(name);
        this.settingsConfig = [
            {
                key: 'VOE_THREADS',
                label: 'VOE Threads',
                type: 'number',
                min: 1,
                max: 32,
                description: 'Number of parallel threads for VOE downloads',
                icon: 'fas fa-download'
            },
            {
                key: 'PARALLEL_DOWNLOADS',
                label: 'Parallel Downloads',
                type: 'number',
                min: 1,
                max: 16,
                description: 'Maximum number of simultaneous downloads',
                icon: 'fas fa-stream'
            },
            {
                key: 'RETRY_MINUTES',
                label: 'Retry Minutes',
                type: 'number',
                min: 1,
                max: 1440,
                description: 'Minutes to wait before retrying failed downloads',
                icon: 'fas fa-redo'
            }
        ];
    }

    render() {
        const settingsCards = this.settingsConfig.map(config => this.#renderSettingCard(config)).join('');
        
        let widget = $(`
        <div class="widget scrollbar-on-hover custom-scrollbar" widget-name="SettingsWidget">
            <div class="settings-header">
                <div class="settings-title">
                    <i class="fas fa-cog"></i>
                    <h1 class="widget-handle">System Settings</h1>
                </div>
                <div class="settings-actions">
                    <button class="reset-settings-btn" title="Reset to defaults">
                        <i class="fas fa-undo"></i>
                        Reset
                    </button>
                    <button class="save-settings-btn" title="Save changes">
                        <i class="fas fa-save"></i>
                        <span class="btn-text">Save Changes</span>
                        <div class="loading-spinner hidden">
                            <i class="fas fa-spinner fa-spin"></i>
                        </div>
                    </button>
                </div>
            </div>
            <div class="settings-container">
                ${settingsCards}
            </div>
            <div class="settings-footer">
                <div class="save-status hidden">
                    <i class="status-icon"></i>
                    <span class="status-text"></span>
                </div>
            </div>
        </div>
        `);

        this.#initSettingValues(widget);
        this.#initEvents(widget);
        return widget.get(0);
    }

    #renderSettingCard(config) {
        return `
        <div class="setting-card" setting="${config.key}">
            <div class="setting-header">
                <div class="setting-icon">
                    <i class="${config.icon}"></i>
                </div>
                <div class="setting-info">
                    <label class="setting-label">${config.label}</label>
                    <p class="setting-description">${config.description}</p>
                </div>
            </div>
            <div class="setting-input-container">
                <input 
                    class="setting-input" 
                    type="${config.type}" 
                    min="${config.min}" 
                    max="${config.max || ''}"
                    placeholder="Enter ${config.label.toLowerCase()}"
                    data-original-value=""
                >
                <div class="input-validation hidden">
                    <i class="fas fa-exclamation-triangle"></i>
                    <span class="validation-message"></span>
                </div>
            </div>
        </div>
        `;
    }

    #initSettingValues(widget) {
        for (let key in SettingsWidget.currentSettings) {
            let value = SettingsWidget.currentSettings[key];
            let input = widget.find('[setting="' + key + '"] .setting-input');
            input.val(value);
            input.attr('data-original-value', value);
        }
        this.#updateSaveButtonState(widget);
    }

    #initEvents(widget) {
        const self = this;

        // Save button click handler
        widget.find('.save-settings-btn').click(function () {
            if (SettingsWidget.isLoading) return;
            
            if (!self.#validateAllInputs(widget)) {
                self.#showStatus(widget, 'error', 'Please fix validation errors before saving');
                return;
            }

            self.#saveSettings(widget);
        });

        // Reset button click handler
        widget.find('.reset-settings-btn').click(function () {
            self.#resetSettings(widget);
        });

        // Input change handlers
        widget.find('.setting-input').on('input', function () {
            const input = $(this);
            self.#validateInput(input);
            self.#updateSaveButtonState(widget);
        });

        // Input blur handlers for final validation
        widget.find('.setting-input').on('blur', function () {
            const input = $(this);
            self.#validateInput(input, true);
        });
    }

    #validateInput(input, showErrors = false) {
        const value = parseFloat(input.val());
        const min = parseFloat(input.attr('min'));
        const max = parseFloat(input.attr('max'));
        const card = input.closest('.setting-card');
        const validation = card.find('.input-validation');
        
        let isValid = true;
        let message = '';

        if (isNaN(value) || value < min) {
            isValid = false;
            message = `Value must be at least ${min}`;
        } else if (max && value > max) {
            isValid = false;
            message = `Value cannot exceed ${max}`;
        }

        if (isValid) {
            card.removeClass('invalid');
            validation.addClass('hidden');
            input.removeClass('error');
        } else if (showErrors) {
            card.addClass('invalid');
            validation.removeClass('hidden').find('.validation-message').text(message);
            input.addClass('error');
        }

        return isValid;
    }

    #validateAllInputs(widget) {
        let allValid = true;
        widget.find('.setting-input').each((_, input) => {
            if (!this.#validateInput($(input), true)) {
                allValid = false;
            }
        });
        return allValid;
    }

    #updateSaveButtonState(widget) {
        const saveBtn = widget.find('.save-settings-btn');
        let hasChanges = false;

        widget.find('.setting-input').each((_, input) => {
            const $input = $(input);
            if ($input.val() !== $input.attr('data-original-value')) {
                hasChanges = true;
                return false;
            }
        });

        saveBtn.toggleClass('has-changes', hasChanges);
        saveBtn.prop('disabled', !hasChanges || SettingsWidget.isLoading);
    }

    #saveSettings(widget) {
        SettingsWidget.isLoading = true;
        const saveBtn = widget.find('.save-settings-btn');
        
        saveBtn.find('.btn-text').text('Saving...');
        saveBtn.find('.loading-spinner').removeClass('hidden');
        saveBtn.addClass('loading');

        let settings = widget.find('[setting]');
        let settingPacket = [];
        
        for (let s of settings) {
            let setting = $(s);
            let input = setting.find('.setting-input');
            settingPacket.push({ 
                key: setting.attr('setting'), 
                value: input.val() 
            });
        }

        sendPacket("setting", "default", {
            "settings": settingPacket
        });

        // Show temporary success (will be updated by server response)
        setTimeout(() => {
            this.#showStatus(widget, 'success', 'Settings saved successfully');
            this.#resetSaveButton(widget);
            
            // Update original values
            widget.find('.setting-input').each((_, input) => {
                const $input = $(input);
                $input.attr('data-original-value', $input.val());
            });
            
            this.#updateSaveButtonState(widget);
        }, 1000);
    }

    #resetSettings(widget) {
        widget.find('.setting-input').each((_, input) => {
            const $input = $(input);
            $input.val($input.attr('data-original-value'));
        });
        
        widget.find('.setting-card').removeClass('invalid');
        widget.find('.input-validation').addClass('hidden');
        widget.find('.setting-input').removeClass('error');
        
        this.#updateSaveButtonState(widget);
        this.#showStatus(widget, 'info', 'Settings reset to previous values');
    }

    #resetSaveButton(widget) {
        SettingsWidget.isLoading = false;
        const saveBtn = widget.find('.save-settings-btn');
        
        saveBtn.find('.btn-text').text('Save Changes');
        saveBtn.find('.loading-spinner').addClass('hidden');
        saveBtn.removeClass('loading');
    }

    #showStatus(widget, type, message) {
        const status = widget.find('.save-status');
        const icon = status.find('.status-icon');
        const text = status.find('.status-text');
        
        status.removeClass('success error info').addClass(type);
        text.text(message);
        
        switch(type) {
            case 'success':
                icon.attr('class', 'status-icon fas fa-check-circle');
                break;
            case 'error':
                icon.attr('class', 'status-icon fas fa-exclamation-circle');
                break;
            case 'info':
                icon.attr('class', 'status-icon fas fa-info-circle');
                break;
        }
        
        status.removeClass('hidden');
        
        setTimeout(() => {
            status.addClass('hidden');
        }, 3000);
    }

    static updateSettings(settings){
        let widget = $('[widget-name="SettingsWidget"]');
        for (let settingItem of settings) {
            let input = widget.find('[setting="' + settingItem.key + '"] .setting-input');
            input.val(settingItem.val);
            input.attr('data-original-value', settingItem.val);
            SettingsWidget.currentSettings[settingItem.key] = settingItem.val;
        }
        
        // Update save button state after updating settings
        if (widget.length > 0) {
            const instance = widget.data('widget-instance');
            if (instance) {
                instance.#updateSaveButtonState(widget);
            }
        }
    }
}