// Patch load / save / init / randomize.
//
// Wires the four buttons in the status bar to commands, then handles the
// /patchlist and /saveinfo JSON responses by populating the matching modal
// dialog. Modals are simple display:none/block + dimmer overlay; no fade
// animations — keeps the JS small and the visual transition adequate.

import { socket } from "./socket.js";

class Patches {
	constructor() {
		this.dimmer = document.getElementById("dimmer");
		this.loadWindow = document.getElementById("patchlistwindow");
		this.saveWindow = document.getElementById("savepatchwindow");
		this.patchNameDisplay = document.getElementById("patch-name");
		// Confirm-once-per-session guard for randomize, mirroring the old UI:
		// the first click prompts; subsequent clicks fire without confirmation.
		this.warnRandomize = true;

		this._wireToolbar();
		this._wireServerListeners();

		// Click outside any open modal closes everything.
		this.dimmer.addEventListener("click", () => this.closeAll());
		// Modal close buttons.
		document.querySelectorAll(".modal-close").forEach((btn) => {
			btn.addEventListener("click", () => this.closeAll());
		});
	}

	_wireToolbar() {
		const onClick = (id, fn) => {
			const el = document.getElementById(id);
			if (el) el.addEventListener("click", fn);
		};
		onClick("btn-load", () => socket.send("/command/listpatches", 1));
		onClick("btn-save", () => socket.send("/command/getsaveinfo", 1));
		onClick("btn-init", () => {
			if (confirm("Initialize patch (start from scratch)?")) {
				socket.send("/command/initpatch", 1);
			}
		});
		onClick("btn-random", () => {
			if (this.warnRandomize) {
				if (!confirm("Randomize all parameters?")) return;
				this.warnRandomize = false;
			}
			socket.send("/command/randomize", 1);
		});
	}

	_wireServerListeners() {
		// Prefix strings here must NOT include the trailing "=" — socket.js
		// splits on "=" first, so subscribers see the path without it.
		socket.onCommand("/patchlist", (path, value) => {
			try {
				this._renderPatchList(JSON.parse(value));
			} catch (e) {
				console.error("malformed /patchlist payload", e);
			}
		});
		socket.onCommand("/saveinfo", (path, value) => {
			try {
				this._renderSaveInfo(JSON.parse(value));
			} catch (e) {
				console.error("malformed /saveinfo payload", e);
			}
		});
		// /patch/name carries a string, not a number — handle as command.
		socket.onCommand("/patch/name", (path, value) => {
			if (this.patchNameDisplay) this.patchNameDisplay.textContent = value;
		});
		// Server emits /pagepatch=… after a load/init/randomize completes; close
		// any open modal so the new sound is immediately audible without an
		// extra dismiss.
		socket.onCommand("/pagepatch", () => this.closeAll());
	}

	closeAll() {
		this.dimmer.classList.remove("shown");
		this.loadWindow.classList.remove("shown");
		this.saveWindow.classList.remove("shown");
	}

	_openModal(modal) {
		this.dimmer.classList.add("shown");
		modal.classList.add("shown");
	}

	_renderPatchList(data) {
		const list = this.loadWindow.querySelector(".listcontent");
		list.textContent = "";
		this._appendHeadline(list, "Your Patches");
		this._appendPatchEntries(list, data.user, /*loadAndClose=*/true);
		this._appendHeadline(list, "Factory Presets");
		// INIT button — sits between the heading and the factory entries.
		const initWrap = document.createElement("div");
		const initLink = document.createElement("a");
		initLink.href = "#";
		initLink.className = "patch-entry init";
		initLink.textContent = "INIT (start from scratch)";
		initLink.addEventListener("click", (ev) => {
			ev.preventDefault();
			socket.send("/command/initpatch", 1);
			this.closeAll();
		});
		initWrap.appendChild(initLink);
		list.appendChild(initWrap);
		this._appendPatchEntries(list, data.factory, /*loadAndClose=*/true);
		this._openModal(this.loadWindow);
	}

