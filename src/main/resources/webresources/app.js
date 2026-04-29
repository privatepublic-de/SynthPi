// Entry point. Connect the WebSocket and bind any controls already present
// in the page. Phase 1 ships only one rotary as a smoke test; later phases
// add panels, dialogs, the modulation matrix, the on-screen keyboard, etc.

import { socket } from "./socket.js";
import { Rotary, Fader, Toggle, Select } from "./controls.js";

document.addEventListener("DOMContentLoaded", () => {
	// Connect first so onParam handlers registered below are subscribed
	// before the server's 1-second initial state dump arrives.
	socket.connect();

	document.querySelectorAll(".rotary").forEach((el) => new Rotary(el));
	document.querySelectorAll(".fader").forEach((el) => new Fader(el));
	document.querySelectorAll(".toggle").forEach((el) => new Toggle(el));
	document.querySelectorAll(".select").forEach((el) => new Select(el));
});
