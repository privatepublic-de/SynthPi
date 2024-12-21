document.addEventListener("DOMContentLoaded", () => {
  document.querySelectorAll(".rotary").forEach((element) => {
    new RotaryController(element);
  });
  document.querySelectorAll(".select").forEach((element) => {
    new SelectElement(element);
  });
  document.querySelectorAll(".toggle").forEach((element) => {
    new ToggleElement(element);
  });
  document.querySelectorAll(".fader").forEach((element) => {
    new FaderController(element);
  });
});

const U = {
  radFactor: Math.PI / 180,
  polar2cartesian: (angle, radius) => {
    return { x: radius * Math.cos(angle), y: radius * Math.sin(angle) };
  },
  capInRange: (v, min, max) => {
    return Math.min(Math.max(v, min), max);
  },
};

class PotDragHandler {
  isDragging = false;
  cx = 0;
  cy = 0;
  /** @type {function} */ updateValueCallback = null;
  /** @type {function} */ finishedCallback = null;
  constructor() {
    window.addEventListener("mousemove", this.move.bind(this));
    window.addEventListener("mouseup", this.stopDrag.bind(this));
  }

  valueForCoordinates(x, y) {
    const y0 = y - this.cy;
    const x0 = x - this.cx;
    let ang = parseInt(Math.atan2(y0, x0) * (180 / Math.PI));
    if (ang < 0 && ang >= -90) {
      ang += 225;
    } else if (ang >= 0 && ang < 45) {
      ang += 225;
    } else if (ang < -90) {
      ang += 225;
    } else if (ang > 135) {
      ang -= 135;
    } else {
      if (ang < 90) {
        ang = 270;
      } else {
        ang = 0;
      }
    }
    return ang / 270.0;
  }

  startDrag(
    /** @type {HTMLElement} */ pot,
    /** @type {MouseEvent} */ e,
    /** @type {function} */ updateValueCallback,
    /** @type {function} */ finishedCallback
  ) {
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
    this.cx += RotaryController.width / 2;
    this.cy += RotaryController.height / 2;
    this.isDragging = true;
    this.updateValueCallback(this.valueForCoordinates(e.pageX, e.pageY));
  }

  move(/** @type {MouseEvent} */ e) {
    if (this.isDragging) {
      this.updateValueCallback(this.valueForCoordinates(e.pageX, e.pageY));
    }
  }

  stopDrag(/** @type {MouseEvent} */ e) {
    if (this.isDragging) {
      this.updateValueCallback(this.valueForCoordinates(e.pageX, e.pageY));
      this.isDragging = false;
      this.finishedCallback();
    }
  }
}

class RotaryController {
  static width = 80;
  static height = 80;
  static dragHandler = new PotDragHandler();
  canvas;
  ctx;
  label;
  valueElement;
  color;
  colorDark;
  colorDim;
  value;
  isBipolar;
  isMix;

  constructor(element) {
    this.color = getComputedStyle(element).getPropertyValue("--color");
    this.colorDark = getComputedStyle(element).getPropertyValue("--color-dark");
    this.colorDim = getComputedStyle(element).getPropertyValue("--color-dim");
    const percent = Math.random();
    this.value = parseInt(128 * percent);
    this.isBipolar = element.dataset.bipolar == "";
    this.isMix = element.dataset.mix == "";
    this.canvas = document.createElement("canvas");
    this.canvas.width = RotaryController.width;
    this.canvas.height = RotaryController.height;
    this.ctx = this.canvas.getContext("2d");
    this.label = document.createElement("label");
    this.label.appendChild(document.createTextNode(element.dataset.label));
    this.valueElement = document.createElement("div");
    this.valueElement.classList.add("value");
    element.appendChild(this.label);
    element.appendChild(this.canvas);
    element.appendChild(this.valueElement);
    this._setValue(percent);
    element.addEventListener("mousedown", (e) => {
      RotaryController.dragHandler.startDrag(
        element,
        e,
        (v) => {
          // change value handler
          this._setValue(v);
        },
        () => {
          // end handler
        }
      );
    });
    element.addEventListener("wheel", (e) => {
      e.preventDefault();
      this._setValue(
        U.capInRange(this.value + Math.sign(e.deltaY) / 150, 0, 1)
      );
    });
  }

