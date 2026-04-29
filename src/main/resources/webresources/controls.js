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
		window.addEventListener("mousemove", this._move.bind(this));
		window.addEventListener("mouseup", this._stop.bind(this));
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

	startDrag(pot, e, updateValueCallback, finishedCallback) {
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
		this.cx += Rotary.width / 2;
		this.cy += Rotary.height / 2;
		this.isDragging = true;
		this.updateValueCallback(this._valueForCoordinates(e.pageX, e.pageY));
	}

	_move(e) {
		if (this.isDragging) {
			this.updateValueCallback(this._valueForCoordinates(e.pageX, e.pageY));
		}
	}

	_stop() {
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

		this.color = getComputedStyle(element).getPropertyValue("--color");
		this.colorDark = getComputedStyle(element).getPropertyValue("--color-dark");
		this.canvas = document.createElement("canvas");
		this.canvas.width = Rotary.width;
		this.canvas.height = Rotary.height;
		this.ctx = this.canvas.getContext("2d");
		this.label = document.createElement("label");
		this.label.appendChild(document.createTextNode(element.dataset.label || ""));
		this.valueElement = document.createElement("div");
		this.valueElement.classList.add("value");
		element.appendChild(this.label);
		element.appendChild(this.canvas);
		element.appendChild(this.valueElement);

		this.value = 0;
		this._setValue(0);

		element.addEventListener("mousedown", (e) => {
			this._userActive = true;
			Rotary.dragHandler.startDrag(
				element, e,
				(v) => this._userSetValue(v),
				() => { this._userActive = false; }
			);
		});
		element.addEventListener("wheel", (e) => {
			e.preventDefault();
			const v = U.capInRange(this.value + Math.sign(-e.deltaY) / 150, 0, 1);
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
		const widthOuter = 2;
		const widthArc = 12;
		const radiusOuter = Rotary.width / 2 - widthOuter / 2;
		const radiusIndicator = Rotary.width / 2;
		const radiusArc = Rotary.width / 2 - widthOuter * 2.5 - widthArc / 2;
		const angleStart = (-angleSpan / 2 - 90) * U.radFactor;
		const angleCenter = -90 * U.radFactor;
		const angleEnd = (-angleSpan / 2 - 90 + angleSpan) * U.radFactor;
		const angleValue = (-angleSpan / 2 - 90 + angleSpan * valuePercent) * U.radFactor;

		this.ctx.clearRect(0, 0, Rotary.width, Rotary.height);
		this.ctx.translate(Rotary.width / 2, Rotary.height / 2);

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

export class Fader {
	static width = 35;
	static height = 155;
	static effectiveHeight = 140;

	constructor(element) {
		this.element = element;
		this.path = element.dataset.osc || null;
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

		const computeValueFromOffset = (offsetY) => U.capInRange(
			(Fader.effectiveHeight - offsetY) / Fader.effectiveHeight, 0, 1
		);

		element.addEventListener("wheel", (e) => {
			e.preventDefault();
			this._userSetValue(U.capInRange(this.value + Math.sign(-e.deltaY) / 150, 0, 1));
		});
		let isDragging = false;
		element.addEventListener("mousedown", (e) => {
			isDragging = true;
			this._userActive = true;
			e.preventDefault();
			this._userSetValue(computeValueFromOffset(e.offsetY));
		});
		element.addEventListener("mousemove", (e) => {
			if (isDragging && e.target !== this.label) {
				this._userSetValue(computeValueFromOffset(e.offsetY));
			}
		});
		const stopDrag = () => {
			isDragging = false;
			this._userActive = false;
		};
		element.addEventListener("mouseup", stopDrag);
		element.addEventListener("mouseleave", stopDrag);

		if (this.path) {
			socket.onParam(this.path, (v) => {
				if (this._userActive) return;
				this._setValue(v);
			});
		}
	}

	_userSetValue(v) {
		this._setValue(v);
		if (this.path) socket.send(this.path, v);
	}

	_setValue(v) {
		this.value = v;
		this._renderValue(v);
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