	_renderSaveInfo(info) {
		const catSel = document.getElementById("savecategory");
		catSel.textContent = "";
		for (const cat of info.categories || []) {
			const opt = document.createElement("option");
			opt.value = cat;
			opt.textContent = cat;
			if (cat === info.selectedcategory) opt.selected = true;
			catSel.appendChild(opt);
		}
		// Pre-fill name input from current patch name display.
		const nameInput = document.getElementById("savename");
		nameInput.value = this.patchNameDisplay ? this.patchNameDisplay.textContent : "";

		// Populate "overwrite existing" list. Each entry has an overwrite link
		// (click to save into that slot) + a separate delete button.
		const existing = this.saveWindow.querySelector(".existing-patches");
		existing.textContent = "";
		for (const v of info.existingPatches || []) {
			const wrap = document.createElement("div");
			wrap.className = `patch-row ${v.category}`;
			const overwrite = document.createElement("a");
			overwrite.href = "#";
			overwrite.className = "patch-entry overwrite";
			overwrite.innerHTML = `<span class="category">${v.category}</span>${escapeHtml(v.name)}`;
			overwrite.addEventListener("click", (ev) => {
				ev.preventDefault();
				this._submitSave(v.id, v.name);
			});
			const del = document.createElement("a");
			del.href = "#";
			del.className = "patch-delete";
			del.textContent = "✕";
			del.title = `Delete patch ${v.name}`;
			del.addEventListener("click", (ev) => {
				ev.preventDefault();
				ev.stopPropagation();
				if (confirm(`Really delete patch\n"${v.name}"?`)) {
					socket.send("/command/savepatch", JSON.stringify({action: "delete", overwrite: v.id}));
					this.closeAll();
				}
			});
			wrap.appendChild(overwrite);
			wrap.appendChild(del);
			existing.appendChild(wrap);
		}

		// "Save as New" button. Re-bind on every render since the closure
		// captures fresh state (and to avoid handler accumulation).
		const saveBtn = document.getElementById("savebutton");
		saveBtn.onclick = (ev) => {
			ev.preventDefault();
			this._submitSave(undefined, undefined);
		};

		this._openModal(this.saveWindow);
		nameInput.focus();
		nameInput.select();
	}

	_appendHeadline(parent, text) {
		const h = document.createElement("div");
		h.className = "headline";
		h.textContent = text;
		parent.appendChild(h);
	}

	_appendPatchEntries(parent, patches, loadAndClose) {
		if (!patches || patches.length === 0) {
			const empty = document.createElement("div");
			empty.className = "empty";
			empty.innerHTML = "<em>(empty)</em>";
			parent.appendChild(empty);
			return;
		}
		let lastCat = null;
		for (const item of patches) {
			if (lastCat !== null && item.category !== lastCat) {
				const sep = document.createElement("div");
				sep.className = "patch-sep";
				parent.appendChild(sep);
			}
			const wrap = document.createElement("div");
			wrap.className = `patch-row ${item.category}`;
			const link = document.createElement("a");
			link.href = "#";
			link.className = "patch-entry";
			link.innerHTML = `<span class="category">${item.category}</span>${escapeHtml(item.name)}`;
			link.addEventListener("click", (ev) => {
				ev.preventDefault();
				socket.send(`/command/loadpatch/${item.id}`, 1);
				if (loadAndClose) this.closeAll();
			});
			wrap.appendChild(link);
			parent.appendChild(wrap);
			lastCat = item.category;
		}
	}

	_submitSave(overwriteId, overwriteName) {
		const name = document.getElementById("savename").value.trim();
		if (!name) return;
		const category = document.getElementById("savecategory").value;
		const message = overwriteId
			? `Overwrite "${overwriteName}" with\n"${name}" (${category})?`
			: `Save new patch\n"${name}" (${category})?`;
		if (!confirm(message)) return;
		socket.send("/command/savepatch", JSON.stringify({
			action: "save",
			name: name,
			category: category,
			overwrite: overwriteId,
		}));
		this.closeAll();
	}
}

// Tiny HTML-escape; patch names are user-supplied so we can't trust them in
// innerHTML. Avoids pulling in a templating dependency for a single escape.
function escapeHtml(s) {
	return String(s)
		.replace(/&/g, "&amp;")
		.replace(/</g, "&lt;")
		.replace(/>/g, "&gt;")
		.replace(/"/g, "&quot;");
}

export function initPatches() {
	return new Patches();
}
