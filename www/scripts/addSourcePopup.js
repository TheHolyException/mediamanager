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
        },
        " .subfolder, .targetfolder": {
            "background-color": "var(--darkBGColor1)",
            "flex-grow": 1,
            "border-top-right-radius": "50px",
            "border-bottom-right-radius": "50px",
            padding: "15px",
            color: "var(--textColor)"
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
            DownloadsWidget.addNewElement(
                getUrls($('.tab-btn.active').attr('tab-name')), 
                getSettings($('.settings-container').find('[config]')),
                $('.targetfolder').val(),
                $('.subfolder').val()
            );
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
            <div class="tab-container">
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
            </div>
            <hr>
            <div class="url-settings"></div>
            <hr>
            <label class="tab-line-container">
                <span class="lable">Target Folder</span>
                <select class="targetfolder" onchange="onTargetSelection()">
                </select>
            </label>
            <label class="tab-line-container">
                <span class="lable">Subfolder</span>
                <textbox-dropdown class="subfolder">
                </textbox-dropdown>
            </label>
        </div>
    `);

    /* content.find('.tab-btn').click(function(){
        content.find('.tab-btn.active').removeClass('active');
        let self = $(this);
        self.addClass('active');

        content.find('.tab.active').removeClass('active');
        content.find('.tab[tab-name="' + self.attr('tab-name') + '"]').addClass('active');
    }); */
    let sourceTabBar = new TabBar(content.find('.tab-bar'), content.find('.tab-container'));

    content.find('#btnResolve').click(function(){
        let aniworld = new Aniworld(content.find('.resolveInfo'));
        aniworld.resolveLinks($('.aniworld-url').val(), $('#aniworldLanguage').val());
    });

    content.find('#btnReset').click(function(){
        Aniworld.resetResolvedLinks();
    });

    for(folder of targetFolders){
        content.find('.targetfolder').append(
            $('<option>').text(folder.displayName).attr("value", folder.identifier)
        );
    }
    

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

function onTargetSelection() {
    let selection = $('.targetfolder').get(0).value;
    sendPacket("requestSubfolders", "default", { "selection": selection })
    let subfolderSelection = $('.subfolder');
    subfolderSelection.empty();
    subfolderSelection.val('');
}