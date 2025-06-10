class ModalLightboxPopup {
    constructor(addNavbar = true){
        this.addNavbar = addNavbar;
        this.addNavbarButtons = [];
        this.content = [];
        this.selectorStyles = {};
        this.preventClosing = false;
    }

    addContentElement(...elem){
        this.content = this.content.concat(elem);
    }

    addNavbarButton(btn){
        this.addNavbarButtons.push(btn);
    }

    setContentSelectorStyles(selectorStyles){
        this.selectorStyles = selectorStyles;
    }

    getPopup(){
        let overlay = $('<section id="editor-overlay"></section>');
        let editorWindow = $('<main id="editor-window"></main>');

        let completeStyle = `
        html:has(#editor-window) {
            overflow: hidden;
        }

        #editor-overlay {
            position: fixed;
            z-index: 99999999999999999999999;
            inset: 0px;
            display: flex;
            justify-content: center;
            align-items: center;
            backdrop-filter: blur(5px);
        }

        #editor-window {
            /* max-width: clamp(250px, 75vw, 500px); */
            max-height: 75svh;
            background-color: var(--darkgray);
            padding: 25px;
            color: white;
            display: flex;
            flex-direction: column;
            gap: 15px;
            overflow-y: auto;
            overflow-x: hidden;
        }

        #editor-buttons {
            width: 100%;
            display: flex;
            justify-content: flex-end;
            gap: 15px;
        }

        #editor-window::-webkit-scrollbar {
            width: 8px;
        }

        #editor-window::-webkit-scrollbar-track {
            background: rgba(255, 255, 255, 0.1);
            border-radius: 4px;
        }

        #editor-window::-webkit-scrollbar-thumb {
            background: rgba(255, 255, 255, 0.3);
            border-radius: 4px;
        }

        #editor-window::-webkit-scrollbar-thumb:hover {
            background: rgba(255, 255, 255, 0.5);
        }
        `;
        const parentSelector = "#editor-window";
        for(let selector in this.selectorStyles){
            completeStyle += parentSelector + selector + " {";
            
            let style = this.selectorStyles[selector];
            for(let prop in style){
                completeStyle += prop + ":" + style[prop] + ";";
            }

            completeStyle += "}";
        }

        overlay.append($("<style>" + completeStyle + "</style>"));

        editorWindow.append(this.content);
        overlay.append(editorWindow);

        if(this.addNavbar){
            let navBarBtnsStyle = $('<style>#editor-buttons > a {cursor: pointer; &:hover {color: var(--hoverColor);}}</style>')
            let navBar = $('<nav id="editor-buttons"></nav>');

            this.addNavbarButtons.forEach((item) => {
                /*
                {
                    func: function() {
                        console.log("closing");
                    },
                    closePopup: true,
                    displayText: "Cancel"
                }
                */
                let btn = $('<a></a>');

                for (var key in item) {
                    if (item.hasOwnProperty(key)) {
                        switch(key){
                            case "func":
                                btn.click(item[key]);
                            break;
                            case "closePopup":
                                if(item[key] === true){
                                    btn.click(() => {
                                        if(!this.preventClosing){
                                            overlay.remove();
                                        }
                                        else{
                                            this.preventClosing = false;
                                        }
                                    });
                                }
                            break;
                            case "displayText":
                                btn.text(item[key]);
                            break;
                        }
                    }
                }

                navBar.append(btn);
            });

            editorWindow.append(navBarBtnsStyle, navBar);
        }


        return overlay;
    }

    showIn(container){
        container.append(this.getPopup());
    }
}