let indexes = new Map();

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
});

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
        /* case "del":
            indexes.delete(content.uuid);
            document.getElementById(content.uuid).remove();
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
            break; */
    }
}

function addObjectToTable(entry) {
    let uuid = indexes.get(entry.uuid);
    let row = null;

    if (uuid == undefined) { // Object is not known and will be added
        console.log("TEST");
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
                    .addClass("fa fa-trash");

                btnDelete.click(function () {
                    table.remove(row);

                    // We only send an update to the server, if the server knows about this item
                    let entity = indexes.get(entry.uuid);
                    indexes.delete(entity.uuid);
                    if (entity.state != "new") {
                        sendPacket("del", "default", { "uuid": entry.uuid });
                    }
                })

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

        // Column - URL
        let data = $('<td>')
            .attr("id", "url-" + entry.uuid)
            .attr('col', 'url')
            .text(entry.url);
        row.append(data);

        // Column - Target 
        let target = $('<td>')
            .attr('col', 'target')
            .text(entry.target);
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








function openAddSourcePopup() {
    var popup = new ModalLightboxPopup();

    popup.setContentSelectorStyles({
        "": {
            "background-color": "var(--darkBGColor2)",
            "border-radius": "25px"
        },
        " .src-input-container":{
            "display": "flex",
            "flex-direction": "column",
            "gap": "15px"
        },
        " .tab-bar": {
            display: "flex"
        },
        " .tab-btn": {
            color: "var(--darkerTextColor)",
            cursor: "pointer",
            padding: "5px 8px"
        },
        " .tab-btn.active": {
            color: "var(--textColor)",
            "box-shadow": "var(--selectedBorderColor) 0px -2px inset"
        },
        " .tab-btn:hover": {
            color: "var(--textColor)"
        },
        " .tab":{
            "min-width": "500px",
            "flex-direction": "column",
            display: "none",
            gap: "15px"
        },
        " .tab.active":{
            display: "flex"
        },
        " .tab-line-container":{
            display: "flex"
        },
        " .lable":{
            "background-color": "var(--darkBGColor1)",
            "border-top-left-radius": "50px",
            "border-bottom-left-radius": "50px",
            "font-weight": "bold",
            "line-height": "56px",
            "padding": "0px 20px;",
            "padding-right": "10px"
        },
        " :is(input, select)":{
            "flex-grow": 1,
            "background-color": "var(--darkBGColor1)",
            "border-top-right-radius": "50px",
            "border-bottom-right-radius": "50px",
            "font-size": "1.2em",
            border: "2px solid var(--darkBGColor1)",
            outline: "none",
            padding: "15px",
            color: "var(--textColor)"
        },
        " .action-bar":{
            display: "fkex",
            gap: "15px"
        },
        " .action-btn":{
            "border-radius": "10px",
            "background-color": "var(--committed)",
            border: "none",
            outlibne: "none",
            color: "var(--textColor)",
            padding: "10px",
            cursor: "pointer"
        },
        " .resolveInfo:empty":{
            display: "none"
        },
        " .resolveInfo":{
            display: "block"
        },
        " .url-settings":{
            "flex-direction": "column",
            display: "flex",
            gap: "15px"
        },
        " .settings-container":{
            "flex-direction": "column",
            display: "flex",
            gap: "15px"
        }
    });

    popup.addContentElement(createSourceInput());

    popup.addNavbarButton({
        func: function () {
            console.log("closing");
        },
        closePopup: true,
        displayText: "Cancel"
    });

    popup.addNavbarButton({
        func: function () {
            addNewElement(getUrls($('.tab-btn.active').attr('tab-name')), getSettings($('.settings-container').find('[config]')));
        },
        closePopup: true,
        displayText: "Add"
    });

    popup.showIn($('html'));
}

function getSettings(settingInputs) {
    let options = {};
    for(let element of settingInputs){
        element = $(element);
        options[element.attr('config')] = getInputValue(element).toString();
    }

    /* let elements = document.getElementsByClassName("settingItem");
    for (i = 0; i < elements.length; i++) {
        let element = elements[i];
        if (element.tagName == 'INPUT')
            options[element.id] = element.checked.toString();
        else if (element.tagName == 'SELECT') {
            options[element.id] = element.value.toString();
        }
    } */
    return options;
}

function createSourceInput() {
    let activeTab = 'url-tab';
    var content = $(`
        <div class="src-input-container">
            <nav class="tab-bar">
                <a class="tab-btn url-tab-btn active" tab-name="url">URL</a>
                <a class="tab-btn aniworld-tab-btn" tab-name="aniworld">Aniworld</a>
            </nav>
            <div class="tab url-tab active" tab-name="url">
                <label class="tab-line-container">
                    <span class="lable">URL</span>
                    <input type="text" class="url">
                </label>
            </div>
            <div class="tab aniworld-tab" tab-name="aniworld">
                <label class="tab-line-container">
                    <span class="lable">URL</span>
                    <input type="text" class="aniworld-url">
                </label>
                <label class="tab-line-container">
                    <span class="lable">Language</span>
                    <select id="aniworldLanguage">
                        <option value="1">German</option>
                        <option value="2">English SUB</option>
                        <option value="3">German SUB</option>
                    </select>
                </label>
                <label class="resolveInfo"></label>
                <div class="action-bar">
                    <button id="btnResolve" class="action-btn">Resolve</button>
                    <button id="btnReset" class="action-btn">Reset</button>
                </div>
            </div>
            <div class="url-settings"></div>
            <div>
                <label class="tab-line-container">
                    <span class="lable">URL</span>
                    <input type="text" class="subfolders">
                    <div class="subfolder-options">
                        <option>Volvo</option>
                        <option>Saab</option>
                        <option>Mercedes</option>
                        <option>Audi</option>
                    </div>
                </label>
            </div>
        </div>
    `);

    content.find('.tab-btn').click(function(){
        content.find('.tab-btn.active').removeClass('active');
        let self = $(this);
        self.addClass('active');

        content.find('.tab.active').removeClass('active');
        content.find('.tab[tab-name="' + self.attr('tab-name') + '"]').addClass('active');
    });

    content.find('#btnResolve').click(function(){
        let aniworld = new Aniworld(content.find('.resolveInfo'));
        aniworld.resolveLinks($('.aniworld-url').val(), $('#aniworldLanguage').val());
    });

    content.find('#btnReset').click(function(){
        Aniworld.resetResolvedLinks();
    });

    loadURLSettings(content.find('.url-settings'), settings);

    return content;
}

function loadURLSettings(container, settings){
    /* const settings = [
        {
            title: "VOE",
            items: {
                enableSeasonAndEpisodeRenaming: {
                    title: "Rename to Episode and Season",
                    type: "boolean",
                    defaultValue: true
                }
            }
        }
    ] */
    let addSeperator = false;
    
    container.append('<hr>');
    container.append($('<h3>').text("Settings"));
    for(let configSection of settings){
        let tab = $('<div>').addClass('settings-container');
        tab.append($('<h4>').text(configSection["title"]));
        
        for(let configName in configSection.items){
            let configDetails = configSection.items[configName];

            let lbl = $('<label>').append(
                $('<span>').text(configDetails.title).addClass('config-lbl-txt'),
                getInputElement(configDetails.type, configDetails.defaultValue).attr('config', configName)
            );

            tab.append(lbl);
        }

        if(addSeperator){
            container.append('<hr>');
        }
        container.append(tab);
        addSeperator = true;
    }
}

function getInputElement(type, value){
    switch(type){
        case "boolean":
            return $('<input>').attr('type', "checkbox");
    }
}

function getInputValue(inputElement){
    let type = inputElement.attr('type');
	let value = null;
	switch (type) {
		case "checkbox":
			value = inputElement.prop('checked');
			break;
		default:
			value = inputElement.val();
			break;
	}

    return value;
}

function getUrls(activeTab){
    console.log("get urls of:", activeTab);

    let val = null;
    switch(activeTab){
        case "url":
            val = $('.url-tab .url').val();
            $('.url-tab .url').val("");
            return val;
        case "aniworld":
            return Aniworld.resolvedLinks;
    }
}

function addNewElement(urls, settings) {
    /* var txtURL = document.getElementById("url");
    let url = "";
    if (resolvedLinks.length > 0) { // Wenn aniworldlinks vorhanden sind, dann diese bevorzugen
        url = resolvedLinks;
    } else {
        url = document.getElementById("url").value;
    } */
    let urlArray = urls.split(";");
    let targetSelection = "stream-animes";//document.getElementById("targetSelection").value;
    let subfolder = ""//document.getElementById("subfolder").value;

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