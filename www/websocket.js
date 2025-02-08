var ws = null;


function connect() {
    //ws = new WebSocket('wss://media.vpn.minebug.de');
    ws = new WebSocket((document.location.protocol === "https:" ? "wss://" : "ws://") + window.location.hostname + ":" + window.location.port);
    setWebSocketStatusFeedback(1);
    ws.onopen = function () {
        send({ "type": "syn" });
        setWebSocketStatusFeedback(2);
        onTargetSelection(); // Refresh subfolders
    };

    ws.onmessage = function (e) {
        let data = JSON.parse(e.data);
        console.log("<-");
        console.log(data);

        if (data.type.startsWith("aniworld")) {
            onWSResponseAniworldParser(data);
            return;
        } else {
            onWSResponseDefault(data);
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

function send(data) {
    console.log("->")
    console.log(data)
    ws.send(JSON.stringify(data));
}

