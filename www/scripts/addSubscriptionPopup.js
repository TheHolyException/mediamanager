// Make sure the function is available globally
window.openSubscriptionPopup = function openSubscriptionPopup(mode = 'add', subscriptionData = null) {
    // mode can be 'add' or 'edit'
    // subscriptionData is required for edit mode

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

    const isEditMode = mode === 'edit';
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
        " .subscription-popup-header": {
            "display": "flex",
            "align-items": "center",
            "gap": "12px",
            "margin-bottom": "24px",
            "padding-bottom": "16px",
            "border-bottom": "1px solid rgba(255, 255, 255, 0.1)"
        },
        " .subscription-popup-header h2": {
            "margin": "0",
            "font-size": "24px",
            "font-weight": "600",
            "color": "#ffffff",
            "display": "flex",
            "align-items": "center",
            "gap": "12px"
        },
        " .subscription-popup-header .header-icon": {
            "font-size": "28px",
            "color": "#007bff",
            "background": "rgba(0, 123, 255, 0.1)",
            "padding": "12px",
            "border-radius": "8px"
        },
        " .subscription-form-container": {
            "display": "flex",
            "flex-direction": "column",
            "gap": "24px"
        },
        " .form-section": {
            "background": "rgba(255, 255, 255, 0.02)",
            "border-radius": "8px",
            "border": "1px solid rgba(255, 255, 255, 0.05)",
            "padding": "20px"
        },
        " .form-section h3": {
            "margin": "0 0 16px 0",
            "font-size": "18px",
            "font-weight": "600",
            "color": "#ffffff",
            "display": "flex",
            "align-items": "center",
            "gap": "8px"
        },
        " .form-section h3 i": {
            "color": "#007bff"
        },
        " .form-grid": {
            "display": "grid",
            "grid-template-columns": "1fr 1fr",
            "gap": "20px"
        },
        " .form-group": {
            "display": "flex",
            "flex-direction": "column",
            "gap": "8px"
        },
        " .form-group.full-width": {
            "grid-column": "1 / -1"
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
        " .form-group label .required": {
            "color": "#dc3545",
            "margin-left": "4px"
        },
        " .form-group input, .form-group select": {
            "background": "rgba(255, 255, 255, 0.05)",
            "border": "1px solid rgba(255, 255, 255, 0.1)",
            "border-radius": "8px",
            "padding": "12px 16px",
            "color": "#ffffff",
            "font-size": "14px",
            "transition": "all 0.3s ease",
            "width": "100%"
        },
        " .form-group select": {
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
            "color": "#ffffff"
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
        " .form-group input:disabled": {
            "background": "rgba(255, 255, 255, 0.02)",
            "color": "rgba(255, 255, 255, 0.5)",
            "cursor": "not-allowed",
            "opacity": "0.6"
        },
        " .input-hint": {
            "font-size": "12px",
            "color": "rgba(255, 255, 255, 0.6)",
            "margin-top": "4px"
        },
        "@media (max-width: 768px)": {
            " .form-grid": {
                "grid-template-columns": "1fr"
            },
            " .subscription-popup-header": {
                "flex-direction": "column",
                "align-items": "flex-start",
                "gap": "8px"
            },
            " .subscription-popup-header h2": {
                "font-size": "20px"
            }
        }
    });

    popup.addContentElement(createSubscriptionForm(isEditMode, subscriptionData));

    // Add cancel button
    popup.addNavbarButton({
        func: function () {
            // No action needed on cancel
        },
        closePopup: true,
        displayText: "Cancel",
        buttonType: "secondary"
    });

    // Add save button
    popup.addNavbarButton({
        func: function () {
            if (isEditMode) {
                saveEditSubscription(subscriptionData);
            } else {
                saveNewSubscription();
            }
        },
        closePopup: true,
        displayText: isEditMode ? "Save Changes" : "Add Subscription",
        buttonType: "primary"
    });

    popup.showIn($('html'));
};

// Also create a regular function declaration for backwards compatibility
function openSubscriptionPopup(mode, subscriptionData) {
    return window.openSubscriptionPopup(mode, subscriptionData);
}

