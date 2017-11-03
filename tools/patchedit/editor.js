"use strict";

/**
 * DOM utilities
 */
let DOM = {
    ready: function(handler) {
        window.document.addEventListener("DOMContentLoaded", handler);
    },
    element: function(selector) {
        if (typeof selector==='string') {
            return document.querySelector(selector);
        }
        else {
            return selector;
        }
    },
    find: function(rootElement, selector, handler) {
        let list = rootElement.querySelectorAll(selector);
        if (handler) {
            for (let i=0;i<list.length;i++) {
                handler(list[i]);
            }
        }
        return list;
    },
    all: function(selector, handler) {
        let list = [];
        if (selector) {
            if (typeof selector==='string') {
                list = document.querySelectorAll(selector);
            }
            else {
                list = [selector];
            }
        }
        if (handler) {
            for (let i=0;i<list.length;i++) {
                handler(list[i]);
            }
        }
        return list;
    },
    on: function(selector, eventName, handler) {
        DOM.all(selector, function(el){ el.addEventListener(eventName, handler) });
    },
    attachInside: function(rootElement, selector, eventName, handler) {
        DOM.find(rootElement, selector, function(el) {
            DOM.on(el, eventName, handler);
        });
    },
    empty: function(selector) {
        DOM.all(selector, function(el){ el.innerHTML='' });
    },
    hide: function(selector) {
        DOM.all(selector, function(el){ el.style.display = 'none' });
    },
    show: function(selector) {
        DOM.all(selector, function(el){ el.style.display = 'block' });
    },
    addClass: function(selector, className) {
        DOM.all(selector, function(el){ el.classList.add(className) });
    },
    removeClass: function(selector, className) {
        if (className) {
            DOM.all(selector, function(el){ el.classList.remove(className) });
        }
        else {
            DOM.all(selector, function(el){ el.className ='' });
        }
    },
    addHTML: function(selector, position, html) { // 'beforebegin', 'afterbegin', 'beforeend', 'afterend'
        let element = DOM.element(selector);
        element.insertAdjacentHTML(position, html);
        return element;
    }
}

var CATs = ['POLY', 'PAD', 'KEYS', 'LEAD', 'BASS', 'FX', 'PERC', 'MISC'];
var lists = { 'source': [], 'target': []};

DOM.ready(function() {
    DOM.on('textarea.in', 'focus', function() {
        console.log("Focused",this);
        this.value = '';
        var label = DOM.element('label[for="'+this.id+'"]');
        label.innerHTML = '&nbsp';
    });
    DOM.on('textarea#output', 'focus', function() {
        this.select();
    });
    DOM.on('textarea', 'keyup', function() {
        console.log("Changed",this);
        if (this.value.length>0) {
            var label = DOM.element('label[for="'+this.id+'"]');
            try {
                var data = JSON.parse(this.value);
                sortPatchList(data);
                label.innerHTML = '&nbsp;';
                renderPatchList(data, DOM.element('*[data-ref="'+this.id+'"]'));
                lists[this.id] = data;
            }
            catch(e) {
                console.log(e);
                label.innerHTML = 'Error parsing data!';
            }
        }
    });
});



function sortPatchList(data) {
    data.sort(function(a,b) {
        var ac = CATs.indexOf(a.category);
        var bc = CATs.indexOf(b.category);
		if (ac<bc)
			return -1;
		if (ac>bc)
			return 1;
		if (a.name<b.name)
			return -1;
		if (a.name>b.name)
			return 1;
		return 0;
    });
}

function renderAll() {
    sortPatchList(lists['target']);
    renderPatchList(lists.target, DOM.element('*[data-ref="target"]'));
    sortPatchList(lists['source']);
    renderPatchList(lists.source, DOM.element('*[data-ref="source"]'));
    var output = JSON.parse(JSON.stringify(lists.target)); // clone
    for (var i=0;i<output.length;i++) {
        delete output[i]['hints'];
    }
    DOM.element('#output').value = JSON.stringify(output);
}

function addHint(obj, hint) {
    if (!Array.isArray(obj.hints)) {
        obj.hints = [];
    }
    obj.hints.push(hint);
}

function renderPatchList(data, container) {
    var type = container.getAttribute('data-ref');
    DOM.empty(container);
    for (var i=0;i<data.length;i++) {
        var p = data[i];
        var date = new Date(p.date);
        var dateS = date.toISOString();
        var toolbar = type==='source'?'<div class="toolbar" data-index="'+i+'"><a class="action" data-action="totarget">to target &rarr;</a></div>':'<div class="toolbar" data-index="'+i+'"><a class="action" data-action="remove">&larr; remove</a><a class="action" data-action="edit">edit</a></div>'
        var hints = '';
        if (Array.isArray(p.hints)) {
            for (var h=0;h<p.hints.length;h++) {
                if (h>0) hints+=', ';
                hints += p.hints[h];
            }
            hints = '<div class="hints">'+hints+'</div>';
        }
        DOM.addHTML(container, 'beforeend', '<div data-index="'+i+'" class="item">'+p.name+'<span class="cat '+p.category+'" title="Date: '+dateS+'">'+p.category+'</span>'+toolbar+hints+'</div>');
    }
    DOM.on('*[data-ref="'+type+'"] a.action', 'click', function(e) {
        var index = this.parentElement.getAttribute('data-index');
        var action = this.getAttribute('data-action');
        var item = this.parentElement.parentElement;
        switch(action) {
            case 'totarget':
                var totarget = lists['source'].splice(index, 1);
                addHint(totarget[0], 'from source');
                lists['target'].push(totarget[0]);
                renderAll();
            break;
            case 'remove':
                var tosource = lists['target'].splice(index, 1);
                addHint(tosource[0], 'from target');
                lists['source'].push(tosource[0]);
                renderAll();
            break;
            case 'edit':
                var p = lists.target[index];
                DOM.addClass(item, 'edit');
                DOM.addClass(item.parentElement, 'edit');

                var selectCatHtml = '<select name="category">';
                for (var j=0;j<CATs.length;j++) { 
                    selectCatHtml += '<option'+(p.category===CATs[j]?' selected':'')+'>'+CATs[j]+'</option>';
                }
                selectCatHtml+='</select>';

                DOM.addHTML(item, 'beforeend', '<div><input name="name" type="text" value="'+p.name+'"/> '+selectCatHtml+' <input type="submit" value="OK"/></div>');

                DOM.find(item, 'input[type="submit"]', function(el) {
                    DOM.on(el, 'click', function() {
                        var name = DOM.find(item, 'input[name="name"]')[0].value;
                        var cat = DOM.find(item, 'select[name="category"]')[0].value;
                        lists.target[index].name = name;
                        lists.target[index].category = cat;
                        lists.target[index].date = Date.now();
                        addHint(lists.target[index], 'edited');
                        DOM.removeClass(item, 'edit');
                        DOM.removeClass(item.parentElement, 'edit');
                        renderAll();
                    });
                });
            break;
        }
    });
}


