$(document).ready(function () {
    
});

class TabBar{
    constructor(tabbar, tabcontainer){
        this.container = $(tabcontainer);
        this.bar = $(tabbar);

        let inst = this;
        let tabs = this.bar.find('[tab-name]');
        if(this.bar.find('[tab-name].active').length == 0){
            let firstTab = $(tabs.get(0));
            firstTab.addClass('active');
            
            let firstTabContainer = $(this.container.find('[tab-name]').get(0));
            firstTabContainer.addClass('active');
            firstTabContainer.css('display', 'flex');   
            firstTabContainer.css('flex-direction', 'column');   
            firstTabContainer.css('gap', '15px');   
        }

        this.container.find('[tab-name]:not(.active)').css('display', 'none');

        tabs.click(function(){
            let self = $(this);
            let tabName = self.attr('tab-name');

            inst.bar.find('[tab-name].active').removeClass('active');
            inst.container.find('[tab-name].active').css('display', 'none');
            inst.container.find('[tab-name].active').removeClass('active');

            self.addClass('active');
            inst.container.find('[tab-name="' + tabName + '"]').addClass('active');
            inst.container.find('[tab-name="' + tabName + '"]').css('display', 'flex');
        });
    }
}