// Make sure the function is available globally
window.openAddSourcePopup = function openAddSourcePopup() {
    // Check dependencies
    if (typeof ModalLightboxPopup === 'undefined') {
        console.error('ModalLightboxPopup is not available');
        alert('Required components are not loaded yet. Please try again in a moment.');
        return;
    }
    
    if (typeof targetFolders === 'undefined') {
        console.error('targetFolders is not available');
        alert('Target folders are not loaded yet. Please try again in a moment.');
        return;
    }
    
    const popup = new ModalLightboxPopup();

    popup.setContentSelectorStyles({
        "": {
            "background": "linear-gradient(135deg, rgba(20, 25, 35, 0.95), rgba(25, 30, 40, 0.95))",
            "backdrop-filter": "blur(20px)",
            "border-radius": "16px",
            "border": "1px solid rgba(255, 255, 255, 0.1)",
            "box-shadow": "0 20px 40px rgba(0, 0, 0, 0.5)",
            "min-width": "min(600px, 95vw)",
            "max-width": "min(800px, 95vw)"
        },
        " .add-download-header": {
            "display": "flex",
            "align-items": "center",
            "gap": "12px",
            "border-bottom": "1px solid rgba(255, 255, 255, 0.1)"
        },
        " .add-download-header h2": {
            "margin": "0",
            "font-size": "24px",
            "font-weight": "600",
            "color": "#ffffff",
            "display": "flex",
            "align-items": "center",
            "gap": "12px"
        },
        " .add-download-header .header-icon": {
            "font-size": "28px",
            "color": "#007bff",
            "background": "rgba(0, 123, 255, 0.1)",
            "padding": "12px",
            "border-radius": "3px"
        },
        " .src-input-container": {
            "display": "flex",
            "flex-direction": "column",
            "gap": "24px"
        },
        " .tab-section": {
            "background": "rgba(255, 255, 255, 0.02)",
            "border-radius": "3px",
            "border": "1px solid rgba(255, 255, 255, 0.05)",
            "overflow": "hidden"
        },
        " .tab-bar": {
            "display": "flex",
            "background": "rgba(255, 255, 255, 0.05)",
            "border-bottom": "1px solid rgba(255, 255, 255, 0.1)"
        },
        " .tab-btn": {
            "flex": "1",
            "padding": "16px 20px",
            "text-align": "center",
            "cursor": "pointer",
            "transition": "all 0.3s ease",
            "font-weight": "500",
            "color": "rgba(255, 255, 255, 0.6)",
            "border": "none",
            "background": "none",
            "position": "relative"
        },
        " .tab-btn:hover": {
            "color": "rgba(255, 255, 255, 0.8)",
            "background": "rgba(255, 255, 255, 0.03)"
        },
        " .tab-btn.active": {
            "color": "#ffffff",
            "background": "rgba(0, 123, 255, 0.1)",
            "border-bottom": "3px solid #007bff"
        },
        " .tab-content": {
            "padding": "24px"
        },
        " .tab": {
            "display": "none",
            "flex-direction": "column",
            "gap": "20px"
        },
        " .tab.active": {
            "display": "flex"
        },
        " .form-group": {
            "display": "flex",
            "flex-direction": "column",
            "gap": "8px"
        },
        " .form-group label": {
            "font-weight": "500",
            "color": "#ffffff",
            "font-size": "14px",
            "display": "flex",
            "align-items": "center",
            "gap": "8px"
        },
        " .form-group label i": {
            "color": "rgba(255, 255, 255, 0.7)",
            "width": "16px"
        },
        " .input-wrapper": {
            "position": "relative",
            "display": "flex",
            "align-items": "center"
        },
        " .form-group input, .form-group select": {
            "background": "rgba(255, 255, 255, 0.05)",
            "border": "1px solid rgba(255, 255, 255, 0.1)",
            "border-radius": "3px",
            "padding": "12px 16px",
            "color": "#ffffff",
            "font-size": "14px",
            "transition": "all 0.3s ease",
            "width": "100%"
        },
        " .form-group select": {
            "background": "rgba(255, 255, 255, 0.05)",
            "color": "#ffffff",
            "border": "1px solid rgba(255, 255, 255, 0.1)",
            "appearance": "none",
            "-webkit-appearance": "none",
            "-moz-appearance": "none",
            "background-image": "url('data:image/svg+xml;charset=US-ASCII,<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 4 5\"><path fill=\"%23ffffff\" d=\"M2 0L0 2h4zm0 5L0 3h4z\"/></svg>')",
            "background-repeat": "no-repeat",
            "background-position": "right 12px center",
            "background-size": "12px",
            "padding-right": "40px"
        },
        " .form-group select option": {
            "background": "#1a1a1a",
            "color": "#ffffff",
            "border": "none"
        },
        " .form-group input:focus, .form-group select:focus": {
            "outline": "none",
            "border-color": "#007bff",
            "background": "rgba(255, 255, 255, 0.08)",
            "box-shadow": "0 0 0 3px rgba(0, 123, 255, 0.1)"
        },
        " .form-group input::placeholder": {
            "color": "rgba(255, 255, 255, 0.5)"
        },
        " .input-hint": {
            "font-size": "12px",
            "color": "rgba(255, 255, 255, 0.6)",
            "margin-top": "4px"
        },
        " .action-buttons": {
            "display": "flex",
            "gap": "12px",
            "margin-top": "8px"
        },
        " .action-btn": {
            "padding": "10px 16px",
            "border-radius": "3px",
            "border": "1px solid rgba(255, 255, 255, 0.2)",
            "background": "rgba(255, 255, 255, 0.05)",
            "color": "#ffffff",
            "cursor": "pointer",
            "transition": "all 0.3s ease",
            "font-weight": "500",
            "font-size": "14px"
        },
        " .action-btn:hover": {
            "background": "rgba(255, 255, 255, 0.1)",
            "transform": "translateY(-1px)"
        },
        " .action-btn.primary": {
            "background": "linear-gradient(135deg, #007bff, #0056b3)",
            "border-color": "#007bff"
        },
        " .action-btn.secondary": {
            "background": "linear-gradient(135deg, #6c757d, #545b62)",
            "border-color": "#6c757d"
        },
        " .resolve-info": {
            "background": "rgba(40, 167, 69, 0.1)",
            "border": "1px solid rgba(40, 167, 69, 0.3)",
            "border-radius": "3px",
            "padding": "12px 16px",
            "color": "#28a745",
            "font-size": "14px",
            "margin-top": "12px"
        },
        " .resolve-info:empty": {
            "display": "none"
        },
        " .settings-section": {
            "background": "rgba(255, 255, 255, 0.02)",
            "border-radius": "3px",
            "border": "1px solid rgba(255, 255, 255, 0.05)",
            "padding": "20px"
        },
        " .settings-section h3": {
            "margin": "0 0 16px 0",
            "font-size": "18px",
            "font-weight": "600",
            "color": "#ffffff",
            "display": "flex",
            "align-items": "center",
            "gap": "8px"
        },
        " .settings-section h3 i": {
            "color": "#ffc107"
        },
        " .settings-group": {
            "margin-bottom": "20px"
        },
        " .settings-group:last-child": {
            "margin-bottom": "0"
        },
        " .settings-group h4": {
            "margin": "0 0 12px 0",
            "font-size": "16px",
            "font-weight": "500",
            "color": "rgba(255, 255, 255, 0.9)",
            "padding-bottom": "8px",
            "border-bottom": "1px solid rgba(255, 255, 255, 0.1)"
        },
        " .setting-item": {
            "display": "flex",
            "align-items": "center",
            "justify-content": "space-between",
            "padding": "8px 0",
            "gap": "16px"
        },
        " .setting-item label": {
            "flex": "1",
            "color": "rgba(255, 255, 255, 0.8)",
            "font-size": "14px"
        },
        " .setting-item input[type=\"checkbox\"]": {
            "width": "18px",
            "height": "18px",
            "accent-color": "#007bff"
        },
        " .target-section": {
            "background": "rgba(255, 255, 255, 0.02)",
            "border-radius": "3px",
            "border": "1px solid rgba(255, 255, 255, 0.05)",
            "padding": "20px"
        },
        " .target-section h3": {
            "margin": "0 0 16px 0",
            "font-size": "18px",
            "font-weight": "600",
            "color": "#ffffff",
            "display": "flex",
            "align-items": "center",
            "gap": "8px"
        },
        " .target-section h3 i": {
            "color": "#17a2b8"
        },
        " .target-grid": {
            "display": "grid",
            "grid-template-columns": "1fr 1fr",
            "gap": "16px"
        },
        "@media (max-width: 768px)": {
            " .target-grid": {
                "grid-template-columns": "1fr"
            },
            " .add-download-header": {
                "flex-direction": "column",
                "align-items": "flex-start",
                "gap": "8px",
                "text-align": "left"
            },
            " .add-download-header h2": {
                "font-size": "20px"
            },
            " .tab-bar": {
                "flex-direction": "column"
            },
            " .tab-btn": {
                "padding": "12px 16px",
                "font-size": "16px"
            },
            " .form-group input, .form-group select": {
                "padding": "14px 16px",
                "font-size": "16px"
            },
            " .action-buttons": {
                "flex-direction": "column",
                "gap": "8px"
            },
            " .action-btn": {
                "padding": "12px 16px",
                "font-size": "16px",
                "width": "100%"
            },
            " .settings-section, .target-section": {
                "padding": "16px"
            }
        },
        "@media (max-width: 480px)": {
            " .add-download-header .header-icon": {
                "font-size": "24px",
                "padding": "8px"
            },
            " .add-download-header h2": {
                "font-size": "18px"
            },
            " .tab-btn": {
                "padding": "10px 12px",
                "font-size": "14px"
            },
            " .form-group input, .form-group select": {
                "padding": "12px 14px",
                "font-size": "16px"
            },
            " .settings-section, .target-section": {
                "padding": "12px"
            },
            " .settings-section h3, .target-section h3": {
                "font-size": "16px"
            }
        },
        " .custom-dropdown": {
            "position": "relative"
        },
        " .dropdown-input": {
            "background": "rgba(255, 255, 255, 0.05)",
            "border": "1px solid rgba(255, 255, 255, 0.1)",
            "border-radius": "2px",
            "padding": "12px 16px",
            "color": "#ffffff",
            "font-size": "14px",
            "width": "100%",
            "cursor": "text"
        },
        " .dropdown-input:focus": {
            "outline": "none",
            "border-color": "#007bff",
            "background": "rgba(255, 255, 255, 0.08)"
        },
        " .targetfolder": {
            "background": "rgba(255, 255, 255, 0.05)",
            "color": "#ffffff",
            "border": "1px solid rgba(255, 255, 255, 0.1)",
            "border-radius": "2px",
            "padding": "12px 16px",
            "font-size": "14px",
            "width": "100%",
            "appearance": "none",
            "-webkit-appearance": "none",
            "-moz-appearance": "none",
            "background-image": "url('data:image/svg+xml;charset=US-ASCII,<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 4 5\"><path fill=\"%23ffffff\" d=\"M2 0L0 2h4zm0 5L0 3h4z\"/></svg>')",
            "background-repeat": "no-repeat",
            "background-position": "right 12px center",
            "background-size": "12px",
            "padding-right": "40px",
            "transition": "all 0.3s ease"
        },
        " .targetfolder:focus": {
            "outline": "none",
            "border-color": "#007bff",
            "background": "rgba(255, 255, 255, 0.08)",
            "box-shadow": "0 0 0 3px rgba(0, 123, 255, 0.1)"
        },
        " .targetfolder option": {
            "background": "#1a1a1a",
            "color": "#ffffff",
            "border": "none"
        }
    });

    popup.addContentElement(createModernSourceInput());

    popup.addNavbarButton({
        func: function () {
            console.log("closing");
        },
        closePopup: true,
        displayText: "Cancel",
        buttonType: "secondary"
    });

    popup.addNavbarButton({
        func: function () {
            DownloadsWidget.addNewElement(
                getUrls($('.tab-btn.active').attr('tab-name')), 
                getSettings($('.settings-section').find('[config]')),
                $('.targetfolder').val(),
                $('.subfolder.download').val()
            );
        },
        closePopup: true,
        displayText: "Add Downloads",
        buttonType: "primary"
    });

    popup.addNavbarButton({
        func: function () {
            DownloadsWidget.addNewElement(
                getUrls($('.tab-btn.active').attr('tab-name')), 
                getSettings($('.settings-section').find('[config]')),
                $('.targetfolder').val(),
                $('.subfolder.download').val()
            );
            
            // Auto-start the downloads
            setTimeout(function() {
                DownloadsWidget.commit();
            }, 100);
        },
        closePopup: true,
        displayText: "Add & Start",
        buttonType: "primary"
    });

    popup.showIn($('html'));
};

