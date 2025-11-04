class DownloadsWidget extends BaseWidget {
    static indexes = new Map();

    constructor(options = {}) {
        super({
            type: 'downloads',
            width: 2,
            height: 2,
            ...options
        });
    }

    createContent() {
        let widgetContent = $(`
        <div class="widget scrollbar-on-hover custom-scrollbar" widget-name="DownloadsWidget">
            <div class="widget-header">
                <div class="widget-title">
                    <i class="fas fa-download"></i>
                    <h1 class="widget-handle">Download Manager</h1>
                </div>
            </div>
            <div class="downloads-content">
                <div class="downloads-header">
                    <div class="queue-stats">
                        <div class="stat-card">
                            <div class="stat-icon"><i class="fas fa-download"></i></div>
                            <div class="stat-info">
                                <span class="stat-value" id="total-downloads">0</span>
                                <span class="stat-label">Total</span>
                            </div>
                        </div>
                        <div class="stat-card">
                            <div class="stat-icon"><i class="fas fa-bars-progress"></i></div>
                            <div class="stat-info">
                                <span class="stat-value" id="active-downloads-count">0</span>
                                <span class="stat-label">Active</span>
                            </div>
                        </div>
                        <div class="stat-card">
                            <div class="stat-icon"><i class="fas fa-check-circle"></i></div>
                            <div class="stat-info">
                                <span class="stat-value" id="completed-downloads">0</span>
                                <span class="stat-label">Completed</span>
                            </div>
                        </div>
                        <div class="stat-card">
                            <div class="stat-icon"><i class="fas fa-exclamation-triangle"></i></div>
                            <div class="stat-info">
                                <span class="stat-value" id="failed-downloads-count">0</span>
                                <span class="stat-label">Failed</span>
                            </div>
                        </div>
                    </div>
                    <nav class="queue-action-bar">
                        <button class="action-btn add-sources-btn">
                            <i class="fa fa-plus"></i>
                            <span>Add Downloads</span>
                        </button>
                        <button class="action-btn commit-sources-btn">
                            <i class="fas fa-paper-plane"></i>
                            <span>Start Queue</span>
                        </button>
                        <button class="action-btn retry-all-btn">
                            <i class="fa-solid fa-rotate-right"></i>
                            <span>Retry Failed</span>
                        </button>
                        <button class="action-btn delete-all-btn">
                            <i class="fa fa-trash"></i>
                            <span>Clear All</span>
                        </button>
                        <button class="action-btn delete-completed-btn">
                            <i class="fa fa-check"></i>
                            <span>Remove Completed</span>
                        </button>
                    </nav>
                </div>
                <div class="table-container scrollable-content">
                    <table class="queue-table">
                        <thead>
                            <tr>
                                <th col="actions">
                                    <i class="fas fa-cog"></i>
                                    <span>Actions</span>
                                </th>
                                <th col="state">
                                    <i class="fas fa-info-circle"></i>
                                    <span>Status</span>
                                </th>
                                <th col="url">
                                    <i class="fas fa-link"></i>
                                    <span>Source URL</span>
                                </th>
                                <th col="target">
                                    <i class="fas fa-folder"></i>
                                    <span>Target Path</span>
                                </th>
                            </tr>
                        </thead>
                        <tbody class="queue-body">
                        </tbody>
                    </table>
                </div>
            </div>
        </div>
        `);

        widgetContent.find('.add-sources-btn').click(function () {
            // Simple function call with timeout fallback
            setTimeout(function () {
                if (typeof window.openAddSourcePopup === 'function') {
                    window.openAddSourcePopup();
                } else if (typeof openAddSourcePopup === 'function') {
                    openAddSourcePopup();
                } else {
                    console.error('openAddSourcePopup function not available');
                    console.log('Available window functions:', Object.keys(window).filter(key => typeof window[key] === 'function' && key.toLowerCase().includes('popup')));
                    alert('Add source dialog is not ready yet. Please try again in a moment.');
                }
            }, 100);
        });

        widgetContent.find('.commit-sources-btn').click(function () {
            DownloadsWidget.commit();
        });

        widgetContent.find('.retry-all-btn').click(function () {
            widgetContent.find('.failed [action="resend"], .retry [action="resend"]').click();
        });

        widgetContent.find('.delete-all-btn').click(function () {
            deleteAllDownloadsAPI();
        });

        widgetContent.find('.delete-completed-btn').click(function () {
            widgetContent.find('.success [action="delete"]').click();
        });

        sendPacket("syn", "default");
        return widgetContent.get(0);
    }

    static commit() {
        let commitPacket = [];
        for (let [uuid, data] of DownloadsWidget.indexes) {
            // We only want to put new entries
            if (data.state != "new") continue;
            commitPacket.push(data);
        }

        addDownloadsAPI(commitPacket);
    }

    static addDownloaderItem(item) {
        let index = DownloadsWidget.indexes.get(item.uuid);
        let tableBodies = $('.queue-body');
        let row = undefined;

        if (index == undefined) {//its a new item and can be aded to all existing tables at once
            row = DownloadsWidget.createNewRow(item);
            DownloadsWidget.setStatusAndTooltip(row, item);
            tableBodies.append(row);
        }
        else {//its an existing item and needs to be added to each table seperatly to update already existing rows
            let dirPath = item.target;
            let subPath = item.title;
            if (!dirPath.endsWith('/'))
                dirPath += '/';
            if (!subPath)
                subPath = '?';

            for (let t of tableBodies) {
                let tableBody = $(t);
                row = tableBody.find('[uuid="' + item.uuid + '"]');

                if (row.length == 0) {
                    row = DownloadsWidget.createNewRow(item);
                    tableBody.append(row);
                }
                else {
                    let stateCol = row.find('[col="state"]');
                    let statusIndicator = stateCol.find('.status-indicator');
                    let statusText = stateCol.find('.status-text');
                    
                    // Update status text
                    statusText.text(item.state);
                    
                    // Update status indicator symbols
                    statusIndicator.find('.error-symbol, .warning-symbol').remove();
                    if (item.hadServerError) {
                        statusIndicator.append($('<i>').addClass('fas fa-exclamation-circle error-symbol').attr('title', 'Download had errors'));
                    } else if (item.hadWarning) {
                        statusIndicator.append($('<i>').addClass('fas fa-exclamation-triangle warning-symbol').attr('title', 'Download had warnings'));
                    }

                    let targetCol = row.find('[col="target"]');
                    targetCol.text(dirPath + subPath);

                    let btnResent = row.find('[action="resend"]');
                    btnResent.css('display', 'block'); // Always visible
                    // Update disabled state based on current item state
                    if (item.state === "new" || item.state.includes("Downloading")) {
                        btnResent.addClass('disabled');
                    } else {
                        btnResent.removeClass('disabled');
                    }

                    let btnResentWOValidation = row.find('[action="resendSkipValidation"]');
                    btnResentWOValidation.toggleClass('force-hide', !(item.state.startsWith('Validation Error')))

                    let resentStream = row.find('[action="resendOtherStream"]')
                    resentStream.css('display', item.autoloaderData != undefined && item.state.startsWith('Error') ? 'block' : 'none')

                    let downloadLogBtn = row.find('[action="downloadLog"]');
                    downloadLogBtn.toggleClass('force-hide', !(item.hadServerError || item.hadWarning));
                }

                DownloadsWidget.setStatusAndTooltip(row, item);
            }
        }


        DownloadsWidget.indexes.set(item.uuid, item);
        DownloadsWidget.updateStatistics();
    }

    static updateStatistics() {
        const items = Array.from(DownloadsWidget.indexes.values());
        const total = items.length;
        const active = items.filter(item => item.state.includes('Downloading') || item.state.includes('Committed')).length;
        const completed = items.filter(item => item.state.includes('Completed')).length;
        const failed = items.filter(item => item.state.includes('Error')).length;

        $('#total-downloads').text(total);
        $('#active-downloads-count').text(active);
        $('#completed-downloads').text(completed);
        $('#failed-downloads-count').text(failed);
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
            .css('display', 'flex')
            .css('justify-content', 'center')
            .css('align-items', 'center')
            .attr('col', 'actions');

        //Column - toolbar - delete
        let deleteBtn = $('<button>')
            .attr('action', 'delete')
            .attr('title', 'Delete from List')
            .addClass('action-icon-btn delete-btn')
            .append($('<i>').addClass('fa fa-trash'))
            .click(function () {
                let data = DownloadsWidget.indexes.get(item.uuid);
                if (data.state != "new") {
                    deleteDownloadAPI(item.uuid);
                }
                DownloadsWidget.indexes.delete(item.uuid);
                $(this).closest('tr').remove();
                DownloadsWidget.updateStatistics();
            });

        //Column - toolbar - resend
        let resentBtn = $('<button>')
            .attr('action', 'resend')
            .attr('title', 'Restart Download')
            .addClass('action-icon-btn retry-btn')
            .append($('<i>').addClass('fa fa-rotate-right'))
            .css('display', 'block') // Always visible
            .click(function () {
                let data = DownloadsWidget.indexes.get(item.uuid);
                // Don't allow retry if disabled or for certain states
                if ($(this).hasClass('disabled') || data.state === "new" || data.state.includes("Downloading")) return;
                data.state = "new";
                addDownloadsAPI([data]);
            });

        // Set initial disabled state based on item state
        if (item.state === "new" || item.state.includes("Downloading")) {
            resentBtn.addClass('disabled');
        }

        //Column - toolbar - resend with other stream
        let resentWithOtherStreamBtn = $('<button>')
            .attr('action', 'resendOtherStream')
            .attr('title', 'Restart Download with other Stream')
            .addClass('action-icon-btn stream-btn')
            .append($('<i>').addClass('fa-solid fa-code-branch'))
            .toggleClass('force-hide', !(item.autoloaderData != undefined && item.state.startsWith('Error')))
            .click(function () {
                let data = DownloadsWidget.indexes.get(item.uuid);
                SelectStreamPopup.request(data);
            });

        //Column - toolbar - view log
        let downloadLogBtn = $('<button>')
            .attr('action', 'downloadLog')
            .attr('title', 'Open Log')
            .addClass('action-icon-btn log-btn')
            .append($('<i>').addClass('fas fa-file-text'))
            .toggleClass('force-hide', !(item.hadServerError || item.hadWarning))
            .click(function () {
                window.open('/api/view-log/' + item.uuid, '_blank');
            });

        toolbar.append(resentBtn, deleteBtn, resentWithOtherStreamBtn, downloadLogBtn);

        //Column - toolbar - resend with validationSkipping
        let resendSkipValidation = $('<button>')
            .attr('action', 'resendSkipValidation')
            .attr('title', 'Restart Download with Validation Skipping')
            .addClass('action-icon-btn stream-btn')
            .append($('<i>').addClass('fa-solid fa-forward'))
            .toggleClass('force-hide', !(item.state.startsWith('Validation Error:')))
            .click(function () {
                let data = DownloadsWidget.indexes.get(item.uuid);
                // Don't allow retry if disabled or for certain states
                if ($(this).hasClass('disabled') || data.state === "new" || data.state.includes("Downloading")) return;
                data.state = "new";
                data.options.skipValidation = "true";
                addDownloadsAPI([data]);
            });
        toolbar.append(resentBtn, deleteBtn, resendSkipValidation);
        //===============================================================================
        //==============================Column - State===============================
        let statusText = item.state.split('\n')[0];
        let statusIndicator = $('<div>').addClass('status-indicator');
        
        // Add warning/error symbols
        if (item.hadServerError) {
            statusIndicator.append($('<i>').addClass('fas fa-exclamation-circle error-symbol').attr('title', 'Download had errors'));
        } else if (item.hadWarning) {
            statusIndicator.append($('<i>').addClass('fas fa-exclamation-triangle warning-symbol').attr('title', 'Download had warnings'));
        }
        
        let state = $('<td>')
            .attr('col', 'state')
            .addClass('status-cell')
            .click(() => navigator.clipboard.writeText(item.state))
            .append(
                $('<div>').addClass('status-content').append(
                    statusIndicator,
                    $('<span>').addClass('status-text').text(statusText)
                )
            );
        //===============================================================================
        //==============================Column - URL===============================
        let url = $('<td>')
            .attr('col', 'url')
            .addClass('url-cell')
            .append(
                $('<div>').addClass('url-content').append(
                    $('<i>').addClass('fas fa-link url-icon'),
                    $('<a>').addClass('url-link')
                        .text(item.url.length > 50 ? item.url.substring(0, 50) + '...' : item.url)
                        .attr('href', item.url)
                        .attr('title', item.url)
                        .attr('target', '_blank')
                )
            );
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
            .addClass('target-cell')
            .append(
                $('<div>').addClass('target-content').append(
                    $('<i>').addClass('fas fa-folder target-icon'),
                    $('<span>').addClass('target-path').text(dirPath + subPath).attr('title', dirPath + subPath)
                )
            );
        //===============================================================================
        row.append(toolbar, state, url, target);
        return row;
    }

    static getStatusClass(statusMessage) {
        if (statusMessage.includes("Validation Error")) {
            return 'validation'
        }

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

        if (statusMessage.includes("Retry scheduled for")) {
            return 'retry'
        }
    }

    static addNewElement(urls, settings, targetSelection, subfolder) {
        if (!urls) {
            console.error("No URLs provided to addNewElement");
            return;
        }
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
                for (let uuid of content.list) {
                    DownloadsWidget.indexes.delete(uuid);
                    $('.queue-table [uuid="' + uuid + '"]').remove();
                }
                DownloadsWidget.updateStatistics();
                break;
        }
    }

}