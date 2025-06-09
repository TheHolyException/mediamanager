class BaseWidget extends GridWidget{
    constructor(options={}){
        super(options);
    }

    createContent() {
        console.warn('No Rendering for widget "' + typeof type + '" defined');
    }

    createHeader(){        
        const actions = document.createElement('div');
        actions.className = 'grid-widget-actions';
        actions.style.position = "absolute";
        actions.style.zIndex = 100;
        actions.style.right = "20px";
        actions.style.top = "15px";
                
        // Add remove button if removable
        if (this.removable) {
            const removeBtn = document.createElement('button');
            removeBtn.className = 'grid-widget-action remove-handle';
            removeBtn.innerHTML = '✕';
            removeBtn.title = 'Remove';
            removeBtn.addEventListener('click', (e) => {
                e.stopPropagation();
                this.remove();
            });
            actions.appendChild(removeBtn);
        }

        return actions;
    }
}