function createSubscriptionForm(isEditMode, subscriptionData) {
    const headerTitle = isEditMode ? "Edit Subscription" : "New Subscription";
    const headerIcon = isEditMode ? "fa-edit" : "fa-plus-circle";

    // Parse directory for edit mode
    let targetFolder = '';
    let subfolder = '';

    if (isEditMode && subscriptionData) {
        const directory = subscriptionData.directory || '';

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
    }

    const content = $(`
        <div class="subscription-form-container">
            <div class="subscription-popup-header">
                <div class="header-icon">
                    <i class="fas ${headerIcon}"></i>
                </div>
                <h2>${headerTitle}</h2>
            </div>

            <div class="form-section">
                <h3>
                    <i class="fas fa-info-circle"></i>
                    Subscription Details
                </h3>
                <div class="form-grid">
                    <div class="form-group full-width">
                        <label>
                            <i class="fas fa-link"></i>
                            Anime URL
                            ${!isEditMode ? '<span class="required">*</span>' : ''}
                        </label>
                        <input type="text"
                               id="popup-sub-url"
                               class="popup-sub-url"
                               placeholder="https://aniworld.to/anime/..."
                               value="${isEditMode && subscriptionData ? subscriptionData.url : ''}"
                               ${isEditMode ? 'disabled readonly' : 'required'}>
                        <div class="input-hint">
                            ${isEditMode ? 'URL cannot be changed after subscription' : 'Enter the full URL to the anime series'}
                        </div>
                    </div>

                    <div class="form-group">
                        <label>
                            <i class="fas fa-language"></i>
                            Preferred Language
                        </label>
                        <select id="popup-sub-language" class="popup-sub-language">
                            <option value="1" ${isEditMode && subscriptionData && subscriptionData.languageId === 1 ? 'selected' : ''}>German (Dub)</option>
                            <option value="2" ${isEditMode && subscriptionData && subscriptionData.languageId === 2 ? 'selected' : ''}>German (Sub)</option>
                            <option value="3" ${isEditMode && subscriptionData && subscriptionData.languageId === 3 ? 'selected' : ''}>English (Dub)</option>
                            <option value="4" ${isEditMode && subscriptionData && subscriptionData.languageId === 4 ? 'selected' : ''}>English (Sub)</option>
                            <option value="5" ${isEditMode && subscriptionData && subscriptionData.languageId === 5 ? 'selected' : ''}>Japanese (Sub)</option>
                        </select>
                        <div class="input-hint">
                            ${isEditMode ? 'Changing language will affect future downloads' : 'Select your preferred language for episodes'}
                        </div>
                    </div>

                    <div class="form-group">
                        <label>
                            <i class="fas fa-ban"></i>
                            Excluded Seasons
                        </label>
                        <input type="text"
                               id="popup-sub-excluded"
                               class="popup-sub-excluded"
                               placeholder="1,3,5"
                               value="${isEditMode && subscriptionData ? (subscriptionData.excludedSeasons || '') : ''}">
                        <div class="input-hint">
                            Comma-separated season numbers to skip downloading
                        </div>
                    </div>
                </div>
            </div>

            <div class="form-section">
                <h3>
                    <i class="fas fa-folder-open"></i>
                    Download Location
                </h3>
                <div class="form-grid">
                    <div class="form-group">
                        <label>
                            <i class="fas fa-hdd"></i>
                            Target Folder
                        </label>
                        <select id="popup-sub-target-folder" class="popup-sub-target-folder">
                        </select>
                    </div>

                    <div class="form-group">
                        <label>
                            <i class="fas fa-folder"></i>
                            Subfolder
                        </label>
                        <input type="text"
                               id="popup-sub-subfolder"
                               class="popup-sub-subfolder"
                               placeholder="Anime/Series Name"
                               list="popup-sub-subfolder-list"
                               value="${subfolder}">
                        <datalist id="popup-sub-subfolder-list" class="popup-sub-subfolder-list">
                        </datalist>
                        <div class="input-hint">
                            Leave empty to auto-detect from title, or select from existing folders
                        </div>
                    </div>
                </div>
            </div>
        </div>
    `);

    // Populate target folders
    const targetFolderSelect = content.find('#popup-sub-target-folder');
    for (let folder of targetFolders) {
        const option = $('<option>').text(folder.displayName).attr('value', folder.identifier);
        if (isEditMode && folder.identifier === targetFolder) {
            option.prop('selected', true);
        }
        targetFolderSelect.append(option);
    }

    // If no target folder was selected (new subscription), select the first one
    if (!isEditMode && targetFolders.length > 0) {
        targetFolderSelect.val(targetFolders[0].identifier);
    }

    // Fetch subfolders for the selected target folder
    if (targetFolder || (!isEditMode && targetFolders.length > 0)) {
        const selectedFolder = targetFolder || targetFolders[0].identifier;
        fetchSubfoldersForPopup(selectedFolder, content.find('#popup-sub-subfolder-list'));
    }

    // Target folder change handler
    content.find('#popup-sub-target-folder').on('change', function() {
        const selection = $(this).val();
        content.find('#popup-sub-subfolder').val('');
        fetchSubfoldersForPopup(selection, content.find('#popup-sub-subfolder-list'));
    });

    // Form validation for add mode
    if (!isEditMode) {
        content.find('#popup-sub-url').on('input', function() {
            validateSubscriptionUrl($(this).val());
        });
    }

    return content;
}

