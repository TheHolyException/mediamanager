class StatisticsWidget extends BaseWidget {
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

        // Initialize charts after DOM is ready
        const self = this;
        setTimeout(() => {
            if (typeof Chart !== 'undefined') {
                self.initCharts();
                window.statisticsWidgetInstance = self;
            } else {
                // Wait for Chart.js to load
                const checkChart = setInterval(() => {
                    if (typeof Chart !== 'undefined') {
                        clearInterval(checkChart);
                        self.initCharts();
                        window.statisticsWidgetInstance = self;
                    }
                }, 100);
            }
        }, 100);
        
        sendPacket('systemInfo', 'default');
        return widget.get(0);
    }

    destroy() {
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
        // Clear global reference
        if (window.statisticsWidgetInstance === this) {
            window.statisticsWidgetInstance = null;
        }
        super.destroy();
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

    static updateStatistics(responseData) {
        let container = $('[widget-name="StatisticsWidget"] .statistics-tables');
        if (container.length == 0) return;

        // Update memory historical chart
        if (responseData.memoryHistory && window.statisticsWidgetInstance && window.statisticsWidgetInstance.memoryChart) {
            const memoryData = responseData.memoryHistory;
            const chart = window.statisticsWidgetInstance.memoryChart;
            
            // Clear existing data
            chart.data.labels = [];
            chart.data.datasets[0].data = [];
            
            // Add historical data points
            memoryData.forEach(point => {
                chart.data.labels.push(new Date(point.timestamp));
                chart.data.datasets[0].data.push(point.usagePercent);
            });
            
            chart.update('none');
        }

        // Update download historical chart
        if (responseData.downloadHistory && window.statisticsWidgetInstance && window.statisticsWidgetInstance.systemChart) {
            const downloadData = responseData.downloadHistory;
            const chart = window.statisticsWidgetInstance.systemChart;
            
            // Clear existing data
            chart.data.labels = [];
            chart.data.datasets.forEach(dataset => {
                dataset.data = [];
            });
            
            // Add historical data points
            downloadData.forEach(point => {
                const timestamp = new Date(point.timestamp);
                chart.data.labels.push(timestamp);
                chart.data.datasets[0].data.push(point.total);        // Total Downloads
                chart.data.datasets[1].data.push(point.active);       // Active Downloads
                chart.data.datasets[2].data.push(point.failed);       // Failed Downloads
                chart.data.datasets[3].data.push(point.completed);    // Completed Downloads
            });
            
            chart.update('none');
        }

        // Update thread historical chart
        if (responseData.threadHistory && window.statisticsWidgetInstance && window.statisticsWidgetInstance.threadChart) {
            const threadData = responseData.threadHistory;
            const chart = window.statisticsWidgetInstance.threadChart;
            
            // Clear existing data
            chart.data.labels = [];
            chart.data.datasets.forEach(dataset => {
                dataset.data = [];
            });
            
            // Add historical data points
            threadData.forEach(point => {
                const timestamp = new Date(point.timestamp);
                chart.data.labels.push(timestamp);
                chart.data.datasets[0].data.push(point.active || 0);         // Active Threads
                chart.data.datasets[1].data.push(point.queued || 0);         // Queued Tasks
                chart.data.datasets[2].data.push(point.RUNNABLE || 0);       // RUNNABLE threads
                chart.data.datasets[3].data.push(point.WAITING || 0);        // WAITING threads
            });
            
            chart.update('none');
        }

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

        container.empty();
        container.append(groups);
    }
}