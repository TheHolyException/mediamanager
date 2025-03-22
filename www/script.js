const yeti = new Yeti();
let grid = undefined;
let targetFolders = [];

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
    $('.embeded-widget').html(new DownloadsWidget().render());
});

function initUI() {
    let mainTabBar = new TabBar($('.embeded-widget-toolbar'), $('.embeded-widget'), true, {
        downloads: function () { $('.embeded-widget').html(new DownloadsWidget().render()); },
        settings: function () { $('.embeded-widget').html(new SettingsWidget().render()); },
        statistics: function () { $('.embeded-widget').html(new StatisticsWidget().render()); },
        subscriptions: function () { $('.embeded-widget').html(new SubscriptionsWidget().render()); },
    });

    $('.toggle-icon.edit-save').click(function(){
        let self = $(this);
        let isEditing = self.find('[type="checkbox"]').prop('checked');
        grid.setStatic(!isEditing);

        if(!isEditing){
            localStorage.setItem('dashboard-content', JSON.stringify(grid.save()));
        }
    })

    let slideCheckbox = $('.slide-checkbox');
    if(localStorage.getItem('show-dashboard') == 'true'){
        slideCheckbox.find('[type="checkbox"]').prop('checked', true);
    }
    
    slideCheckbox.click(function(){
        let self = $(this);
        let showDashboard = self.find('[type="checkbox"]').prop('checked');
        localStorage.setItem('show-dashboard', showDashboard);
    })



    setupGridstack();
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
        dashboardItems = JSON.parse(dashboardData);
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
        case "systemInfo":
            StatisticsWidget.updateStatistics(content);
            break;
        /* case "syn": // Acknowledge data sync
            for (i = 0; i < content.data.length; i++) {
                let entry = content.data[i];
                DownloadsWidget.addDownloaderItem(entry);
            }
            break; */
        case "targetFolders":
            targetFolders = content.targets;
            break;
        /* case "del":
            indexes.delete(content.uuid);
            document.getElementById(content.uuid).remove();
            break;
            break;*/
        case "setting":
            SettingsWidget.updateSettings(content.settings);

            break;
        case "requestSubfoldersResponse":
            subfolderSelection = $('.subfolder.download')
            subfolderSelection.empty();
            subfolderSelection.append($('<option>'));

            for (let folder of content.subfolders) {
                let option = $('<option>');
                option.attr('value', folder);
                option.text(folder);
                subfolderSelection.append(option);
            }
            break;
    }
}

function setWebSocketStatusFeedback(status) {
    let statusElement = $('#ws-state');
    let statusIcon = $('#ws-state-icon');

    switch (status) {
        case 1:
            statusElement.attr('class', 'connecting');
            statusIcon.attr('class', 'fa-solid fa-spinner fa-spin');
            break;
        case 2:
            statusElement.attr('class', 'connected');
            statusIcon.attr('class', 'fa-solid fa-plug');
            break;
        case 4:
            statusElement.attr('class', 'failed');
            statusIcon.attr('class', 'fa-solid fa-plug-circle-xmark');
            break;
    }
}