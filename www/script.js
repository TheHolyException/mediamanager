const yeti = new Yeti();
//let indexes = new Map();
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

    setupGridstack();
}

function setupGridstack() {
    GridStack.renderCB = function (el, w) {
        el.innerHTML = w.content;
    };

    let insert = [{ h: 2, content: 'new item' }];

    let grid = GridStack.init({
        cellHeight: 70,
        minRow: 1,
        acceptWidgets: true,
        float: true
    });
    GridStack.setupDragIn('.grid-stack-item', {/* appendTo: 'body',  */helper: 'clone' }, insert);

    let isAdded = false;
    grid.on('added', function (event, items) {
        console.log(items);
        if (!isAdded) {
            let o = items[0];
            let addedElem = $(o.el);
            let widgetName = addedElem.attr('widget-name');
            isAdded = true;
            addedElem.remove();
            grid.addWidget(WidgetManager.getWidget(widgetName, "").render(), {
                x: o.x,
                y: o.y,
                w: o.w,
                h: o.h,
                //content: `<img src="https://coding-garden.de/resources/images/Icon.svg">`
                //content: $('<span>').text("THIS IS A TEST").get(0).outerHTML
            });
        }
        else {
            isAdded = false;
        }
    });
}

function onWSResponseDefault(cmd, content) {
    switch (cmd) {
        case "systemInfo":
            StatisticsWidget.updateStatistics(content);
            break;
        case "syn": // Acknowledge data sync
            for (i = 0; i < content.data.length; i++) {
                let entry = content.data[i];
                DownloadsWidget.addDownloaderItem(entry);
            }
            break;
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
            subfolderSelection = $('.subfolder')
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