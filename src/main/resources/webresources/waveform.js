// OSC1 / OSC2 waveform display.
//
// The engine pushes /waveform/oscN=v1,v2,…  every time the oscillator's
// wave parameter changes (or on initial state dump). Each value is an
// approximation of the waveform's bar amplitude — we render the bars to
// a small canvas and set that as the OSC1/OSC2 wave knob's background
// image. The Rotary's transparent canvas sits on top of it, so the user
// sees the waveform behind the rotary's arc / indicator.

import { socket } from "./socket.js";

const W = 80;
const H = 80;

export function initWaveform() {
	socket.onCommand("/waveform/osc1", (path, value) => render("/osc/1/wave", value));
	socket.onCommand("/waveform/osc2", (path, value) => render("/osc/2/wave", value));
}

function render(targetPath, csv) {
	const target = document.querySelector(`[data-osc="${targetPath}"]`);
	if (!target || !csv) return;
	const heights = csv.split(",").map((s) => parseFloat(s));
	if (!heights.length) return;
	const canvas = document.createElement("canvas");
	canvas.width = W;
	canvas.height = H;
	const ctx = canvas.getContext("2d");
	// Determine the value range to scale into the canvas height.
	let max = 0;
	for (let i = 0; i < heights.length; i++) {
		const a = Math.abs(heights[i]);
		if (a > max) max = a;
	}
	if (max === 0) max = 1;
	ctx.fillStyle = "rgba(255,255,255,0.18)";
	const barWidth = W / heights.length;
	for (let i = 0; i < heights.length; i++) {
		const v = heights[i] / max;
		const barHeight = Math.abs(v) * (H / 2);
		const top = (H / 2) - (v >= 0 ? barHeight : 0);
		ctx.fillRect(i * barWidth, top, Math.max(barWidth - 0.5, 0.5), barHeight);
	}
	target.style.backgroundImage = `url("${canvas.toDataURL()}")`;
}
