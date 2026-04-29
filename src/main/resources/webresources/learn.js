// MIDI learn mode.
//
// Click the "Learn" toggle in the status bar to enter learn mode. While
// active, hovering any control with a data-osc attribute sends
// /command/learn/start=<path> to the server, which arms it to map the
// next-incoming MIDI CC to that parameter. Hovering off any target sends
// /command/learn/stop=1.
//
// After the user wiggles a CC, the server pushes /paramselected=<path>
// to confirm; we briefly highlight the matching control with .paramselected
// for 8 seconds (matches the old UI's timing).
//
// Click Learn again to exit; sends /command/learn/stop=1 and clears
// state.

import { socket } from "./socket.js";

const PARAM_FLASH_MS = 8000;

class Learn {
	constructor() {
		this.btn = document.getElementById("btn-learn");
		this.active = false;
		this.currentTarget = null;

		if (this.btn) this.btn.addEventListener("click", () => {
			if (this.active) this._stop();
			else this._start();
		});

		// Server confirms a CC was learned by emitting the parameter path.
		socket.onCommand("/paramselected", (path, value) => {
			this._flash(value);
		});

		// Bind once so add/removeEventListener get the same reference.
		this._onMouseOver = this._onMouseOver.bind(this);
	}

	_start() {
		this.active = true;
		document.body.classList.add("learnmode");
		if (this.btn) this.btn.classList.add("active");
		document.addEventListener("mouseover", this._onMouseOver);
	}

	_stop() {
		this.active = false;
		document.body.classList.remove("learnmode");
		if (this.btn) this.btn.classList.remove("active");
		document.removeEventListener("mouseover", this._onMouseOver);
		socket.send("/command/learn/stop", 1);
		this.currentTarget = null;
	}

	_onMouseOver(ev) {
		// closest() walks ancestors; finds the nearest data-osc whether
		// the user hovered the canvas, the label, or the value display.
		const el = ev.target.closest("[data-osc]");
		const path = el ? el.dataset.osc : null;
		if (path === this.currentTarget) return;
		this.currentTarget = path;
		if (path) {
			// /command/learn/start carries the path as its value, not a number.
			socket.send("/command/learn/start", path);
		} else {
			// Hovered out of any target — pause learning until they hover something.
			socket.send("/command/learn/stop", 1);
		}
	}

	_flash(path) {
		const el = document.querySelector(`[data-osc="${cssEscape(path)}"]`);
		if (!el) return;
		el.classList.add("paramselected");
		setTimeout(() => el.classList.remove("paramselected"), PARAM_FLASH_MS);
	}
}

// CSS.escape isn't universal yet on every embedded browser; fall back to
// the WHATWG algorithm enough for our paths (which only contain /, letters,
// digits, and digits).
function cssEscape(s) {
	if (typeof CSS !== "undefined" && CSS.escape) return CSS.escape(s);
	return String(s).replace(/(["\\])/g, "\\$1");
}

export function initLearn() {
	return new Learn();
}
