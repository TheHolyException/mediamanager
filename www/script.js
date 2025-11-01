const yeti = new Yeti();
let dashboard;
let targetFolders = [];
getTargetsAPI();

const settings = [
    {
        title: "VOE",
        items: {
            enableSeasonAndEpisodeRenaming: {
                title: "Rename to Episode and Season",
                type: "boolean",
                defaultValue: true
            },
            enableSessionRecovery: {
                title: "Session Recovery",
                type: "boolean",
                defaultValue: false
            }
        }
    }
]

connect(); // Connect WebSocket
$(document).ready(function () {
    initUI();
    $('.embeded-widget').html(new DownloadsWidget().createContent());
});

/**
 * Centralized API error handler using Yeti popups
 * @param {object} xhr - The jQuery XHR object from the failed AJAX request
 * @param {string} defaultMessage - Default error message to show if parsing fails
 * @param {object} options - Additional options for Yeti popup
 */
function handleAPIError(xhr, defaultMessage, options = {}) {
    let errorMessage = defaultMessage || 'An error occurred';
    
    // Try to extract error message from response
    try {
        if (xhr.responseText) {
            const errorResponse = JSON.parse(xhr.responseText);
            errorMessage = errorResponse.error || errorMessage;
        }
    } catch (e) {
        // Use default message if response parsing fails
    }
    
    // Show error popup using Yeti
    yeti.show({
        message: errorMessage,
        severity: 'nok',
        time: 5000,
        ...options // Allow overriding default options
    });
    
    return errorMessage;
}

/**
 * Centralized API success handler using Yeti popups
 * @param {object} response - The response data from successful API call
 * @param {string} defaultMessage - Default success message to show
 * @param {object} options - Additional options for Yeti popup
 */
function handleAPISuccess(response, defaultMessage, options = {}) {
    let successMessage = defaultMessage || 'Operation completed successfully';
    
    // Try to extract success message from response
    if (response && response.message) {
        successMessage = response.message;
    }
    
    // Show success popup using Yeti
    yeti.show({
        message: successMessage,
        severity: 'ok',
        time: 3000,
        ...options // Allow overriding default options
    });
    
    return successMessage;
}

/**
 * Adds downloads
 * @param {Array} downloadList - Array of download items to add
 * @param {Function} onSuccess - Callback for successful API call
 * @param {Function} onError - Callback for failed API call
 */
function addDownloadsAPI(downloadList, onSuccess, onError) {
    $.ajax({
        url: '/api/downloads',
        method: 'POST',
        contentType: 'application/json',
        data: JSON.stringify({
            list: downloadList
        }),
        success: function(response) {
            handleAPISuccess(response, 'Downloads added successfully');
            if (onSuccess) onSuccess(response);
        },
        error: function(xhr, status, error) {
            console.error('Failed to add downloads:', error);
            const errorMessage = handleAPIError(xhr, 'Failed to add downloads');
            if (onError) onError(errorMessage);
        }
    });
}

/**
 * Deletes a specific download
 * @param {string} uuid - UUID of the download to delete
 * @param {Function} onSuccess - Callback for successful API call
 * @param {Function} onError - Callback for failed API call
 */
function deleteDownloadAPI(uuid, onSuccess, onError) {
    $.ajax({
        url: `/api/downloads/${encodeURIComponent(uuid)}`,
        method: 'DELETE',
        success: function(response) {
            handleAPISuccess(response, 'Download deleted successfully');
            if (onSuccess) onSuccess(response);
        },
        error: function(xhr, status, error) {
            console.error('Failed to delete download:', error);
            const errorMessage = handleAPIError(xhr, 'Failed to delete download');
            if (onError) onError(errorMessage);
        }
    });
}

/**
 * Deletes all downloads
 * @param {Function} onSuccess - Callback for successful API call
 * @param {Function} onError - Callback for failed API call
 */
