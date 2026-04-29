// Tiny shared modal manager. Feature modules call open(modalEl) to show
// their dialog; the dimmer overlay + every .modal-close button auto-close
// every registered modal. Means each feature module doesn't have to wire
// the close affordances itself, and a click outside one modal dismisses
// any other modal that happens to be open too.

const registered = new Set();
let dimmer = null;
let setupDone = false;

function ensureSetup() {
	if (setupDone) return;
	setupDone = true;
	dimmer = document.getElementById("dimmer");
	if (dimmer) dimmer.addEventListener("click", closeAll);
	document.querySelectorAll(".modal-close").forEach((btn) => {
		btn.addEventListener("click", closeAll);
	});
}

export function open(modalEl) {
	ensureSetup();
	registered.add(modalEl);
	if (dimmer) dimmer.classList.add("shown");
	modalEl.classList.add("shown");
}

export function closeAll() {
	if (dimmer) dimmer.classList.remove("shown");
	registered.forEach((m) => m.classList.remove("shown"));
}
