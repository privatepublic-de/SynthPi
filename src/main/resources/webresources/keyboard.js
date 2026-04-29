// On-screen piano, computer-keyboard MIDI input, and pitch/mod wheels.
// All three are entry points for note play; group them in one module
// since they share the /play/note/<n>=1|0 protocol.

import { socket } from "./socket.js";

const FIRST_NOTE = 36;

// KEYMAP uses KeyboardEvent.code (layout-independent — refers to the
// physical US-QWERTY position), so a German-layout user pressing the
// physical Z position (labeled "Y") still gets the same MIDI offset.
// IntlBackslash is the German "<>" key left of Z. Two rows give two
// overlapping octaves; the top row starts at offset 12.
const KEYMAP = {
	IntlBackslash: -1,
	KeyZ: 0, KeyS: 1, KeyX: 2, KeyD: 3, KeyC: 4, KeyV: 5, KeyG: 6, KeyB: 7,
	KeyH: 8, KeyN: 9, KeyJ: 10, KeyM: 11,
	Comma: 12, KeyL: 13, Period: 14, Semicolon: 15, Slash: 16, Quote: 17,
	KeyQ: 12, Digit2: 13, KeyW: 14, Digit3: 15, KeyE: 16, KeyR: 17,
	Digit5: 18, KeyT: 19, Digit6: 20, KeyY: 21, Digit7: 22, KeyU: 23,
	KeyI: 24, Digit9: 25, KeyO: 26, Digit0: 27, KeyP: 28,
	BracketLeft: 29, Equal: 30, BracketRight: 31,
};

class Keyboard {
	constructor() {
		this._wireOnScreenKeys();
		this._wireComputerKeyboard();
	}

	/**
	 * Each .wk / .bk div in the keyboard area is a momentary push button
	 * for a MIDI note. Mouse + touch press sends note-on; release sends
	 * note-off. Server echoes /play/note/N=1|0 back to set the .pressed
	 * class so highlights stay in sync across multiple browser tabs and
	 * external MIDI input.
	 */
	_wireOnScreenKeys() {
		const keys = document.querySelectorAll("#keyboard .wk, #keyboard .bk");
		keys.forEach((el, i) => {
			const note = FIRST_NOTE + i;
			const path = `/play/note/${note}`;
			el.dataset.osc = path;

			let pressed = false;
			const press = () => {
				if (pressed) return;
				pressed = true;
				socket.send(path, 1);
			};
			const release = () => {
				if (!pressed) return;
				pressed = false;
				socket.send(path, 0);
			};

			el.addEventListener("mousedown", press);
			el.addEventListener("mouseup", release);
			el.addEventListener("mouseleave", release);
			el.addEventListener("touchstart", (e) => {
				e.preventDefault();
				press();
			}, { passive: false });
			el.addEventListener("touchend", release);
			el.addEventListener("touchcancel", release);

			// Server pushes /play/note/N=1|0 to all clients (including the
			// originator) so the visual highlight follows engine state.
			socket.onParam(path, (v) => {
				el.classList.toggle("pressed", v > 0);
			});
		});
	}

	/**
	 * Physical computer keyboard → MIDI. ShiftLeft / ShiftRight cycle the
	 * octave (released-only to avoid spam). Held notes tracked by
	 * KeyboardEvent.code so the matching note-off uses the original MIDI
	 * number even if the octave shifted mid-hold.
	 */
	_wireComputerKeyboard() {
		let kbOctave = 4;
		const heldNotes = {};

		const captureEnabled = () => {
			const ae = document.activeElement;
			if (ae && (ae.tagName === "INPUT" || ae.tagName === "TEXTAREA" || ae.isContentEditable)) {
				return false;
			}
			// Modal open → don't steal keypresses.
			if (document.querySelector("#dimmer.shown")) return false;
			return true;
		};

		document.body.addEventListener("keydown", (e) => {
			if (e.repeat) return;
			if (!captureEnabled()) return;
			const offset = KEYMAP[e.code];
			if (typeof offset === "undefined") return;
			if (Object.prototype.hasOwnProperty.call(heldNotes, e.code)) return;
			const note = offset + 12 * kbOctave;
			if (note < 0 || note > 127) return;
			heldNotes[e.code] = note;
			socket.send(`/play/note/${note}`, 1);
			e.preventDefault();
		});

		document.body.addEventListener("keyup", (e) => {
			if (captureEnabled()) {
				if (e.code === "ShiftLeft" && kbOctave > 0) kbOctave--;
				else if (e.code === "ShiftRight" && kbOctave < 8) kbOctave++;
			}
			const note = heldNotes[e.code];
			if (typeof note === "undefined") return;
			delete heldNotes[e.code];
			socket.send(`/play/note/${note}`, 0);
			e.preventDefault();
		});

		// Page focus loss can swallow keyup (alt-tab, click outside browser)
		// — release everything proactively to avoid stuck notes.
		window.addEventListener("blur", () => {
			Object.keys(heldNotes).forEach((code) => {
				socket.send(`/play/note/${heldNotes[code]}`, 0);
				delete heldNotes[code];
			});
		});
	}
}

export function initKeyboard() {
	return new Keyboard();
}
