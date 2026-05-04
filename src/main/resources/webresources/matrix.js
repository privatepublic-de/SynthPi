// Compact modulation matrix.
//
// Rows = sources (LFO, Mod Env 1, Mod Env 2, Mod Wheel, Pressure, Velocity, Keyboard).
// Cols = targets (Pitch All, Pitch 2, PW 1, PW 2, Wave 1, Wave 2,
//                Filter 1, Filter 2, Noise, Ring, Delay Time,
//                LFO Amt, LFO Rate, OSC2 Vol, Vol).
// Each valid cell is a small bipolar rotary bound to the source's
// MOD_<TARGET>_AMOUNT path. Empty cells render as blank space.
//
// This module owns the only UI for these parameters now — the LFO
// panel's "depth" knobs and the Mod Env 1 panel's depth knobs were
// removed in this phase since the matrix supersedes them. (Sources
// still have their core controls — rate, type, ADSR — in their own
// panels.)

import { Rotary } from "./controls.js";

const SOURCES = ["LFO", "Mod Env 1", "Mod Env 2", "Mod Wheel", "Pressure", "Velocity", "Keyboard"];
const TARGETS = [
	"Pitch All", "Pitch 2", "PW 1", "PW 2", "Wave 1", "Wave 2",
	"Filter 1", "Filter 2", "Noise", "Ring", "Delay Time",
	"LFO Amt", "LFO Rate", "OSC2 Vol", "Vol",
];

// 7×15 path table; null = source has no routing to that target.
// Columns 0-7: Pitch All, Pitch 2, PW 1, PW 2, Wave 1, Wave 2, Filter 1, Filter 2
// Columns 8-14: Noise, Ring, Delay Time, LFO Amt, LFO Rate, OSC2 Vol, Vol
const PATHS = [
	// LFO
	[
		"/mod/1/depth/pitch", "/mod/1/depth/pitch/2", "/mod/1/depth/pw/1", "/mod/1/depth/pw/2",
		"/mod/1/depth/wave/1", "/mod/1/depth/wave/2", "/mod/1/depth/filter/1", "/mod/1/depth/filter/2",
		"/mod/1/depth/noise", "/mod/1/depth/ring", "/mod/1/depth/delaytime",
		null, "/mod/1/depth/lforate", "/mod/1/depth/osc2vol", "/mod/1/depth/vol",
	],
	// Mod Env 1
	[
		"/mod/env/depth/pitch", "/mod/env/depth/pitch/2", "/mod/env/depth/pw/1", "/mod/env/depth/pw/2",
		"/mod/env/depth/wave", "/mod/env/depth/wave/2", "/mod/env/depth/filter/1", "/mod/env/depth/filter/2",
		"/mod/env/depth/noise", "/mod/env/depth/am", null,
		"/mod/env/depth/lfoamt", "/mod/env/depth/lforate", "/mod/env/depth/osc2vol", null,
	],
	// Mod Env 2
	[
		"/mod/env/2/depth/pitch", "/mod/env/2/depth/pitch/2", "/mod/env/2/depth/pw/1", "/mod/env/2/depth/pw/2",
		"/mod/env/2/depth/wave/1", "/mod/env/2/depth/wave/2", "/mod/env/2/depth/filter", "/mod/env/2/depth/filter/2",
		"/mod/env/2/depth/noise", "/mod/env/2/depth/ring", null,
		"/mod/env/2/depth/lfoamt", "/mod/env/2/depth/lforate", "/mod/env/2/depth/osc2vol", null,
	],
	// Mod Wheel
	[
		"/mod/wheel/depth/pitch", "/mod/wheel/depth/pitch/2", "/mod/wheel/depth/pw/1", "/mod/wheel/depth/pw/2",
		"/mod/wheel/depth/wave/1", "/mod/wheel/depth/wave/2", "/mod/wheel/depth/filter/1", "/mod/wheel/depth/filter/2",
		"/mod/wheel/depth/noise", "/mod/wheel/depth/ring", "/mod/wheel/depth/delaytime",
		"/mod/wheel/depth/lfoamt", "/mod/wheel/depth/lforate", "/mod/wheel/depth/osc2vol", "/mod/wheel/depth/vol",
	],
	// Pressure
	[
		"/mod/press/depth/pitch", "/mod/press/depth/pitch/2", "/mod/press/depth/pw/1", "/mod/press/depth/pw/2",
		"/mod/press/depth/wave/1", "/mod/press/depth/wave/2", "/mod/press/depth/filter", "/mod/press/depth/filter/2",
		"/mod/press/depth/noise", "/mod/press/depth/ring", "/mod/press/depth/delaytime",
		"/mod/press/depth/lfoamt", "/mod/press/depth/lfo", "/mod/press/depth/osc2vol", null,
	],
	// Velocity
	[
		null, null, "/mod/vel/depth/pw/1", "/mod/vel/depth/pw/2",
		"/mod/vel/depth/wave/1", "/mod/vel/depth/wave/2", "/mod/vel/depth/filter/1", "/mod/vel/depth/filter/2",
		"/mod/vel/depth/noise", "/mod/vel/depth/ring", null,
		"/mod/vel/depth/lfoamt", null, "/mod/vel/depth/osc2vol", null,
	],
	// Keyboard
	[
		"/mod/key/depth/pitch", "/mod/key/depth/pitch/2", "/mod/key/depth/pw/1", "/mod/key/depth/pw/2",
		"/mod/key/depth/wave/1", "/mod/key/depth/wave/2", "/mod/key/depth/filter/1", "/mod/key/depth/filter/2",
		"/mod/key/depth/noise", "/mod/key/depth/ring", null,
		"/mod/key/depth/lfoamt", "/mod/key/depth/lforate", "/mod/key/depth/osc2vol", null,
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
		const inner = document.createElement("div");
		inner.className = "modmatrix-col-label";
		inner.textContent = t;
		th.appendChild(inner);
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