function deleteAllDownloadsAPI(onSuccess, onError) {
    $.ajax({
        url: '/api/downloads',
        method: 'DELETE',
        success: function(response) {
            handleAPISuccess(response, 'All downloads deleted successfully');
            if (onSuccess) onSuccess(response);
        },
        error: function(xhr, status, error) {
            console.error('Failed to delete all downloads:', error);
            const errorMessage = handleAPIError(xhr, 'Failed to delete all downloads');
            if (onError) onError(errorMessage);
        }
    });
}

function getTargetsAPI() {
    $.ajax({
        url: '/api/targets',
        method: 'GET',
        success: function(response) {
            targetFolders = response;
        },
        error: function(xhr, status, error) {
            console.error('Failed to get targets:', error);
            const errorMessage = handleAPIError(xhr, 'Failed to get targets');
            if (onError) onError(errorMessage);
        }
    });
}

function getSystemInfoAPI() {
    $.ajax({
        url: '/api/system',
        method: 'GET',
        success: function(response) {
            StatisticsWidget.updateStatistics(response);
        },
        error: function(xhr, status, error) {
            console.error('Failed to get system info:', error);
            const errorMessage = handleAPIError(xhr, 'Failed to get system info');
            if (onError) onError(errorMessage);
        }
    })
}

function initUI() {
    // Initialize tab navigation - TabBar constructor initializes itself
    const tabNavigation = new TabBar($('.embeded-widget-toolbar'), $('.embeded-widget'), true, {
        downloads: function () { $('.embeded-widget').html(new DownloadsWidget().createContent()); },
        settings: function () { $('.embeded-widget').html(new SettingsWidget().createContent()); },
        statistics: function () { 
            $('.embeded-widget').html(new StatisticsWidget().createContent()); 
            // Trigger immediate system info request when statistics tab is opened
            getSystemInfoAPI();
        },
        subscriptions: function () { $('.embeded-widget').html(new SubscriptionsWidget().createContent()); },
    });
    // TabBar instance stored for potential future use
    window.tabNavigation = tabNavigation;

    $('.edit-save-btn').click(function(){
        let editCheckbox = $(this).find('.edit-checkbox');
        let isEditing = editCheckbox.prop('checked');
        let newEditingState = !isEditing;
        
        editCheckbox.prop('checked', newEditingState);
        
        // Update button appearance
        let editIcon = $(this).find('.edit-icon');
        let saveIcon = $(this).find('.save-icon');
        let btnText = $(this).find('.btn-text');
        
        if(newEditingState) {
            editIcon.addClass('hidden');
            saveIcon.removeClass('hidden');
            btnText.text('Save');
            $(this).attr('title', 'Save Changes');
            $('body').addClass('grid-editing');
        } else {
            editIcon.removeClass('hidden');
            saveIcon.addClass('hidden');
            btnText.text('Edit');
            $(this).attr('title', 'Toggle Edit Mode');
            $('body').removeClass('grid-editing');
        }
        
        dashboard.setEditMode(newEditingState)
        dashboard.setEnableDragDrop(newEditingState);
        //grid.setStatic(!newEditingState);

        if(!newEditingState){
            //localStorage.setItem('dashboard-content', JSON.stringify(grid.save()));
            localStorage.setItem('dashboard-content', JSON.stringify(dashboard.getLayout()));
        }
    })

    // Modern toggle handling
    let modernToggle = $('.toggle-input');
    if(localStorage.getItem('show-dashboard') == 'true'){
        modernToggle.prop('checked', true);
        $('body').addClass('grid-view');
    }
    
    modernToggle.change(function(){
        let isGridView = $(this).prop('checked');
        localStorage.setItem('show-dashboard', isGridView);
        
        // Toggle grid-view class on body for responsive styling
        if(isGridView) {
            $('body').addClass('grid-view');
        } else {
            $('body').removeClass('grid-view');
        }
    })



    setupGridDashboard();
    //setupGridstack();
}

