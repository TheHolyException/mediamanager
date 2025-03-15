class DownloadsWidget extends BaseWidget{
    constructor(name = "Statistics") {
        super(name);
    }

    render(){
        return `
        <div class="widget">
            <h1>Download Queue</h1>
            <nav class="queue-action-bar">
                <a class="add-sources-btn" onclick="openAddSourcePopup()"><i class="fa fa-plus"></i> Add</a>
                <a class="commit-sources-btn" onclick="onCommit()"><i class="fas fa-paper-plane"></i> Commit</a>
            </nav>
            <table class="queue-table">
                <tr>
                    <th col="actions">Actions</th>
                    <th col="state">Status</th>
                    <th col="url">URL</th>
                    <th col="target">Target</th>
                </tr>
            </table>
        </div>
        `;
    }

    initEvents(elem){
        super.initEvents(elem);
    }
}