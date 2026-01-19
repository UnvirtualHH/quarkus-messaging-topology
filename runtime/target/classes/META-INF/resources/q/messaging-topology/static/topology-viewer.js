// runtime/src/main/resources/META-INF/resources/q/messaging-topology/static/topology-viewer.js

// Initialize Mermaid
mermaid.initialize({
    startOnLoad: true,
    theme: 'default',
    flowchart: {
        useMaxWidth: true,
        htmlLabels: true,
        curve: 'basis'
    }
});

let currentChannel = {};

/**
 * Show channel details modal
 */
async function showChannelDetails(service, channelName, topic, direction, className, methodName) {
    currentChannel = { service, channelName, topic, direction, className, methodName };

    // Update modal header
    document.getElementById('modalTitle').textContent = topic;
    document.getElementById('modalSubtitle').textContent =
        direction === 'incoming' ? '⬇️ Consumer' : '⬆️ Producer';

    // Update detail values
    document.getElementById('detailService').textContent = service;
    document.getElementById('detailChannelName').textContent = channelName;
    document.getElementById('detailTopic').textContent = topic;
    document.getElementById('detailDirection').textContent = direction;
    document.getElementById('detailImplementation').textContent = className + '.' + methodName + '()';

    // Check if local service
    const localTopology = await fetch('/q/messaging-topology').then(r => r.json());
    const isLocal = localTopology.serviceName === service;

    // Show/hide send button based on local/remote and direction
    const sendButton = document.getElementById('sendMessageBtn');
    if (!isLocal || direction === 'incoming') {
        sendButton.style.display = 'none';
    } else {
        sendButton.style.display = 'flex';
    }

    // Load schema
    await loadSchema(service, channelName, direction);

    // Show modal
    document.getElementById('channelModal').classList.add('show');
}

/**
 * Close the modal
 */
function closeModal() {
    document.getElementById('channelModal').classList.remove('show');
    document.getElementById('messageComposer').classList.remove('show');
}

/**
 * Toggle message composer visibility
 */
function toggleMessageComposer() {
    const composer = document.getElementById('messageComposer');
    composer.classList.toggle('show');

    if (composer.classList.contains('show')) {
        loadExamplePayload();
    }
}

/**
 * Load schema for a channel
 */
async function loadSchema(service, channelName, direction) {
    const schemaViewer = document.getElementById('schemaViewer');

    try {
        const localTopology = await fetch('/q/messaging-topology').then(r => r.json());
        const isLocal = localTopology.serviceName === service;

        let schemaUrl;
        if (isLocal) {
            schemaUrl = `/q/messaging-topology/schema?channel=${channelName}&direction=${direction}`;
        } else {
            schemaUrl = await findRemoteSchemaUrl(service, channelName, direction);
        }

        if (!schemaUrl) {
            schemaViewer.innerHTML = createWarningMessage(
                '⚠️ Could not reach service: ' + service,
                'The service may be offline or not accessible'
            );
            return;
        }

        const response = await fetch(schemaUrl);

        if (response.ok) {
            const schema = await response.json();
            schemaViewer.innerHTML = '<pre style="margin:0;">' +
                JSON.stringify(schema, null, 2) + '</pre>';
        } else {
            schemaViewer.innerHTML = createInfoMessage(
                'Schema information not available',
                '💡 Add fields to your message class to enable schema introspection'
            );
        }
    } catch (error) {
        schemaViewer.innerHTML = createErrorMessage('Failed to load schema: ' + error.message);
    }
}

/**
 * Find remote schema URL for a service
 */
async function findRemoteSchemaUrl(serviceName, channelName, direction) {
    try {
        const response = await fetch('/q/messaging-topology/services');
        if (!response.ok) {
            console.error('Failed to get service list');
            return null;
        }

        const services = await response.json();

        for (const serviceUrl of services) {
            try {
                const topologyResponse = await fetch(`${serviceUrl}/q/messaging-topology`);
                if (topologyResponse.ok) {
                    const topology = await topologyResponse.json();
                    if (topology.serviceName === serviceName) {
                        return `${serviceUrl}/q/messaging-topology/schema?channel=${channelName}&direction=${direction}`;
                    }
                }
            } catch (e) {
                console.log('Service not reachable:', serviceUrl);
            }
        }

        return null;
    } catch (error) {
        console.error('Error finding remote service:', error);
        return null;
    }
}

