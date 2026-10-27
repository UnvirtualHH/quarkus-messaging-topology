import { QwcHotReloadElement, html, css } from 'qwc-hot-reload-element';
import { nothing } from 'lit';
import { JsonRpc } from 'jsonrpc';
import { themeState } from 'theme-state';
import { notifier } from 'notifier';
import mermaid from 'mermaid/mermaid.esm.min.mjs';
import '@vaadin/button';
import '@vaadin/icon';
import '@vaadin/tabs';
import '@vaadin/grid';
import '@vaadin/text-field';
import { columnBodyRenderer } from '@vaadin/grid/lit.js';
import 'qui-themed-code-block';

const MIN_SCALE = 0.1;
const MAX_SCALE = 4;
const CLICK_TOLERANCE = 5;

let renderCounter = 0;

/**
 * Dev UI page showing the messaging topology of all services as interactive diagram and as channel list.
 */
export class QwcMessagingTopology extends QwcHotReloadElement {

    jsonRpc = new JsonRpc(this);

    static styles = css`
        :host {
            display: flex;
            flex-direction: column;
            height: 100%;
            min-height: 0;
            color: var(--lumo-body-text-color);
        }

        /* Toolbar */
        .toolbar {
            display: flex;
            align-items: center;
            gap: var(--lumo-space-m);
            padding: 0 var(--lumo-space-m) var(--lumo-space-s);
            flex-wrap: wrap;
        }
        .stats {
            display: flex;
            gap: var(--lumo-space-s);
            flex-wrap: wrap;
        }
        .stat {
            display: flex;
            align-items: baseline;
            gap: 6px;
            padding: 4px 10px;
            border-radius: var(--lumo-border-radius-m);
            background: var(--lumo-contrast-5pct);
            font-size: var(--lumo-font-size-s);
            color: var(--lumo-secondary-text-color);
        }
        .stat strong {
            color: var(--lumo-body-text-color);
            font-size: var(--lumo-font-size-m);
        }
        .spacer {
            flex: 1;
        }
        .actions {
            display: flex;
            align-items: center;
            gap: 2px;
        }
        .zoom-value {
            min-width: 44px;
            text-align: center;
            font-size: var(--lumo-font-size-s);
            color: var(--lumo-secondary-text-color);
            font-variant-numeric: tabular-nums;
        }
        vaadin-tabs {
            box-shadow: none;
        }

        /* Layout */
        .body {
            flex: 1;
            display: flex;
            min-height: 0;
            border-top: 1px solid var(--lumo-contrast-10pct);
        }
        .main {
            flex: 1;
            min-width: 0;
            display: flex;
            flex-direction: column;
        }
        .message {
            margin: auto;
            max-width: 480px;
            text-align: center;
            color: var(--lumo-secondary-text-color);
        }
        .message vaadin-icon {
            width: 48px;
            height: 48px;
            color: var(--lumo-contrast-30pct);
            margin-bottom: var(--lumo-space-m);
        }
        code {
            font-family: var(--lumo-font-family-monospace, monospace);
            font-size: 0.9em;
        }

        /* Diagram */
        .viewport {
            position: relative;
            flex: 1;
            overflow: hidden;
            cursor: grab;
            touch-action: none;
            user-select: none;
            background-image: radial-gradient(var(--lumo-contrast-10pct) 1px, transparent 1px);
            background-size: 22px 22px;
        }
        .viewport.panning {
            cursor: grabbing;
        }
        .stage {
            position: absolute;
            top: 0;
            left: 0;
            transform-origin: 0 0;
        }
        .stage svg {
            display: block;
            overflow: visible;
            background: transparent !important;
        }
        .overlay {
            position: absolute;
            left: var(--lumo-space-m);
            bottom: var(--lumo-space-m);
            display: flex;
            gap: var(--lumo-space-m);
            align-items: center;
            padding: 6px 12px;
            border-radius: var(--lumo-border-radius-l);
            background: var(--lumo-base-color);
            box-shadow: var(--lumo-box-shadow-xs);
            font-size: var(--lumo-font-size-xs);
            color: var(--lumo-secondary-text-color);
            pointer-events: none;
        }
        .legend-item {
            display: flex;
            align-items: center;
            gap: 6px;
        }
        .swatch {
            width: 12px;
            height: 12px;
            border-radius: 3px;
            border: 1.5px solid;
        }
        .swatch.local {
            background: var(--lumo-primary-color);
            border-color: var(--lumo-primary-color);
        }
        .swatch.service {
            background: var(--lumo-primary-color-10pct);
            border-color: var(--lumo-primary-color);
        }
        .swatch.topic {
            background: var(--lumo-success-color-10pct);
            border-color: var(--lumo-success-color);
            border-radius: 50% / 30%;
        }
        .swatch.hot {
            background: var(--lumo-warning-color-10pct, hsla(45, 100%, 50%, 0.15));
            border-color: var(--lumo-warning-color, hsl(45, 100%, 42%));
            border-radius: 50% / 30%;
        }
        .hint {
            border-left: 1px solid var(--lumo-contrast-10pct);
            padding-left: var(--lumo-space-m);
        }

        /* Mermaid output, themed with Lumo variables */
        .stage .node {
            cursor: pointer;
            transition: opacity 0.15s;
        }
        .stage .node .label-container,
        .stage .node .label-container path {
            stroke-width: 1.5px !important;
            transition: filter 0.15s;
        }
        .stage .node .nodeLabel,
        .stage .node .nodeLabel p,
        .stage .node .label text {
            color: var(--lumo-body-text-color) !important;
            fill: var(--lumo-body-text-color) !important;
        }
        .stage .node.serviceClass .label-container,
        .stage .node.serviceClass .label-container path {
            fill: var(--lumo-primary-color-10pct) !important;
            stroke: var(--lumo-primary-color) !important;
        }
        .stage .node.localServiceClass .label-container,
        .stage .node.localServiceClass .label-container path {
            fill: var(--lumo-primary-color) !important;
            stroke: var(--lumo-primary-color) !important;
        }
        .stage .node.localServiceClass .nodeLabel,
        .stage .node.localServiceClass .nodeLabel p {
            color: var(--lumo-primary-contrast-color) !important;
        }
        .stage .node.topicClass .label-container,
        .stage .node.topicClass .label-container path {
            fill: var(--lumo-success-color-10pct) !important;
            stroke: var(--lumo-success-color) !important;
        }
        .stage .node.hotTopicClass .label-container,
        .stage .node.hotTopicClass .label-container path {
            fill: var(--lumo-warning-color-10pct, hsla(45, 100%, 50%, 0.15)) !important;
            stroke: var(--lumo-warning-color, hsl(45, 100%, 42%)) !important;
        }
        .stage .node:hover .label-container,
        .stage .node.selected .label-container {
            filter: drop-shadow(0 0 6px var(--lumo-primary-color-50pct));
        }
        .stage .node.selected .label-container,
        .stage .node.selected .label-container path {
            stroke-width: 3px !important;
        }
        .stage .flowchart-link {
            stroke: var(--lumo-contrast-40pct) !important;
            stroke-width: 1.5px !important;
            transition: opacity 0.15s;
        }
        .stage marker path,
        .stage .arrowheadPath,
        .stage .arrowMarkerPath {
            fill: var(--lumo-contrast-40pct) !important;
            stroke: var(--lumo-contrast-40pct) !important;
        }
        .stage .flowchart-link.highlight {
            stroke: var(--lumo-primary-color) !important;
            stroke-width: 2.5px !important;
        }
        .stage .edgeLabel {
            transition: opacity 0.15s;
        }
        /* font sizes are set via themeCSS, so Mermaid measures the labels with them */
        .stage .edgeLabel,
        .stage .edgeLabel p,
        .stage .edgeLabel span,
        .stage .labelBkg {
            background-color: var(--lumo-base-color) !important;
            color: var(--lumo-secondary-text-color) !important;
        }
        .stage .edgeLabel rect {
            fill: var(--lumo-base-color) !important;
            opacity: 1 !important;
        }
        .stage .dimmed {
            opacity: 0.2;
        }

        /* Channel list */
        .list {
            flex: 1;
            display: flex;
            flex-direction: column;
            min-height: 0;
            padding: var(--lumo-space-s) var(--lumo-space-m) 0;
            gap: var(--lumo-space-s);
        }
        .list vaadin-text-field {
            max-width: 360px;
        }
        vaadin-grid {
            flex: 1;
            min-height: 0;
        }
        .muted {
            color: var(--lumo-secondary-text-color);
        }
        .mono {
            font-family: var(--lumo-font-family-monospace, monospace);
            font-size: var(--lumo-font-size-s);
        }

        /* Badges */
        .badge {
            display: inline-flex;
            align-items: center;
            gap: 4px;
            padding: 1px 8px;
            border-radius: 999px;
            font-size: var(--lumo-font-size-xxs);
            font-weight: 600;
            line-height: 1.6;
            white-space: nowrap;
            background: var(--lumo-contrast-10pct);
            color: var(--lumo-secondary-text-color);
        }
        .badge vaadin-icon {
            width: 10px;
            height: 10px;
        }
        .badge.outgoing {
            background: var(--lumo-primary-color-10pct);
            color: var(--lumo-primary-text-color);
        }
        .badge.incoming {
            background: var(--lumo-success-color-10pct);
            color: var(--lumo-success-text-color);
        }
        .badge.local {
            background: var(--lumo-primary-color);
            color: var(--lumo-primary-contrast-color);
        }

        /* Details panel */
        .details {
            width: 400px;
            flex-shrink: 0;
            overflow-y: auto;
            border-left: 1px solid var(--lumo-contrast-10pct);
            background: var(--lumo-base-color);
        }
        .details-header {
            position: sticky;
            top: 0;
            z-index: 1;
            display: flex;
            align-items: flex-start;
            gap: var(--lumo-space-s);
            padding: var(--lumo-space-m);
            background: var(--lumo-base-color);
            border-bottom: 1px solid var(--lumo-contrast-10pct);
        }
        .details-icon {
            flex-shrink: 0;
            width: 36px;
            height: 36px;
            border-radius: var(--lumo-border-radius-m);
            display: flex;
            align-items: center;
            justify-content: center;
            background: var(--lumo-primary-color-10pct);
            color: var(--lumo-primary-text-color);
        }
        .details-icon.topic {
            background: var(--lumo-success-color-10pct);
            color: var(--lumo-success-text-color);
        }
        .details-icon vaadin-icon {
            width: 18px;
            height: 18px;
        }
        .details-title {
            flex: 1;
            min-width: 0;
        }
        .details-title .title {
            font-size: var(--lumo-font-size-l);
            font-weight: 600;
            overflow-wrap: anywhere;
        }
        .details-title .subtitle {
            margin-top: 2px;
            font-size: var(--lumo-font-size-s);
            color: var(--lumo-secondary-text-color);
            display: flex;
            gap: 6px;
            align-items: center;
            flex-wrap: wrap;
        }
        section {
            padding: var(--lumo-space-m);
            border-bottom: 1px solid var(--lumo-contrast-5pct);
        }
        section h4 {
            margin: 0 0 var(--lumo-space-s);
            font-size: var(--lumo-font-size-xs);
            font-weight: 600;
            text-transform: uppercase;
            letter-spacing: 0.05em;
            color: var(--lumo-secondary-text-color);
            display: flex;
            justify-content: space-between;
            align-items: center;
        }
        .props {
            display: grid;
            grid-template-columns: max-content 1fr;
            gap: 6px var(--lumo-space-m);
            font-size: var(--lumo-font-size-s);
        }
        .props .key {
            color: var(--lumo-secondary-text-color);
        }
        .props .value {
            overflow-wrap: anywhere;
        }
        .entries {
            display: flex;
            flex-direction: column;
            gap: 4px;
        }
        .entry {
            display: flex;
            align-items: center;
            gap: var(--lumo-space-s);
            padding: 6px 8px;
            border-radius: var(--lumo-border-radius-m);
            cursor: pointer;
            font-size: var(--lumo-font-size-s);
        }
        .entry:hover {
            background: var(--lumo-contrast-5pct);
        }
        .entry .entry-main {
            flex: 1;
            min-width: 0;
        }
        .entry .entry-sub {
            color: var(--lumo-secondary-text-color);
            font-size: var(--lumo-font-size-xs);
            overflow: hidden;
            text-overflow: ellipsis;
            white-space: nowrap;
        }
        .entry > vaadin-icon {
            width: 12px;
            height: 12px;
            color: var(--lumo-contrast-40pct);
        }
        .link {
            color: var(--lumo-primary-text-color);
            cursor: pointer;
        }
        .link:hover {
            text-decoration: underline;
        }
        .empty {
            font-size: var(--lumo-font-size-s);
            color: var(--lumo-secondary-text-color);
            font-style: italic;
        }
        table.schema {
            width: 100%;
            border-collapse: collapse;
            font-size: var(--lumo-font-size-s);
        }
        table.schema td {
            padding: 4px 0;
            border-bottom: 1px solid var(--lumo-contrast-5pct);
            vertical-align: top;
        }
        table.schema td:last-child {
            text-align: right;
            color: var(--lumo-secondary-text-color);
        }
        .send-actions {
            display: flex;
            gap: var(--lumo-space-s);
            margin-top: var(--lumo-space-s);
        }
        .editor {
            border: 1px solid var(--lumo-contrast-10pct);
            border-radius: var(--lumo-border-radius-m);
            overflow: hidden;
            max-height: 320px;
            overflow-y: auto;
        }
    `;

