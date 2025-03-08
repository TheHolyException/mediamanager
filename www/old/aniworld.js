let resolvedLinks = "";
let btnResolve = document.getElementById("btnResolve");
let btnReset = document.getElementById("btnReset");
let resolveInfoElement = document.getElementById("resolveInfo");
let aniworldLanguage = document.getElementById("aniworldLanguage");
//btnReset.disabled = true;

function resolveAniworld() {
    setAniworldStatusColor("WAIT");
    btnResolve.disabled = true;
    resolveInfoElement.innerText = "Processing..."
    sendPacket("resolve", "aniworld", {
        url: document.getElementById("aniworld-url").value,
        language: aniworldLanguage.value
    })
}

function onWSResponseAniworldParser(cmd, content) {
    let responseText = "";

    switch (cmd) {
        case "links":
            if (content.error != undefined) {
                responseText = content.error;
                setAniworldStatusColor("ERROR");
                btnReset.disabled = false;
                break;
            }
            resolvedLinks = "";
            let links = content.links;
            let counter = 0;
            for (let index in links) {
                let link = links[index];
                resolvedLinks += link + ";";
                counter++;
            }
            resolvedLinks = resolvedLinks.substring(0, resolvedLinks.length - 1);
            responseText = "Resolved " + counter + " Links"
            setAniworldStatusColor("OK");
            btnReset.disabled = false;
            break;
    }

    resolveInfoElement.innerText = responseText;
    btnResolve.disabled = false;
}


function setAniworldStatusColor(status) {
    let statusElement = document.getElementById('resolveInfo');
    for (i = 0; i < statusElement.classList.length; i++) {
        if (statusElement.classList[i].startsWith('resolverStatus'))
            statusElement.classList.remove(statusElement.classList[i]);
    }
    if (status != "") {
        statusElement.classList.add('resolverStatus' + status);
    }
}

function resetAniworld() {
    btnReset.disabled = true;
    resolvedLinks = "";
    resolveInfoElement.innerText = "";
    setAniworldStatusColor("");
}