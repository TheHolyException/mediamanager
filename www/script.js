let indexes = new Map();
const yeti = new Yeti();

const settings = {
    voe: {
        title: "VOE",
        items: {
            enableSeasonAndEpisodeRenaming: "Rename to Episode and Season",
            enableSessionRecovery: "Session Recovery",
            useDirectMemory: "Use Direct Memory"
        }
    }
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

function onWSResponseDefault(cmd, content) {
    switch (cmd) {
        case "systemInfo":
            let heapInfo = document.getElementById("heapInfo");
            heapInfo.innerText = content.heap;

            let dockerInfo = document.getElementById("dockerInfo");
            dockerInfo.innerText = content.containerHeap;

            let downloaderInfo = document.getElementById("downloaderInfo");
            downloaderInfo.innerText = content.handler;
            break;
        case "syn": // Acknowledge data sync
            for (i = 0; i < content.data.length; i++) {
                let entry = content.data[i];
                addObjectToTable(entry);
            }
            break;
        case "del":
            indexes.delete(content.uuid);
            document.getElementById(content.uuid).remove();
            break;
        case "sch": // State Change of item
            indexes.get(content.uuid).state = content.state;
            break;
        case "setting":
            let settingObject = document.getElementById(content.key);
            if (settingObject.tagName == 'INPUT') {
                settingObject.value = content.val;
            }
            break;
        case "requestSubfoldersResponse":
            let subfolderSelection = document.getElementById("subfolderSelection");
            subfolderSelection.innerHTML = '';
            { // Dummy element adden
                let option = document.createElement('option');
                subfolderSelection.appendChild(option);
            }

            for (let index in content.subfolders) {
                let folder = content.subfolders[index];
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
        row = document.createElement("tr");
        row.setAttribute("id", entry.uuid);
        table.appendChild(row);

        // Column - toolbar
        {
            let toolbar = document.createElement("td");
            toolbar.classList.add('toolbar');

            // Delete
            {
                let btnDelete = document.createElement("i");
                btnDelete.setAttribute("id", "delete-" + entry.uuid);
                btnDelete.classList.add("fa");
                btnDelete.classList.add("fa-trash");
                btnDelete.addEventListener('click', function () {
                    table.removeChild(row);

                    // We only send an update to the server, if the server knows about this item
                    let entity = indexes.get(entry.uuid);
                    indexes.delete(entity.uuid);
                    if (entity.state != "new") {
                        sendPacket("del", "default", { "uuid": entry.uuid });
                    }
                })
                toolbar.appendChild(btnDelete);
            }

            // Resend
            {
                let btnResent = document.createElement("i");
                btnResent.setAttribute("id", "resent-" + entry.uuid);
                btnResent.classList.add("fa");
                btnResent.classList.add("fa-rotate-right")
                btnResent.addEventListener('click', function () {
                    let entity = indexes.get(entry.uuid);
                    if (!entity.state.startsWith("Error")) return;
                    entity.state = "new";
                    sendPacket("put", "default", entity);
                });

                let displayMode = entry.state.startsWith('Error') ? 'block' : 'none';
                btnResent.style.display = displayMode;

                toolbar.appendChild(btnResent);
            }

            row.appendChild(toolbar);
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
        btnResent.style.display = displayMode;

        row = document.getElementById(entry.uuid);
    }

    let tooltip = "Status:\n" + entry.state + "\n";
    tooltip += "Options:\n"
    for (let key in entry.options) {
        tooltip += "\t" + key + ": " + entry.options[key] + "\n"
    }
    row.title = tooltip;

    row.classList = getStatusClass(entry.state);
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

        sendPacket("setting", "default", { key: key, val: val })
    }
}

function addNewElement() {
    var txtURL = document.getElementById("url");
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
    txtURL.value = ""; // reset input field
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

        sendPacket("put", "default", data)
    }
}

function onClear() {
    sendPacket("del-all", "default", {});

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
    sendPacket("requestSubfolders", "default", { "selection": selection })
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