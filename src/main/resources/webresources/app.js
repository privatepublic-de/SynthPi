// Entry point. Connect the WebSocket, instantiate any controls present in the
// page, and wire mode-conditional UI bits (BLEP subpanel, digital-delay right-
// rate knob).
//
// Subsequent phases plug additional features in by importing more modules
// here (patches.js, settings.js, learn.js, matrix.js, keyboard.js, etc.).

import { socket } from "./socket.js";
import { Rotary, Fader, Toggle, Select } from "./controls.js";
import { initPatches } from "./patches.js";
import { initSettings } from "./settings.js";
import { initLearn } from "./learn.js";
import { initMatrix } from "./matrix.js";
import { initKeyboard } from "./keyboard.js";
import { initWaveform, clearWaveform } from "./waveform.js";

// Mode index → CSS body attribute value. Order matches the engine enum:
// BLEP = 0 (the polyBLEP "VA" oscillator), WAVETABLE = 1, ADDITIVE = 2,
// EXITER = 3. The "blep" string here is what triggers the BLEP subpanel
// reveal via body[data-osc-mode="blep"] .blep-only.
const OSC_MODE_NAMES = ["blep", "wt", "add", "exc"];

document.addEventListener("DOMContentLoaded", () => {
	// Connect first so onParam handlers below are registered before the
	// server's 1-second initial state dump arrives.
	socket.connect();

	document.querySelectorAll(".rotary").forEach((el) => new Rotary(el));
	document.querySelectorAll(".fader").forEach((el) => new Fader(el));
	document.querySelectorAll(".toggle").forEach((el) => new Toggle(el));
	document.querySelectorAll(".select").forEach((el) => new Select(el));

	initPatches();
	initSettings();
	initLearn();
	// Matrix builds its own controls dynamically; runs after the static
	// querySelectorAll loops above so the matrix's rotaries instantiate
	// fresh (matrix.js calls `new Rotary(...)` directly).
	initMatrix();
	initKeyboard();
	initWaveform();
	wireConditionalSubpanels();
});

/**
 * Show/hide UI bits that depend on the current value of a mode parameter.
 * Implemented declaratively via body data attributes + CSS — pure JS would
 * also work but the data-attribute approach scales as more conditional
 * subpanels arrive in later phases.
 */
function wireConditionalSubpanels() {
	// /osc/mode is a 4-value selector — 0=BLEP, 1=WAVETABLE, 2=ADDITIVE,
	// 3=EXITER (matches OSC_MODE_NAMES + the v3 engine enum). Toggle a body
	// attribute that CSS uses to show the BLEP / WT subpanels, and swap the
	// per-mode label on any element carrying data-label-<mode> (the OSC1/2
	// wave knobs read differently in each engine — e.g. Exciter uses them
	// as Excitation / Damping, not Wave / Wave).
	socket.onParam("/osc/mode", (v) => {
		const mode = Math.round(v * 3);
		const key = OSC_MODE_NAMES[mode] || "blep";
		document.body.dataset.oscMode = key;
		const dataKey = "label" + key.charAt(0).toUpperCase() + key.slice(1);
		document.querySelectorAll(`[data-${"label-" + key}]`).forEach((el) => {
			const labelEl = el.querySelector("label");
			if (labelEl) labelEl.textContent = el.dataset[dataKey];
		});
		// VA + Exciter don't push /waveform/oscN — drop the previous mode's
		// backdrop so it doesn't sit stale behind the wave knobs.
		if (key === "blep" || key === "exc") clearWaveform();
	});

	// /fx/delay/type — 0=tape, >0=digital. Right-channel rate knob is only
	// useful for the digital delay (tape uses a single ramp).
	socket.onParam("/fx/delay/type", (v) => {
		document.body.dataset.delayType = v > 0 ? "digital" : "tape";
	});
}
