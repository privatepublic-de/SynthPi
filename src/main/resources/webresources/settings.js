// Settings modal: MIDI channel, polyphony, pitch bend range + fix, audio
// buffer size, output limiter. Triggered by the gear button in the status
// bar; uses /command/settings/load + /settings response, and saves via
// /command/settings/save with the full JSON object.
//
// The "transfer performance data" field still lives on the server but no
// longer has UI per the rewrite scope decision. We preserve its current
// value on save (object spread of _lastSettings) so it doesn't get
// silently flipped off.

import { socket } from "./socket.js";

class Settings {
	constructor() {
		this.modal = document.getElementById("settingswindow");
		this.dimmer = document.getElementById("dimmer");
		this._lastSettings = null;

		const btn = document.getElementById("btn-settings");
		if (btn) btn.addEventListener("click", () => {
			socket.send("/command/settings/load", 1);
		});

		socket.onCommand("/settings", (path, value) => {
			try {
				this._render(JSON.parse(value));
			} catch (e) {
				console.error("malformed /settings payload", e);
			}
		});
	}

	_render(s) {
		this._lastSettings = s;
		const channelOptions = [
			`<option value="0" ${s.midichannel === 0 ? "selected" : ""}>All channels</option>`,
			...range(1, 17).map((n) => `<option value="${n}" ${s.midichannel === n ? "selected" : ""}>${n}</option>`),
		].join("");
		const polyOptions = range(2, 9).map((n) =>
			`<option value="${n}" ${s.polyphonyvoices === n ? "selected" : ""}>${n} voices</option>`).join("");
		const bendOptions = range(1, 13).map((n) =>
			`<option value="${n * 100}" ${s.pitchbendrange === n * 100 ? "selected" : ""}>${n} semitone${n > 1 ? "s" : ""}</option>`).join("");
		const bufferOptions = [32, 64, 96, 128, 256, 512].map((n) =>
			`<option value="${n}" ${s.audiobuffersize === n ? "selected" : ""}>${n} samples</option>`).join("");

		const content = this.modal.querySelector(".settings-content");
		content.innerHTML = `
			<div class="settings-form">
				<label>MIDI channel <select name="midichannel">${channelOptions}</select></label>
				<label>Polyphony <select name="polyphonyvoices">${polyOptions}</select></label>
				<label>Pitch bend range <select name="pitchbendrange">${bendOptions}</select></label>
				<label class="settings-checkbox"><input type="checkbox" name="pitchbendfix" ${s.pitchbendfix ? "checked" : ""}> Fix strange pitch-bend behaviour (macOS)</label>
				<label>Audio buffer size <select name="audiobuffersize">${bufferOptions}</select> <small>(needs restart)</small></label>
				<label class="settings-checkbox"><input type="checkbox" name="limiterenabled" ${s.limiterenabled ? "checked" : ""}> Output limiter active</label>
				<button class="settings-save" type="button">Save settings</button>
			</div>
		`;
		content.querySelector(".settings-save").addEventListener("click", () => this._save());

		this.dimmer.classList.add("shown");
		this.modal.classList.add("shown");
	}

	_save() {
		const form = this.modal.querySelector(".settings-form");
		const get = (name) => form.querySelector(`[name="${name}"]`);
		// Preserve any fields the form doesn't touch (e.g. transferperformancedata)
		// so saving the modal doesn't silently change unseen settings.
		const data = {
			...this._lastSettings,
			midichannel: parseInt(get("midichannel").value),
			polyphonyvoices: parseInt(get("polyphonyvoices").value),
			pitchbendrange: parseInt(get("pitchbendrange").value),
			pitchbendfix: get("pitchbendfix").checked,
			audiobuffersize: parseInt(get("audiobuffersize").value),
			limiterenabled: get("limiterenabled").checked,
		};
		socket.send("/command/settings/save", JSON.stringify(data));
		this._close();
	}

	_close() {
		this.dimmer.classList.remove("shown");
		this.modal.classList.remove("shown");
	}
}

function range(start, endExclusive) {
	const out = [];
	for (let i = start; i < endExclusive; i++) out.push(i);
	return out;
}

export function initSettings() {
	return new Settings();
}
