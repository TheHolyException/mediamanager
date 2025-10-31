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
        
        Aniworld.isResolving = true;
        
        const params = new URLSearchParams({
            url: links,
            language: languageID.toString()
        });
        
        fetch(`/api/aniworld/resolve?${params}`, {
            method: 'GET'
        })
        .then(response => response.json())
        .then(data => {
            if (data.error) {
                Aniworld.stateLabel.text(data.error);
                Aniworld.setAniworldStatusColor("ERROR");
            } else {
                Aniworld.resolvedLinks = data.links.join(';');
                Aniworld.stateLabel.text("Resolved " + data.links.length + " Links");
                Aniworld.setAniworldStatusColor("OK");
            }
            Aniworld.isResolving = false;
        })
        .catch(error => {
            console.error('Error resolving anime links:', error);
            Aniworld.stateLabel.text("Error: " + error.message);
            Aniworld.setAniworldStatusColor("ERROR");
            Aniworld.isResolving = false;
        });
    }

    static resetResolvedLinks() {
        Aniworld.resolvedLinks = "";
        Aniworld.stateLabel.text("");
        Aniworld.setAniworldStatusColor("");
    }

    static setAniworldStatusColor(state) {
        Aniworld.stateLabel.attr('state', state);
    }

}