// Also create a regular function declaration for backwards compatibility
function openAddSourcePopup() {
    return window.openAddSourcePopup();
}

function createModernSourceInput() {
    const content = $(`
        <div class="src-input-container">
            <div class="add-download-header">
                <div class="header-icon">
                    <i class="fas fa-download"></i>
                </div>
                <h2>Add New Downloads</h2>
            </div>

            <div class="tab-section">
                <nav class="tab-bar">
                    <button class="tab-btn active" tab-name="url">
                        <i class="fas fa-link"></i>
                        Direct URL
                    </button>
                    <button class="tab-btn" tab-name="aniworld">
                        <i class="fas fa-globe"></i>
                        Aniworld
                    </button>
                </nav>
                
                <div class="tab-content">
                    <div class="tab url-tab active" tab-name="url">
                        <div class="form-group">
                            <label>
                                <i class="fas fa-link"></i>
                                Download URL
                            </label>
                            <div class="input-wrapper">
                                <input type="text" class="url" placeholder="https://example.com/video.mp4">
                            </div>
                            <div class="input-hint">
                                Enter the direct URL to the video file or streaming source
                            </div>
                        </div>
                    </div>
                    
                    <div class="tab aniworld-tab" tab-name="aniworld">
                        <div class="form-group">
                            <label>
                                <i class="fas fa-globe"></i>
                                Aniworld URL
                            </label>
                            <div class="input-wrapper">
                                <input type="text" class="aniworld-url" placeholder="https://aniworld.to/anime/...">
                            </div>
                            <div class="input-hint">
                                Enter the URL to the anime series on Aniworld
                            </div>
                        </div>
                        
                        <div class="form-group">
                            <label>
                                <i class="fas fa-language"></i>
                                Preferred Language
                            </label>
                            <select id="aniworldLanguage">
                                <option value="1">German (Dub)</option>
                                <option value="2">English (Sub)</option>
                                <option value="3">German (Sub)</option>
                            </select>
                        </div>
                        
                        <div class="action-buttons">
                            <button id="btnResolve" class="action-btn primary">
                                <i class="fas fa-search"></i>
                                Resolve Episodes
                            </button>
                            <button id="btnReset" class="action-btn secondary">
                                <i class="fas fa-undo"></i>
                                Reset
                            </button>
                        </div>
                        
                        <div class="resolve-info"></div>
                    </div>
                </div>
            </div>

            <div class="settings-section">
                <h3>
                    <i class="fas fa-cogs"></i>
                    Download Settings
                </h3>
                <div class="url-settings"></div>
            </div>

            <div class="target-section">
                <h3>
                    <i class="fas fa-folder-open"></i>
                    Download Location
                </h3>
                <div class="target-grid">
                    <div class="form-group">
                        <label>
                            <i class="fas fa-hdd"></i>
                            Target Folder
                        </label>
                        <select class="targetfolder">
                        </select>
                    </div>
                    <div class="form-group">
                        <label>
                            <i class="fas fa-folder"></i>
                            Subfolder
                        </label>
                        <div class="custom-dropdown">
                            <textbox-dropdown class="subfolder download dropdown-input">
                            </textbox-dropdown>
                        </div>
                        <div class="input-hint">
                            Optional subfolder within the target directory
                        </div>
                    </div>
                </div>
            </div>
        </div>
    `);

    // Tab switching functionality
    content.find('.tab-btn').click(function(){
        const $this = $(this);
        const tabName = $this.attr('tab-name');
        
        // Update tab buttons
        content.find('.tab-btn').removeClass('active');
        $this.addClass('active');
        
        // Update tab content
        content.find('.tab').removeClass('active');
        content.find('.tab[tab-name="' + tabName + '"]').addClass('active');
    });

    // Aniworld functionality
    content.find('#btnResolve').click(function(){
        let aniworld = new Aniworld(content.find('.resolve-info'));
        aniworld.resolveLinks($('.aniworld-url').val(), $('#aniworldLanguage').val());
    });

    content.find('#btnReset').click(function(){
        Aniworld.resetResolvedLinks();
        content.find('.resolve-info').empty();
    });

    // Populate target folders
    for(let folder of targetFolders){
        content.find('.targetfolder').append(
            $('<option>').text(folder.displayName).attr("value", folder.identifier)
        );
    }
    
    // Restore saved target folder selection from session storage
    const savedTargetFolder = sessionStorage.getItem('addDownloadDialog_targetFolder');
    if (savedTargetFolder) {
        const targetFolderSelect = content.find('.targetfolder');
        targetFolderSelect.val(savedTargetFolder);
        // Trigger the selection event to load subfolders with correct context
        if (targetFolderSelect.length > 0) {
            let selection = targetFolderSelect.val();
            sessionStorage.setItem('addDownloadDialog_targetFolder', selection);
            fetchSubfolders(selection, content.find('.subfolder.download'));
            let subfolderSelection = content.find('.subfolder.download');
            subfolderSelection.empty();
            subfolderSelection.val('');
        }
    }

    // Add event handler for target folder selection
    content.find('.targetfolder').on('change', function() {
        const selection = $(this).val();
        
        // Save selection to session storage
        sessionStorage.setItem('addDownloadDialog_targetFolder', selection);
        
        fetchSubfolders(selection, content.find('.subfolder.download'));
        let subfolderSelection = content.find('.subfolder.download');
        subfolderSelection.empty();
        subfolderSelection.val('');
    });

    // Load settings
    loadModernURLSettings(content.find('.url-settings'), settings);

    return content;
}

