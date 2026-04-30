// Compact modulation matrix.
//
// Rows = sources (LFO / Mod Env 1 / Mod Env 2 / Channel Pressure).
// Cols = targets (Pitch 1 / Pitch 2 / PW 1 / PW 2 / Wave 1 / Wave 2 /
// Filter 1 / Filter 2 / Vol / OSC2 Vol / Ring / Noise / LFO Rate /
// Delay Time).
// Each valid cell is a small bipolar rotary bound to the source's
// MOD_<TARGET>_AMOUNT path. ~30 of 56 cells are valid; empty cells
// render as blank space.
//
// This module owns the only UI for these parameters now — the LFO
// panel's "depth" knobs and the Mod Env 1 panel's depth knobs were
// removed in this phase since the matrix supersedes them. (Sources
// still have their core controls — rate, type, ADSR — in their own
// panels.)

import { Rotary } from "./controls.js";

const SOURCES = ["LFO", "Mod Env 1", "Mod Env 2", "Pressure"];
const TARGETS = [
	"Pitch 1", "Pitch 2", "PW 1", "PW 2", "Wave 1", "Wave 2",
	"Filter 1", "Filter 2", "Vol", "OSC2 Vol", "Ring", "Noise",
	"LFO Rate", "Delay Time",
];

// 4×14 path table; null = source has no routing to that target.
const PATHS = [
	// LFO
	[
		"/mod/1/depth/pitch", "/mod/1/depth/pitch/2", "/mod/1/depth/pw/1", "/mod/1/depth/pw/2",
		"/mod/1/depth/wave/1", "/mod/1/depth/wave/2", "/mod/1/depth/filter/1", "/mod/1/depth/filter/2",
		"/mod/1/depth/vol", null, null, null, null, "/mod/1/depth/delaytime",
	],
	// Mod Env 1
	[
		"/mod/env/depth/pitch", "/mod/env/depth/pitch/2", "/mod/env/depth/pw/1", "/mod/env/depth/pw/2",
		"/mod/env/depth/wave", null, null, null,
		null, null, "/mod/env/depth/am", "/mod/env/depth/noise", null, null,
	],
	// Mod Env 2
	[
		"/mod/env/2/depth/pitch", "/mod/env/2/depth/pitch/2", "/mod/env/2/depth/pw/1", "/mod/env/2/depth/pw/2",
		null, null, "/mod/env/2/depth/filter", null,
		null, "/mod/env/2/depth/osc2vol", null, "/mod/env/2/depth/noise", "/mod/env/2/depth/lforate", null,
	],
	// Channel pressure
	[
		"/mod/press/depth/pitch", "/mod/press/depth/pitch/2", null, null,
		null, null, "/mod/press/depth/filter", null,
		null, null, null, null, "/mod/press/depth/lfo", null,
	],
];

export function initMatrix() {
	const container = document.getElementById("modmatrix");
	if (!container) return;

	const table = document.createElement("table");
	table.className = "modmatrix-table";

	// Column headers (rotated vertical text via CSS).
	const thead = document.createElement("thead");
	const headerRow = document.createElement("tr");
	headerRow.appendChild(document.createElement("th"));  // empty top-left corner
	for (const t of TARGETS) {
		const th = document.createElement("th");
		th.textContent = t;
		headerRow.appendChild(th);
	}
	thead.appendChild(headerRow);
	table.appendChild(thead);

	// Body rows — one per source.
	const tbody = document.createElement("tbody");
	const pendingKnobs = [];
	for (let r = 0; r < SOURCES.length; r++) {
		const tr = document.createElement("tr");
		const rowLabel = document.createElement("th");
		rowLabel.className = "modmatrix-row-label";
		rowLabel.textContent = SOURCES[r];
		tr.appendChild(rowLabel);
		for (let c = 0; c < TARGETS.length; c++) {
			const td = document.createElement("td");
			const path = PATHS[r][c];
			if (path) {
				const knob = document.createElement("div");
				knob.className = "rotary";
				knob.dataset.osc = path;
				knob.dataset.bipolar = "";
				knob.dataset.size = "tiny";
				knob.title = `${SOURCES[r]} → ${TARGETS[c]}`;
				td.appendChild(knob);
				pendingKnobs.push(knob);
			}
			tr.appendChild(td);
		}
		tbody.appendChild(tr);
	}
	table.appendChild(tbody);
	// Append to live DOM before instantiating Rotary so getComputedStyle
	// can resolve inherited CSS variables (--color, --color-dark).
	container.appendChild(table);
	for (const knob of pendingKnobs) {
		new Rotary(knob);
	}
}
