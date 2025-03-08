class OptionTextfield extends HTMLElement  {
    static observedAttributes = ["options"];

    optionListID = undefined;

    constructor() {
        super();
        this.attachShadow({ mode: "open" });

        // Create input and select elements
        this.input = document.createElement("input");
        this.input.type = "text";
        this.input.placeholder = "Select or type...";

        this.select = document.createElement("select");

        // Default styling
        const style = document.createElement("style");
        style.textContent = `
            :host {
                display: inline-block;
                font-family: Arial, sans-serif;
            }
            input, select {
                padding: 8px;
                font-size: 16px;
                margin-right: 5px;
            }
        `;

        // Append elements to shadow DOM
        this.shadowRoot.append(style, this.input, this.select);
    }

    connectedCallback() {
        //console.log("Element added");
        
        const options = this.getAttribute("options");
        if (options) {
            options.split(",").forEach(optionText => {
                const option = document.createElement("option");
                option.value = optionText.trim();
                option.textContent = optionText.trim();
                this.select.appendChild(option);
            });
        }

        // Event Listeners
        this.select.addEventListener("change", () => {
            this.input.value = this.select.value;
        });

        this.input.addEventListener("input", () => {
            this.select.value = "";
        });
    }

    attributeChangedCallback(name, oldValue, newValue) {
        //console.log("Attribute changed: " + name);
        switch (name) {
            case "options":
                this.optionListID = newValue;
                break;
        }
    }
}

customElements.define("custom-textbox", CustomTextbox);