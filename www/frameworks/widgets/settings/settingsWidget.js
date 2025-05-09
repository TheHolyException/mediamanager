class SettingsWidget extends BaseWidget {
    static currentSettings = {};

    constructor(name = "Downloads") {
        super(name);
    }

    render() {
        let widget = $(`
        <div class="widget scrollbar-on-hover custom-scrollbar" widget-name="SettingsWidget">
            <h1 class="widget-handle">Global Settings</h1>
            <nav class="settings-action-bar">
                <a class="save-settings-btn"><i class="fas fa-save"></i> Save</a>
            </nav>
            <div class="settings">
                <label class="setting" setting="VOE_THREADS">
                    <span>VOE Threads</span>
                    <input class="input" type="number" max="10" min="1">
                </label>
                <label class="setting" setting="PARALLEL_DOWNLOADS">
                    <span>Parallel Downloads</span>
                    <input class="input" type="number" max="10" min="1">
                </label>
                <label class="setting" setting="RETRY_MINUTES">
                    <span>Retry Downloads (min)</span>
                    <input class="input" type="number" max="999999999" min="0">
                </label>
            </div>
        </div>
        `);

        this.#initSettingValues(widget);
        this.#initEvents(widget);
        return widget.get(0);
    }

    #initSettingValues(widget) {
        for (let key in SettingsWidget.currentSettings) {
            let value = SettingsWidget.currentSettings[key];
            let elem = widget.find('[setting="' + key + '"] input');
            elem.val(value);
        }
    }

    #initEvents(widget) {
        widget.find('.save-settings-btn').click(function () {
            let settings = widget.find('[setting]');
            let settingPacket = [];
            for (let s of settings) {
                let setting = $(s);
                settingPacket.push({ key: setting.attr('setting'), value: setting.find('[class="input"]').val() })
            }

            sendPacket("setting", "default", {
                "settings": settingPacket
            });
        });
    }

    static updateSettings(settings){
        let widget = $('[widget-name="SettingsWidget"]');
        for (let settingItem of settings) {
            widget.find('[setting="' + settingItem.key + '"] input').val(settingItem.val);
            SettingsWidget.currentSettings[settingItem.key] = settingItem.val;
        }
    }
}