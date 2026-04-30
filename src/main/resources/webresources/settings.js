// Settings modal: MIDI port selection, polyphony, pitch bend range + fix, audio
// buffer size, audio device, output limiter. Triggered by the gear button in the
// status bar; uses /command/settings/load + /settings response, saves via
// /command/settings/save with the full JSON object.
//
// The "transfer performance data" field still lives on the server but no longer
// has UI. We preserve its current value on save (object spread of _lastSettings)
// so it doesn't get silently flipped off.

import { socket } from "./socket.js";
import * as modal from "./modal.js";

class Settings {
	constructor() {
		this.modalEl = document.getElementById("settingswindow");
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

		socket.onCommand("/restarting", (path, value) => {
			modal.closeAll();
			document.body.classList.add("restarting");
		});
	}

	_render(s) {
		this._lastSettings = s;

		const portsHtml = (s.midiports || []).map((p) => {
			const unavailable = !p.available ? " port-unavailable" : "";
			const channelOptions = [
				`<option value="0" ${p.channel === 0 ? "selected" : ""}>All channels</option>`,
				...range(1, 17).map((n) =>
					`<option value="${n}" ${p.channel === n ? "selected" : ""}>${n}</option>`),
			].join("");
			return `
				<div class="midi-port-row${unavailable}">
					<label class="settings-checkbox">
						<input type="checkbox" name="midiport" data-key="${escHtml(p.key)}" ${p.enabled ? "checked" : ""}>
						${escHtml(p.name)}${p.vendor ? ` <small>(${escHtml(p.vendor)})</small>` : ""}
						${unavailable ? ` <small class="port-unavail-note">(not connected)</small>` : ""}
					</label>
					<select name="midichan" data-key="${escHtml(p.key)}">${channelOptions}</select>
				</div>`;
		}).join("");

		const portsSection = s.midiports && s.midiports.length > 0
			? `<div class="settings-section-label">MIDI ports</div>
			   <div class="midi-port-list">${portsHtml}</div>`
			: `<div class="settings-section-label">MIDI ports</div>
			   <div class="midi-port-list-empty">No MIDI input devices detected.</div>`;

		const polyOptions = range(2, 9).map((n) =>
			`<option value="${n}" ${s.polyphonyvoices === n ? "selected" : ""}>${n} voices</option>`).join("");
		const bendOptions = range(1, 13).map((n) =>
			`<option value="${n * 100}" ${s.pitchbendrange === n * 100 ? "selected" : ""}>${n} semitone${n > 1 ? "s" : ""}</option>`).join("");
		const bufferOptions = [32, 64, 96, 128, 256, 512].map((n) =>
			`<option value="${n}" ${s.audiobuffersize === n ? "selected" : ""}>${n} samples</option>`).join("");

		const devices = s.audiodevices || [];
		const deviceOptions = [
			`<option value="" ${!s.audiodevicename ? "selected" : ""}>System default</option>`,
			...devices.map((d) =>
				`<option value="${escHtml(d)}" ${s.audiodevicename === d ? "selected" : ""}>${escHtml(d)}</option>`),
		].join("");

		const content = this.modalEl.querySelector(".settings-content");
		content.innerHTML = `
			<div class="settings-form">
				${portsSection}
				<div class="settings-section-label">Synth</div>
				<label>Polyphony <select name="polyphonyvoices">${polyOptions}</select></label>
				<label>Pitch bend range <select name="pitchbendrange">${bendOptions}</select></label>
				<label class="settings-checkbox"><input type="checkbox" name="pitchbendfix" ${s.pitchbendfix ? "checked" : ""}> Fix strange pitch-bend behaviour (macOS)</label>
				<label>Audio buffer size <select name="audiobuffersize">${bufferOptions}</select> <small>(needs restart)</small></label>
				<label>Audio output device <select name="audiodevicename">${deviceOptions}</select> <small>(needs restart)</small></label>
				<label class="settings-checkbox"><input type="checkbox" name="limiterenabled" ${s.limiterenabled ? "checked" : ""}> Output limiter active</label>
				<button class="settings-save" type="button">Save settings</button>
			</div>
		`;
		content.querySelector(".settings-save").addEventListener("click", () => this._save());

		modal.open(this.modalEl);
	}

	_save() {
		const form = this.modalEl.querySelector(".settings-form");
		const get = (name) => form.querySelector(`[name="${name}"]`);

		const midiports = Array.from(form.querySelectorAll('[name="midiport"]')).map((cb) => {
			const chanEl = form.querySelector(`[name="midichan"][data-key="${CSS.escape(cb.dataset.key)}"]`);
			return {
				key: cb.dataset.key,
				enabled: cb.checked,
				channel: chanEl ? parseInt(chanEl.value) : 0,
			};
		});

		const newDevice = get("audiodevicename").value;
		const data = {
			...this._lastSettings,
			midiports,
			polyphonyvoices: parseInt(get("polyphonyvoices").value),
			pitchbendrange: parseInt(get("pitchbendrange").value),
			pitchbendfix: get("pitchbendfix").checked,
			audiobuffersize: parseInt(get("audiobuffersize").value),
			audiodevicename: newDevice,
			limiterenabled: get("limiterenabled").checked,
		};
		socket.send("/command/settings/save", JSON.stringify(data));
		modal.closeAll();
	}
}

function range(start, endExclusive) {
	const out = [];
	for (let i = start; i < endExclusive; i++) out.push(i);
	return out;
}

function escHtml(str) {
	return String(str)
		.replace(/&/g, "&amp;")
		.replace(/"/g, "&quot;")
		.replace(/</g, "&lt;")
		.replace(/>/g, "&gt;");
}

export function initSettings() {
	return new Settings();
}
