class DownloadsWidget extends BaseWidget {
    static indexes = new Map();

    constructor(name = "Statistics") {
        super(name);
    }

    render() {
        let widget = $(`
        <div class="widget">
            <h1 class="widget-handle">Download Queue</h1>
            <nav class="queue-action-bar">
                <a class="add-sources-btn" onclick="openAddSourcePopup()"><i class="fa fa-plus"></i> Add</a>
                <a class="commit-sources-btn"><i class="fas fa-paper-plane"></i> Commit</a>
                <a class="retry-all-btn"><i class="fa-solid fa-rotate-right"></i> Retry All Failed</a>
                <a class="delete-all-btn"><i class="fa fa-trash"></i> Delete All</a>
                <a class="delete-completed-btn"><i class="fa fa-trash"></i> Delete Completed</a>
            </nav>
            <table class="queue-table">
                <tr>
                    <th col="actions">Actions</th>
                    <th col="state">Status</th>
                    <th col="url">URL</th>
                    <th col="target">Target</th>
                </tr>
            </table>
        </div>
        `);

        widget.find('.commit-sources-btn').click(function () {
            DownloadsWidget.commit();
        });

        widget.find('.retry-all-btn').click(function(){
            widget.find('.failed [action="resend"]').click();
        });

        widget.find('.delete-all-btn').click(function(){
            sendPacket("del-all", "default");
        });

        widget.find('.delete-completed-btn').click(function(){
            widget.find('.success [action="delete"]').click();
        });

        sendPacket("syn", "default");
        return widget.get(0);
    }

    static commit() {
        let commitPacket = [];
        for (let [uuid, data] of DownloadsWidget.indexes) {
            // We only want to put new entries
            if (data.state != "new") continue;
            commitPacket.push(data);
        }

        sendPacket("put", "default", {
            "list": commitPacket
        });
    }

    static addDownloaderItem(item) {
        let index = DownloadsWidget.indexes.get(item.uuid);
        let tables = $('.queue-table');
        let row = undefined;

        if (index == undefined) {//its a new item and can be aded to all existing tables at once
            row = DownloadsWidget.createNewRow(item);
            DownloadsWidget.setStatusAndTooltip(row, item);
            tables.append(row);
        }
        else {//its an existing item and needs to be added to each table seperatly to update already existing rows
            let dirPath = item.target;
            let subPath = item.title;
            if (!dirPath.endsWith('/'))
                dirPath += '/';
            if (!subPath)
                subPath = '?';

            for(let t of tables){
                let table = $(t);
                row = table.find('[uuid="' + item.uuid + '"]');

                if(row.length == 0){
                    row = DownloadsWidget.createNewRow(item);
                    table.append(row);
                }
                else{
                    let stateCol = row.find('[col="state"]');
                    stateCol.text(item.state.split('\n')[0]);

                    let targetCol = row.find('[col="target"]');
                    targetCol.text(dirPath + subPath);

                    let btnResent = row.find('[action="resend"]');
                    btnResent.css('display', item.state.startsWith('Error') ? 'block' : 'none');
                }

                DownloadsWidget.setStatusAndTooltip(row, item);
            }
        }


        DownloadsWidget.indexes.set(item.uuid, item);
    }

    static setStatusAndTooltip(row, item) {
        let tooltip = "Status:\n" + item.state + "\n";
        tooltip += "Options:\n";
        for (let key in item.options) {
            tooltip += "\t" + key + ": " + item.options[key] + "\n"
        }
        row.attr('title', tooltip);

        row.attr('class', DownloadsWidget.getStatusClass(item.state));
    }

    static createNewRow(item) {
        //Row
        let row = $('<tr>')
            .attr('uuid', item.uuid);

        //==============================Column - toolbar===============================
        //Column - toolbar
        let toolbar = $('<td>')
            .addClass('toolbar')
            .attr('col', 'actions');

        //Column - toolbar - delete
        let deleteBtn = $('<i>')
            .attr('action', 'delete')
            .attr('title', 'Delete from List')
            .addClass('fa fa-trash')
            .click(function () {
                let data = DownloadsWidget.indexes.get(item.uuid);
                if (data.state != "new") {
                    sendPacket("del", "default", { "uuid": item.uuid });
                }
                DownloadsWidget.indexes.delete(item.uuid);
            });

        //Column - toolbar - resend
        let resentBtn = $('<i>')
            .attr('action', 'resend')
            .attr('title', 'Restart Download')
            .addClass('fa fa-rotate-right')
            .css('display', item.state.startsWith('Error') ? 'block' : 'none')
            .click(function () {
                let data = DownloadsWidget.indexes.get(item.uuid);
                if (!data.state.startsWith("Error")) return;
                data.state = "new";
                sendPacket("put", "default", {
                    "list": [data]
                });
            });

        //Column - toolbar - resend with other stream
        let resentWithOtherStreamBtn = $('<i>')
            .attr('action', 'resendOtherStream')
            .attr('title', 'Restart Download with other Stream')
            .addClass('fa-solid fa-code-branch')
            .css('display', item.autoloaderData != undefined && item.state.startsWith('Error') ? 'block' : 'none')
            .click(function () {
                let data = DownloadsWidget.indexes.get(item.uuid);
                SelectStreamPopup.request(data);
            });

        toolbar.append(resentBtn, deleteBtn, resentWithOtherStreamBtn);
        //===============================================================================
        //==============================Column - State===============================
        let state = $('<td>')
            .text(item.state.split('\n')[0])
            .attr('col', 'state')
            .click(() => navigator.clipboard.writeText(item.state));
        //===============================================================================
        //==============================Column - URL===============================
        let url = $('<td>')
            .attr('col', 'url')
            .append($('<a>').text(item.url).attr('href', item.url));
        //===============================================================================
        //==============================Column - Target===============================
        let dirPath = item.target;
        let subPath = item.title;
        if (!dirPath.endsWith('/'))
            dirPath += '/';
        if (!subPath)
            subPath = '?';

        let target = $('<td>')
            .attr('col', 'target')
            .text(dirPath + subPath);
        //===============================================================================
        row.append(toolbar, state, url, target);
        return row;
    }

    static getStatusClass(statusMessage) {
        if (statusMessage.includes("rror")) {
            return 'failed'
        }

        if (statusMessage.includes("Committed")) {
            return 'committed'
        }

        if (statusMessage.includes("Downloading")) {
            return 'downlading'
        }

        if (statusMessage.includes("Completed")) {
            return 'success'
        }
    }

    static addNewElement(urls, settings, targetSelection, subfolder) {
        let urlArray = urls.split(";");

        for (let urlElement of urlArray) {
            let obj = {
                uuid: DownloadsWidget.uuidv4(),
                state: "new",
                created: new Date().getTime(),
                url: urlElement,
                options: settings,
                target: targetSelection + "/" + subfolder
            }
            DownloadsWidget.addDownloaderItem(obj);
        }
    }



    static uuidv4() {
        return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'
            .replace(/[xy]/g, function (c) {
                const r = Math.random() * 16 | 0,
                    v = c == 'x' ? r : (r & 0x3 | 0x8);
                return v.toString(16);
            });
    }

    static onWSResponse(cmd, content) {
        switch (cmd) {
            case "syn": // Acknowledge data sync
                for (let i = 0; i < content.data.length; i++) {
                    let entry = content.data[i];
                    DownloadsWidget.addDownloaderItem(entry);
                }
                break;
            case "del":
                for(let uuid of content.list){
                    DownloadsWidget.indexes.delete(uuid);
                    $('.queue-table [uuid="' + uuid + '"]').remove();
                }
                break;
        }
    }
}