function setupGridDashboard(){
    dashboard = new GridDashboard('.dashboard', 20, 20, {
        //useCSSAuto: true,
        gap: 10,
        editMode: false,
        showGridLines: true,
        enableDragDrop: false,
        allowOverlapping: true,
        cellWidth: "auto",
        cellHeight: "auto"
    });

    // Create a custom HTML palette
    const customPalette = new CustomHTMLWidgetPalette('.widget-toolbar-bar', {
        widgetSelector: '[widget-type]',
        enableClickToAdd: false,
        enableHoverEffects: true,
    });

    customPalette.registerWidgetTemplate("downloads", new DownloadsWidget())
    customPalette.registerWidgetTemplate("settings", new SettingsWidget())
    customPalette.registerWidgetTemplate("statistics", new StatisticsWidget())
    customPalette.registerWidgetTemplate("subscriptions", new SubscriptionsWidget())

    // Register with a dashboard
    customPalette.registerDashboard(dashboard);

    let dashboardData = localStorage.getItem('dashboard-content');
    if(dashboardData){
        let dashboardItems = JSON.parse(dashboardData);
        dashboard.loadFromLayout(dashboardItems);
    }
}

function setupGridstack() {
    GridStack.renderCB = function (el, w) {
        el.innerHTML = w.content;
    };

    let insert = [{ h: 2, content: 'new item' }];

    grid = GridStack.init({
        cellHeight: 70,
        minRow: 1,
        acceptWidgets: true,
        float: true,
        handle: '.widget-handle',
        staticGrid: true
    });

    GridStack.setupDragIn('.grid-stack-item', {/* appendTo: 'body',  */helper: 'clone' }, insert);

    let addToggle = true;
    grid.on('added', function (event, items) {
        addToggle = !addToggle;
        if(addToggle){
            return
        }

        let o = items[0];
        let addedElem = $(o.el);
        let widgetName = o.widgetName;

        if(!widgetName){
            widgetName = addedElem.attr('widget-name');
        }

        grid.removeWidget(o.el);
        let widget = $(WidgetManager.getWidget(widgetName, "").render());
        addRemoveButtonToWidget(widget);
        grid.addWidget(widget.get(0), {
            x: o.x,
            y: o.y,
            w: o.w,
            h: o.h,
            widgetName: widgetName
        });
    });

    let dashboardData = localStorage.getItem('dashboard-content');
    if(dashboardData){
        let dashboardItems = JSON.parse(dashboardData);
        for(let item of dashboardItems){
            grid.addWidget(WidgetManager.getWidget(item.widgetName, "").render(), {
                x: item.x,
                y: item.y,
                w: item.w,
                h: item.h,
                widgetName: item.widgetName
            })
        }
    }
}

function addRemoveButtonToWidget(widget){
    let removeBtn = $('<button>')
        .addClass('remove-widget-btn')
        .html('<i class="fa-solid fa-trash"></i>')
        .click(function(){
            grid.removeWidget(this.parentElement);
        });
    widget.append(removeBtn);
}

function onWSResponseDefault(cmd, content) {
    switch (cmd) {
        case "setting":
            SettingsWidget.updateSettings(content.settings);

            break;
    }
}

function setWebSocketStatusFeedback(status) {
    let statusElement = $('#ws-state');
    let statusIcon = $('#ws-state-icon');
    let statusText = statusElement.find('.status-text');

    switch (status) {
        case 1:
            statusElement.attr('class', 'status-indicator connecting');
            statusIcon.attr('class', 'fa-solid fa-spinner');
            statusText.text('Connecting...');
            break;
        case 2:
            statusElement.attr('class', 'status-indicator connected');
            statusIcon.attr('class', 'fa-solid fa-wifi');
            statusText.text('Connected');
            break;
        case 4:
            statusElement.attr('class', 'status-indicator failed');
            statusIcon.attr('class', 'fa-solid fa-wifi-slash');
            statusText.text('Disconnected');
            break;
    }
}