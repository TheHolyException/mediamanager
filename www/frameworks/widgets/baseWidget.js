class BaseWidget{
    constructor(name){
        this.name = name;
    }

    render(){
        console.warn('No Rendering for widget "' + this.name + '" defined');
    }

    initEvents(elem){
        
    }
}