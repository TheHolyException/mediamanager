class StatisticsWidget extends BaseWidget {
    constructor(name = "Downloads") {
        super(name);
    }

    render() {
        let widget = $(`
        <div class="widget" widget-name="StatisticsWidget">
            <h1 class="widget-handle">Statistics</h1>
            <div class="statistics">
                <div class="statistic-group">
                    <h2>aniworld</h2>
                    <table>
                        <tr>
                            <td>Episode Language Requests
                            </td>
                            <td>27</td>
                        </tr>
                    </table>
                </div>
            </div>
        </div>
        `);

        sendPacket('systemInfo', 'default');
        return widget.get(0);
    }

    static updateStatistics(responseData){
        let container = $('[widget-name="StatisticsWidget"] .statistics');
        if(container.length == 0)
            return;

        let groups = []

        for(let groupName in responseData){
            let groupData = responseData[groupName];
            let groupElem = $('<div>').addClass('statistic-group');
            let statTable = $('<table>');
            groupElem.append($('<h2>').text(groupName));
            
            for(let dataName in groupData){
                let dataValue = groupData[dataName];
                let row = $('<tr>');
                row.append(
                    $('<td>').text(dataName),
                    $('<td>').text(dataValue)
                )
                statTable.append(row);
            }

            groupElem.append(statTable);
            groups.push(groupElem);
        }

        container.empty();
        container.append(groups);
    }
}