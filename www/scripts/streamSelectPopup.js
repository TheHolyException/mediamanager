class SelectStreamPopup {
    static downloadData = undefined

    static request(downloadData) {
        SelectStreamPopup.downloadData = downloadData;
        sendPacket("getAlternateProviders", "autoloader", downloadData.autoloaderData);
    }

    static open(downloadData, streamList) {
        var popup = new ModalLightboxPopup();

        popup.setContentSelectorStyles({
            "": {
                "background-color": "var(--darkBGColor2)",
                "border-radius": "25px"
            },
            " .src-input-container": {
                "display": "flex",
                "flex-direction": "column",
                "gap": "15px",
                "min-width": "500px"
            },
            " .lable": {
                "background-color": "var(--darkBGColor1)",
                "border-top-left-radius": "50px",
                "border-bottom-left-radius": "50px",
                "font-weight": "bold",
                "line-height": "56px",
                "padding": "0px 20px;",
                "padding-right": "10px"
            },
            " :is(input, select)": {
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
            " .tab-line-container": {
                display: "flex"
            },
            " .stream": {
                "background-color": "var(--darkBGColor1)",
                "flex-grow": 1,
                "border-top-right-radius": "50px",
                "border-bottom-right-radius": "50px",
                padding: "15px",
                color: "var(--textColor)"
            }
        });

        let content = SelectStreamPopup.#createSourceInput(streamList);
        popup.addContentElement(content);

        popup.addNavbarButton({
            func: function () {

            },
            closePopup: true,
            displayText: "Cancel"
        });

        popup.addNavbarButton({
            func: function () {
                let data = SelectStreamPopup.downloadData;
                console.log(data);
                console.log(data.autoloaderData);

                let hasFound = false;
                let selectedStreamName = content.find('.stream').val();
                for (let stream of streamList) {
                    if (stream.name == selectedStreamName) {
                        data.url = stream.url;
                        hasFound = true;
                        break;
                    }
                }

                if (!hasFound) {
                    console.log("Failed to determin stream source")
                    return;
                }

                addDownloadsAPI([data]);
            },
            closePopup: true,
            displayText: "Submit"
        });

        popup.showIn($('html'));
    }

    static #createSourceInput(streamList) {
        let activeTab = 'url-tab';
        var content = $(`
            <div class="src-input-container">
                <label class="tab-line-container">
                    <span class="lable">Stream</span>
                    <select class="stream">
                    </select>
                </label>
            </div>
        `);



        let streamSelect = content.find('.stream');
        for (let stream of streamList) {
            streamSelect.append($('<option>').text(stream.name));
        }

        return content;
    }

    static onWSResponse(cmd, content) {
        switch (cmd) {
            case "getAlternateProvidersResponse":
                SelectStreamPopup.open(SelectStreamPopup.downloadData, content.providers);
        }
    }
}