    static properties = {
        _data: { state: true },
        _error: { state: true },
        _tab: { state: true },
        _selection: { state: true },
        _filter: { state: true },
        _scale: { state: true },
        _payload: { state: true },
        _sending: { state: true }
    };

    constructor() {
        super();
        this._tab = 0;
        this._filter = '';
        this._scale = 1;
        this._view = { x: 0, y: 0, s: 1 };
        this._svg = null;
        this._needsFit = true;
        this._themeObserver = () => this._renderDiagram();
        this._keyListener = (e) => {
            if (e.key === 'Escape' && this._selection) {
                this._select(null);
            }
        };
    }

    connectedCallback() {
        super.connectedCallback();
        themeState.addObserver(this._themeObserver);
        document.addEventListener('keydown', this._keyListener);
        this._load();
    }

    disconnectedCallback() {
        themeState.removeObserver(this._themeObserver);
        document.removeEventListener('keydown', this._keyListener);
        super.disconnectedCallback();
    }

    hotReload() {
        this._load();
    }

    // ---------------------------------------------------------------------------------------------------- data

    _load() {
        this.jsonRpc.getTopology().then(response => {
            this._error = null;
            this._setData(response.result);
        }).catch(error => {
            this._error = error?.error?.message || 'Could not load the topology';
        });
    }

