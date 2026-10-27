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
let cachedTopology = null;

document.addEventListener('click', function(e) {
    const channelEl = e.target.closest('.channel[data-service]');
    if (channelEl) {
        showChannelDetails(
            channelEl.dataset.service,
            channelEl.dataset.channel,
            channelEl.dataset.topic,
            channelEl.dataset.direction,
            channelEl.dataset.class,
            channelEl.dataset.method
        );
    }
});

async function getLocalTopology() {
    if (!cachedTopology) {
        cachedTopology = await fetch('/q/messaging-topology').then(r => r.json());
    }
    return cachedTopology;
}

async function showChannelDetails(service, channelName, topic, direction, className, methodName) {
    currentChannel = { service, channelName, topic, direction, className, methodName };

    document.getElementById('modalTitle').textContent = topic;
    document.getElementById('modalSubtitle').textContent =
        direction === 'incoming' ? '⬇️ Consumer' : '⬆️ Producer';

    document.getElementById('detailService').textContent = service;
    document.getElementById('detailChannelName').textContent = channelName;
    document.getElementById('detailTopic').textContent = topic;
    document.getElementById('detailDirection').textContent = direction;
    document.getElementById('detailImplementation').textContent = className + '.' + methodName + '()';

    const localTopology = await getLocalTopology();
    const isLocal = localTopology && localTopology.serviceName === service;

    const sendButton = document.getElementById('sendMessageBtn');
    sendButton.style.display = (!isLocal || direction === 'incoming') ? 'none' : 'flex';

    loadSchema(channelName, direction);

    document.getElementById('channelModal').classList.add('show');
}

function closeModal() {
    document.getElementById('channelModal').classList.remove('show');
    document.getElementById('messageComposer').classList.remove('show');
    currentSchema = null;
}

function toggleMessageComposer() {
    const composer = document.getElementById('messageComposer');
    composer.classList.toggle('show');

    if (composer.classList.contains('show')) {
        loadExamplePayload();
    }
}

async function loadSchema(channelName, direction) {
    try {
        const topology = await getLocalTopology();

        if (topology && topology.channels) {
            const channel = topology.channels.find(ch =>
                ch.channelName === channelName && ch.direction === direction
            );

            if (channel && channel.schema) {
                currentSchema = channel.schema;
            } else {
                currentSchema = null;
            }
        }
    } catch (error) {
        console.error('Failed to load schema:', error);
        currentSchema = null;
    }
}

async function loadExamplePayload() {
    const textarea = document.getElementById('messagePayload');

    try {
        const topology = await getLocalTopology();

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

        textarea.value = JSON.stringify({
            id: "test-" + Date.now(),
            message: "Test message from topology viewer",
            timestamp: new Date().toISOString()
        }, null, 2);
    } catch (error) {
        console.error('Failed to load example:', error);
    }
}

async function sendMessage() {
    const result = document.getElementById('sendResult');
    result.innerHTML = '<div style="color: #999;">Sending...</div>';

    try {
        let payload;

        if (document.getElementById('jsonMode').style.display !== 'none') {
            const jsonText = document.getElementById('messagePayload').value;
            payload = JSON.parse(jsonText);
        } else {
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
            result.innerHTML = '<div style="color: #27AE60; padding: 10px; background: #e8f5e9; border-radius: 4px;">✅ Message sent successfully!</div>';
        } else {
            const error = await response.json();
            result.innerHTML = '<div style="color: #e74c3c; padding: 10px; background: #ffebee; border-radius: 4px;">❌ Failed: ' + (error.error || 'Unknown error') + '</div>';
        }
    } catch (error) {
        result.innerHTML = '<div style="color: #e74c3c; padding: 10px; background: #ffebee; border-radius: 4px;">❌ Error: ' + error.message + '</div>';
    }
}

