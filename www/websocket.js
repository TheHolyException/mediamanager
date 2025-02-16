var ws = null;


function connect() {
    ws = new WebSocket((document.location.protocol === "https:" ? "wss://" : "ws://") + window.location.hostname + ":" + window.location.port);
    setWebSocketStatusFeedback(1);
    ws.onopen = function () {
        sendPacket("syn", "default", {});
        setWebSocketStatusFeedback(2);
        onTargetSelection(); // Refresh subfolders
    };

    ws.onmessage = function (e) {
        let data = JSON.parse(e.data);
        console.log("<-");
        console.log(data);

        let targetSystem = "default";
        if (data.targetSystem != undefined) targetSystem = data.targetSystem;

        let cmd = data.cmd;
        let content = data.content;

        switch (targetSystem) {
            case "default":
                onWSResponseDefault(cmd, content);
                break;
            case "aniworld":
                onWSResponseAniworldParser(cmd, content);
                break;
            case "autoloader":
                onWSResponseAutoloader(cmd, content);
                break;
        }
    };

    ws.onclose = function (e) {
        console.log('Socket is closed. Reconnect will be attempted in 1 second.', e.reason);
        setTimeout(function () {
            connect();
        }, 1000);
        setWebSocketStatusFeedback(4);
    };

    ws.onerror = function (err) {
        console.error('Socket encountered error: ', err.message, 'Closing socket');
        ws.close();
        setWebSocketStatusFeedback(4);
    };
}

function sendPacket(cmd, targetSystem, content) {
    let request = {
        cmd: cmd,
        targetSystem: targetSystem,
        content: content
    }
    console.log("->")
    console.log(JSON.stringify(request))
    ws.send(JSON.stringify(request));
}