    _setData(data) {
        const services = data.services || [];
        const nodes = data.nodes || {};
        const serviceIds = new Map();
        const topicIds = new Map();
        for (const [id, node] of Object.entries(nodes)) {
            (node.kind === 'service' ? serviceIds : topicIds).set(node.name, id);
        }

        const entries = [];
        const topics = new Map();
        for (const service of services) {
            for (const channel of service.channels || []) {
                const topic = channel.topic || channel.channelName;
                const entry = {
                    key: `${service.serviceName}|${channel.direction}|${channel.channelName}|${channel.className}|${channel.methodName}`,
                    service: service.serviceName,
                    local: service.serviceName === data.localService,
                    topic,
                    channel
                };
                entries.push(entry);
                if (!topics.has(topic)) {
                    topics.set(topic, { name: topic, producers: [], consumers: [] });
                }
                const t = topics.get(topic);
                (channel.direction === 'outgoing' ? t.producers : t.consumers).push(entry);
            }
        }

        const edges = entries.map(e => {
            const serviceId = serviceIds.get(e.service);
            const topicId = topicIds.get(e.topic);
            return e.channel.direction === 'outgoing'
                ? { from: serviceId, to: topicId, entry: e }
                : { from: topicId, to: serviceId, entry: e };
        });

        const diagramChanged = !this._data || this._data.diagram !== data.diagram;
        this._data = {
            ...data,
            services,
            entries,
            topics,
            edges,
            serviceIds,
            topicIds,
            stats: {
                services: services.length,
                topics: topics.size,
                producers: entries.filter(e => e.channel.direction === 'outgoing').length,
                consumers: entries.filter(e => e.channel.direction === 'incoming').length
            }
        };

        // Keep the selection if it still exists after a refresh
        if (this._selection) {
            this._selection = this._resolveSelection(this._selection);
        }
        if (diagramChanged) {
            this._renderDiagram();
        }
    }

