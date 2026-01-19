// deployment/src/main/resources/dev-ui/qwc-messaging-topology.js
import { LitElement, html, css } from 'lit';
import { JsonRpc } from 'jsonrpc';
import '@vaadin/grid';
import '@vaadin/text-field';
import '@vaadin/button';

export class QwcMessagingTopology extends LitElement {

    jsonRpc = new JsonRpc(this);

    static properties = {
        _services: { state: true },
        _topology: { state: true },
        _loading: { state: true },
        _error: { state: true }
    }

    constructor() {
        super();
        this._services = [];
        this._topology = null;
        this._loading = false;
        this._error = null;
    }

    connectedCallback() {
        super.connectedCallback();
        this._loadLocalTopology();
    }

    static styles = css`
        :host {
            display: flex;
            flex-direction: column;
            gap: 20px;
            padding: 20px;
        }
        .header {
            display: flex;
            justify-content: space-between;
            align-items: center;
        }
        .service-discovery {
            background: #f5f5f5;
            padding: 20px;
            border-radius: 8px;
        }
        .service-input {
            display: flex;
            gap: 10px;
            margin-bottom: 15px;
        }
        .services-list {
            display: flex;
            flex-wrap: wrap;
            gap: 10px;
        }
        .service-chip {
            background: #4A90E2;
            color: white;
            padding: 8px 15px;
            border-radius: 20px;
            display: flex;
            align-items: center;
            gap: 8px;
        }
        .service-chip.error {
            background: #E74C3C;
        }
        .service-chip button {
            background: none;
            border: none;
            color: white;
            cursor: pointer;
            font-size: 16px;
        }
        .topology-view {
            background: white;
            border: 1px solid #ddd;
            border-radius: 8px;
            padding: 20px;
            min-height: 400px;
        }
        .channel-grid {
            display: grid;
            grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
            gap: 15px;
            margin-top: 20px;
        }
        .channel-card {
            border: 1px solid #ddd;
            border-radius: 8px;
            padding: 15px;
        }
        .channel-card.incoming {
            border-left: 4px solid #27AE60;
        }
        .channel-card.outgoing {
            border-left: 4px solid #E67E22;
        }
        .mermaid-container {
            background: #f8f9fa;
            padding: 20px;
            border-radius: 8px;
            overflow: auto;
        }
        .loading {
            text-align: center;
            padding: 40px;
        }
        .error {
            background: #FEE;
            border: 1px solid #E74C3C;
            color: #C0392B;
            padding: 15px;
            border-radius: 8px;
        }
    `;

    render() {
        return html`
            <div class="header">
                <h2>🔄 Messaging Topology</h2>
                <vaadin-button @click=${this._generateDiagram}>
                    Generate Diagram
                </vaadin-button>
            </div>

            ${this._error ? html`
                <div class="error">
                    ${this._error}
                </div>
            ` : ''}

            <div class="service-discovery">
                <h3>Service Discovery</h3>
                <div class="service-input">
                    <vaadin-text-field
                        id="serviceUrl"
                        placeholder="http://localhost:8081"
                        style="flex: 1">
                    </vaadin-text-field>
                    <vaadin-button @click=${this._addService}>
                        Add Service
                    </vaadin-button>
                </div>

                <div class="services-list">
                    <div class="service-chip">
                        <span>🟢 This Service</span>
                    </div>
                    ${this._services.map(service => html`
                        <div class="service-chip ${service.error ? 'error' : ''}">
                            <span>${service.error ? '🔴' : '🟢'} ${service.url}</span>
                            <button @click=${() => this._removeService(service.url)}>×</button>
                        </div>
                    `)}
                </div>
            </div>

            ${this._loading ? html`
                <div class="loading">Loading topology...</div>
            ` : ''}

            ${this._topology ? html`
                <div class="topology-view">
                    <h3>Local Service Topology</h3>
                    <p><strong>Service:</strong> ${this._topology.serviceName}</p>
                    <p><strong>Version:</strong> ${this._topology.version}</p>
                    
                    <div class="channel-grid">
                        ${this._topology.channels.map(channel => html`
                            <div class="channel-card ${channel.direction}">
                                <div style="font-weight: bold; margin-bottom: 8px;">
                                    ${channel.direction === 'incoming' ? '⬇️' : '⬆️'}
                                    ${channel.topic || channel.channelName}
                                </div>
                                <div style="font-size: 0.9em; color: #666;">
                                    <div>Channel: ${channel.channelName}</div>
                                    <div>Method: ${channel.methodName}</div>
                                    <div>Connector: ${channel.connector || 'unknown'}</div>
                                </div>
                            </div>
                        `)}
                    </div>
                </div>
            ` : ''}

            <div id="mermaidOutput" class="mermaid-container"></div>
        `;
    }

