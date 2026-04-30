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
		this.commandSubs = new Map();  // path -> single callback (exact match)
	}

	connect() {
		const url = `ws://${location.host}/oscsocket`;
		this.ws = new WebSocket(url);
		this.ws.addEventListener("open", () => {
			this.connected = true;
			document.body.classList.remove("connecting", "restarting");
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
	 * Subscribe to inbound non-numeric messages by exact path. Used for
	 * command-style responses like /patchlist, /saveinfo, /paramselected,
	 * /label/<param-path>, /waveform/oscN, /pagepatch, /patch/name. The
	 * callback receives (path, valueString).
	 *
	 * Note: this is exact-match only. Earlier the dispatcher did a
	 * startsWith / first-match-wins prefix search, which silently routed
	 * /label/osc/1/waveset into the /label/osc/1/wave subscriber (and
	 * the equivalent /mod/1/depth/pitch/2 vs /mod/1/depth/pitch in the
	 * matrix). Every existing subscriber wants exact match, so the
	 * "prefix" semantics was over-engineered and broken.
	 */
	onCommand(path, cb) {
		this.commandSubs.set(path, cb);
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

			// Command-style exact-path subscribers win over numeric-parameter
			// subscribers, so e.g. /label/osc/1/wave doesn't fall through
			// to the /osc/1/wave rotary's parseFloat path.
			const cmdCb = this.commandSubs.get(path);
			if (cmdCb) {
				cmdCb(path, value);
				continue;
			}

			const subs = this.paramSubs.get(path);
			if (subs) {
				const num = parseFloat(value);
				for (let j = 0; j < subs.length; j++) subs[j](num);
			}
		}
	}
}

export const socket = new Socket();
