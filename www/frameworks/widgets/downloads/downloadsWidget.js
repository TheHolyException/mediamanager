class DownloadsWidget extends BaseWidget {
    static indexes = new Map();

    constructor(name = "Statistics") {
        super(name);
    }

    render() {
        let widget = $(`
        <div class="widget">
            <h1>Download Queue</h1>
            <nav class="queue-action-bar">
                <a class="add-sources-btn" onclick="openAddSourcePopup()"><i class="fa fa-plus"></i> Add</a>
                <a class="commit-sources-btn"><i class="fas fa-paper-plane"></i> Commit</a>
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

    static addDownloaderItem_v2(item) {
        let index = DownloadsWidget.indexes.get(item.uuid);
        let row = undefined;

        if (index == undefined) {//its a new item and can be aded to all existing tables at once
            let table = $('.queue-table');

            //Row
            row = $('<tr>')
                .attr('uuid', entry.uuid);

            //==============================Column - toolbar===============================
            //Column - toolbar
            let toolbar = $('<td>')
                .addClass('toolbar')
                .attr('col', 'actions');

            //Column - toolbar - delete
            let deleteBtn = $('<i>')
                .addClass('fa fa-trash')
                .click(function () {
                    row.remove();

                    let data = DownloadsWidget.indexes.get(index.uuid);
                    if (data.state != "new") {
                        sendPacket("del", "default", { "uuid": index.uuid });
                    }
                    DownloadsWidget.indexes.delete(index.uuid);
                });

            //Column - toolbar - resend
            let resentBtn = $('<i>')
                .addClass('fa fa-rotate-right')
                .css('display', index.state.startsWith('Error') ? 'block' : 'none')
                .click(function () {
                    let data = DownloadsWidget.indexes.get(index.uuid);
                    if (!data.state.startsWith("Error")) return;
                    data.state = "new";
                    sendPacket("put", "default", {
                        "list": [data]
                    });
                });

            toolbar.append(deleteBtn, resentBtn);
            //===============================================================================
            //==============================Column - State===============================
            let state = $('<td>')
                .text(index.state.split('\n')[0])
                .attr('col', 'state')
                .click(() => navigator.clipboard.writeText(index.state));
            //===============================================================================
            //==============================Column - URL===============================
            let url = $('<td>')
                .attr('col', 'url')
                .append($('<a>').text(index.url).attr('href', index.url));
            //===============================================================================
            //==============================Column - Target===============================
            let dirPath = index.target;
            let subPath = index.title;
            if (!dirPath.endsWith('/'))
                dirPath += '/';
            if (!subPath)
                subPath = '?';

            let target = $('<td>')
                .attr('col', 'target')
                .text(dirPath + subPath);
            //===============================================================================
            row.append(toolbar, state, url, target);
            table.append(row);
        }
        else {//its an existing item and needs to be added to each table seperatly to update already existing rows

        }
    }

    static addDownloaderItem(entry) {
        let uuid = DownloadsWidget.indexes.get(entry.uuid);
        let row = null;

        if (uuid == undefined) { // Object is not known and will be added
            let table = $('.queue-table');

            // Row
            row = $('<tr>')
                .attr('id', entry.uuid);
            table.append(row);

            // Column - toolbar
            {
                let toolbar = $('<td>')
                    .addClass('toolbar')
                    .attr('col', 'actions');

                // Delete
                {
                    let btnDelete = $('<i>')
                        .attr("id", "delete-" + entry.uuid)
                        .addClass("fa fa-trash")
                        .click(function () {
                            row.remove();

                            // We only send an update to the server, if the server knows about this item
                            let entity = DownloadsWidget.indexes.get(entry.uuid);
                            DownloadsWidget.indexes.delete(entity.uuid);
                            if (entity.state != "new") {
                                sendPacket("del", "default", { "uuid": entry.uuid });
                            }
                        });

                    toolbar.append(btnDelete);
                }

                // Resend
                {
                    let btnResent = $('<i>')
                        .attr("id", "resent-" + entry.uuid)
                        .addClass("fa fa-rotate-right");

                    btnResent.click(function () {
                        let entity = DownloadsWidget.indexes.get(entry.uuid);
                        if (!entity.state.startsWith("Error")) return;
                        entity.state = "new";
                        //sendPacket("put", "default", entity);
                        sendPacket("put", "default", {
                            "list": [entity]
                        });
                    });

                    let displayMode = entry.state.startsWith('Error') ? 'block' : 'none';
                    btnResent.css('display', displayMode);

                    toolbar.append(btnResent);
                }

                row.append(toolbar);
            }

            // Column - Status
            let status = $('<td>')
                .text(entry.state.split('\n')[0])
                .attr("id", "status-" + entry.uuid)
                .attr('col', 'state')
                .click(() => navigator.clipboard.writeText(entry.state));
            row.append(status);

            // Column - URL
            let data = $('<td>')
                .attr("id", "url-" + entry.uuid)
                .attr('col', 'url')
                .append($('<a>').text(entry.url).attr('href', entry.url));
            row.append(data);

            // Column - Target
            let dirPath = entry.target;
            let subPath = entry.title;
            if (!dirPath.endsWith('/'))
                dirPath += '/';
            if (!subPath)
                subPath = '?';

            let target = $('<td>')
                .attr("id", "target-" + entry.uuid)
                .attr('col', 'target')
                .text(dirPath + subPath);
            row.append(target);

        } else {
            let dirPath = entry.target;
            let subPath = entry.title;
            if (!dirPath.endsWith('/'))
                dirPath += '/';
            if (!subPath)
                subPath = '?';

            let statusColumn = $('#status-' + entry.uuid);
            statusColumn.text(entry.state.split('\n')[0]);

            let targetColumn = $('#target-' + entry.uuid);
            targetColumn.text(dirPath + subPath);

            let btnResent = $('#resent-' + entry.uuid);
            let displayMode = entry.state.startsWith('Error') ? 'block' : 'none';
            btnResent.css('display', displayMode);

            row = $('#' + entry.uuid);
        }

        let tooltip = "Status:\n" + entry.state + "\n";
        tooltip += "Options:\n";
        for (let key in entry.options) {
            tooltip += "\t" + key + ": " + entry.options[key] + "\n"
        }
        row.attr('title', tooltip);

        row.get(0).classList = DownloadsWidget.getStatusClass(entry.state);
        DownloadsWidget.indexes.set(entry.uuid, entry);
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
}