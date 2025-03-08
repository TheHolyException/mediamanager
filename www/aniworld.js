class Aniworld{
    constructor(stateLabel){
        if(!Aniworld.instance){
            Aniworld.instance = this;
        }

        Aniworld.stateLabel = stateLabel;
        Aniworld.resolvedLinks = "";
        Aniworld.isResolving = false;
        return Aniworld.instance;
    }

    resolveLinks(links, languageID){
        if(Aniworld.isResolving)
            return;

        console.log("Resolve:", links, languageID);

        Aniworld.setAniworldStatusColor("WAIT");
        Aniworld.stateLabel.text("Processing...");
        sendPacket("resolve", "aniworld", {
            url: links,
            language: languageID
        });

        Aniworld.isResolving = true;
    }

    static resetResolvedLinks() {
        Aniworld.resolvedLinks = "";
        Aniworld.stateLabel.text("");
        Aniworld.setAniworldStatusColor("");
    }

    static setAniworldStatusColor(state) {
        Aniworld.stateLabel.attr('state', state);
    }

    static onWSResponseAniworldParser(cmd, content) {
        let responseText = "";
    
        switch (cmd) {
            case "links":
                if (content.error != undefined) {
                    responseText = content.error;
                    Aniworld.setAniworldStatusColor("ERROR");
                    break;
                }

                let links = content.links;
                Aniworld.resolvedLinks = links.join(';');
                responseText = "Resolved " + links.length + " Links"
                Aniworld.setAniworldStatusColor("OK");
                Aniworld.isResolving = false;
                break;
        }
    
        Aniworld.stateLabel.text(responseText);
    }
}