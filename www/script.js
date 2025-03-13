let indexes = new Map();
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


$(document).ready(function () {
    connect(); // Connect WebSocket
    addUiEvents();
});

function addUiEvents(){
    let mainTabBar = new TabBar($('.main-nav-bar'), $('.main-tab-container'));
}

function onWSResponseDefault(cmd, content) {
    switch (cmd) {
        /* case "systemInfo":
            let heapInfo = document.getElementById("heapInfo");
            heapInfo.innerText = content.heap;

            let dockerInfo = document.getElementById("dockerInfo");
            dockerInfo.innerText = content.containerHeap;

            let downloaderInfo = document.getElementById("downloaderInfo");
            downloaderInfo.innerText = content.handler;
            break; */
        case "syn": // Acknowledge data sync
            for (i = 0; i < content.data.length; i++) {
                let entry = content.data[i];
                addObjectToTable(entry);
            }
            break;
        case "targetFolders":
            targetFolders = content.targets;
            break;
        /* case "del":
            indexes.delete(content.uuid);
            document.getElementById(content.uuid).remove();
            break;
        case "setting":
            let settingObject = document.getElementById(content.key);
            if (settingObject.tagName == 'INPUT') {
                settingObject.value = content.val;
            }
            break;*/
        case "requestSubfoldersResponse":
            subfolderSelection = $('.subfolder')
            subfolderSelection.empty();
            subfolderSelection.append($('<option>'));

            for (let folder of content.subfolders) {
                //let folder = content.subfolders[index];
                let option = $('<option>');
                option.attr('value', folder);
                option.text(folder);
                subfolderSelection.append(option);
            }
            break; 
    }
}

function addObjectToTable(entry) {
    let uuid = indexes.get(entry.uuid);
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
                    .click(function() {
                        row.remove();
    
                        // We only send an update to the server, if the server knows about this item
                        let entity = indexes.get(entry.uuid);
                        indexes.delete(entity.uuid);
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
                    let entity = indexes.get(entry.uuid);
                    if (!entity.state.startsWith("Error")) return;
                    entity.state = "new";
                    sendPacket("put", "default", entity);
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

        /* // Column - Name
        let name = $('<td>')
            .attr("id", "name-" + entry.uuid)
            .attr('col', 'name')
            .text(entry.title);
        row.append(name); */

        // Column - URL
        let data = $('<td>')
            .attr("id", "url-" + entry.uuid)
            .attr('col', 'url')
            .append($('<a>').text(entry.url).attr('href', entry.url));
        row.append(data);

        // Column - Target
        let dirPath = entry.target;
        let subPath = entry.title;
        if(!dirPath.endsWith('/'))
            dirPath += '/';
        if(!subPath)
            subPath = '?';

        let target = $('<td>')
            .attr('col', 'target')
            .text(dirPath + subPath);
        row.append(target);

    } else {
        let statusColumn = $('#status-' + entry.uuid);
        statusColumn.text(entry.state.split('\n')[0]);

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

    row.get(0).classList = getStatusClass(entry.state);
    indexes.set(entry.uuid, entry);
}

// Events
function onCommit() {
    console.log("commit");
    //let rows = document.getElementById("table").getElementsByTagName("tr");
    let rows = $('.queue-table').find('tr');
    console.log(rows);
    for (i = 1; i < rows.length; i++) {
        let uuid = rows[i].id;
        let data = indexes.get(uuid);

        console.log(rows[i]);

        // We only want to put new entries
        if (data.state != "new") continue;

        sendPacket("put", "default", data)
    }
}

// Utils
function uuidv4() {
    return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'
        .replace(/[xy]/g, function (c) {
            const r = Math.random() * 16 | 0,
                v = c == 'x' ? r : (r & 0x3 | 0x8);
            return v.toString(16);
        });
}

function setWebSocketStatusFeedback(status) {
    let statusElement = document.getElementById('statusElement');
    for (i = 0; i < statusElement.classList.length; i++) {
        if (statusElement.classList[i].startsWith('statusElement-status'))
            statusElement.classList.remove(statusElement.classList[i]);
    }
    statusElement.classList.add('statusElement-status' + status);

    let statusText = document.getElementById("statusText");
    switch (status) {
        case 1:
            statusText.innerText = "Connecting...";
            break;
        case 2:
            statusText.innerText = "Connected";
            break;
        case 4:
            statusText.innerText = "WebSocket connection failed";
            break;
    }
}

function getStatusClass(statusMessage) {
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










function addNewElement(urls, settings, targetSelection, subfolder) {
    /* var txtURL = document.getElementById("url");
    let url = "";
    if (resolvedLinks.length > 0) { // Wenn aniworldlinks vorhanden sind, dann diese bevorzugen
        url = resolvedLinks;
    } else {
        url = document.getElementById("url").value;
    } */
    let urlArray = urls.split(";");
    //let targetSelection = "stream-animes";//document.getElementById("targetSelection").value;
    //let subfolder = ""//document.getElementById("subfolder").value;

    for(let urlElement of urlArray){
        let obj = {
            uuid: uuidv4(),
            state: "new",
            created: new Date().getTime(),
            url: urlElement,
            options: settings,
            target: targetSelection + "/" + subfolder
        }
        addObjectToTable(obj);
    }

    //txtURL.value = ""; // reset input field
}