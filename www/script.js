let indexes = new Map();

const settings = {
    voe: {
        title: "VOE",
        items: {
            enableSeasonAndEpisodeRenaming: "Rename to Episode and Season",
            enableSessionRecovery: "Session Recovery",
            useDirectMemory: "Use Direct Memory"
        }
    }
    /*,
    streamtape: {
        title: "Streamtape",
        items: {
            none: "none"
        }
    }*/
}

connect(); // Connect WebSocket
buildUI();

function buildUI() {
    var settingsContainer = document.getElementById("settingsContainer");
    for (let key in settings) {
        let group = settings[key];
        let groupDiv = document.createElement("div");
        groupDiv.classList.add("settingsGroup");

        // Title
        let groupTitle = document.createElement("label");
        groupTitle.innerText = group.title;
        groupDiv.appendChild(groupTitle)

        // Settings
        for (let settingKey in group.items) {
            let setting = group.items[settingKey];
            let settingDiv = document.createElement("div");

            // Input
            let settingInput = document.createElement("input");
            settingInput.setAttribute("type", "checkbox");
            settingInput.id = settingKey;
            settingInput.classList.add("settingItem");

            // Setting Label
            let settingLabel = document.createElement("label");
            settingLabel.innerText = setting;
            settingDiv.appendChild(settingLabel);
            settingLabel.appendChild(settingInput);

            groupDiv.appendChild(settingDiv);

            if (setting == "enableSeasonAndEpisodeRenaming")
                setting.checked = true;
        }

        settingsContainer.appendChild(groupDiv);
    }
}

function onElementAdd() {
    let url = "";
    if (resolvedLinks.length > 0) { // Wenn aniworldlinks vorhanden sind, dann diese bevorzugen
        url = resolvedLinks;
    } else {
        url = document.getElementById("url").value;
    }
    let urlArray = url.split(";");


    for (let i = 0; i < urlArray.length; i++) {
        let urlElement = urlArray[i];

        let uuid = uuidv4();
        let settings = getSettings();
        let targetSelection = document.getElementById("targetSelection").value;
        let subfolder = document.getElementById("subfolder").value;

        let obj = {
            uuid: uuid,
            state: "new",
            created: new Date().getTime(),
            url: urlElement,
            options: settings,
            target: targetSelection + "/" + subfolder
        }

        addObjectToTable(obj);
    }
    url.value = ""; // reset input field
}

function onWSResponseDefault(data) {
    switch (data.type) {
        case "systemInfo":
            let heapInfo = document.getElementById("heapInfo");
            heapInfo.innerText = data.heap;

            let dockerInfo = document.getElementById("dockerInfo");
            dockerInfo.innerText = data.containerHeap;

            let downloaderInfo = document.getElementById("downloaderInfo");
            downloaderInfo.innerText = data.handler;
            break;
        case "syn": // Acknowledge data sync
            for (i = 0; i < data.content.length; i++) {
                let entry = data.content[i];
                addObjectToTable(entry);
            }
            break;
        case "del":
            indexes.delete(data.uuid);
            document.getElementById(data.uuid).remove();
            break;
        case "sch": // State Change of item
            indexes.get(data.uuid).state = data.state;
            break;
        case "setting":
            let entry = data.content;
            let settingObject = document.getElementById(entry.key);
            if (settingObject.tagName == 'INPUT') {
                settingObject.value = entry.val;
            }
            break;
        case "targetSelectionResolved":
            let subfolderSelection = document.getElementById("subfolderSelection");
            subfolderSelection.innerHTML = '';
            { // Dummy element adden
                let option = document.createElement('option');
                subfolderSelection.appendChild(option);
            }

            for (let index in data.subfolders) {
                let folder = data.subfolders[index];
                let option = document.createElement('option');
                option.setAttribute('value', folder);
                option.innerText = folder;
                //option.setAttribute('text', folder);
                subfolderSelection.appendChild(option);
            }
            break;

    }
}

