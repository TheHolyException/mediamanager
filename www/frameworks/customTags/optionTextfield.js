class OptionTextfield extends HTMLElement  {
    constructor() {
        super();
        this.attachShadow({ mode: "open" });

        // Create elements
        this.wrapper = document.createElement("div");
        this.wrapper.setAttribute("class", "wrapper");

        this.input = document.createElement("input");
        this.input.type = "text";
        this.input.placeholder = "Start typing...";

        this.dropdown = document.createElement("ul");
        this.dropdown.setAttribute("class", "dropdown");

        // Styling
        const style = document.createElement("style");
        style.textContent = `
            :host {
                display: flex;
            }
            .wrapper {
                position: relative;
                display: inline-block;
                width: 100%;
                display: flex;
            }
            input {
                font-size: 16px;
                border: none;
                background-color: inherit;
                outline: none;
                color: inherit;
                flex-grow: 1;
            }
            .dropdown {
                outline: none;
                position: absolute;
                top: 100%;
                left: 0;
                width: 100%;
                background-color: var(--darkBGColor1);
                border: 2px solid var(--darkBGColor2);
                border-radius: 4px;
                max-height: 150px;
                overflow-y: auto;
                display: none;
                list-style: none;
                padding: 0;
                margin: 5px 0 0;
                box-shadow: 0px 2px 5px rgba(0, 0, 0, 0.2);
                z-index: 10;
            }
            .dropdown li {
                padding: 8px;
                cursor: pointer;
            }
            .dropdown li:hover {
                background-color: var(--darkBGColor3);
            }
        `;

        // Append elements
        this.wrapper.append(this.input, this.dropdown);
        this.shadowRoot.append(style, this.wrapper);
    }

    connectedCallback() {
        this.loadOptions();

        this.input.addEventListener("focus", () => this.showDropdown());
        this.input.addEventListener("input", () => this.filterOptions());
        this.input.addEventListener("blur", () => setTimeout(() => this.hideDropdown(), 200));

        // Initialize MutationObserver
        this.observer = new MutationObserver(() => this.loadOptions());
        this.observer.observe(this, {
            childList: true,
            subtree: true,
            characterData: true,
            attributes: true, // Observe attribute changes in <option>
        });
    }

    disconnectedCallback() {
        // Stop observing when the element is removed
        this.observer.disconnect();
    }

    loadOptions() {
        this.options = [];
        // Get <option> elements inside <custom-textbox>
        const optionElements = this.querySelectorAll("option");
        optionElements.forEach(option => {
            this.options.push({
                value: option.getAttribute("value") || option.textContent,
                text: option.textContent
            });
        });
    }

    renderOptions(filteredOptions = this.options) {
        this.dropdown.innerHTML = "";
        filteredOptions.forEach(({ value, text }) => {
            const item = document.createElement("li");
            item.textContent = text;
            item.dataset.value = value;
            item.addEventListener("click", () => {
                this.input.value = value;
                this.dispatchEvent(new Event("change")); // 🔥 Trigger change event
                this.hideDropdown();
            });
            this.dropdown.appendChild(item);
        });

        this.dropdown.style.display = filteredOptions.length ? "block" : "none";
    }

    filterOptions() {
        const value = this.input.value.toLowerCase();
        const filtered = this.options.filter(opt => {
            return opt.value.toLowerCase().startsWith(value)
        });
        this.renderOptions(filtered);
    }

    showDropdown() {
        if (this.options.length) {
            this.renderOptions();
            this.dropdown.style.display = "block";
        }
    }

    hideDropdown() {
        this.dropdown.style.display = "none";
    }

    get value() {
        return this.input.value;
    }

    set value(newValue) {
        this.input.value = newValue;
        this.dispatchEvent(new Event("change"));
    }
}

customElements.define("textbox-dropdown", OptionTextfield);