    _resolveSelection(selection) {
        switch (selection.kind) {
            case 'service':
                return this._data.services.some(s => s.serviceName === selection.name) ? selection : null;
            case 'topic':
                return this._data.topics.has(selection.name) ? selection : null;
            case 'channel': {
                const entry = this._data.entries.find(e => e.key === selection.entry.key);
                return entry ? { kind: 'channel', entry } : null;
            }
            default:
                return null;
        }
    }

    // ---------------------------------------------------------------------------------------------------- render

    render() {
        if (this._error) {
            return this._message('font-awesome-solid:triangle-exclamation', this._error);
        }
        if (!this._data) {
            return this._message('font-awesome-solid:diagram-project', 'Loading topology…');
        }
        if (!this._data.enabled) {
            return this._message('font-awesome-solid:power-off',
                html`Messaging Topology is disabled (<code>quarkus.messaging-topology.enabled=false</code>).`);
        }
        if (this._data.entries.length === 0) {
            return this._message('font-awesome-solid:diagram-project',
                html`No messaging channels found. Channels are discovered from <code>@Incoming</code>,
                     <code>@Outgoing</code> and <code>@Channel</code>.`);
        }

        return html`
            ${this._renderToolbar()}
            <div class="body">
                <div class="main">
                    ${this._tab === 0 ? this._renderDiagramView() : this._renderChannelList()}
                </div>
                ${this._selection ? this._renderDetails() : nothing}
            </div>`;
    }

    _message(icon, text) {
        return html`
            <div class="message">
                <vaadin-icon icon="${icon}"></vaadin-icon>
                <div>${text}</div>
            </div>`;
    }

    _renderToolbar() {
        const stats = this._data.stats;
        return html`
            <div class="toolbar">
                <vaadin-tabs .selected=${this._tab} @selected-changed=${this._onTabChanged}>
                    <vaadin-tab>
                        <vaadin-icon icon="font-awesome-solid:diagram-project"></vaadin-icon>&nbsp;Diagram
                    </vaadin-tab>
                    <vaadin-tab>
                        <vaadin-icon icon="font-awesome-solid:list"></vaadin-icon>&nbsp;Channels
                    </vaadin-tab>
                </vaadin-tabs>
                <div class="stats">
                    <span class="stat"><strong>${stats.services}</strong> services</span>
                    <span class="stat"><strong>${stats.topics}</strong> topics</span>
                    <span class="stat"><strong>${stats.producers}</strong> producers</span>
                    <span class="stat"><strong>${stats.consumers}</strong> consumers</span>
                </div>
                <div class="spacer"></div>
                <div class="actions">
                    ${this._tab === 0 ? html`
                        <vaadin-button theme="tertiary icon" title="Zoom out" @click=${() => this._zoomBy(1 / 1.25)}>
                            <vaadin-icon icon="font-awesome-solid:magnifying-glass-minus"></vaadin-icon>
                        </vaadin-button>
                        <span class="zoom-value">${Math.round(this._scale * 100)}%</span>
                        <vaadin-button theme="tertiary icon" title="Zoom in" @click=${() => this._zoomBy(1.25)}>
                            <vaadin-icon icon="font-awesome-solid:magnifying-glass-plus"></vaadin-icon>
                        </vaadin-button>
                        <vaadin-button theme="tertiary icon" title="Fit to screen" @click=${this._fit}>
                            <vaadin-icon icon="font-awesome-solid:expand"></vaadin-icon>
                        </vaadin-button>
                        <vaadin-button theme="tertiary icon" title="Copy Mermaid source" @click=${this._copyMermaid}>
                            <vaadin-icon icon="font-awesome-solid:copy"></vaadin-icon>
                        </vaadin-button>` : nothing}
                    <vaadin-button theme="tertiary icon" title="Refresh" @click=${this._load}>
                        <vaadin-icon icon="font-awesome-solid:rotate"></vaadin-icon>
                    </vaadin-button>
                </div>
            </div>`;
    }

    _onTabChanged(e) {
        if (this._tab !== e.detail.value) {
            this._tab = e.detail.value;
        }
    }

    // ---------------------------------------------------------------------------------------------------- diagram

    _renderDiagramView() {
        return html`
            <div class="viewport"
                 @wheel=${this._onWheel}
                 @pointerdown=${this._onPointerDown}
                 @pointermove=${this._onPointerMove}
                 @pointerup=${this._onPointerUp}
                 @pointercancel=${this._onPointerUp}
                 @dblclick=${this._fit}>
                <div class="stage"></div>
                <div class="overlay">
                    <span class="legend-item"><span class="swatch local"></span>This service</span>
                    <span class="legend-item"><span class="swatch service"></span>Service</span>
                    <span class="legend-item"><span class="swatch topic"></span>Topic</span>
                    <span class="legend-item"><span class="swatch hot"></span>Busy topic</span>
                    <span class="hint">Scroll to zoom · drag to pan · click a node</span>
                </div>
            </div>`;
    }

