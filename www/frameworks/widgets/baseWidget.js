class BaseWidget extends GridWidget{
    constructor(options={}){
        super(options);
        this.eventListeners = [];
        this.isInitialized = false;
        this.isDestroyed = false;
        this.intervals = [];
        this.timeouts = [];
    }

    createContent() {
        console.warn('No Rendering for widget "' + this.type + '" defined');
    }

    init() {
        if (this.isInitialized || this.isDestroyed) return;
        this.isInitialized = true;
        this.onInit();
    }

    onInit() {
        // Override in child classes for initialization logic
    }

    addEventListener(element, event, handler, options) {
        if (this.isDestroyed) return;
        element.addEventListener(event, handler, options);
        this.eventListeners.push({ element, event, handler, options });
    }

    setInterval(callback, delay) {
        if (this.isDestroyed) return;
        const intervalId = setInterval(callback, delay);
        this.intervals.push(intervalId);
        return intervalId;
    }

    setTimeout(callback, delay) {
        if (this.isDestroyed) return;
        const timeoutId = setTimeout(callback, delay);
        this.timeouts.push(timeoutId);
        return timeoutId;
    }

    clearInterval(intervalId) {
        clearInterval(intervalId);
        const index = this.intervals.indexOf(intervalId);
        if (index > -1) {
            this.intervals.splice(index, 1);
        }
    }

    clearTimeout(timeoutId) {
        clearTimeout(timeoutId);
        const index = this.timeouts.indexOf(timeoutId);
        if (index > -1) {
            this.timeouts.splice(index, 1);
        }
    }

    removeAllEventListeners() {
        this.eventListeners.forEach(({ element, event, handler, options }) => {
            element.removeEventListener(event, handler, options);
        });
        this.eventListeners = [];
    }

    clearAllTimers() {
        this.intervals.forEach(intervalId => clearInterval(intervalId));
        this.timeouts.forEach(timeoutId => clearTimeout(timeoutId));
        this.intervals = [];
        this.timeouts = [];
    }

    onDestroy() {
        // Override in child classes for cleanup logic
    }

    destroy() {
        if (this.isDestroyed) return;
        this.isDestroyed = true;
        
        this.onDestroy();
        this.removeAllEventListeners();
        this.clearAllTimers();
        
        if (this.element && this.element.parentNode) {
            this.element.parentNode.removeChild(this.element);
        }
        
        super.destroy && super.destroy();
    }

    createHeader(){        
        const actions = document.createElement('div');
        actions.className = 'grid-widget-actions';
                
        if (this.removable) {
            const removeBtn = document.createElement('button');
            removeBtn.className = 'grid-widget-action remove-handle';
            removeBtn.innerHTML = '✕';
            removeBtn.title = 'Remove';
            this.addEventListener(removeBtn, 'click', (e) => {
                e.stopPropagation();
                this.remove();
            });
            actions.appendChild(removeBtn);
        }

        return actions;
    }
}