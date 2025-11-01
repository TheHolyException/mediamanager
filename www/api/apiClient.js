/**
 * Centralized API client for MediaManager
 * Contains all REST API calls used throughout the application
 */
class ApiClient {
    
    /**
     * Generic fetch wrapper with error handling
     * @param {string} url - The API endpoint URL
     * @param {object} options - Fetch options (method, headers, body, etc.)
     * @returns {Promise} Promise that resolves to the response data
     */
    static async request(url, options = {}) {
        const defaultOptions = {
            headers: {
                'Content-Type': 'application/json',
                ...options.headers
            },
            ...options
        };

        try {
            const response = await fetch(url, defaultOptions);
            
            if (response.ok) {
                // Handle different response types
                const contentType = response.headers.get('content-type');
                if (contentType && contentType.includes('application/json')) {
                    return await response.json();
                }
                return await response.text();
            } else {
                // Try to get error message from response
                let errorMessage = 'Request failed';
                try {
                    const errorData = await response.json();
                    errorMessage = errorData.error || errorMessage;
                } catch (e) {
                    errorMessage = response.statusText || errorMessage;
                }
                throw new Error(`${response.status}: ${errorMessage}`);
            }
        } catch (error) {
            console.error(`API request failed: ${url}`, error);
            throw error;
        }
    }

    // ========================================
    // AutoLoader API Methods
    // ========================================

    /**
     * Get all anime subscriptions
     * @returns {Promise<Array>} List of subscriptions
     */
    static async getSubscriptions() {
        return this.request('/api/autoloader/subscriptions');
    }

    /**
     * Add a new anime subscription
     * @param {object} subscriptionData - Subscription data
     * @returns {Promise<object>} Created subscription
     */
    static async addSubscription(subscriptionData) {
        return this.request('/api/autoloader/subscriptions', {
            method: 'POST',
            body: JSON.stringify(subscriptionData)
        });
    }

    /**
     * Update an existing subscription
     * @param {number} id - Subscription ID
     * @param {object} updateData - Data to update
     * @returns {Promise<object>} Updated subscription
     */
    static async updateSubscription(id, updateData) {
        return this.request(`/api/autoloader/subscriptions/${id}`, {
            method: 'PUT',
            body: JSON.stringify(updateData)
        });
    }

    /**
     * Delete a subscription
     * @param {number} id - Subscription ID
     * @returns {Promise} 
     */
    static async deleteSubscription(id) {
        return this.request(`/api/autoloader/subscriptions/${id}`, {
            method: 'DELETE'
        });
    }

    /**
     * Scan a subscription for new episodes
     * @param {number} id - Subscription ID
     * @returns {Promise<object>} Scan results
     */
    static async scanSubscription(id) {
        return this.request(`/api/autoloader/subscriptions/${id}/scan`, {
            method: 'POST'
        });
    }

    /**
     * Start download for a subscription
     * @param {number} id - Subscription ID
     * @returns {Promise<object>} Download status
     */
    static async downloadSubscription(id) {
        return this.request(`/api/autoloader/subscriptions/${id}/download`, {
            method: 'POST'
        });
    }

    /**
     * Pause a subscription
     * @param {number} id - Subscription ID
     * @returns {Promise<object>} Updated subscription
     */
    static async pauseSubscription(id) {
        return this.request(`/api/autoloader/subscriptions/${id}/pause`, {
            method: 'POST'
        });
    }

    /**
     * Resume a subscription
     * @param {number} id - Subscription ID
     * @returns {Promise<object>} Updated subscription
     */
    static async resumeSubscription(id) {
        return this.request(`/api/autoloader/subscriptions/${id}/resume`, {
            method: 'POST'
        });
    }

    /**
     * Get alternate providers for an episode
     * @param {number} animeId - Anime/subscription ID
     * @param {number} seasonId - Season ID
     * @param {number} episodeId - Episode ID
     * @returns {Promise<Array>} List of alternate providers
     */
    static async getAlternateProviders(animeId, seasonId, episodeId) {
        const url = `/api/autoloader/subscriptions/${animeId}/providers?seasonId=${seasonId}&episodeId=${episodeId}`;
        const data = await this.request(url);
        return data.providers;
    }

    // ========================================
    // Settings API Methods
    // ========================================

    /**
     * Get all settings
     * @returns {Promise<object>} Settings data
     */
    static async getSettings() {
        return this.request('/api/settings');
    }

    /**
     * Update settings
     * @param {object} settingsData - Settings to update
     * @returns {Promise<object>} Updated settings
     */
    static async updateSettings(settingsData) {
        return this.request('/api/settings', {
            method: 'POST',
            body: JSON.stringify(settingsData)
        });
    }

    // ========================================
    // Aniworld API Methods
    // ========================================

    /**
     * Resolve Aniworld URL
     * @param {string} url - Aniworld URL to resolve
     * @param {number} languageId - Language ID
     * @returns {Promise<object>} Resolved data
     */
    static async resolveAniworldUrl(url, languageId) {
        const params = new URLSearchParams({
            url,
            language: languageId.toString()
        });
        
        return this.request(`/api/aniworld/resolve?${params}`);
    }

    // ========================================
    // Download API Methods (if needed in future)
    // ========================================

    /**
     * Get download status/list
     * Note: Currently using WebSocket, but could be migrated to REST API later
     */
    // static async getDownloads() {
    //     return this.request('/api/downloads');
    // }

    // ========================================
    // Statistics API Methods (if needed in future)
    // ========================================

    /**
     * Get statistics data
     * Note: Currently using WebSocket, but could be migrated to REST API later
     */
    // static async getStatistics() {
    //     return this.request('/api/statistics');
    // }
}

// Export for use in other modules
window.ApiClient = ApiClient;