    updated() {
        if (this._tab !== 0 || !this._svg) {
            return;
        }
        const stage = this._stage();
        if (stage && !stage.firstElementChild) {
            this._mountSvg(stage);
        }
    }

    _stage() {
        return this.shadowRoot.querySelector('.stage');
    }

    _viewport() {
        return this.shadowRoot.querySelector('.viewport');
    }

    async _renderDiagram() {
        if (!this._data || !this._data.diagram) {
            return;
        }
        const dark = themeState.theme?.name === 'dark';
        const style = getComputedStyle(this);
        const fontFamily = style.getPropertyValue('--lumo-font-family').trim() || 'sans-serif';
        const monoFamily = style.getPropertyValue('--lumo-font-family-monospace').trim() || 'monospace';
        mermaid.initialize({
            startOnLoad: false,
            securityLevel: 'strict',
            theme: dark ? 'dark' : 'default',
            fontFamily,
            // Part of the rendered SVG, so Mermaid measures labels with these fonts (no clipped labels)
            themeCSS: `
                .nodeLabel { font-weight: 500; }
                .edgeLabel, .edgeLabel p, .edgeLabel span { font-size: 12px; font-family: ${monoFamily}; }`,
            flowchart: {
                useMaxWidth: false,
                htmlLabels: true,
                curve: 'basis',
                nodeSpacing: 36,
                rankSpacing: 64,
                padding: 14
            }
        });
        try {
            const { svg } = await mermaid.render(`messaging-topology-${++renderCounter}`, this._data.diagram);
            this._svg = svg;
        } catch (e) {
            this._error = 'Could not render the diagram: ' + (e?.message || e);
            return;
        }
        const stage = this._stage();
        if (stage) {
            this._mountSvg(stage);
        }
    }

    _mountSvg(stage) {
        stage.innerHTML = this._svg;
        const svg = stage.querySelector('svg');
        if (svg) {
            const box = svg.viewBox.baseVal;
            svg.removeAttribute('style');
            svg.setAttribute('width', box.width);
            svg.setAttribute('height', box.height);
        }
        this._decorate();
        if (this._needsFit) {
            this._needsFit = false;
            // wait for layout so the viewport has its final size
            requestAnimationFrame(() => this._fit());
        } else {
            this._applyTransform();
        }
    }

    /**
     * Mermaid prefixes element ids with the render id, e.g. "messaging-topology-1-flowchart-svc_orders-0".
     * Generated node ids never contain '-', so everything up to the last '-' can be cut off.
     */
    _nodeIdOf(element) {
        const node = element?.closest?.('.node');
        if (!node || !node.id) {
            return null;
        }
        const match = node.id.match(/flowchart-([^-]+)-\d+$/);
        return match ? match[1] : null;
    }

    _edgeIdOf(id) {
        return id ? id.substring(id.lastIndexOf('-') + 1) : '';
    }

    /**
     * Highlights the selected node with its connections and dims everything else.
     */
    _decorate() {
        const stage = this._stage();
        if (!stage) {
            return;
        }
        const related = this._relatedGraph();
        stage.querySelectorAll('.node').forEach(node => {
            const id = this._nodeIdOf(node);
            node.classList.toggle('selected', related !== null && related.selected.has(id));
            node.classList.toggle('dimmed', related !== null && !related.nodes.has(id));
        });
        stage.querySelectorAll('.flowchart-link').forEach(path => {
            const id = this._edgeIdOf(path.id);
            const isRelated = related !== null && related.edgePrefixes.some(p => id.startsWith(p));
            path.classList.toggle('highlight', isRelated);
            path.classList.toggle('dimmed', related !== null && !isRelated);
        });
        stage.querySelectorAll('g.edgeLabel').forEach(label => {
            const id = this._edgeIdOf(label.querySelector('[data-id]')?.getAttribute('data-id'));
            const isRelated = related !== null && related.edgePrefixes.some(p => id.startsWith(p));
            label.classList.toggle('dimmed', related !== null && !isRelated);
        });
    }

    _relatedGraph() {
        const selection = this._selection;
        if (!selection || !this._data) {
            return null;
        }
        let edges;
        let selected;
        if (selection.kind === 'service') {
            const id = this._data.serviceIds.get(selection.name);
            selected = new Set([id]);
            edges = this._data.edges.filter(e => e.from === id || e.to === id);
        } else if (selection.kind === 'topic') {
            const id = this._data.topicIds.get(selection.name);
            selected = new Set([id]);
            edges = this._data.edges.filter(e => e.from === id || e.to === id);
        } else {
            edges = this._data.edges.filter(e => e.entry.key === selection.entry.key);
            selected = new Set(edges.flatMap(e => [e.from, e.to]));
        }
        const nodes = new Set([...selected, ...edges.flatMap(e => [e.from, e.to])]);
        const edgePrefixes = edges.map(e => `L_${e.from}_${e.to}_`);
        return { selected, nodes, edgePrefixes };
    }

    _applyTransform() {
        const stage = this._stage();
        if (stage) {
            stage.style.transform = `translate(${this._view.x}px, ${this._view.y}px) scale(${this._view.s})`;
        }
        this._scale = this._view.s;
    }

