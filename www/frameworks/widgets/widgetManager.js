class WidgetManager{
    static  widgets = {
        downloads: DownloadsWidget,
        settings: SettingsWidget,
        statistics: StatisticsWidget,
        subscriptions: SubscriptionsWidget,
    }

    static getWidget(widgetName, title){
        let inst = new this.widgets[widgetName](title);
        return inst;
    }
}