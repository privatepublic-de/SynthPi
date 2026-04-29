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
import { initWaveform } from "./waveform.js";

const OSC_MODE_NAMES = ["va", "add", "exc", "blep"];

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
	// /osc/mode is a 4-value selector — 0=VA, 1=Additive, 2=Exciter, 3=BLEP.
	// Toggle a body attribute that CSS uses to show the BLEP subpanel only
	// in BLEP mode.
	socket.onParam("/osc/mode", (v) => {
		const mode = Math.round(v * 3);
		document.body.dataset.oscMode = OSC_MODE_NAMES[mode] || "va";
	});

	// /fx/delay/type — 0=tape, >0=digital. Right-channel rate knob is only
	// useful for the digital delay (tape uses a single ramp).
	socket.onParam("/fx/delay/type", (v) => {
		document.body.dataset.delayType = v > 0 ? "digital" : "tape";
	});
}
