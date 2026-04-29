// Canvas-rendered control components, ported from the prototype at
// webresources/controls/script.js and adapted to be ES modules with
// socket binding via data-osc attributes.

import { socket } from "./socket.js";

const U = {
	radFactor: Math.PI / 180,
	polar2cartesian: (angle, radius) => ({
		x: radius * Math.cos(angle),
		y: radius * Math.sin(angle),
	}),
	capInRange: (v, min, max) => Math.min(Math.max(v, min), max),
};

class PotDragHandler {
	constructor() {
		this.isDragging = false;
		this.cx = 0;
		this.cy = 0;
		this.updateValueCallback = null;
		this.finishedCallback = null;
		window.addEventListener("mousemove", (e) => this._onMove(e.pageX, e.pageY));
		window.addEventListener("mouseup", () => this._onStop());
		window.addEventListener("touchmove", (e) => {
			if (this.isDragging) {
				e.preventDefault();
				const t = e.touches[0];
				this._onMove(t.pageX, t.pageY);
			}
		}, { passive: false });
		window.addEventListener("touchend", () => this._onStop());
		window.addEventListener("touchcancel", () => this._onStop());
		// Browser focus loss (Alt-Tab, OS modal) eats the mouseup that would
		// normally clear isDragging. Without this catch the originating
		// rotary's _userActive flag stays true, server pushes get silently
		// dropped, and push-return wheels never snap back.
		window.addEventListener("blur", () => this._onStop());
	}

	_valueForCoordinates(x, y) {
		const y0 = y - this.cy;
		const x0 = x - this.cx;
		let ang = parseInt(Math.atan2(y0, x0) * (180 / Math.PI));
		if (ang < 0 && ang >= -90) ang += 225;
		else if (ang >= 0 && ang < 45) ang += 225;
		else if (ang < -90) ang += 225;
		else if (ang > 135) ang -= 135;
		else ang = ang < 90 ? 270 : 0;
		return ang / 270.0;
	}

	startDrag(pot, x, y, updateValueCallback, finishedCallback, width, height) {
		this.updateValueCallback = updateValueCallback;
		this.finishedCallback = finishedCallback;
		let el = pot;
		this.cx = 0;
		this.cy = 0;
		do {
			this.cx += el.offsetLeft;
			this.cy += el.offsetTop;
			el = el.offsetParent;
		} while (el);
		// Caller passes per-Rotary dimensions so tiny knobs in the matrix get
		// their own center math, not the static default.
		this.cx += (width || Rotary.width) / 2;
		this.cy += (height || Rotary.height) / 2;
		this.isDragging = true;
		this.updateValueCallback(this._valueForCoordinates(x, y));
	}

	_onMove(x, y) {
		if (this.isDragging) {
			this.updateValueCallback(this._valueForCoordinates(x, y));
		}
	}

	_onStop() {
		if (this.isDragging) {
			this.isDragging = false;
			this.finishedCallback();
		}
	}
}

export class Rotary {
	static width = 80;
	static height = 80;
	static dragHandler = new PotDragHandler();

