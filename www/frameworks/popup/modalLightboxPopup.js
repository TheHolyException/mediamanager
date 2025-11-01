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

        let completeStyle = ``;
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
            let navBar = $('<nav id="editor-buttons"></nav>');

            this.addNavbarButtons.forEach((item) => {
                let btn = $('<button></button>');

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
                            case "buttonType":
                                btn.addClass(item[key]);
                            break;
                        }
                    }
                }

                navBar.append(btn);
            });

            editorWindow.append(navBar);
        }


        return overlay;
    }

    showIn(container){
        container.append(this.getPopup());
    }
}