function loadModernURLSettings(container, settings) {
    if (!settings || settings.length === 0) {
        container.append('<p style="color: rgba(255, 255, 255, 0.6); font-style: italic;">No additional settings available</p>');
        return;
    }
    
    for(let configSection of settings) {
        let settingsGroup = $('<div>').addClass('settings-group');
        settingsGroup.append($('<h4>').text(configSection.title));
        
        for(let configName in configSection.items) {
            let configDetails = configSection.items[configName];
            
            let settingItem = $('<div>').addClass('setting-item');
            let label = $('<label>').text(configDetails.title);
            let input = getInputElement(configDetails.type, configDetails.defaultValue)
                .attr('config', configName);
            
            settingItem.append(label, input);
            settingsGroup.append(settingItem);
        }
        
        container.append(settingsGroup);
    }
}

// Keep existing helper functions
function getSettings(settingInputs) {
    let options = {};
    for(let element of settingInputs){
        element = $(element);
        options[element.attr('config')] = getInputValue(element).toString();
    }
    return options;
}

function getInputElement(type, value){
    switch(type){
        case "boolean":
            return $('<input>').attr('type', "checkbox").prop('checked', value || false);
        default:
            return $('<input>').attr('type', "text").val(value || "");
    }
}

function getInputValue(inputElement){
    let type = inputElement.attr('type');
    let value = null;
    switch (type) {
        case "checkbox":
            value = inputElement.prop('checked');
            break;
        default:
            value = inputElement.val();
            break;
    }
    return value;
}

