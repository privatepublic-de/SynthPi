// OSC1 / OSC2 waveform / spectrum scope strip.
//
// Renders into the persistent canvas elements inside .osc-scope-strip.
// The engine pushes /waveform/oscN=v1,v2,… on wave parameter changes:
//   - WAVETABLE sends 50 positive amplitude samples → polyline scope trace.
//   - ADDITIVE sends 50 negative harmonic volumes → spectrum bar view.
// Sign distinguishes them. Updates are RAF-debounced: rapid WebSocket
// bursts (e.g. while dragging a knob) collapse to one render per frame.
//
// The scope screens are interactive: vertical drag and mouse-wheel change
// the OSC wave/timbre parameter — same step size as the rotary knobs.

import { socket } from "./socket.js";

const WAVEFORM_COLOR = "#39FF14"; // phosphor green
const SPECTRUM_COLOR = "#e9a84a"; // amber

const latestData = {};
const pendingRaf  = {};

// ---------------------------------------------------------------------------
// Scope screen interaction — shared drag state, window-level move/up mirrors
// the PotDragHandler pattern so dragging off the canvas works cleanly.
// ---------------------------------------------------------------------------

let scopeDrag = null; // { startY, startValue, path }

window.addEventListener("mousemove", (e) => {
	if (!scopeDrag) return;
	const delta = (scopeDrag.startY - e.pageY) / 150;
	socket.send(scopeDrag.path, Math.min(1, Math.max(0, scopeDrag.startValue + delta)));
});
window.addEventListener("mouseup",     () => { scopeDrag = null; });
window.addEventListener("blur",        () => { scopeDrag = null; });
window.addEventListener("touchmove", (e) => {
	if (!scopeDrag) return;
	e.preventDefault();
	const delta = (scopeDrag.startY - e.touches[0].pageY) / 150;
	socket.send(scopeDrag.path, Math.min(1, Math.max(0, scopeDrag.startValue + delta)));
}, { passive: false });
window.addEventListener("touchend",    () => { scopeDrag = null; });
window.addEventListener("touchcancel", () => { scopeDrag = null; });

function bindScopeInteraction(num, path) {
	const canvas = document.getElementById(`scope-canvas-${num}`);
	if (!canvas) return;

	let currentValue = 0;
	socket.onParam(path, (v) => { currentValue = v; });

	canvas.addEventListener("mousedown", (e) => {
		e.preventDefault();
		scopeDrag = { startY: e.pageY, startValue: currentValue, path };
	});
	canvas.addEventListener("touchstart", (e) => {
		e.preventDefault();
		scopeDrag = { startY: e.touches[0].pageY, startValue: currentValue, path };
	}, { passive: false });
	canvas.addEventListener("wheel", (e) => {
		e.preventDefault();
		socket.send(path, Math.min(1, Math.max(0, currentValue - Math.sign(e.deltaY) / 150)));
	}, { passive: false });
}

// ---------------------------------------------------------------------------
// Rendering
// ---------------------------------------------------------------------------

function scheduleRender(num, csv) {
	latestData[num] = csv;
	if (pendingRaf[num]) return;
	pendingRaf[num] = requestAnimationFrame(() => {
		delete pendingRaf[num];
		renderToCanvas(num, latestData[num]);
	});
}

export function initWaveform() {
	socket.onCommand("/waveform/osc1", (_p, value) => scheduleRender(1, value));
	socket.onCommand("/waveform/osc2", (_p, value) => scheduleRender(2, value));
	bindScopeInteraction(1, "/osc/1/wave");
	bindScopeInteraction(2, "/osc/2/wave");
	// Clear any leftover backgroundImage from the old knob-backdrop approach.
	for (const path of ["/osc/1/wave", "/osc/2/wave"]) {
		const el = document.querySelector(`[data-osc="${path}"]`);
		if (el) el.style.backgroundImage = "";
	}
}

export function clearWaveform() {
	for (const num of [1, 2]) {
		const canvas = document.getElementById(`scope-canvas-${num}`);
		if (!canvas) continue;
		canvas.getContext("2d").clearRect(0, 0, canvas.width, canvas.height);
	}
}

function renderToCanvas(num, csv) {
	const canvas = document.getElementById(`scope-canvas-${num}`);
	if (!canvas || !csv) return;

	const values = csv.split(",").map(Number);
	if (!values.length) return;

	// Match canvas pixel buffer to its CSS layout size for sharp rendering.
	const W   = canvas.offsetWidth  || 200;
	const H   = canvas.offsetHeight || 48;
	const dpr = window.devicePixelRatio || 1;
	const pw  = Math.round(W * dpr);
	const ph  = Math.round(H * dpr);
	if (canvas.width !== pw || canvas.height !== ph) {
		canvas.width  = pw;
		canvas.height = ph;
	}

	const ctx = canvas.getContext("2d");
	ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
	ctx.clearRect(0, 0, W, H);

	const allNegative = values.every(v => v < 0);
	if (allNegative) drawSpectrum(ctx, values.map(v => -v), W, H);
	else             drawWaveform(ctx, values, W, H);
}

function drawWaveform(ctx, samples, W, H) {
	let min = Infinity, max = -Infinity;
	for (const v of samples) { if (v < min) min = v; if (v > max) max = v; }
	const mid       = (min + max) / 2;
	const halfRange = Math.max((max - min) / 2, 1);
	const pad       = 5;
	const halfH     = H / 2 - pad;

	ctx.strokeStyle = WAVEFORM_COLOR;
	ctx.shadowColor = WAVEFORM_COLOR;
	ctx.shadowBlur  = 5;
	ctx.lineWidth   = 1.5;
	ctx.lineJoin    = "round";
	ctx.beginPath();
	for (let i = 0; i < samples.length; i++) {
		const x    = (i / (samples.length - 1)) * W;
		const norm = (samples[i] - mid) / halfRange;
		const y    = H / 2 - norm * halfH;
		i === 0 ? ctx.moveTo(x, y) : ctx.lineTo(x, y);
	}
	ctx.stroke();
}

function drawSpectrum(ctx, magnitudes, W, H) {
	let max = 0;
	for (const v of magnitudes) if (v > max) max = v;
	if (max === 0) max = 1;
	const usable   = H - 3;
	const barWidth = W / magnitudes.length;

	ctx.shadowColor = SPECTRUM_COLOR;
	ctx.shadowBlur  = 4;
	for (let i = 0; i < magnitudes.length; i++) {
		const norm = magnitudes[i] / max;
		const barH = norm * usable;
		ctx.globalAlpha = 0.3 + norm * 0.7;
		ctx.fillStyle   = SPECTRUM_COLOR;
		ctx.fillRect(i * barWidth, H - barH, Math.max(barWidth - 0.5, 0.5), barH);
	}
	ctx.globalAlpha = 1;
}