  _setValue(percent) {
    this.value = percent;
    this._renderValue(percent);
    let displayValue = "";
    if (this.isMix) {
      const intPercent = Math.round(100 * percent);
      displayValue = 100 - intPercent + " : " + intPercent;
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
    const radiusOuter = RotaryController.width / 2 - widthOuter / 2;
    const radiusIndicator = RotaryController.width / 2;
    const radiusArc =
      RotaryController.width / 2 - widthOuter * 2.5 - widthArc / 2;
    const angleStart = (-angleSpan / 2 - 90) * U.radFactor;
    const angleCenter = -90 * U.radFactor;
    const angleEnd = (-angleSpan / 2 - 90 + angleSpan) * U.radFactor;
    const angleValue =
      (-angleSpan / 2 - 90 + angleSpan * valuePercent) * U.radFactor;

    this.ctx.clearRect(0, 0, RotaryController.width, RotaryController.height);
    this.ctx.translate(RotaryController.width / 2, RotaryController.height / 2);

    // outer ring
    this.ctx.lineWidth = widthOuter;
    if (this.isMix) {
      // this.ctx.beginPath();
      // this.ctx.arc(0, 0, radiusOuter, angleStart, angleEnd);
      // this.ctx.strokeStyle = this.colorDark;
      // this.ctx.stroke();
    } else if (this.isBipolar) {
      const isNegative = angleValue < angleCenter;
      if (isNegative) {
        this.ctx.beginPath();
        this.ctx.arc(0, 0, radiusOuter, angleStart, angleValue);
        this.ctx.strokeStyle = this.colorDark;
        this.ctx.stroke();
        this.ctx.beginPath();
        this.ctx.arc(0, 0, radiusOuter, angleValue, angleCenter);
        this.ctx.strokeStyle = this.color;
        this.ctx.stroke();
        this.ctx.beginPath();
        this.ctx.arc(0, 0, radiusOuter, angleCenter, angleEnd);
        this.ctx.strokeStyle = this.colorDark;
        this.ctx.stroke();
      } else {
        this.ctx.beginPath();
        this.ctx.arc(0, 0, radiusOuter, angleStart, angleCenter);
        this.ctx.strokeStyle = this.colorDark;
        this.ctx.stroke();

        this.ctx.beginPath();
        this.ctx.arc(0, 0, radiusOuter, angleCenter, angleValue);
        this.ctx.strokeStyle = this.color;
        this.ctx.stroke();

        this.ctx.beginPath();
        this.ctx.arc(0, 0, radiusOuter, angleValue, angleEnd);
        this.ctx.strokeStyle = this.colorDark;
        this.ctx.stroke();
      }
    } else {
      this.ctx.beginPath();
      this.ctx.arc(0, 0, radiusOuter, angleStart, angleValue);
      this.ctx.strokeStyle = this.color;
      this.ctx.stroke();
      this.ctx.beginPath();
      this.ctx.arc(0, 0, radiusOuter, angleValue, angleEnd);
      this.ctx.strokeStyle = this.colorDark;
      this.ctx.stroke();
    }

    // value ring
    if (this.isMix) {
      this.ctx.beginPath();
      const radius = radiusArc + widthArc / 2 - widthOuter / 2;
      this.ctx.arc(0, 0, radius, angleStart, angleEnd);
      this.ctx.lineWidth = widthArc;
      this.ctx.strokeStyle = this.colorDark;
      this.ctx.stroke();
    } else {
      this.ctx.beginPath();
      if (this.isBipolar) {
        if (angleCenter < angleValue) {
          this.ctx.arc(0, 0, radiusArc, angleCenter, angleValue);
        } else {
          this.ctx.arc(0, 0, radiusArc, angleValue, angleCenter);
        }
      } else {
        this.ctx.arc(0, 0, radiusArc, angleStart, angleValue);
      }
      this.ctx.lineWidth = widthArc;
      this.ctx.strokeStyle = this.color;
      this.ctx.stroke();
    }

    // indicator
    this.ctx.beginPath();
    this.ctx.lineWidth = widthOuter;
    let p = U.polar2cartesian(angleValue, radiusIndicator);
    this.ctx.moveTo(p.x, p.y);
    p = U.polar2cartesian(angleValue, radiusArc - widthArc / 2);
    this.ctx.lineTo(p.x, p.y);
    this.ctx.strokeStyle = this.color;
    this.ctx.stroke();

    // reset transform
    this.ctx.setTransform(1, 0, 0, 1, 0, 0);
  }
}

class SelectElement {
  optionElements;