function getUrls(activeTab) {
    let val = null;
    switch(activeTab){
        case "url":
            val = $('.url-tab .url').val().trim();
            $('.url-tab .url').val("");
            return val;
        case "aniworld":
            return Aniworld.resolvedLinks;
    }
}

function onTargetSelection() {
    let selection = $('.targetfolder').get(0).value;
    
    // Save selection to session storage
    sessionStorage.setItem('addDownloadDialog_targetFolder', selection);
    
    fetchSubfolders(selection, $('.subfolder.download'));
    let subfolderSelection = $('.subfolder.download');
    subfolderSelection.empty();
    subfolderSelection.val('');
}

/**
 * Fetches subfolders from the REST API
 * @param {string} targetPath - The target path to get subfolders for
 * @param {jQuery} $subfolderElement - The jQuery element to populate with options
 */
function fetchSubfolders(targetPath, $subfolderElement) {
    if (!targetPath) return;
    
    // Clear existing options
    $subfolderElement.empty();
    $subfolderElement.val('');
    
    // Make REST API call
    $.ajax({
        url: `/api/subfolders/${encodeURIComponent(targetPath)}`,
        method: 'GET',
        success: function(subfolders) {
            // Add empty option first
            $subfolderElement.append($('<option>'));
            
            // Add each subfolder as an option
            for (let folder of subfolders) {
                let option = $('<option>');
                option.attr('value', folder);
                option.text(folder);
                $subfolderElement.append(option);
            }
        },
        error: function(xhr, status, error) {
            console.warn('Failed to fetch subfolders:', error);
            
            // Only show error popup for actual errors (not 204 No Content)
            if (xhr.status !== 204) {
                handleAPIError(xhr, 'Failed to load subfolders');
            }
        }
    });
}

// Make sure the function is available when the document is ready
$(document).ready(function() {
    console.log('addSourcePopup.js loaded, openAddSourcePopup available:', typeof window.openAddSourcePopup);
});