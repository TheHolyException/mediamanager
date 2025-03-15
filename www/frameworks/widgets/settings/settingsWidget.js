class SettingsWidget extends BaseWidget {
    constructor(name = "Downloads") {
        super(name);
    }

    render() {
        return `
        <div class="widget" widget-name="SettingsWidget">
            <h1>Global Settings</h1>
            <nav class="queue-action-bar">
                <a class="save-settings-btn" onclick="saveGlobalSettings()"><i class="fas fa-save"></i> Save</a>
            </nav>
            <div class="settings">
                <label class="setting" setting="VOE_THREADS">
                    <span>VOE Threads</span>
                    <input type="number" value="4" max="10" min="1">
                </label>
                <label class="setting" setting="PARALLEL_DOWNLOADS">
                    <span>Parallel Downloads</span>
                    <input type="number" value="4" max="10" min="1">
                </label>
            </div>
        </div>
        `;
    }

    initEvents(elem) {
        super.initEvents(elem);
    }
}