  constructor(element) {
    let options = element.dataset.list.split(",");
    let selectIndex = parseInt(Math.random() * options.length);
    options.forEach((opt, i) => {
      element.insertAdjacentHTML(
        "beforeend",
        `<div class="option${
          i == selectIndex ? " selected" : ""
        }" data-value="${i}">${opt}</div>`
      );
    });
    this.optionElements = element.querySelectorAll(".option");
    this.optionElements.forEach((optEl) => {
      optEl.addEventListener("click", (ev) => {
        this._setValue(ev.target.dataset.value);
      });
    });
  }

  _setValue(v) {
    this.optionElements.forEach((optEl) => {
      if (optEl.dataset.value == v) {
        optEl.classList.add("selected");
      } else {
        optEl.classList.remove("selected");
      }
    });
  }
}

class ToggleElement {
  value = false;
  element;

  constructor(element) {
    this.element = element;
    this._setValue(Math.random() < 0.5);
    element.addEventListener("click", (ev) => {
      this._setValue(!this.value);
    });
  }

  _setValue(v) {
    this.value = v;
    if (v == true) {
      this.element.classList.add("selected");
    } else {
      this.element.classList.remove("selected");
    }
  }
}

class FaderController {
  static width = 35;
  static height = 155;
  static effectiveHeight = 140;
  color;
  colorDark;
  colorDim;
  canvas;
  ctx;
  valueElement;
  label;
  value;

  constructor(element) {
    this.color = getComputedStyle(element).getPropertyValue("--color");
    this.colorDark = getComputedStyle(element).getPropertyValue("--color-dark");
    this.colorDim = getComputedStyle(element).getPropertyValue("--color-dim");
    const percent = Math.random();
    this.value = parseInt(128 * percent);
    this.canvas = document.createElement("canvas");
    this.canvas.width = FaderController.width;
    this.canvas.height = FaderController.height;
    this.ctx = this.canvas.getContext("2d");
    this.label = document.createElement("label");
    this.label.appendChild(document.createTextNode(element.dataset.label));
    this.valueElement = document.createElement("div");
    this.valueElement.classList.add("value");
    element.appendChild(this.label);
    element.appendChild(this.canvas);
    element.appendChild(this.valueElement);
    this._setValue(percent);
    element.addEventListener("wheel", (e) => {
      e.preventDefault();
      this._setValue(
        U.capInRange(this.value + Math.sign(e.deltaY) / 150, 0, 1)
      );
    });
    let isDragging = false;
    element.addEventListener("mousedown", (e) => {
      isDragging = true;
      e.preventDefault();
      this._setValue(
        U.capInRange(
          (FaderController.effectiveHeight - e.offsetY) /
            FaderController.effectiveHeight,
          0,
          1
        )
      );
    });
    element.addEventListener("mousemove", (e) => {
      if (isDragging && e.target != this.label) {
        console.log(
          (FaderController.effectiveHeight - e.offsetY) /
            FaderController.effectiveHeight,
          e.offsetY,
          e.target
        );
        this._setValue(
          U.capInRange(
            (FaderController.effectiveHeight - e.offsetY) /
              FaderController.effectiveHeight,
            0,
            1
          )
        );
      }
    });
    element.addEventListener("mouseup", (e) => {
      isDragging = false;
    });
    element.addEventListener("mouseleave", (e) => {
      isDragging = false;
    });
  }

  _setValue(v) {
    this.value = v;
    this._renderValue(v);
    this.valueElement.innerHTML = Math.round(127 * v);
  }

  _renderValue(percent) {
    this.ctx.clearRect(0, 0, FaderController.width, FaderController.height);
    this.ctx.beginPath();
    this.ctx.lineWidth = 1;
    this.ctx.moveTo(0, FaderController.effectiveHeight);
    this.ctx.lineTo(0, 0);
    this.ctx.strokeStyle = this.colorDark;
    this.ctx.stroke();
    this.ctx.lineWidth = 1;
    this.ctx.moveTo(
      0,
      FaderController.effectiveHeight -
        percent * FaderController.effectiveHeight
    );
    this.ctx.lineTo(
      FaderController.width,
      FaderController.effectiveHeight -
        percent * FaderController.effectiveHeight
    );
    this.ctx.stroke();
    this.ctx.beginPath();
    this.ctx.lineWidth = 20;
    this.ctx.moveTo(FaderController.width / 2, FaderController.effectiveHeight);
    this.ctx.lineTo(
      FaderController.width / 2,
      FaderController.effectiveHeight -
        percent * FaderController.effectiveHeight
    );
    this.ctx.strokeStyle = this.color;
    this.ctx.stroke();
  }
}