	constructor(element) {
		this.element = element;
		this.path = element.dataset.osc || null;
		this.isBipolar = element.dataset.bipolar !== undefined;
		this.isMix = element.dataset.mix !== undefined;
		// Echo guard: while the user is actively dragging, ignore inbound
		// /path=value pushes from the server so the knob doesn't fight back.
		// (The server already suppresses value-echo to the originating session,
		// but other server-side state changes — patch loads, MIDI input — can
		// still arrive while the user is mid-drag.)
		this._userActive = false;

		// "tiny" variant for the modulation matrix — about half size, with
		// proportionally thinner outer ring and value arc.
		const tiny = element.dataset.size === "tiny";
		this.width = tiny ? 36 : Rotary.width;
		this.height = tiny ? 36 : Rotary.height;
		this.widthOuter = tiny ? 1 : 2;
		this.widthArc = tiny ? 5 : 12;

		this.color = getComputedStyle(element).getPropertyValue("--color");
		this.colorDark = getComputedStyle(element).getPropertyValue("--color-dark");
		this.canvas = document.createElement("canvas");
		this.canvas.width = this.width;
		this.canvas.height = this.height;
		this.ctx = this.canvas.getContext("2d");
		this.label = document.createElement("label");
		this.label.appendChild(document.createTextNode(element.dataset.label || ""));
		this.valueElement = document.createElement("div");
		this.valueElement.classList.add("value");
		element.appendChild(this.label);
		element.appendChild(this.canvas);
		element.appendChild(this.valueElement);

		// Push-return controls (pitch wheel / mod wheel): release snaps back
		// to the resting position — 0.5 for bipolar (centered) or 0 otherwise.
		this.pushReturn = element.dataset.pushReturn !== undefined;
		// Once the server pushes a /label/<path> for this param, we trust
		// it as the value display and stop overwriting with raw 0..127.
		// Stays false until/unless a label arrives.
		this._serverLabelReceived = false;

		this.value = 0;
		this._setValue(0);

		const startPress = (x, y) => {
			this._userActive = true;
			Rotary.dragHandler.startDrag(
				element, x, y,
				(v) => this._userSetValue(v),
				() => {
					this._userActive = false;
					if (this.pushReturn) {
						const center = this.isBipolar ? 0.5 : 0;
						this._userSetValue(center);
					}
				},
				this.width, this.height,
			);
		};
		element.addEventListener("mousedown", (e) => startPress(e.pageX, e.pageY));
		element.addEventListener("touchstart", (e) => {
			e.preventDefault();
			const t = e.touches[0];
			startPress(t.pageX, t.pageY);
		}, { passive: false });
		element.addEventListener("wheel", (e) => {
			e.preventDefault();
			const v = U.capInRange(this.value + Math.sign(e.deltaY) / 150, 0, 1);
			this._userSetValue(v);
		});
		// Double-click resets to 0 (matches old jQuery UI behaviour).
		element.addEventListener("dblclick", () => {
			this._userSetValue(0);
		});

		if (this.path) {
			socket.onParam(this.path, (v) => {
				if (this._userActive) return;
				this._setValue(v);
			});
			// Subscribe unconditionally — when the server has a label for this
			// param (e.g. "440 Hz", "−3.4 dB"), it'll arrive and we'll switch
			// the display over. Params without a label never push and the raw
			// 0..127 keeps showing.
			socket.onCommand(`/label${this.path}`, (path, value) => {
				this._serverLabelReceived = true;
				this.valueElement.textContent = value;
			});
		}
	}

	/** User-driven change: update local + send to server. */
	_userSetValue(percent) {
		this._setValue(percent);
		if (this.path) socket.send(this.path, percent);
	}

	/** Update local state and re-render. Used by both user-driven changes and server pushes. */
	_setValue(percent) {
		this.value = percent;
		this._renderValue(percent);
		// Once the server has pushed a /label/<path>, leave the display to it.
		// The next /label push will refresh after the engine processes the
		// new value (typically within one audio buffer).
		if (this._serverLabelReceived) return;
		let displayValue;
		if (this.isMix) {
			const intPercent = Math.round(100 * percent);
			displayValue = (100 - intPercent) + " : " + intPercent;
		} else if (this.isBipolar) {
			displayValue = Math.round(127 * percent) - 64;
		} else {
			displayValue = Math.round(127 * percent);
		}
		this.valueElement.innerHTML = displayValue;
	}