    _fit() {
        const viewport = this._viewport();
        const svg = this._stage()?.querySelector('svg');
        if (!viewport || !svg) {
            return;
        }
        const width = svg.viewBox.baseVal.width;
        const height = svg.viewBox.baseVal.height;
        const available = viewport.getBoundingClientRect();
        if (!width || !height || !available.width || !available.height) {
            return;
        }
        const s = Math.min(1.5, Math.max(MIN_SCALE, Math.min(available.width / width, available.height / height) * 0.9));
        this._view = {
            s,
            x: (available.width - width * s) / 2,
            y: (available.height - height * s) / 2
        };
        this._applyTransform();
    }

    _zoomBy(factor, clientX, clientY) {
        const viewport = this._viewport();
        if (!viewport) {
            return;
        }
        const rect = viewport.getBoundingClientRect();
        const cx = clientX !== undefined ? clientX - rect.left : rect.width / 2;
        const cy = clientY !== undefined ? clientY - rect.top : rect.height / 2;
        const s = Math.min(MAX_SCALE, Math.max(MIN_SCALE, this._view.s * factor));
        const ratio = s / this._view.s;
        this._view = {
            s,
            x: cx - (cx - this._view.x) * ratio,
            y: cy - (cy - this._view.y) * ratio
        };
        this._applyTransform();
    }

    _onWheel(e) {
        e.preventDefault();
        this._zoomBy(Math.exp(-e.deltaY * 0.0015), e.clientX, e.clientY);
    }

    _onPointerDown(e) {
        if (e.button !== 0) {
            return;
        }
        this._drag = {
            startX: e.clientX,
            startY: e.clientY,
            viewX: this._view.x,
            viewY: this._view.y,
            target: e.composedPath()[0],
            moved: false
        };
        e.currentTarget.setPointerCapture(e.pointerId);
    }

    _onPointerMove(e) {
        if (!this._drag) {
            return;
        }
        const dx = e.clientX - this._drag.startX;
        const dy = e.clientY - this._drag.startY;
        if (!this._drag.moved && Math.hypot(dx, dy) < CLICK_TOLERANCE) {
            return;
        }
        this._drag.moved = true;
        e.currentTarget.classList.add('panning');
        this._view = { ...this._view, x: this._drag.viewX + dx, y: this._drag.viewY + dy };
        const stage = this._stage();
        if (stage) {
            stage.style.transform = `translate(${this._view.x}px, ${this._view.y}px) scale(${this._view.s})`;
        }
    }

    _onPointerUp(e) {
        if (!this._drag) {
            return;
        }
        const drag = this._drag;
        this._drag = null;
        e.currentTarget.classList.remove('panning');
        if (drag.moved || e.type === 'pointercancel') {
            return;
        }
        const nodeId = this._nodeIdOf(drag.target);
        const node = nodeId ? this._data.nodes[nodeId] : null;
        this._select(node ? { kind: node.kind, name: node.name } : null);
    }

    _copyMermaid() {
        navigator.clipboard.writeText(this._data.diagram)
            .then(() => notifier.showInfoMessage('Mermaid source copied to clipboard'));
    }

    // ---------------------------------------------------------------------------------------------------- list

    _renderChannelList() {
        const filter = this._filter.trim().toLowerCase();
        const items = this._data.entries.filter(e => !filter || [
            e.service, e.topic, e.channel.channelName, e.channel.connector, e.channel.messageType,
            e.channel.className, e.channel.methodName
        ].some(v => v && v.toLowerCase().includes(filter)));

        return html`
            <div class="list">
                <vaadin-text-field placeholder="Filter by service, topic, channel or type" clear-button-visible
                                   .value=${this._filter}
                                   @value-changed=${e => this._filter = e.detail.value}>
                    <vaadin-icon slot="prefix" icon="font-awesome-solid:magnifying-glass"></vaadin-icon>
                </vaadin-text-field>
                <vaadin-grid .items=${items} theme="no-border row-stripes"
                             @active-item-changed=${this._onRowActivated}>
                    <vaadin-grid-column header="Direction" auto-width flex-grow="0"
                                        ${columnBodyRenderer(e => this._directionBadge(e.channel.direction), [])}>
                    </vaadin-grid-column>
                    <vaadin-grid-column header="Topic" auto-width
                                        ${columnBodyRenderer(e => html`<strong>${e.topic}</strong>${e.topic !== e.channel.channelName
                                                ? html`<div class="muted mono">channel ${e.channel.channelName}</div>` : nothing}`, [])}>
                    </vaadin-grid-column>
                    <vaadin-grid-column header="Service" auto-width
                                        ${columnBodyRenderer(e => html`${e.service} ${e.local ? html`<span class="badge local">this</span>` : nothing}`, [])}>
                    </vaadin-grid-column>
                    <vaadin-grid-column header="Implementation" auto-width
                                        ${columnBodyRenderer(e => html`<span class="mono">${this._implementation(e.channel)}</span>`, [])}>
                    </vaadin-grid-column>
                    <vaadin-grid-column header="Message type" auto-width
                                        ${columnBodyRenderer(e => html`<span class="mono">${this._simpleName(e.channel.messageType) || '–'}</span>`, [])}>
                    </vaadin-grid-column>
                    <vaadin-grid-column header="Connector" auto-width
                                        ${columnBodyRenderer(e => e.channel.connector ? html`<span class="badge">${e.channel.connector}</span>` : html`<span class="muted">in-app</span>`, [])}>
                    </vaadin-grid-column>
                </vaadin-grid>
            </div>`;
    }

