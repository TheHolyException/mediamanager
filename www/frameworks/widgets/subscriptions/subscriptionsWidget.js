class SubscriptionsWidget extends BaseWidget {
    constructor(name = "Downloads") {
        super(name);
    }

    render() {
        let widget = $(`
        <div class="widget" widget-name="SubscriptionsWidget">
            <h1 class="widget-handle">Statistics</h1>
            <nav class="statistics-action-bar">
                <a class="add-download-btn"><i class="fa fa-plus"></i> Add</a>
            </nav>
            <div class="auto-downloader">
                <div class="add-new-download">
                    <div class="settings">
                        <label class="setting url">
                            <span>URL</span>
                            <input class="input" type="text">
                        </label>
                        <label class="setting subfolder">
                            <span>Subfolder</span>
                            <input class="input" type="text">
                        </label>
                    </div>
                </div>
                <table class="auto-downloader-table">
                    <tr>
                        <th col="actions">Actions</th>
                        <th col="title">Title</th>
                        <th col="url">URL</th>
                        <th col="unloaded">Unloaded</th>
                        <th col="last-scan">Last Scan</th>
                    </tr>
                </table>
            </div>
        </div>
        `);

        widget.find('.add-download-btn').click(function () {
            let widget = $(this).closest('[widget-name="SubscriptionsWidget"]');
            let subfolderInput = widget.find('.setting.subfolder input');
            let urlInput = widget.find('.setting.url input');

            SubscriptionsWidget.autoloaderSave(
                subfolderInput.val(),
                urlInput.val(),
                1
            );

            subfolderInput.val('');
            urlInput.val('');
        });
        sendPacket('getData', 'autoloader');
        return widget.get(0);
    }

    static addAutoloaderItem(item) {
        let table = $('.auto-downloader-table');
        let itemId = item.id;

        let existing = $('[autoloader="' + itemId + '"]');
        if (existing.length > 0){
            existing.remove();
        }
        
        let row = $('<tr>').attr('autoloader', itemId);

        // Toolbar
        {
            let toolbar = $('<td>')
                .addClass('toolbar')
                .attr('col', 'actions');

            // Run Download
            {
                let btnDownload = $('<i>')
                    .addClass('fa fa-download')
                    .attr('title', 'Start Download')
                    .click(function(){
                        sendPacket("runDownload", "autoloader", { id: item.id })
                    });
                toolbar.append(btnDownload);
            }

            // Run Unsubscribe
            {
                let btnUnsubscribe = $('<i>')
                    .addClass('fa-solid fa-ban')
                    .attr('title', 'Unsubscribe')
                    .click(function(){
                        sendPacket("unsubscribe", "autoloader", { id: item.id })
                    });
                toolbar.append(btnUnsubscribe);
            }

            row.append(toolbar);
        }
        // Title
        {
            let txtTitle = $('<td>')
                .text(item.title);
            row.append(txtTitle);
        }

        // URL
        {
            let urlCell = $('<td>');

            let link = $('<a>')
                .attr('href', item.url)
                .attr('target', '_blank')
                .text('Aniworld');

            urlCell.append(link)
            row.append(urlCell);
        }

        // Unloaded Episodes
        {
            let txtUnloaded = $('<td>')
                .text(item.unloaded);
            row.append(txtUnloaded);
        }

        // LastScan
        {
            let date = new Date(item.lastScan);
            let hours = date.getHours().toString().padStart(2, '0');
            let minutes = date.getMinutes().toString().padStart(2, '0');
            let txtLastScan = $('<td>')
                .text(hours + ':' + minutes);
            row.append(txtLastScan);
        }

        table.append(row);
    }

    static autoloaderSave(subfolder, url, languageId) {    
        let request = {
            url: url,
            languageId: languageId,
            directory: subfolder
        }
        
        sendPacket('subscribe', 'autoloader', request);
    }



    static onWSResponse(cmd, content) {
        switch (cmd) {
            case "syn":
                for (let index in content.items) {
                    SubscriptionsWidget.addAutoloaderItem(content.items[index]);
                }
                break;
            case "del":
                $('[autoloader="' + content.id + '"]').remove();
                break;
        }
    }    
}