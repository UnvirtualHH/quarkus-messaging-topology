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
let currentSchema = null;
let currentMode = 'json'; // 'json' or 'form'

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
    currentSchema = null;
    currentMode = 'json';
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
    try {
        const localTopology = await fetch('/q/messaging-topology').then(r => r.json());
        const isLocal = localTopology.serviceName === service;

        let topology;
        if (isLocal) {
            topology = localTopology;
        } else {
            // Try to get remote topology
            const remoteUrl = await findRemoteServiceUrl(service);
            if (remoteUrl) {
                const response = await fetch(`${remoteUrl}/q/messaging-topology`);
                if (response.ok) {
                    topology = await response.json();
                }
            }
        }

        if (topology && topology.channels) {
            // Find the matching channel and extract its schema
            const channel = topology.channels.find(ch =>
                ch.channelName === channelName && ch.direction === direction
            );

            if (channel && channel.schema) {
                currentSchema = channel.schema;
                console.log('✅ Schema loaded:', currentSchema);
            } else {
                console.log('⚠️ No schema found for channel');
                currentSchema = null;
            }
        }
    } catch (error) {
        console.error('Failed to load schema:', error);
        currentSchema = null;
    }
}

// Removed: findRemoteSchemaUrl - no longer needed as schema is in topology response

/**
 * Load example payload for a channel
 */
