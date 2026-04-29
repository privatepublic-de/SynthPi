// WebSocket layer for SynthPi.
// Single shared Socket singleton; controls register interest in specific paths
// or path-prefix groups; outbound user changes go through send() / sendCommand().

const RECONNECT_MS = 2000;

class Socket {
	constructor() {
		this.ws = null;
		this.connected = false;
		this.outbox = [];
		this.paramSubs = new Map();    // path -> array of callbacks
		this.commandSubs = new Map();  // prefix -> single callback
	}

	connect() {
		const url = `ws://${location.host}/oscsocket`;
		this.ws = new WebSocket(url);
		this.ws.addEventListener("open", () => {
			this.connected = true;
			document.body.classList.remove("connecting");
			document.body.classList.add("connected");
			while (this.outbox.length) this.ws.send(this.outbox.shift());
		});
		this.ws.addEventListener("close", () => {
			this.connected = false;
			document.body.classList.remove("connected");
			document.body.classList.add("connecting");
			setTimeout(() => this.connect(), RECONNECT_MS);
		});
		this.ws.addEventListener("message", (ev) => this._dispatch(ev.data));
	}

	/** Send a parameter update or any other path=value message. */
	send(path, value) {
		const msg = `${path}=${value}`;
		if (this.connected) this.ws.send(msg);
		else this.outbox.push(msg);
		// Locally fan out to onParam listeners as if the server had echoed
		// the value back. The server only echoes the label to the
		// originating session (to avoid knob-fight on rotaries), so any
		// state-derived UI — mode-conditional subpanels, body attributes —
		// would otherwise stay stale until the next reload. Listeners that
		// also drive the originating control are idempotent (_setValue
		// against an already-current value is a no-op).
		const subs = this.paramSubs.get(path);
		if (subs) {
			const num = parseFloat(value);
			if (!Number.isNaN(num)) {
				for (let j = 0; j < subs.length; j++) subs[j](num);
			}
		}
	}

	/**
	 * Subscribe to inbound parameter updates for a specific path. Multiple
	 * subscribers per path are allowed (e.g. a knob and a labelled value
	 * display both watching /amp/volume).
	 */
	onParam(path, cb) {
		let subs = this.paramSubs.get(path);
		if (!subs) {
			subs = [];
			this.paramSubs.set(path, subs);
		}
		subs.push(cb);
		return () => {
			const arr = this.paramSubs.get(path);
			if (arr) {
				const i = arr.indexOf(cb);
				if (i >= 0) arr.splice(i, 1);
			}
		};
	}

	/**
	 * Subscribe to inbound messages whose path starts with {@code prefix}.
	 * Used for command-style responses like /patchlist=…, /saveinfo=…,
	 * /paramselected=…, /label/…, /play/note/…, /waveform/…
	 * The callback receives (path, valueString).
	 */
	onCommand(prefix, cb) {
		this.commandSubs.set(prefix, cb);
	}

	_dispatch(text) {
		// Server frames may contain multiple newline-separated messages.
		const lines = text.split("\n");
		for (let i = 0; i < lines.length; i++) {
			const msg = lines[i];
			if (!msg) continue;
			const eq = msg.indexOf("=");
			if (eq < 0) continue;
			const path = msg.slice(0, eq);
			const value = msg.slice(eq + 1);

			// Command-style prefixes win over exact-path parameter subscribers,
			// because /label/<path> shouldn't fall through to the bare-path
			// rotary subscriber.
			let handled = false;
			for (const [prefix, cb] of this.commandSubs) {
				if (path.startsWith(prefix)) {
					cb(path, value);
					handled = true;
					break;
				}
			}
			if (handled) continue;

			const subs = this.paramSubs.get(path);
			if (subs) {
				const num = parseFloat(value);
				for (let j = 0; j < subs.length; j++) subs[j](num);
			}
		}
	}
}

export const socket = new Socket();
