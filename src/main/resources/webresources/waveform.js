// OSC1 / OSC2 waveform display.
//
// The engine pushes /waveform/oscN=v1,v2,…  every time the oscillator's
// wave parameter changes (or on initial state dump). The render shape
// depends on the engine:
//   - WAVETABLE sends 50 amplitude samples remapped to 1..37 (centerline 19).
//     We draw a continuous polyline so the result actually looks like a wave.
//   - ADDITIVE sends 50 harmonic volumes remapped to -37..-1 (always negative).
//     We draw bars rising from the bottom — a spectrum, not a waveform.
// Sign distinguishes them: any positive value means waveform, all-negative
// means spectrum. The Rotary's transparent canvas sits on top of the
// generated image so the arc / indicator stays in front.

import { socket } from "./socket.js";

const W = 80;
const H = 80;
// CRT-phosphor green for the wavetable line — meant to read as an
// oscilloscope trace behind the rotary. shadowBlur fakes the phosphor
// halo (a real P31 tube would also have decay; we don't, since each
// frame is a static image set as a backdrop). The spectrum view stays
// white because it's a frequency-domain plot, not a scope trace.
const SCOPE_STROKE = "#39FF14";
const SCOPE_GLOW = 3;
const SPECTRUM_FILL = "rgba(255,255,255,0.25)";

export function initWaveform() {
	socket.onCommand("/waveform/osc1", (path, value) => render("/osc/1/wave", value));
	socket.onCommand("/waveform/osc2", (path, value) => render("/osc/2/wave", value));
}

/** Drop the wave-knob backdrop. Called on mode change to VA / Exciter where
 *  the engine no longer pushes /waveform/oscN, so a stale image from the
 *  previous mode would otherwise linger. */
export function clearWaveform() {
	for (const path of ["/osc/1/wave", "/osc/2/wave"]) {
		const el = document.querySelector(`[data-osc="${path}"]`);
		if (el) el.style.backgroundImage = "";
	}
}

function render(targetPath, csv) {
	const target = document.querySelector(`[data-osc="${targetPath}"]`);
	if (!target || !csv) return;
	const values = csv.split(",").map((s) => parseFloat(s));
	if (!values.length) return;
	const allNegative = values.every((v) => v < 0);
	const dataUrl = allNegative
		? renderSpectrum(values.map((v) => -v))
		: renderWaveform(values);
	target.style.backgroundImage = `url("${dataUrl}")`;
}

/** Continuous polyline through the samples, centered vertically.
 *  Auto-scales to fill the canvas regardless of the actual amplitude. */
function renderWaveform(samples) {
	let min = Infinity, max = -Infinity;
	for (const v of samples) { if (v < min) min = v; if (v > max) max = v; }
	const mid = (min + max) / 2;
	const halfRange = Math.max((max - min) / 2, 1);
	const padding = 6;
	const usableHalf = (H / 2) - padding;
	const canvas = document.createElement("canvas");
	canvas.width = W;
	canvas.height = H;
	const ctx = canvas.getContext("2d");
	ctx.strokeStyle = SCOPE_STROKE;
	ctx.shadowColor = SCOPE_STROKE;
	ctx.shadowBlur = SCOPE_GLOW;
	ctx.lineWidth = 1.5;
	ctx.lineJoin = "round";
	ctx.beginPath();
	for (let i = 0; i < samples.length; i++) {
		const x = (i / (samples.length - 1)) * W;
		const norm = (samples[i] - mid) / halfRange;
		const y = (H / 2) - norm * usableHalf;
		if (i === 0) ctx.moveTo(x, y);
		else ctx.lineTo(x, y);
	}
	ctx.stroke();
	return canvas.toDataURL();
}

/** Bars rising from the bottom — a harmonic spectrum view. */
function renderSpectrum(magnitudes) {
	let max = 0;
	for (const v of magnitudes) if (v > max) max = v;
	if (max === 0) max = 1;
	const padding = 4;
	const usable = H - padding;
	const canvas = document.createElement("canvas");
	canvas.width = W;
	canvas.height = H;
	const ctx = canvas.getContext("2d");
	ctx.fillStyle = SPECTRUM_FILL;
	const barWidth = W / magnitudes.length;
	for (let i = 0; i < magnitudes.length; i++) {
		const v = magnitudes[i] / max;
		const barHeight = v * usable;
		ctx.fillRect(i * barWidth, H - barHeight, Math.max(barWidth - 0.5, 0.5), barHeight);
	}
	return canvas.toDataURL();
}