function addObjectToTable(entry) {
    let uuid = indexes.get(entry.uuid);
    let row = null;

    if (uuid == undefined) { // Object is not known and will be added
        let table = document.getElementById("table");

        // Row
        {
            row = document.createElement("tr");
            row.setAttribute("id", entry.uuid);
            table.appendChild(row);

            // Column - Delete
            let btnDelete = document.createElement("td");
            btnDelete.addEventListener('click', function () {
                table.removeChild(row);

                // We only send an update to the server, if the server knows about this item
                let entity = indexes.get(entry.uuid);
                indexes.delete(entity.uuid);
                if (entity.state != "new") {
                    send({
                        type: "del",
                        uuid: entry.uuid
                    });
                }
            })
            btnDelete.setAttribute("id", "delete-" + entry.uuid);
            let icon = document.createElement("i");
            icon.classList.add("fa");
            icon.classList.add("fa-trash");

            btnDelete.appendChild(icon);
            row.appendChild(btnDelete);
        }

        // Column - Resend
        {
            let btnResent = document.createElement("td");
            btnResent.addEventListener('click', function () {
                debugger;
                let entity = indexes.get(entry.uuid);
                if (!entity.state.startsWith("Error")) return;
                entity.state = "new";
                let request = {
                    type: "put",
                    content: entity
                }
                send(request);
            });
            btnResent.setAttribute("id", "resent-" + entry.uuid);

            let icon = document.createElement("i");
            icon.classList.add("fa");
            icon.classList.add("fa-rotate-right")

            let displayMode = entry.state.startsWith('Error') ? 'block' : 'none';
            icon.style.display = displayMode;

            btnResent.appendChild(icon);
            row.appendChild(btnResent);
        }

        // Column - Status
        let status = document.createElement("td");
        status.addEventListener('click', () => navigator.clipboard.writeText(entry.state));
        status.innerText = entry.state.split('\n')[0];
        status.setAttribute("id", "status-" + entry.uuid);
        row.appendChild(status);

        // Column - URL
        let data = document.createElement("td");
        data.setAttribute("id", "url-" + entry.uuid);
        data.innerText = entry.url;
        row.appendChild(data);

        // Column - Target 
        let target = document.createElement("td");
        target.innerHTML = entry.target;
        row.appendChild(target);

    } else {
        let statusColumn = document.getElementById("status-" + entry.uuid);
        statusColumn.innerText = entry.state.split('\n')[0];

        let btnResent = document.getElementById("resent-" + entry.uuid);
        let displayMode = entry.state.startsWith('Error') ? 'block' : 'none';
        btnResent.getElementsByTagName("i")[0].style.display = displayMode;

        row = document.getElementById(entry.uuid);
    }

    let tooltip = "Status:\n" + entry.state + "\n";
    tooltip += "Options:\n"
    for (let key in entry.options) {
        tooltip += "\t" + key + ": " + entry.options[key] + "\n"
    }
    row.title = tooltip;

    row.classList = getStatusClass(entry.state);
    console.log();
    indexes.set(entry.uuid, entry);
}


function getSettings() {
    let options = {};
    let elements = document.getElementsByClassName("settingItem");
    for (i = 0; i < elements.length; i++) {
        let element = elements[i];
        if (element.tagName == 'INPUT')
            options[element.id] = element.checked.toString();
        else if (element.tagName == 'SELECT') {
            options[element.id] = element.value.toString();
        }
    }
    return options;
}

function saveGlobalSettings() {
    let settings = document.getElementsByClassName('globalSetting');
    for (i = 0; i < settings.length; i++) {
        let key = settings[i].id;
        let val = settings[i].value;

        let request = {
            type: "setting",
            content: { key: key, val: val }
        }
        send(request);
    }
}

function addNewElement() {
    let url = "";
    if (resolvedLinks.length > 0) { // Wenn aniworldlinks vorhanden sind, dann diese bevorzugen
        url = resolvedLinks;
    } else {
        url = document.getElementById("url").value;
    }
    let urlArray = url.split(";");


    for (let i = 0; i < urlArray.length; i++) {
        let urlElement = urlArray[i];

        let uuid = uuidv4();
        let settings = getSettings();
        let targetSelection = document.getElementById("targetSelection").value;
        let subfolder = document.getElementById("subfolder").value;

        let obj = {
            uuid: uuid,
            state: "new",
            created: new Date().getTime(),
            url: urlElement,
            options: settings,
            target: targetSelection + "/" + subfolder
        }

        addObjectToTable(obj);
    }
    url.value = ""; // reset input field
}

// Events
function onCommit() {
    let rows = document.getElementById("table").getElementsByTagName("tr");
    for (i = 1; i < rows.length; i++) {
        let uuid = rows[i].id;
        let data = indexes.get(uuid);

        console.log(rows[i]);

        // We only want to put new entries
        if (data.state != "new") continue;


        let request = {
            type: "put",
            content: data
        }

        console.log(request);
        send(request);
    }
}

function onClear() {
    let request = {
        type: "del-all"
    }
    send(request)

    let table = document.getElementById("table");
    let keys = Object.keys(indexes);
    console.log(keys);
    console.log(indexes);
    for (const [key, item] of indexes) {
        if (item.state != "new")
            continue;
        indexes.delete(key);
        let row = document.getElementById(item.uuid);
        table.removeChild(row);
    }
}

function onPaste() {
    var autoadd = document.getElementById("autoadd");
    if (!autoadd.checked) return;
    var url = document.getElementById("url");
    setTimeout(function () {
        addNewElement();
    }, 100);
}

function onElementAdd() {
    addNewElement();
}

function onTargetSelection() {
    let selection = document.getElementById("targetSelection").value;
    let request = {
        type: "targetSelection",
        selection: selection
    }
    send(request);
    subfolderSelection.innerHTML = '';
    { // Dummy element adden
        let option = document.createElement('option');
        subfolderSelection.appendChild(option);
    }
}

function onSubfolderSelection() {
    let txtSubfolder = document.getElementById('subfolder');
    let val = document.getElementById('subfolderSelection').value;
    txtSubfolder.value = val;
}

// Utils

Date.prototype.getUTCTime = function () {
    return this.getTime() + (this.getTimezoneOffset() * 60000);
}

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