	_renderValue(valuePercent) {
		const angleSpan = 240;
		const widthOuter = this.widthOuter;
		const widthArc = this.widthArc;
		const radiusOuter = this.width / 2 - widthOuter / 2;
		const radiusIndicator = this.width / 2;
		const radiusArc = this.width / 2 - widthOuter * 2.5 - widthArc / 2;
		const angleStart = (-angleSpan / 2 - 90) * U.radFactor;
		const angleCenter = -90 * U.radFactor;
		const angleEnd = (-angleSpan / 2 - 90 + angleSpan) * U.radFactor;
		const angleValue = (-angleSpan / 2 - 90 + angleSpan * valuePercent) * U.radFactor;

		this.ctx.clearRect(0, 0, this.width, this.height);
		this.ctx.translate(this.width / 2, this.height / 2);

		this.ctx.lineWidth = widthOuter;
		if (this.isMix) {
			// no outer ring for mix
		} else if (this.isBipolar) {
			const isNegative = angleValue < angleCenter;
			if (isNegative) {
				this._arc(0, 0, radiusOuter, angleStart, angleValue, this.colorDark);
				this._arc(0, 0, radiusOuter, angleValue, angleCenter, this.color);
				this._arc(0, 0, radiusOuter, angleCenter, angleEnd, this.colorDark);
			} else {
				this._arc(0, 0, radiusOuter, angleStart, angleCenter, this.colorDark);
				this._arc(0, 0, radiusOuter, angleCenter, angleValue, this.color);
				this._arc(0, 0, radiusOuter, angleValue, angleEnd, this.colorDark);
			}
		} else {
			this._arc(0, 0, radiusOuter, angleStart, angleValue, this.color);
			this._arc(0, 0, radiusOuter, angleValue, angleEnd, this.colorDark);
		}

		// value ring
		this.ctx.lineWidth = widthArc;
		if (this.isMix) {
			const radius = radiusArc + widthArc / 2 - widthOuter / 2;
			this._arc(0, 0, radius, angleStart, angleEnd, this.colorDark);
		} else if (this.isBipolar) {
			if (angleCenter < angleValue) {
				this._arc(0, 0, radiusArc, angleCenter, angleValue, this.color);
			} else {
				this._arc(0, 0, radiusArc, angleValue, angleCenter, this.color);
			}
		} else {
			this._arc(0, 0, radiusArc, angleStart, angleValue, this.color);
		}

		// indicator
		this.ctx.beginPath();
		this.ctx.lineWidth = widthOuter;
		const p1 = U.polar2cartesian(angleValue, radiusIndicator);
		this.ctx.moveTo(p1.x, p1.y);
		const p2 = U.polar2cartesian(angleValue, radiusArc - widthArc / 2);
		this.ctx.lineTo(p2.x, p2.y);
		this.ctx.strokeStyle = this.color;
		this.ctx.stroke();

		this.ctx.setTransform(1, 0, 0, 1, 0, 0);
	}

	_arc(cx, cy, radius, start, end, color) {
		this.ctx.beginPath();
		this.ctx.arc(cx, cy, radius, start, end);
		this.ctx.strokeStyle = color;
		this.ctx.stroke();
	}
}

// Toggle, Select, Fader follow the same pattern but aren't wired in phase 1.
// They're ported here so phase 2 can import them without redoing the work.

export class Toggle {
	constructor(element) {
		this.element = element;
		this.path = element.dataset.osc || null;
		this.value = false;
		this._setValue(false);
		element.addEventListener("click", () => {
			this._userSetValue(!this.value);
		});
		if (this.path) {
			socket.onParam(this.path, (v) => this._setValue(v > 0));
		}
	}

	_userSetValue(v) {
		this._setValue(v);
		if (this.path) socket.send(this.path, v ? 1 : 0);
	}

	_setValue(v) {
		this.value = v;
		this.element.classList.toggle("selected", v);
	}
}

export class Select {
	constructor(element) {
		this.element = element;
		this.path = element.dataset.osc || null;
		const options = element.dataset.list.split(",");
		this.optionCount = options.length;
		options.forEach((opt, i) => {
			element.insertAdjacentHTML(
				"beforeend",
				`<div class="option" data-value="${i}">${opt}</div>`
			);
		});
		this.optionElements = element.querySelectorAll(".option");
		this.optionElements.forEach((optEl) => {
			optEl.addEventListener("click", (ev) => {
				this._userSetValue(parseInt(ev.target.dataset.value));
			});
		});
		this._setSelected(0);
		if (this.path) {
			socket.onParam(this.path, (v) => {
				// Server values are 0..1; map to discrete index using the same
				// rounding rule the engine uses (Math.round(val*(N-1))).
				const idx = Math.round(v * (this.optionCount - 1));
				this._setSelected(idx);
			});
		}
	}

	_userSetValue(idx) {
		this._setSelected(idx);
		if (this.path) {
			socket.send(this.path, idx / (this.optionCount - 1));
		}
	}

	_setSelected(idx) {
		this.selectedIndex = idx;
		this.optionElements.forEach((optEl) => {
			optEl.classList.toggle("selected", parseInt(optEl.dataset.value) === idx);
		});
	}
}

/** Shared single-active-fader drag tracker — mirrors PotDragHandler. The
 *  active fader is set on press and cleared on any release event, including
 *  window blur, so dragging off the element / out of the window doesn't
 *  leave the fader stuck in dragging state. */
