

function onWSResponseAutoloader(cmd, content) {

    switch (cmd) {
        case "syn":
            for (let index in content.items) {
                autoloaderAddTableItem(content.items[index]);
            }
            break;
    }

}


function autoloaderAddTableItem(item) {
    let table = document.getElementById('autoloadertable');
    let itemId = "autoloader-row-" + item.id;

    let existing = document.getElementById(itemId);
    if (existing != undefined)
        table.removeChild(existing);

    let row = document.createElement('tr');
    row.setAttribute("id", itemId);

    // Toolbar
    {
        let toolbar = document.createElement("td");
        toolbar.classList.add('toolbar');

        // Run Download
        {
            let btnDownload = document.createElement("i");
            btnDownload.classList.add("fa");
            btnDownload.classList.add("fa-download");
            btnDownload.addEventListener('click', function () {
                sendPacket("runDownload", "autoloader", { id: item.id })
            });
            toolbar.appendChild(btnDownload);
        }
        row.appendChild(toolbar);
    }
    // Title
    {
        let txtTitle = document.createElement('td');
        txtTitle.innerText = item.title;
        row.appendChild(txtTitle);
    }

    // URL
    {
        let td = document.createElement('td');
        let txtURL = document.createElement('label');
        txtURL.innerText = 'Aniworld';

        let link = document.createElement('a');
        link.setAttribute('href', item.url);
        link.setAttribute('target', '_blank')

        link.appendChild(txtURL);
        td.appendChild(link)
        row.appendChild(td);
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
        let hours = date.getHours().toString().padStart(2, '0');
        let minutes = date.getMinutes().toString().padStart(2, '0');
        txtLastScan.innerText = hours + ':' + minutes;
        row.appendChild(txtLastScan);
    }

    //table.appendChild(row);
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