/**
 * Load example payload for a channel
 */
async function loadExamplePayload() {
    const textarea = document.getElementById('messagePayload');

    try {
        const localTopology = await fetch('/q/messaging-topology').then(r => r.json());
        const isLocal = localTopology.serviceName === currentChannel.service;

        let exampleUrl;
        if (isLocal) {
            exampleUrl = `/q/messaging-topology/example?channel=${currentChannel.channelName}&direction=${currentChannel.direction}`;
        } else {
            const baseUrl = await findRemoteServiceUrl(currentChannel.service);
            if (baseUrl) {
                exampleUrl = `${baseUrl}/q/messaging-topology/example?channel=${currentChannel.channelName}&direction=${currentChannel.direction}`;
            }
        }

        if (exampleUrl) {
            const response = await fetch(exampleUrl);
            if (response.ok) {
                const example = await response.json();
                textarea.value = JSON.stringify(example, null, 2);
                return;
            }
        }

        // Fallback example
        textarea.value = JSON.stringify({
            id: "test-" + Date.now(),
            message: "Test message from topology viewer",
            timestamp: new Date().toISOString()
        }, null, 2);
    } catch (error) {
        console.error('Failed to load example:', error);
    }
}

/**
 * Find remote service URL by name
 */
async function findRemoteServiceUrl(serviceName) {
    try {
        const response = await fetch('/q/messaging-topology/services');
        if (!response.ok) return null;

        const services = await response.json();

        for (const serviceUrl of services) {
            try {
                const topologyResponse = await fetch(`${serviceUrl}/q/messaging-topology`);
                if (topologyResponse.ok) {
                    const topology = await topologyResponse.json();
                    if (topology.serviceName === serviceName) {
                        return serviceUrl;
                    }
                }
            } catch (e) {
                // Ignore and continue
            }
        }
        return null;
    } catch (error) {
        return null;
    }
}

/**
 * Send a test message
 */
async function sendMessage() {
    const payload = document.getElementById('messagePayload').value;
    const result = document.getElementById('sendResult');

    result.innerHTML = '<div style="color: #999;">Sending...</div>';

    try {
        const response = await fetch('/q/messaging-topology/send', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                channel: currentChannel.channelName,
                topic: currentChannel.topic,
                payload: JSON.parse(payload)
            })
        });

        if (response.ok) {
            result.innerHTML = createSuccessMessage('✅ Message sent successfully!');
        } else {
            const error = await response.text();
            result.innerHTML = createErrorMessage('❌ Failed: ' + error);
        }
    } catch (error) {
        result.innerHTML = createErrorMessage('❌ Error: ' + error.message);
    }
}

/**
 * Export schema as JSON file
 */
function exportSchema() {
    const schema = document.getElementById('schemaViewer').textContent;
    const blob = new Blob([schema], { type: 'application/json' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `${currentChannel.channelName}-schema.json`;
    a.click();
    URL.revokeObjectURL(url);
}

/**
 * Helper: Create success message HTML
 */
function createSuccessMessage(message) {
    return `<div style="color: #27AE60; padding: 10px; background: #e8f5e9; border-radius: 4px;">${message}</div>`;
}

/**
 * Helper: Create error message HTML
 */
function createErrorMessage(message) {
    return `<div style="color: #e74c3c; padding: 10px; background: #ffebee; border-radius: 4px;">${message}</div>`;
}

/**
 * Helper: Create info message HTML
 */
function createInfoMessage(title, subtitle) {
    return `
        <div style="color: #999;">
            <p>${title}</p>
            <p style="font-size: 0.9em; margin-top: 8px;">${subtitle}</p>
        </div>
    `;
}

/**
 * Helper: Create warning message HTML
 */
function createWarningMessage(title, subtitle) {
    return `
        <div style="color: #ff9800;">
            <p>${title}</p>
            <p style="font-size: 0.9em; margin-top: 8px;">${subtitle}</p>
        </div>
    `;
}

/**
 * Keyboard shortcuts
 */
document.addEventListener('keydown', (e) => {
    if (e.key === 'Escape') {
        closeModal();
    }
});