    _onRowActivated(e) {
        const entry = e.detail.value;
        if (entry) {
            this._select({ kind: 'channel', entry });
        }
    }

    // ---------------------------------------------------------------------------------------------------- details

    _select(selection) {
        this._selection = selection;
        if (selection?.kind === 'channel') {
            const example = selection.entry.channel.examplePayload;
            this._payload = example !== undefined && example !== null ? JSON.stringify(example, null, 2) : '{}';
        }
        this.updateComplete.then(() => this._decorate());
    }

    _renderDetails() {
        switch (this._selection.kind) {
            case 'service':
                return this._renderServiceDetails(this._selection.name);
            case 'topic':
                return this._renderTopicDetails(this._selection.name);
            default:
                return this._renderChannelDetails(this._selection.entry);
        }
    }

    _detailsHeader(icon, iconClass, title, subtitle) {
        return html`
            <div class="details-header">
                <div class="details-icon ${iconClass}"><vaadin-icon icon="${icon}"></vaadin-icon></div>
                <div class="details-title">
                    <div class="title">${title}</div>
                    <div class="subtitle">${subtitle}</div>
                </div>
                <vaadin-button theme="tertiary icon" title="Close" @click=${() => this._select(null)}>
                    <vaadin-icon icon="font-awesome-solid:xmark"></vaadin-icon>
                </vaadin-button>
            </div>`;
    }

    _renderServiceDetails(name) {
        const service = this._data.services.find(s => s.serviceName === name);
        const entries = this._data.entries.filter(e => e.service === name);
        const local = name === this._data.localService;
        const produces = entries.filter(e => e.channel.direction === 'outgoing');
        const consumes = entries.filter(e => e.channel.direction === 'incoming');

        return html`
            <aside class="details">
                ${this._detailsHeader('font-awesome-solid:server', '', name, html`
                    ${service?.version ? html`<span>v${service.version}</span>` : nothing}
                    ${local ? html`<span class="badge local">this service</span>` : nothing}`)}
                <section>
                    <div class="props">
                        ${service?.groupId ? html`<span class="key">Group</span><span class="value mono">${service.groupId}</span>` : nothing}
                        ${service?.projectName ? html`<span class="key">Project</span><span class="value">${service.projectName}</span>` : nothing}
                        ${!local && service?.serviceUrl ? html`<span class="key">URL</span>
                            <a class="value link" href="${service.serviceUrl}/q/dev-ui" target="_blank">${service.serviceUrl}</a>` : nothing}
                    </div>
                </section>
                <section>
                    <h4>Produces <span>${produces.length}</span></h4>
                    ${this._renderEntries(produces, e => e.topic)}
                </section>
                <section>
                    <h4>Consumes <span>${consumes.length}</span></h4>
                    ${this._renderEntries(consumes, e => e.topic)}
                </section>
            </aside>`;
    }

    _renderTopicDetails(name) {
        const topic = this._data.topics.get(name);
        const all = [...topic.producers, ...topic.consumers];
        const connectors = [...new Set(all.map(e => e.channel.connector).filter(Boolean))];
        const types = [...new Set(all.map(e => e.channel.messageType).filter(Boolean))];

        return html`
            <aside class="details">
                ${this._detailsHeader('font-awesome-solid:database', 'topic', name, html`
                    <span>${topic.producers.length} producer(s) · ${topic.consumers.length} consumer(s)</span>`)}
                <section>
                    <div class="props">
                        <span class="key">Connector</span>
                        <span class="value">${connectors.length ? connectors.map(c => html`<span class="badge">${c}</span> `) : html`<span class="muted">in-app</span>`}</span>
                        <span class="key">Message type</span>
                        <span class="value mono">${types.length ? types.map(t => html`<div title="${t}">${this._simpleName(t)}</div>`) : '–'}</span>
                    </div>
                </section>
                <section>
                    <h4>Producers <span>${topic.producers.length}</span></h4>
                    ${this._renderEntries(topic.producers, e => e.service)}
                </section>
                <section>
                    <h4>Consumers <span>${topic.consumers.length}</span></h4>
                    ${this._renderEntries(topic.consumers, e => e.service)}
                </section>
            </aside>`;
    }

    _renderEntries(entries, title) {
        if (entries.length === 0) {
            return html`<div class="empty">None</div>`;
        }
        return html`
            <div class="entries">
                ${entries.map(e => html`
                    <div class="entry" @click=${() => this._select({ kind: 'channel', entry: e })}>
                        ${this._directionBadge(e.channel.direction)}
                        <div class="entry-main">
                            <div>${title(e)}</div>
                            <div class="entry-sub mono">${this._implementation(e.channel)}</div>
                        </div>
                        <vaadin-icon icon="font-awesome-solid:chevron-right"></vaadin-icon>
                    </div>`)}
            </div>`;
    }

