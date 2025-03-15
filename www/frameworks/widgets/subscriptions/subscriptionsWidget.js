class DownloadsWidget extends BaseWidget{
    constructor(name = "Downloads") {
        super(name);
    }

    render(){
        return `
        <h1>Subscriptions</h1>
        `;
    }

    initEvents(elem){
        super(elem);   
    }
}