    async _loadLocalTopology() {
        this._loading = true;
        try {
            const response = await fetch('/q/messaging-topology');
            this._topology = await response.json();
        } catch (e) {
            this._error = 'Failed to load local topology: ' + e.message;
        } finally {
            this._loading = false;
        }
    }

    async _addService() {
        const input = this.shadowRoot.getElementById('serviceUrl');
        const url = input.value.trim();

        if (!url) return;

        if (this._services.find(s => s.url === url)) {
            this._error = 'Service already added';
            return;
        }

        const service = { url, topology: null, error: null };
        this._services = [...this._services, service];

        try {
            const response = await fetch(`${url}/q/messaging-topology`);
            if (!response.ok) throw new Error(`HTTP ${response.status}`);

            service.topology = await response.json();
            this._services = [...this._services];
            this._error = null;
        } catch (e) {
            service.error = e.message;
            this._services = [...this._services];
            this._error = `Failed to fetch from ${url}: ${e.message}`;
        }

        input.value = '';
    }

    _removeService(url) {
        this._services = this._services.filter(s => s.url !== url);
    }

    async _generateDiagram() {
        const allTopologies = [this._topology];

        for (const service of this._services) {
            if (service.topology) {
                allTopologies.push(service.topology);
            }
        }

        const mermaid = this._generateMermaidDiagram(allTopologies);

        const output = this.shadowRoot.getElementById('mermaidOutput');
        output.innerHTML = `<pre class="mermaid">${mermaid}</pre>`;

        // Mermaid rendern (müsste importiert werden)
        if (window.mermaid) {
            window.mermaid.init(undefined, output.querySelector('.mermaid'));
        }
    }

    _generateMermaidDiagram(topologies) {
        const lines = ['graph LR'];

        const topics = new Map();

        // Services sammeln
        for (const topology of topologies) {
            if (!topology) continue;

            const serviceId = this._sanitize(topology.serviceName);
            lines.push(`    ${serviceId}["📦 ${topology.serviceName}"]`);

            for (const channel of topology.channels) {
                const topic = channel.topic || channel.channelName;

                if (!topics.has(topic)) {
                    topics.set(topic, { producers: [], consumers: [] });
                }

                if (channel.direction === 'incoming') {
                    topics.get(topic).consumers.push({
                        service: topology.serviceName,
                        method: channel.methodName
                    });
                } else {
                    topics.get(topic).producers.push({
                        service: topology.serviceName,
                        method: channel.methodName
                    });
                }
            }
        }

        // Topics rendern
        for (const [topic, data] of topics) {
            const topicId = this._sanitize('topic_' + topic);
            lines.push(`    ${topicId}(("💬 ${topic}"))`);
        }

        // Connections
        for (const [topic, data] of topics) {
            const topicId = this._sanitize('topic_' + topic);

            for (const producer of data.producers) {
                const serviceId = this._sanitize(producer.service);
                lines.push(`    ${serviceId} -->|"${producer.method}"| ${topicId}`);
            }

            for (const consumer of data.consumers) {
                const serviceId = this._sanitize(consumer.service);
                lines.push(`    ${topicId} -->|"${consumer.method}"| ${serviceId}`);
            }
        }

        return lines.join('\n');
    }

    _sanitize(str) {
        return str.replace(/[^a-zA-Z0-9_]/g, '_');
    }
}

customElements.define('qwc-messaging-topology', QwcMessagingTopology);