function validateSubscriptionUrl(url) {
    const isValid = url && url.trim().length > 0 && url.includes('aniworld.to');
    // You can add visual feedback here if needed
    return isValid;
}

function fetchSubfoldersForPopup(targetPath, $datalistElement) {
    if (!targetPath) return;

    // Clear existing options
    $datalistElement.empty();

    // Make REST API call
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
        },
        error: function(xhr, status, error) {
            console.warn('Failed to fetch subfolders:', error);

            // Only show error for actual errors (not 204 No Content)
            if (xhr.status !== 204) {
                console.error('Error fetching subfolders for target:', targetPath, error);
            }
        }
    });
}

function saveNewSubscription() {
    const url = $('#popup-sub-url').val().trim();

    if (!validateSubscriptionUrl(url)) {
        alert('Please enter a valid Aniworld URL');
        return false;
    }

    const subfolder = $('#popup-sub-subfolder').val().trim();

    const data = {
        url: url,
        languageId: parseInt($('#popup-sub-language').val()),
        directory: subfolder,
        excludedSeasons: $('#popup-sub-excluded').val().trim()
    };

    ApiClient.addSubscription(data)
        .then(subscription => {
            showNotification('Subscription added successfully!', 'success');
        })
        .catch(error => {
            console.error('Error adding subscription:', error);
            showNotification('Failed to add subscription: ' + error.message, 'error');
        });

    return true;
}

function saveEditSubscription(originalData) {
    if (!originalData || !originalData.id) {
        console.error('No subscription data provided for edit');
        return false;
    }

    const subfolder = $('#popup-sub-subfolder').val().trim();

    const data = {
        id: originalData.id,
        languageId: parseInt($('#popup-sub-language').val()),
        directory: subfolder,
        excludedSeasons: $('#popup-sub-excluded').val().trim()
    };

    ApiClient.updateSubscription(originalData.id, data)
        .then(subscription => {
            showNotification('Subscription updated successfully!', 'success');
        })
        .catch(error => {
            console.error('Error updating subscription:', error);
            showNotification('Failed to update subscription: ' + error.message, 'error');
        });

    return true;
}

function showNotification(message, type = 'info') {
    // Valid toast severity types
    const validSeverities = ['success', 'info', 'error', 'warning'];
    const severity = validSeverities.includes(type) ? type : 'info';

    // Check if toast is available globally
    if (typeof window.toast !== 'undefined') {
        window.toast.show({
            message: message,
            severity: severity,
            duration: 5000
        });
    } else if (typeof toast !== 'undefined') {
        toast.show({
            message: message,
            severity: severity,
            duration: 5000
        });
    } else {
        // Fallback to console logging if toast is not available
        console.log(`${type.toUpperCase()}: ${message}`);
    }
}