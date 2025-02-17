function onWSResponseAutoloader(cmd, content) {

    switch (cmd) {
        case "syn":
            for (let index in content.items) {
                autoloaderAddTableItem(content.items[index]);
            }
            break;
    }

}

function onTest() {
    let table = document.getElementById('autoloadertable');
    let tr = Array.from(table.children);
    console.log(tr);
    for (let i in tr) {
        console.log(i);
        if (i == 0) continue; // do not remove the table header xD
        table.removeChild(tr[i]);
    }
    sendPacket('getData', 'autoloader');
}


function autoloaderAddTableItem(item) {
    console.log(item);
    let table = document.getElementById('autoloadertable');

    let row = document.createElement('tr');

    // Title
    {
        let txtTitle = document.createElement('td');
        txtTitle.innerText = item.title;
        row.appendChild(txtTitle);
    }

    // URL
    {
        let txtURL = document.createElement('td');
        txtURL.innerText = item.url;
        row.appendChild(txtURL);
    }

    // Unloaded Episodes
    {
        let txtUnloaded = document.createElement('td');
        txtUnloaded.innerText = item.unloaded;
        row.appendChild(txtUnloaded);
    }

    // LastScan
    {
        let txtLastScan = document.createElement('td');
        let date = new Date(item.lastScan);
        txtLastScan.innerText = date.getHours() + ':' + date.getMinutes();
        row.appendChild(txtLastScan);
    }

    console.log("AHHHHHHHh");
    table.appendChild(row);
}

function autoloaderSave() {
    let input = document.getElementById('autoloaderInputText');
    let text = input.value;
    if (text.length == 0) return;

    let request = {
        url: text,
        languageId: 1
    }

    sendPacket('subscribe', 'autoloader', request);
    input.value = '';
}