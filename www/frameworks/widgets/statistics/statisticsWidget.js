class StatisticsWidget extends BaseWidget {
    static activeInstances = new Set();
    constructor(options = {}) {
        super({
            type: 'statistics',
            width: 2,
            height: 2,
            ...options
        });
        this.memoryChart = null;
        this.systemChart = null;
        this.threadChart = null;
    }

    // Static properties for managing global polling
    static pollingInterval = null;
    static intersectionObserver = null;
    static mutationObserver = null;
    static isPollingActive = false;

    createContent() {
        let widget = $(`
        <div class="widget scrollbar-on-hover custom-scrollbar" widget-name="StatisticsWidget">
            <div class="widget-header">
                <div class="widget-title">
                    <i class="fas fa-chart-bar"></i>
                    <h1 class="widget-handle">System Statistics</h1>
                </div>
            </div>
            <div class="statistics scrollable-content">
                <div class="chart-grid">
                    <div class="chart-container">
                        <h3>RAM Usage History</h3>
                        <canvas id="memoryChart" width="300" height="200"></canvas>
                        <div class="memory-details">
                            <div class="memory-item">
                                <span class="label">Current:</span>
                                <span class="value" id="memory-used">-</span>
                            </div>
                            <div class="memory-item">
                                <span class="label">Max:</span>
                                <span class="value" id="memory-max">-</span>
                            </div>
                            <div class="memory-item">
                                <button id="gc-button" class="gc-button" onclick="StatisticsWidget.triggerGC()">
                                    <i class="fas fa-trash"></i> Force GC
                                </button>
                            </div>
                        </div>
                    </div>
                    <div class="chart-container">
                        <h3>Download Statistics History</h3>
                        <canvas id="systemChart" width="300" height="200"></canvas>
                        <div class="system-details">
                            <div class="system-item">
                                <span class="label">CPU Cores:</span>
                                <span class="value" id="cpu-cores">-</span>
                            </div>
                            <div class="system-item">
                                <span class="label">Active Now:</span>
                                <span class="value" id="active-downloads">-</span>
                            </div>
                        </div>
                    </div>
                    <div class="chart-container">
                        <h3>Thread Pool History</h3>
                        <canvas id="threadChart" width="300" height="200"></canvas>
                        <div class="thread-details">
                            <div class="thread-item">
                                <span class="label">Max Threads:</span>
                                <span class="value" id="max-threads">-</span>
                            </div>
                            <div class="thread-item">
                                <span class="label">Active:</span>
                                <span class="value" id="active-threads">-</span>
                            </div>
                        </div>
                    </div>
                </div>
                <div class="statistics-tables">
                    <!-- Dynamic content will be inserted here -->
                </div>
            </div>
        </div>
        `);

        this.setTimeout(() => this.init(), 100);
        
        // Store widget instance for updates
        widget.data('widgetInstance', this);

        // Initialize visibility monitoring when widget is created
        StatisticsWidget.initializeVisibilityMonitoring();

        return widget.get(0);
    }

    onInit() {
        if (typeof Chart !== 'undefined') {
            this.initCharts();
            StatisticsWidget.activeInstances.add(this);
        } else {
            // Wait for Chart.js to load
            const checkChart = this.setInterval(() => {
                if (typeof Chart !== 'undefined') {
                    this.clearInterval(checkChart);
                    this.initCharts();
                    StatisticsWidget.activeInstances.add(this);
                }
            }, 100);
        }
    }

    onDestroy() {
        if (this.memoryChart) {
            this.memoryChart.destroy();
            this.memoryChart = null;
        }
        if (this.systemChart) {
            this.systemChart.destroy();
            this.systemChart = null;
        }
        if (this.threadChart) {
            this.threadChart.destroy();
            this.threadChart = null;
        }
        StatisticsWidget.activeInstances.delete(this);
    }

    initCharts() {
        const memoryCtx = document.getElementById('memoryChart');
        const systemCtx = document.getElementById('systemChart');
        const threadCtx = document.getElementById('threadChart');

        if (!memoryCtx || !systemCtx || !threadCtx) return;

        // Memory Usage Historical Chart
        this.memoryChart = new Chart(memoryCtx, {
            type: 'line',
            data: {
                labels: [],
                datasets: [{
                    label: 'RAM Usage %',
                    data: [],
                    borderColor: '#dc3545',
                    backgroundColor: 'rgba(220, 53, 69, 0.1)',
                    borderWidth: 2,
                    fill: true,
                    tension: 0.4,
                    pointRadius: 2,
                    pointHoverRadius: 4
                }]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                interaction: {
                    intersect: false,
                    mode: 'index'
                },
                scales: {
                    x: {
                        type: 'time',
                        time: {
                            unit: 'second',
                            displayFormats: {
                                second: 'HH:mm:ss'
                            }
                        },
                        ticks: {
                            color: '#ffffff',
                            font: { size: 10 },
                            maxTicksLimit: 8
                        },
                        grid: {
                            color: 'rgba(255, 255, 255, 0.1)'
                        }
                    },
                    y: {
                        beginAtZero: true,
                        max: 100,
                        ticks: {
                            color: '#ffffff',
                            font: { size: 10 },
                            callback: function(value) {
                                return value + '%';
                            }
                        },
                        grid: {
                            color: 'rgba(255, 255, 255, 0.1)'
                        }
                    }
                },
                plugins: {
                    legend: {
                        labels: {
                            color: '#ffffff',
                            font: { size: 11 }
                        }
                    },
                    tooltip: {
                        backgroundColor: 'rgba(0, 0, 0, 0.8)',
                        titleColor: '#ffffff',
                        bodyColor: '#ffffff',
                        borderColor: '#ffffff',
                        borderWidth: 1
                    }
                }
            }
        });

        // Download Statistics Historical Chart
        this.systemChart = new Chart(systemCtx, {
            type: 'line',
            data: {
                labels: [],
                datasets: [
                    {
                        label: 'Total Downloads',
                        data: [],
                        borderColor: '#007bff',
                        backgroundColor: 'rgba(0, 123, 255, 0.1)',
                        borderWidth: 2,
                        fill: false,
                        tension: 0.4,
                        pointRadius: 2,
                        pointHoverRadius: 4
                    },
                    {
                        label: 'Active Downloads',
                        data: [],
                        borderColor: '#28a745',
                        backgroundColor: 'rgba(40, 167, 69, 0.1)',
                        borderWidth: 2,
                        fill: false,
                        tension: 0.4,
                        pointRadius: 2,
                        pointHoverRadius: 4
                    },
                    {
                        label: 'Failed Downloads',
                        data: [],
                        borderColor: '#dc3545',
                        backgroundColor: 'rgba(220, 53, 69, 0.1)',
                        borderWidth: 2,
                        fill: false,
                        tension: 0.4,
                        pointRadius: 2,
                        pointHoverRadius: 4
                    },
                    {
                        label: 'Completed Downloads',
                        data: [],
                        borderColor: '#6c757d',
                        backgroundColor: 'rgba(108, 117, 125, 0.1)',
                        borderWidth: 2,
                        fill: false,
                        tension: 0.4,
                        pointRadius: 2,
                        pointHoverRadius: 4
                    }
                ]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                interaction: {
                    intersect: false,
                    mode: 'index'
                },
                scales: {
                    x: {
                        type: 'time',
                        time: {
                            unit: 'second',
                            displayFormats: {
                                second: 'HH:mm:ss'
                            }
                        },
                        ticks: {
                            color: '#ffffff',
                            font: { size: 10 },
                            maxTicksLimit: 8
                        },
                        grid: {
                            color: 'rgba(255, 255, 255, 0.1)'
                        }
                    },
                    y: {
                        beginAtZero: true,
                        ticks: {
                            color: '#ffffff',
                            font: { size: 10 },
                            stepSize: 1
                        },
                        grid: {
                            color: 'rgba(255, 255, 255, 0.1)'
                        }
                    }
                },
                plugins: {
                    legend: {
                        labels: {
                            color: '#ffffff',
                            font: { size: 11 }
                        }
                    },
                    tooltip: {
                        backgroundColor: 'rgba(0, 0, 0, 0.8)',
                        titleColor: '#ffffff',
                        bodyColor: '#ffffff',
                        borderColor: '#ffffff',
                        borderWidth: 1
                    }
                }
            }
        });

        // Thread Pool Historical Chart
        this.threadChart = new Chart(threadCtx, {
            type: 'line',
            data: {
                labels: [],
                datasets: [
                    {
                        label: 'Active Threads',
                        data: [],
                        borderColor: '#28a745',
                        backgroundColor: 'rgba(40, 167, 69, 0.1)',
                        borderWidth: 2,
                        fill: false,
                        tension: 0.4,
                        pointRadius: 2,
                        pointHoverRadius: 4
                    },
                    {
                        label: 'Queued Tasks',
                        data: [],
                        borderColor: '#ffc107',
                        backgroundColor: 'rgba(255, 193, 7, 0.1)',
                        borderWidth: 2,
                        fill: false,
                        tension: 0.4,
                        pointRadius: 2,
                        pointHoverRadius: 4
                    },
                    {
                        label: 'RUNNABLE',
                        data: [],
                        borderColor: '#17a2b8',
                        backgroundColor: 'rgba(23, 162, 184, 0.1)',
                        borderWidth: 2,
                        fill: false,
                        tension: 0.4,
                        pointRadius: 2,
                        pointHoverRadius: 4
                    },
                    {
                        label: 'WAITING',
                        data: [],
                        borderColor: '#6f42c1',
                        backgroundColor: 'rgba(111, 66, 193, 0.1)',
                        borderWidth: 2,
                        fill: false,
                        tension: 0.4,
                        pointRadius: 2,
                        pointHoverRadius: 4
                    }
                ]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                interaction: {
                    intersect: false,
                    mode: 'index'
                },
                scales: {
                    x: {
                        type: 'time',
                        time: {
                            unit: 'second',
                            displayFormats: {
                                second: 'HH:mm:ss'
                            }
                        },
                        ticks: {
                            color: '#ffffff',
                            font: { size: 10 },
                            maxTicksLimit: 8
                        },
                        grid: {
                            color: 'rgba(255, 255, 255, 0.1)'
                        }
                    },
                    y: {
                        beginAtZero: true,
                        ticks: {
                            color: '#ffffff',
                            font: { size: 10 },
                            stepSize: 1
                        },
                        grid: {
                            color: 'rgba(255, 255, 255, 0.1)'
                        }
                    }
                },
                plugins: {
                    legend: {
                        labels: {
                            color: '#ffffff',
                            font: { size: 11 }
                        }
                    },
                    tooltip: {
                        backgroundColor: 'rgba(0, 0, 0, 0.8)',
                        titleColor: '#ffffff',
                        bodyColor: '#ffffff',
                        borderColor: '#ffffff',
                        borderWidth: 1
                    }
                }
            }
        });
    }

    static onWSResponse(cmd, content) {
        if (cmd === 'systemInfo') {
            StatisticsWidget.updateStatistics(content);
        }
    }

    static triggerGC() {
        $.ajax({
            url: '/api/system/gc',
            method: 'POST',
            success: function(response) {
                console.log('Garbage collection triggered successfully:', response);
                if (typeof handleAPISuccess === 'function') {
                    handleAPISuccess(response, 'Garbage collection triggered successfully');
                }
            },
            error: function(xhr, status, error) {
                console.error('Failed to trigger garbage collection:', error);
                if (typeof handleAPIError === 'function') {
                    handleAPIError(xhr, 'Failed to trigger garbage collection');
                }
            }
        });
    }

    static updateStatistics(responseData) {
        // Update all active statistics widget instances
        StatisticsWidget.activeInstances.forEach(instance => {
            if (instance.memoryChart && responseData.memoryHistory) {
                const memoryData = responseData.memoryHistory;
                const chart = instance.memoryChart;
                
                // Clear existing data
                chart.data.labels = [];
                chart.data.datasets[0].data = [];
                
                // Process memory history data
                if (memoryData.timestamp && Array.isArray(memoryData.timestamp) && memoryData.timestamp.length > 0) {
                    memoryData.timestamp.forEach((timestamp, index) => {
                        chart.data.labels.push(new Date(timestamp));
                        chart.data.datasets[0].data.push(memoryData.usagePercent[index] || 0);
                    });
                }
                
                chart.update('none');
            }
            
            // Update download historical chart
            if (instance.systemChart && responseData.downloadHistory) {
                const downloadData = responseData.downloadHistory;
                const chart = instance.systemChart;
                
                chart.data.labels = [];
                chart.data.datasets.forEach(dataset => dataset.data = []);
                
                if (downloadData.timestamp && Array.isArray(downloadData.timestamp) && downloadData.timestamp.length > 0) {
                    downloadData.timestamp.forEach((timestamp, index) => {
                        chart.data.labels.push(new Date(timestamp));
                        chart.data.datasets[0].data.push(downloadData.total[index] || 0);
                        chart.data.datasets[1].data.push(downloadData.active[index] || 0);
                        chart.data.datasets[2].data.push(downloadData.failed[index] || 0);
                        chart.data.datasets[3].data.push(downloadData.completed[index] || 0);
                    });
                }
                
                chart.update('none');
            }

            // Update thread historical chart
            if (instance.threadChart && responseData.threadHistory) {
                const threadData = responseData.threadHistory;
                const chart = instance.threadChart;
                
                chart.data.labels = [];
                chart.data.datasets.forEach(dataset => dataset.data = []);
                
                if (threadData.timestamp && Array.isArray(threadData.timestamp) && threadData.timestamp.length > 0) {
                    threadData.timestamp.forEach((timestamp, index) => {
                        chart.data.labels.push(new Date(timestamp));
                        chart.data.datasets[0].data.push(threadData.active[index] || 0);
                        chart.data.datasets[1].data.push(threadData.queued[index] || 0);
                        chart.data.datasets[2].data.push(threadData.RUNNABLE[index] || 0);
                        chart.data.datasets[3].data.push(threadData.WAITING[index] || 0);
                    });
                }
                
                chart.update('none');
            }
        });

        // Update current memory information
        if (responseData.memory) {
            $('#memory-used').text(responseData.memory.current || '-');
            $('#memory-max').text(responseData.memory.max || '-');
        }

        // Update current system information
        if (responseData.system) {
            $('#cpu-cores').text(responseData.system.availableProcessors || '-');
            $('#active-downloads').text(responseData.system.activeDownloads || 0);
        }

        // Update current thread information
        if (responseData.threadPool) {
            $('#max-threads').text(responseData.threadPool.max || '-');
            $('#active-threads').text(responseData.threadPool.active || 0);
        }

        // Update tables for other data
        let groups = [];
        const excludeGroups = ['memory', 'system', 'docker', 'memoryHistory', 'downloadHistory', 'threadHistory'];
        
        for (let groupName in responseData) {
            if (excludeGroups.includes(groupName)) continue;
            
            let groupData = responseData[groupName];
            if (!groupData || typeof groupData !== 'object') continue;
            
            let groupElem = $('<div>').addClass('statistic-group');
            let statTable = $('<table>');
            groupElem.append($('<h3>').text(groupName));
            
            for (let dataName in groupData) {
                let dataValue = groupData[dataName];
                let row = $('<tr>');
                row.append(
                    $('<td>').text(dataName),
                    $('<td>').text(dataValue)
                );
                statTable.append(row);
            }

            groupElem.append(statTable);
            groups.push(groupElem);
        }

        // Update statistics tables for all active widget instances
        if (StatisticsWidget.activeInstances.size > 0) {
            const containers = $('.statistics-tables');
            containers.each(function() {
                const container = $(this);
                container.empty();
                container.append(groups.map(group => group.clone()));
            });
        }
    }

    // Static methods for visibility-based polling management
    static initializeVisibilityMonitoring() {
        // Only initialize once
        if (this.intersectionObserver && this.mutationObserver) {
            return;
        }

        // Set up Intersection Observer to detect when statistics widgets are visible
        this.intersectionObserver = new IntersectionObserver((entries) => {
            let hasVisibleWidget = false;
            
            entries.forEach(entry => {
                if (entry.isIntersecting && entry.target.matches('[widget-name="StatisticsWidget"]')) {
                    hasVisibleWidget = true;
                    
                    // Immediately fetch data when widget becomes visible, but only if charts are ready
                    const widget = $(entry.target);
                    const widgetInstance = widget.data('widgetInstance');
                    
                    if (widgetInstance && widgetInstance.memoryChart && typeof getSystemInfoAPI === 'function') {
                        // Charts are ready, fetch data immediately
                        getSystemInfoAPI();
                    } else {
                        // Charts not ready yet, wait for them to initialize
                        const checkCharts = setInterval(() => {
                            const updatedWidgetInstance = widget.data('widgetInstance');
                            if (updatedWidgetInstance && updatedWidgetInstance.memoryChart && typeof getSystemInfoAPI === 'function') {
                                clearInterval(checkCharts);
                                getSystemInfoAPI();
                            }
                        }, 50);
                        
                        // Safety timeout to avoid infinite waiting
                        setTimeout(() => clearInterval(checkCharts), 2000);
                    }
                }
            });
            
            // Check if any statistics widgets are currently visible
            this.updatePollingState();
        }, {
            threshold: 0.1 // Trigger when 10% of the widget is visible
        });

        // Set up Mutation Observer to watch for statistics widgets being added/removed
        this.mutationObserver = new MutationObserver((mutations) => {
            let shouldUpdate = false;
            
            mutations.forEach(mutation => {
                // Check added nodes
                mutation.addedNodes.forEach(node => {
                    if (node.nodeType === Node.ELEMENT_NODE) {
                        if (node.matches && node.matches('[widget-name="StatisticsWidget"]')) {
                            this.intersectionObserver.observe(node);
                            shouldUpdate = true;
                        }
                        // Also check child nodes
                        const statsWidgets = node.querySelectorAll && node.querySelectorAll('[widget-name="StatisticsWidget"]');
                        if (statsWidgets && statsWidgets.length > 0) {
                            statsWidgets.forEach(widget => {
                                this.intersectionObserver.observe(widget);
                            });
                            shouldUpdate = true;
                        }
                    }
                });
                
                // Check removed nodes
                mutation.removedNodes.forEach(node => {
                    if (node.nodeType === Node.ELEMENT_NODE) {
                        if (node.matches && node.matches('[widget-name="StatisticsWidget"]')) {
                            this.intersectionObserver.unobserve(node);
                            shouldUpdate = true;
                        }
                        // Also check child nodes
                        const statsWidgets = node.querySelectorAll && node.querySelectorAll('[widget-name="StatisticsWidget"]');
                        if (statsWidgets && statsWidgets.length > 0) {
                            statsWidgets.forEach(widget => {
                                this.intersectionObserver.unobserve(widget);
                            });
                            shouldUpdate = true;
                        }
                    }
                });
            });
            
            if (shouldUpdate) {
                this.updatePollingState();
            }
        });

        // Start observing the document body for changes
        this.mutationObserver.observe(document.body, {
            childList: true,
            subtree: true
        });

        // Observe any existing statistics widgets
        const existingWidgets = document.querySelectorAll('[widget-name="StatisticsWidget"]');
        existingWidgets.forEach(widget => {
            this.intersectionObserver.observe(widget);
        });

        // Initial polling state update
        this.updatePollingState();
    }

    static updatePollingState() {
        // Check if any statistics widgets are currently visible
        const allStatsWidgets = document.querySelectorAll('[widget-name="StatisticsWidget"]');
        let hasVisibleWidget = false;
        
        allStatsWidgets.forEach(widget => {
            const rect = widget.getBoundingClientRect();
            const isVisible = rect.top < window.innerHeight && 
                            rect.bottom > 0 && 
                            rect.left < window.innerWidth && 
                            rect.right > 0 &&
                            widget.offsetParent !== null; // Check if element is not hidden
            
            if (isVisible) {
                hasVisibleWidget = true;
            }
        });

        // Start or stop polling based on visibility
        if (hasVisibleWidget && !this.isPollingActive) {
            this.startPolling();
        } else if (!hasVisibleWidget && this.isPollingActive) {
            this.stopPolling();
        }
    }

    static startPolling() {
        if (this.pollingInterval !== null) return;

        this.isPollingActive = true;
        this.pollingInterval = setInterval(() => {
            if (typeof getSystemInfoAPI === 'function') {
                getSystemInfoAPI();
            }
        }, 5000);
    }

    static stopPolling() {
        if (this.pollingInterval === null) return;

        this.isPollingActive = false;
        clearInterval(this.pollingInterval);
        this.pollingInterval = null;
    }
}