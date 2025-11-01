var ws = null;


function connect() {
    ws = new WebSocket((document.location.protocol === "https:" ? "wss://" : "ws://") + window.location.hostname + ":" + window.location.port);
    setWebSocketStatusFeedback(1);
    ws.onopen = function () {
        sendPacket("syn", "default", {});
        setWebSocketStatusFeedback(2);
    };

    ws.onmessage = function (e) {
        let data = JSON.parse(e.data);

        let cmd = data.cmd;
        let content = data.content;

        // Handle keepalive messages
        if (cmd === "keepalive") {
            if (data.type === "ping") {
                // Respond to server ping with pong
                sendPacket("keepalive", "default", { type: "pong", timestamp: Date.now() });
            }
            return; // Don't process keepalive messages further
        }

        if (data.cmd != "systemInfo" || true) {
            console.log("Receiving:")
            console.log(data)
        }

        let targetSystem = "default";
        if (data.targetSystem != undefined) targetSystem = data.targetSystem;

        if (cmd == "response") {
            yeti.show({
                message: content.message,
                severity: getYetiServerity(content.code),
                time: 5000
            });
            return;
        }

        switch (targetSystem) {
            case "default":
                onWSResponseDefault(cmd, content);
                DownloadsWidget.onWSResponse(cmd, content);
                StatisticsWidget.onWSResponse(cmd, content);
                SettingsWidget.onWSResponse(cmd, content);
                break;
            case "autoloader":
                SubscriptionsWidget.onWSResponse(cmd, content);
                SelectStreamPopup.onWSResponse(cmd, content);
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
    if(ws.readyState  !== WebSocket.OPEN)
        return;

    let request = {
        cmd: cmd,
        targetSystem: targetSystem,
        content: content
    }
    if (cmd != "systemInfo" && cmd != "keepalive") {
        console.log("Sending")
        console.log(request)
    }
    ws.send(JSON.stringify(request));
}

function getYetiServerity(code) {
    switch (code) {
        case 2: return 'ok'
        case 3: return 'warn'
        case 4: return 'nok'
        default: return 'info'
    }
}