async function loadExamplePayload() {
    const textarea = document.getElementById('messagePayload');

    try {
        const localTopology = await fetch('/q/messaging-topology').then(r => r.json());
        const isLocal = localTopology.serviceName === currentChannel.service;

        let topology;
        if (isLocal) {
            topology = localTopology;
        } else {
            const remoteUrl = await findRemoteServiceUrl(currentChannel.service);
            if (remoteUrl) {
                const response = await fetch(`${remoteUrl}/q/messaging-topology`);
                if (response.ok) {
                    topology = await response.json();
                }
            }
        }

        if (topology && topology.channels) {
            const channel = topology.channels.find(ch =>
                ch.channelName === currentChannel.channelName &&
                ch.direction === currentChannel.direction
            );

            if (channel && channel.examplePayload) {
                textarea.value = JSON.stringify(channel.examplePayload, null, 2);
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
    const result = document.getElementById('sendResult');
    result.innerHTML = '<div style="color: #999;">Sending...</div>';

    try {
        let payload;

        if (currentMode === 'json') {
            // Get payload from JSON textarea
            const jsonText = document.getElementById('messagePayload').value;
            payload = JSON.parse(jsonText);
        } else {
            // Get payload from form fields
            payload = getFormData();
        }

        const response = await fetch('/q/messaging-topology/send', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                channel: currentChannel.channelName,
                topic: currentChannel.topic,
                payload: payload
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
 * Switch to JSON mode
 */
function switchToJsonMode() {
    currentMode = 'json';
    document.getElementById('jsonMode').style.display = 'block';
    document.getElementById('formMode').style.display = 'none';
    document.getElementById('jsonModeBtn').classList.add('active');
    document.getElementById('formModeBtn').classList.remove('active');
}

/**
 * Switch to Form mode
 */
function switchToFormMode() {
    currentMode = 'form';
    document.getElementById('jsonMode').style.display = 'none';
    document.getElementById('formMode').style.display = 'block';
    document.getElementById('jsonModeBtn').classList.remove('active');
    document.getElementById('formModeBtn').classList.add('active');

    // Generate form fields from schema
    generateFormFields();
}

/**
 * Generate form fields based on the schema
 */
function generateFormFields() {
    const formFields = document.getElementById('formFields');

    if (!currentSchema || !currentSchema.properties) {
        formFields.innerHTML = '<div style="color: #999; padding: 20px; text-align: center;">No schema available to generate form fields.</div>';
        return;
    }

    let html = '';

    for (const [fieldName, fieldSchema] of Object.entries(currentSchema.properties)) {
        const fieldType = fieldSchema.type;
        const isRequired = currentSchema.required && currentSchema.required.includes(fieldName);

        html += `
            <div class="form-field">
                <label for="field_${fieldName}">
                    ${fieldName}
                    ${isRequired ? '<span class="required">*</span>' : ''}
                    ${fieldSchema.description ? `<span class="field-hint">${fieldSchema.description}</span>` : ''}
                </label>
                ${generateInputField(fieldName, fieldSchema)}
            </div>
        `;
    }

    formFields.innerHTML = html;

    // Pre-populate with example data if available
    populateFormWithExample();
}

/**
 * Generate appropriate input field based on schema type
 */
function generateInputField(fieldName, fieldSchema) {
    const fieldType = fieldSchema.type;
    const fieldId = `field_${fieldName}`;

    switch (fieldType) {
        case 'string':
            if (fieldSchema.format === 'date-time') {
                return `<input type="datetime-local" id="${fieldId}" name="${fieldName}" class="form-input">`;
            } else if (fieldSchema.format === 'date') {
                return `<input type="date" id="${fieldId}" name="${fieldName}" class="form-input">`;
            } else if (fieldSchema.format === 'email') {
                return `<input type="email" id="${fieldId}" name="${fieldName}" class="form-input">`;
            } else if (fieldSchema.format === 'uri') {
                return `<input type="url" id="${fieldId}" name="${fieldName}" class="form-input">`;
            } else if (fieldSchema.enum) {
                let options = fieldSchema.enum.map(val => `<option value="${val}">${val}</option>`).join('');
                return `<select id="${fieldId}" name="${fieldName}" class="form-input">${options}</select>`;
            } else {
                return `<input type="text" id="${fieldId}" name="${fieldName}" class="form-input">`;
            }

        case 'integer':
        case 'number':
            return `<input type="number" id="${fieldId}" name="${fieldName}" class="form-input" step="${fieldType === 'integer' ? '1' : 'any'}">`;

        case 'boolean':
            return `
                <div class="checkbox-wrapper">
                    <input type="checkbox" id="${fieldId}" name="${fieldName}" class="form-checkbox">
                    <span class="checkbox-label">Enable</span>
                </div>
            `;

        case 'array':
            return `<textarea id="${fieldId}" name="${fieldName}" class="form-input" rows="3" placeholder="Enter comma-separated values"></textarea>`;

        case 'object':
            return `<textarea id="${fieldId}" name="${fieldName}" class="form-input" rows="4" placeholder="Enter JSON object"></textarea>`;

        default:
            return `<input type="text" id="${fieldId}" name="${fieldName}" class="form-input">`;
    }
}

/**
 * Populate form fields with example data
 */
async function populateFormWithExample() {
    try {
        const localTopology = await fetch('/q/messaging-topology').then(r => r.json());
        const isLocal = localTopology.serviceName === currentChannel.service;

        let topology;
        if (isLocal) {
            topology = localTopology;
        } else {
            const remoteUrl = await findRemoteServiceUrl(currentChannel.service);
            if (remoteUrl) {
                const response = await fetch(`${remoteUrl}/q/messaging-topology`);
                if (response.ok) {
                    topology = await response.json();
                }
            }
        }

        if (topology && topology.channels) {
            const channel = topology.channels.find(ch =>
                ch.channelName === currentChannel.channelName &&
                ch.direction === currentChannel.direction
            );

            if (channel && channel.examplePayload) {
                const example = channel.examplePayload;

                // Populate form fields with example values
                for (const [fieldName, value] of Object.entries(example)) {
                    const fieldId = `field_${fieldName}`;
                    const fieldElement = document.getElementById(fieldId);

                    if (fieldElement) {
                        if (fieldElement.type === 'checkbox') {
                            fieldElement.checked = value === true;
                        } else if (fieldElement.type === 'datetime-local' && value) {
                            // Convert ISO string to datetime-local format
                            const date = new Date(value);
                            fieldElement.value = date.toISOString().slice(0, 16);
                        } else if (typeof value === 'object') {
                            fieldElement.value = JSON.stringify(value, null, 2);
                        } else if (Array.isArray(value)) {
                            fieldElement.value = value.join(', ');
                        } else {
                            fieldElement.value = value;
                        }
                    }
                }
            }
        }
    } catch (error) {
        console.error('Failed to populate form with example:', error);
    }
}

/**
 * Collect form data and convert to JSON
 */
function getFormData() {
    const formFields = document.getElementById('formFields');
    const inputs = formFields.querySelectorAll('input, textarea, select');
    const data = {};

    inputs.forEach(input => {
        const fieldName = input.name;
        const fieldSchema = currentSchema.properties[fieldName];

        if (!fieldSchema) return;

        let value;

        if (input.type === 'checkbox') {
            value = input.checked;
        } else if (input.type === 'number') {
            value = input.value ? (fieldSchema.type === 'integer' ? parseInt(input.value) : parseFloat(input.value)) : null;
        } else if (input.type === 'datetime-local' && input.value) {
            // Convert to ISO string
            value = new Date(input.value).toISOString();
        } else if (fieldSchema.type === 'array' && input.value) {
            // Parse comma-separated values
            value = input.value.split(',').map(v => v.trim()).filter(v => v);
        } else if (fieldSchema.type === 'object' && input.value) {
            try {
                value = JSON.parse(input.value);
            } catch (e) {
                value = input.value; // Keep as string if not valid JSON
            }
        } else {
            value = input.value || null;
        }

        if (value !== null && value !== '') {
            data[fieldName] = value;
        }
    });

    return data;
}

/**
 * Keyboard shortcuts
 */
document.addEventListener('keydown', (e) => {
    if (e.key === 'Escape') {
        closeModal();
    }
});