    _renderChannelDetails(entry) {
        const channel = entry.channel;
        const outgoing = channel.direction === 'outgoing';

        return html`
            <aside class="details">
                ${this._detailsHeader(outgoing ? 'font-awesome-solid:arrow-right-from-bracket' : 'font-awesome-solid:arrow-right-to-bracket',
                    outgoing ? '' : 'topic', channel.channelName, html`
                    ${this._directionBadge(channel.direction)}
                    <span>${outgoing ? `${entry.service} → ${entry.topic}` : `${entry.topic} → ${entry.service}`}</span>`)}
                <section>
                    <div class="props">
                        <span class="key">Service</span>
                        <span class="value link" @click=${() => this._select({ kind: 'service', name: entry.service })}>${entry.service}</span>
                        <span class="key">Topic</span>
                        <span class="value link" @click=${() => this._select({ kind: 'topic', name: entry.topic })}>${entry.topic}</span>
                        <span class="key">Connector</span>
                        <span class="value">${channel.connector ? html`<span class="badge">${channel.connector}</span>` : html`<span class="muted">in-app</span>`}</span>
                        <span class="key">Implementation</span>
                        <span class="value mono" title="${channel.className}">${this._implementation(channel)}</span>
                        <span class="key">Message type</span>
                        <span class="value mono" title="${channel.messageType || ''}">${this._simpleName(channel.messageType) || '–'}</span>
                    </div>
                </section>
                ${this._renderSchema(channel)}
                ${this._renderSend(entry)}
            </aside>`;
    }

    _renderSchema(channel) {
        const rows = this._schemaRows(channel.schema);
        return html`
            <section>
                <h4>Schema
                    ${channel.schema && Object.keys(channel.schema).length ? html`
                        <vaadin-button theme="tertiary small" @click=${() => this._copy(JSON.stringify(channel.schema, null, 2), 'Schema copied to clipboard')}>
                            <vaadin-icon icon="font-awesome-solid:copy" slot="prefix"></vaadin-icon>Copy
                        </vaadin-button>` : nothing}
                </h4>
                ${rows.length ? html`
                    <table class="schema">
                        ${rows.map(r => html`
                            <tr>
                                <td class="mono" style="padding-left: ${r.depth * 14}px">${r.name}</td>
                                <td class="mono">${r.type}</td>
                            </tr>`)}
                    </table>` : html`<div class="empty">No schema available</div>`}
            </section>`;
    }

    _schemaRows(schema, depth = 0, rows = []) {
        if (!schema || !schema.properties) {
            if (depth === 0 && schema && schema.type && schema.type !== 'object') {
                rows.push({ name: '(value)', type: this._typeLabel(schema), depth });
            }
            return rows;
        }
        for (const [name, property] of Object.entries(schema.properties)) {
            rows.push({ name, type: this._typeLabel(property), depth });
            if (depth < 2) {
                this._schemaRows(property.items?.properties ? property.items : property, depth + 1, rows);
            }
        }
        return rows;
    }

    _typeLabel(property) {
        if (property.enum) {
            return property.enum.join(' | ');
        }
        return property.javaType || property.type;
    }

    _renderSend(entry) {
        const channel = entry.channel;
        if (channel.direction !== 'outgoing') {
            return nothing;
        }
        if (!entry.local || !channel.sendable) {
            return html`
                <section>
                    <h4>Test message</h4>
                    <div class="empty">
                        ${entry.local
                            ? html`Test messages can only be sent through an injected <code>Emitter</code> or <code>MutinyEmitter</code>.`
                            : html`Open the Dev UI of ${entry.service} to send test messages to this channel.`}
                    </div>
                </section>`;
        }
        return html`
            <section>
                <h4>Send test message</h4>
                <div class="editor">
                    <qui-themed-code-block mode="json" editable
                                           .content=${this._payload}
                                           .value=${this._payload}
                                           @value-changed=${e => this._payload = e.detail.value}>
                    </qui-themed-code-block>
                </div>
                <div class="send-actions">
                    <vaadin-button theme="primary" ?disabled=${this._sending} @click=${() => this._send(channel)}>
                        <vaadin-icon icon="font-awesome-solid:paper-plane" slot="prefix"></vaadin-icon>Send
                    </vaadin-button>
                    <vaadin-button theme="tertiary" @click=${() => this._select({ kind: 'channel', entry })}>
                        Reset example
                    </vaadin-button>
                </div>
            </section>`;
    }

    _send(channel) {
        this._sending = true;
        this.jsonRpc.sendMessage({ channel: channel.channelName, payload: this._payload })
            .then(response => {
                const result = response.result;
                if (result.success) {
                    notifier.showSuccessMessage(result.message);
                } else {
                    notifier.showErrorMessage(result.message);
                }
            })
            .catch(error => notifier.showErrorMessage(error?.error?.message || 'Sending failed'))
            .finally(() => this._sending = false);
    }

    // ---------------------------------------------------------------------------------------------------- helpers

    _directionBadge(direction) {
        return direction === 'outgoing'
            ? html`<span class="badge outgoing"><vaadin-icon icon="font-awesome-solid:arrow-up"></vaadin-icon>produces</span>`
            : html`<span class="badge incoming"><vaadin-icon icon="font-awesome-solid:arrow-down"></vaadin-icon>consumes</span>`;
    }

    _implementation(channel) {
        const className = this._simpleName(channel.className);
        return channel.injected ? `${className}.${channel.methodName}` : `${className}.${channel.methodName}()`;
    }

    _simpleName(className) {
        if (!className) {
            return '';
        }
        return className.substring(className.lastIndexOf('.') + 1).replace(/\$/g, '.');
    }

    _copy(text, message) {
        navigator.clipboard.writeText(text).then(() => notifier.showInfoMessage(message));
    }
}

customElements.define('qwc-messaging-topology', QwcMessagingTopology);