function exportSchema() {
    if (!currentSchema) {
        alert('No schema available for this channel.');
        return;
    }
    const blob = new Blob([JSON.stringify(currentSchema, null, 2)], { type: 'application/json' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = (currentChannel.channelName || 'schema') + '-schema.json';
    a.click();
    URL.revokeObjectURL(url);
}

function switchToJsonMode() {
    document.getElementById('jsonMode').style.display = 'block';
    document.getElementById('formMode').style.display = 'none';
    document.getElementById('jsonModeBtn').classList.add('active');
    document.getElementById('formModeBtn').classList.remove('active');
}

function switchToFormMode() {
    document.getElementById('jsonMode').style.display = 'none';
    document.getElementById('formMode').style.display = 'block';
    document.getElementById('jsonModeBtn').classList.remove('active');
    document.getElementById('formModeBtn').classList.add('active');
    generateFormFields();
}

function generateFormFields() {
    const formFields = document.getElementById('formFields');

    if (!currentSchema || !currentSchema.properties) {
        formFields.innerHTML = '<div style="color: #999; padding: 20px; text-align: center;">No schema available to generate form fields.</div>';
        return;
    }

    let html = '';

    for (const [fieldName, fieldSchema] of Object.entries(currentSchema.properties)) {
        const isRequired = currentSchema.required && currentSchema.required.includes(fieldName);

        html += '<div class="form-field">';
        html += '<label for="field_' + fieldName + '">' + fieldName;
        if (isRequired) html += '<span class="required">*</span>';
        html += '</label>';
        html += generateInputField(fieldName, fieldSchema);
        html += '</div>';
    }

    formFields.innerHTML = html;
    populateFormWithExample();
}

function generateInputField(fieldName, fieldSchema) {
    const fieldType = fieldSchema.type;
    const fieldId = 'field_' + fieldName;

    switch (fieldType) {
        case 'string':
            return '<input type="text" id="' + fieldId + '" name="' + fieldName + '" class="form-input">';
        case 'integer':
            return '<input type="number" id="' + fieldId + '" name="' + fieldName + '" class="form-input" step="1">';
        case 'number':
            return '<input type="number" id="' + fieldId + '" name="' + fieldName + '" class="form-input" step="any">';
        case 'boolean':
            return '<div class="checkbox-wrapper"><input type="checkbox" id="' + fieldId + '" name="' + fieldName + '" class="form-checkbox"><span class="checkbox-label">Enable</span></div>';
        case 'array':
            return '<textarea id="' + fieldId + '" name="' + fieldName + '" class="form-input" rows="3" placeholder="Enter comma-separated values"></textarea>';
        case 'object':
            return '<textarea id="' + fieldId + '" name="' + fieldName + '" class="form-input" rows="4" placeholder="Enter JSON object"></textarea>';
        default:
            return '<input type="text" id="' + fieldId + '" name="' + fieldName + '" class="form-input">';
    }
}

async function populateFormWithExample() {
    try {
        const topology = await getLocalTopology();

        if (topology && topology.channels) {
            const channel = topology.channels.find(ch =>
                ch.channelName === currentChannel.channelName &&
                ch.direction === currentChannel.direction
            );

            if (channel && channel.examplePayload) {
                for (const [fieldName, value] of Object.entries(channel.examplePayload)) {
                    const fieldElement = document.getElementById('field_' + fieldName);
                    if (!fieldElement) continue;

                    if (fieldElement.type === 'checkbox') {
                        fieldElement.checked = value === true;
                    } else if (typeof value === 'object') {
                        fieldElement.value = JSON.stringify(value, null, 2);
                    } else {
                        fieldElement.value = value;
                    }
                }
            }
        }
    } catch (error) {
        console.error('Failed to populate form with example:', error);
    }
}

function getFormData() {
    const formFields = document.getElementById('formFields');
    const inputs = formFields.querySelectorAll('input, textarea, select');
    const data = {};

    inputs.forEach(function(input) {
        const fieldName = input.name;
        if (!currentSchema || !currentSchema.properties || !currentSchema.properties[fieldName]) return;

        const fieldSchema = currentSchema.properties[fieldName];
        let value;

        if (input.type === 'checkbox') {
            value = input.checked;
        } else if (input.type === 'number') {
            value = input.value ? (fieldSchema.type === 'integer' ? parseInt(input.value) : parseFloat(input.value)) : null;
        } else if (fieldSchema.type === 'array' && input.value) {
            value = input.value.split(',').map(function(v) { return v.trim(); }).filter(function(v) { return v; });
        } else if (fieldSchema.type === 'object' && input.value) {
            try {
                value = JSON.parse(input.value);
            } catch (e) {
                value = input.value;
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

document.addEventListener('keydown', function(e) {
    if (e.key === 'Escape') {
        closeModal();
    }
});