class FaderDragHandler {
	constructor() {
		this.active = null;
		window.addEventListener("mousemove", (e) => {
			if (this.active) this.active._dragTo(e.pageY);
		});
		window.addEventListener("mouseup", () => this._stop());
		window.addEventListener("touchmove", (e) => {
			if (this.active) {
				e.preventDefault();
				this.active._dragTo(e.touches[0].pageY);
			}
		}, { passive: false });
		window.addEventListener("touchend", () => this._stop());
		window.addEventListener("touchcancel", () => this._stop());
		window.addEventListener("blur", () => this._stop());
	}
	start(fader) { this.active = fader; }
	_stop() {
		if (this.active) {
			this.active._userActive = false;
			this.active = null;
		}
	}
}

export class Fader {
	static width = 35;
	static height = 155;
	static effectiveHeight = 140;
	static dragHandler = new FaderDragHandler();

	constructor(element) {
		this.element = element;
		this.path = element.dataset.osc || null;
		this._serverLabelReceived = false;
		this.color = getComputedStyle(element).getPropertyValue("--color");
		this.colorDark = getComputedStyle(element).getPropertyValue("--color-dark");
		this.canvas = document.createElement("canvas");
		this.canvas.width = Fader.width;
		this.canvas.height = Fader.height;
		this.ctx = this.canvas.getContext("2d");
		this.label = document.createElement("label");
		this.label.appendChild(document.createTextNode(element.dataset.label || ""));
		this.valueElement = document.createElement("div");
		this.valueElement.classList.add("value");
		element.appendChild(this.label);
		element.appendChild(this.canvas);
		element.appendChild(this.valueElement);
		this.value = 0;
		this._userActive = false;
		this._setValue(0);

		element.addEventListener("wheel", (e) => {
			e.preventDefault();
			this._userSetValue(U.capInRange(this.value + Math.sign(e.deltaY) / 150, 0, 1));
		});
		// Press starts a drag. mousemove + mouseup live on the window via
		// FaderDragHandler so dragging off the element doesn't release.
		element.addEventListener("mousedown", (e) => {
			e.preventDefault();
			this._userActive = true;
			Fader.dragHandler.start(this);
			this._dragTo(e.pageY);
		});
		element.addEventListener("touchstart", (e) => {
			e.preventDefault();
			this._userActive = true;
			Fader.dragHandler.start(this);
			this._dragTo(e.touches[0].pageY);
		}, { passive: false });

		if (this.path) {
			socket.onParam(this.path, (v) => {
				if (this._userActive) return;
				this._setValue(v);
			});
			socket.onCommand(`/label${this.path}`, (path, value) => {
				this._serverLabelReceived = true;
				this.valueElement.textContent = value;
			});
		}
	}

	_userSetValue(v) {
		this._setValue(v);
		if (this.path) socket.send(this.path, v);
	}

	/** Translate a window-coordinate pageY into a 0..1 fader value and apply. */
	_dragTo(pageY) {
		const rect = this.canvas.getBoundingClientRect();
		const localY = pageY - (rect.top + window.scrollY);
		this._userSetValue(U.capInRange(
			(Fader.effectiveHeight - localY) / Fader.effectiveHeight, 0, 1
		));
	}

	_setValue(v) {
		this.value = v;
		this._renderValue(v);
		if (this._serverLabelReceived) return;
		this.valueElement.innerHTML = Math.round(127 * v);
	}

	_renderValue(percent) {
		this.ctx.clearRect(0, 0, Fader.width, Fader.height);
		this.ctx.beginPath();
		this.ctx.lineWidth = 2;
		this.ctx.strokeStyle = this.colorDark;
		this.ctx.moveTo(1, Fader.effectiveHeight);
		this.ctx.lineTo(1, 0);
		this.ctx.stroke();
		const handleY = Fader.effectiveHeight - percent * Fader.effectiveHeight;
		this.ctx.beginPath();
		this.ctx.lineWidth = 2;
		this.ctx.moveTo(1, handleY);
		this.ctx.lineTo(Fader.width / 2 + 6, handleY);
		this.ctx.strokeStyle = this.color;
		this.ctx.stroke();
		this.ctx.beginPath();
		this.ctx.lineWidth = 12;
		this.ctx.moveTo(Fader.width / 2, Fader.effectiveHeight);
		this.ctx.lineTo(Fader.width / 2, handleY);
		this.ctx.strokeStyle = this.color;
		this.ctx.stroke();
	}
}
