"use strict";
$(document).ready(function () {
	var warnRandomize = true;
	var dontreconnect = false;
	var observers = [];
	var socketHandler = function() {
		// Create new WebSocket
		this.wsurl = "ws://"+window.location.host+"/oscsocket";
		this.reconnectTimer = 0;
	};
	socketHandler.prototype = {
			createAndConnect: function() {
				if (this.reconnectTimer) {
					clearTimeout(this.reconnectTimer);
					this.reconectTimer = 0;
				}
				this.mySocket = new WebSocket(this.wsurl);
				// Attach listeners
				var datalitetimer = 0;
				this.mySocket.onmessage = function(event) {
					//$("#datalight").stop().css("opacity", 1).animate({"opacity":0},300);
					$("#datalight").css('visibility','visible');
					if (datalitetimer) {
						clearTimeout(datalitetimer);
					}
					datalitetimer = setTimeout(function() {$("#datalight").css('visibility','hidden')}, 300);
					screensaverOff();
					this.dispatchMessage(event.data);
				}.bind(this);
				this.mySocket.onopen = function(event) {
					console.log("Socket opened "+event);
					this.setStatus(true);
					let self = this;
					this.pingInterval = setInterval(function(){ self.sendValueDirectly('*','*')},15000);
				}.bind(this);
				this.mySocket.onclose = function(event) {
					console.log("Socket closed ", event);
					clearInterval(this.pingInterval);
					this.setStatus(false);
				}.bind(this);
				this.mySocket.onerror = function(event) {
					console.log("Socket exception ", event);
				}.bind(this);		
			},
			setStatus: function(connected) {
				if (connected) {
					$('body').removeClass("connecting");
					$('body').addClass("connected");
				}
				else {
					if (!dontreconnect) {
						$('body').addClass("connecting");
						$('body').removeClass("connected");
						if (this.reconnectTimer) {
							clearTimeout(this.reconnectTimer);
							this.reconectTimer = 0;
						}
						this.reconnectTimer = setTimeout(function() {
							console.log("Reconnecting ...");
							this.createAndConnect()
						}.bind(this), 5000);
					}
				}
			},
			dispatchMessage: function(data) {
				var parts, elmts, el, tagname, val;
				var lines = data.split("\n");
//				console.log("Received "+lines.length+" lines");
				for (var i=0; i<lines.length; i++) {
					if (lines[i]=="/pagepatch") {
						$(".window").fadeOut();
						continue;
					}
					parts = lines[i].split("=",2);
					if (parts.length==2) {
						if (parts[0]=="/paramselected") {
							clearTimeout(window.selectedtimer);
							$("#main *").removeClass("paramselected");
							$("*[data-control-path='"+parts[1]+"']").addClass("paramselected");
							$(".checkbox[data-osc='"+parts[1]+"']").addClass("paramselected");
							window.selectedtimer = setTimeout(function() {$("#main *").removeClass("paramselected");}, 8000);
							continue;
						}
						if (parts[0]=="/label/limiter/reduction") {
							var value = parseFloat(parts[1])-1;
							renderLimiter(value);
							continue;
						}
						if (parts[0].indexOf("/play/note/")==0) {
							if (parts[1]=='1') { 
								$("*[data-osc='"+parts[0]+"']").addClass("pressed");
							}
							else {
								$("*[data-osc='"+parts[0]+"']").removeClass("pressed");
							}
							continue;
						}
						if (parts[0]=="/config/performancedata") {
							if (parts[1]=='true') { 
								$(document.body).addClass("performancedata");
							}
							else {
								$(document.body).removeClass("performancedata");
							}
							continue;
						}
						if (parts[0]=="/settings") {
							renderSettings(parts[1]);
						}
						if (parts[0]=="/patchlist") {
							// list patches
							var patchlists = jQuery.parseJSON(parts[1]);
							renderPatchList(patchlists);
							continue;
						}
						if (parts[0]=="/saveinfo") {
							// list save slots
							var info = jQuery.parseJSON(parts[1]);
							renderSaveWindow(info);
							continue;
						}
						elmts = $("*[data-osc='"+parts[0]+"']");
						elmts.each(function(idx) {
							el = $(this);
							tagname = el.prop("tagName");
							val = parts[1];
							if (tagname=="LABEL") {
								el.text(val);
							}
							if (tagname=="INPUT") {
								if (el.attr("data-cursor")) {
									val = val*200-100;
								}
								else {
									val = val*200;
								}
								el.data("remoteupdate", true);
								el.val(parseInt(val)).trigger('change');
								el.data("remoteupdate", false);
							}
							if (tagname=="SPAN" && el.hasClass("checkbox")) {
								if (val>0) {
									if (el.parents(".exclusive, .exclusiveunstyled").length>0) {
										// uncheck others
										var checked = el.parents(".exclusive, .exclusiveunstyled").find(".checked").removeClass("checked");
									}
									el.addClass("checked");
								}
								else {
									el.removeClass("checked");
								}
							}
						});
						updateObservers(parts[0],parts[1]);
					}
				}
			},
			sendValue: function(el, val) {
				var path = el.attr("data-osc");
				if (path) {
					var tagname = el.prop("tagName");
					if (tagname=="INPUT") {
						if (el.attr("data-cursor")) {
							val = (val+100)/200.0;
						}
						else {
							val = val/200.0;
						}
					}
					else if (tagname=="SPAN" && el.hasClass("checkbox")) {
						var oscsend = el.attr("data-osc-send");
						if (oscsend) {
							if (!el.hasClass("checked")) {
								return; // nothing to send
							}
							var pts = oscsend.split(",");
							path = pts[0];
							val = pts[1];
						}
						else {
							val = el.hasClass("checked")?1:0;
						}
					}
					if (val==1 && warnRandomize && path=="/command/randomize") {
						warnRandomize = !confirm("Randomize patch? Your current sound settings will be lost!");
						if (warnRandomize) {
							return;
						}
					}
					if (val==1 && path=="/command/shutdown") {
						if (!confirm("Shutdown SynthPi?\n\nReally?")) {
							return;
						}
					}
					// console.log("Sending for "+tagname+" path "+path+" val "+val);
					this.mySocket.send(path+"="+val);
					updateObservers(path,val);
				}
			},
			sendValueDirectly: function(path, val) {
				this.mySocket.send(path+"="+val);
			}
	};
	
	var socket = new socketHandler();
	
	var dimmer = {
			show: function() {
				$("#dimmer").fadeIn();
				$(document.body).css('top', -(document.documentElement.scrollTop) + 'px').addClass("noscroll");	
			},
			hide: function() {
				$("#dimmer").fadeOut();
				$(document.body).removeClass("noscroll");	
			}
	};

	var pushHandler = function(index) {
		var el = $(this);
		el.on("mousedown", {element: el}, function(ev) {
			socket.sendValue(ev.data.element, 1);
			el.addClass("pressed");
		});
		el.on("mouseup mouseleave", {element: el}, function(ev) {
			if (el.hasClass("pressed")) {
				socket.sendValue(ev.data.element, 0);
				el.removeClass("pressed");
			}
		});
		el.click(function(ev){
			ev.preventDefault();
		});
	};
	var renderPatchList = function(lists) {
		var addEntry = function(item, element) {
			element.append("<div class='"+item.category+"'><a href='#' class='push' data-osc='/command/loadpatch/"+item.id+"'><span class='category'>"+item.category+"</span>"+item.name+"</a></div>");
		}
		var columnsOut = function(list, element) {
			if (list && list.length>0) {
				var lastCat = list[0].category;
				for (var i=0;i<list.length;i++) {
					if (list[i].category!=lastCat) {
						element.append("<br/>");
					}
					addEntry(list[i], element)
					lastCat = list[i].category;
				}
			}
			else {
				element.append("<div><em>(empty)</em></div><div></div>");	
			}
		}
		var elist = $("#loadpatchlist .listcontent");
		elist.empty();
		elist.append("<div class='headline'>Your Patches</div>");
		columnsOut(lists.user, elist);
		elist.append("<div class='headline'>Factory Presets</div>");
		elist.append("<div><a href='#' class='push' data-osc='/command/initpatch' style='text-align:center'>INIT (start from scratch)</a></div><br/>");
		columnsOut(lists.factory, elist);
		dimmer.show();
		$("#patchlistwindow").fadeIn();
		$("#patchlistwindow .windowtitle").click(function(ev){
			$("#patchlistwindow").fadeOut();
			dimmer.hide();
		});
		$("#loadpatchlist a.push").each(pushHandler);
	}
	
	var renderSaveWindow = function(info) {
		var catselect = $("#pcategories");
		catselect.empty();
		$.each(info.categories, function(ix, v) {
			catselect.append("<option value='"+v+"' "+(v==info.selectedcategory?"selected":"")+">"+v+"</option>");
		});
		
		var existing = $("#existingpatches .listcontent");
		existing.empty();
		if (info.existingPatches) {
			$.each(info.existingPatches, function(ix, v) {
				existing.append("<div class='"+v.category+"'><a href='#' class='push overwrite' data-overwrite-id='"+v.id+"' data-overwrite-name='"+v.name+"'><span class='category'>"+v.category+"</span>"+v.name+"</a><a href='#' class='iconbutton deletepatch' data-delete-id='"+v.id+"' data-delete-name='"+v.name+" title='Delete this patch'></a></div>");
			});
		}
		dimmer.show();		
		$("#savepatchwindow").fadeIn();
		$("#savepatchwindow .btnclose").unbind().click(function(ev){
			$("#savepatchwindow").fadeOut();
			dimmer.hide();
		});
		$("#savebutton, .overwrite").unbind().click('click', function(ev) {
			var name = $("#savename").val();
			var type = $("#pcategories").val();
			var overwriteId = $(ev.target).attr("data-overwrite-id");
			var overwriteName = $(ev.target).attr("data-overwrite-name");
			var message;
			if (overwriteId!=undefined) {
				message = "Overwrite existing patch \""+overwriteName+"\" with\n\n \""+name+" ("+type+")\"?";
			}
			else {
				message = "Save new patch\n\n"+name+" ("+type+")?";
			}
			if (confirm(message)) {
				socket.sendValueDirectly("/command/savepatch", JSON.stringify({action:"save", name:name, category:type, overwrite: overwriteId}));
				$("#savepatchwindow").fadeOut();
				dimmer.hide();
			}
		});
		$(".deletepatch").click('click', function(ev) {
			var deleteid = $(ev.target).attr("data-delete-id");
			var deleteName = $(ev.target).attr("data-delete-name");
			if (confirm("Really delete patch\n\n"+deleteName+"?")) {
				$("#savepatchwindow").hide();
				dimmer.hide();
				socket.sendValueDirectly("/command/savepatch", JSON.stringify({action:"delete", overwrite: deleteid}));
//					socket.sendValueDirectly("/command/getsaveinfo","1");
			}
		});
		$("#savename").focus();
		$("#savename").val($("*[data-osc='/patch/name']").first().text());
	}
	
	var renderSettings = function(settingsjson) {
		var settings = jQuery.parseJSON(settingsjson);
		dimmer.show();		
		$("#settings").fadeIn();
		$("#settings .btnclose").unbind().click(function(ev){
			$("#settings").fadeOut();
			dimmer.hide();
		});
		$("#set_midichannel").val(settings.midichannel);
		$("#set_pitchbendrange").val(settings.pitchbendrange);
		$("#set_pitchbendfix").prop("checked",settings.pitchbendfix);
		$("#set_liveperformance").prop("checked",settings.transferperformancedata);
		$("#set_limiter").prop("checked",settings.limiterenabled);
		$("#set_polyphonyvoices").val(settings.polyphonyvoices);
		$("#set_buffersize").val(settings.audiobuffersize);
		$("#savesettings").unbind().click(function() {
			settings.midichannel = parseInt($("#set_midichannel").val());
			settings.pitchbendrange = parseInt($("#set_pitchbendrange").val());
			settings.pitchbendfix = $("#set_pitchbendfix").prop("checked");
			settings.polyphonyvoices = parseInt($("#set_polyphonyvoices").val());
			settings.audiobuffersize = parseInt($("#set_buffersize").val());
			settings.transferperformancedata = $("#set_liveperformance").prop("checked");
			settings.limiterenabled = $("#set_limiter").prop("checked");
			socket.sendValueDirectly("/command/settings/save", JSON.stringify(settings));
			$("#settings").fadeOut();
			dimmer.hide();
		});
	}

	jQuery.fn.cleanWhitespace = function() {
		var textnodes = this.contents().filter(
				function() { return (this.nodeType == 3 && !/\S/.test(this.nodeValue)); })
				.remove();
		return this;
	}
	
	var updateObservers = function(path, value) {
		var obs;
		for (var i=0; i<observers.length; i++) {
			obs = observers[i];
			if (path.indexOf(obs.path)==0) {
				if (obs.element) {
					obs.element.attr("data-osc-value", value);
					obs.element.attr("data-osc-path", path);
					if (value>0) {
						obs.element.addClass("checked");
					}
					else {
						obs.element.removeClass("checked");
					}
				}
				if (obs.fn) {
					obs.fn(path, value);
				}
			}
		}
	};
	
	observers.push({ path:'/command/sequence/', fn: function(path, value) {
		if (value!=1) { return };
		if (path.indexOf('stop')>-1) {
			$('#stopbutton').addClass("disabled");
		}
		else {
			$('#stopbutton').removeClass("disabled");
		}
	}});

	var id = 0;
	$(".dial").each(function(index){
		var el = $(this);
		var hilitenon0 = el.closest('.highlightnonzero').length;
		el.attr("disabled","disabled");
		var cclass = (el.hasClass("large")?"large":el.hasClass("small")?"small":"medium");
		var container = $(document.createElement("div"));
		container.addClass("container "+cclass);
		container.attr("id","kn"+id);
		container.attr("data-control-path", el.attr("data-osc"));
		var fgcolor = el.css("color");
		if (!el.parents().hasClass("perfcontrols") && !el.parents().hasClass("gray")) {  
			var bgcolor = $.Color(el, "color").lightness(0.2).saturation(0.3).toHexString();	
		}
		else {
			var bgcolor = $.Color(el, "color").lightness(0.2).saturation(0.0).toHexString();
		}
		var markercolor = fgcolor; //$.Color(el, "color").lightness(0.5).toHexString();
		var scalecolor = $.Color(el, "color").lightness(0.1).toHexString();
		var hilitecolor = "#7F337F";
		var width = el.width();
		var cursor = el.attr("data-cursor");
		var labelpath = el.attr("data-osc-label");
		el.wrap(container);
		el.knob({
			'min': cursor?-100:0, 
			'max': cursor?100:200, 
			'cursor': cursor?12:0,
			'step': 1,
			'angleArc': 240, 
			'angleOffset': -120,
			'width': width,
			'fgColor': fgcolor, 
			'inputColor': 'inherit', 
			'bgColor': bgcolor,
			'fontWeight': 'normal',
			'displayInput': labelpath?false:true,
			'release': function(v) {
				if (el.data("remoteupdate")!=true) {
					socket.sendValue(el, v);
				} 
			},
			'change': function(v) {
				if (el.data("remoteupdate")!=true) {
					socket.sendValue(el, v);
				} 
			},
			'draw': function() {
				var mcol = markercolor;
				if (hilitenon0 && this.v) {
					this.o.bgColor = hilitecolor;
					mcol = fgcolor;
				}
				else {
					this.o.bgColor = bgcolor;
				}
				this.draw();
				var ctx = this.c;
				var cx = ctx.canvas.width/2;
				var steps = 10;
				var angleStep = (this.endAngle-this.startAngle)/steps;
				ctx.strokeStyle = scalecolor;
				var r1 = this.radius;
				var r2 = this.radius + this.lineWidth/2;
				for (var i=0;i<steps+1;i++) {
					if (cursor && i==5) continue;
					if (i==2 || i==5 || i==8) {
						ctx.lineWidth = 3;						
					}
					else {
						ctx.lineWidth = 1;
					}
					ctx.beginPath();
					ctx.moveTo(cx+Math.cos(this.startAngle+angleStep*i)*r1, cx+Math.sin(this.startAngle+angleStep*i)*r1);
					ctx.lineTo(cx+Math.cos(this.startAngle+angleStep*i)*r2, cx+Math.sin(this.startAngle+angleStep*i)*r2);
					ctx.closePath();
					ctx.stroke();
				}
				if (cursor) {
					ctx.strokeStyle = mcol;
					ctx.beginPath();
					ctx.moveTo(cx, 0);
					ctx.lineTo(cx, this.lineWidth*2);
					ctx.closePath();
					ctx.stroke();
				}
				return false;
			}
		});
		$("#kn"+id).append("<label class='static'>"+el.attr("title")+"</label>");
		if (labelpath) {
			$("#kn"+id).append("<label class='dynamic' data-osc='"+labelpath+"'>...</label>");
		}
		$("#kn"+id+" label.static").click(function() {
			socket.sendValue(el, 0);
			el.val(0).trigger("change");
		});
		id++;
	});
	
	$(".fader").each(function() {
		var el = $(this);
		var bipolar = el.data('cursor')==10;
		var labelpath = el.attr("data-osc-label");
		var hilitenon0 = el.closest('.highlightnonzero').length;
		el.attr("type","hidden");
		var labelspan = labelpath?'<label data-osc="'+labelpath+'">...</label>':"<label>...</label>";
		el.wrap('<div class="fcontainer"></div>').after('<div class="scale"><div class="handle inner">'+labelspan+'</div></div>').after('<label>'+el.attr("title")+'</label>');
		var dispval = el.parent().find('.handle label');
		var scale = el.parent().find('.scale');
		if (bipolar) {
			scale.prepend('<div class="middle"></div>');
		}
		var handle = el.parent().find('.handle');
		var hheight = handle.outerHeight();
		var height = scale.height()-hheight;
		var updatePos = function(val) {
			val = parseFloat(val);
			var y;
			if (bipolar) {
				y = height - height*((val+100)/200.0);
			}
			else {
				y = height - height*(val/200.0);
			}
			handle.css('top', y)
		};
		var updateY = function(y) {
			var val = Math.round(200 - (y/height)*200);
			if (bipolar && val<-40) {
				val = 100;
			}
			if (val<0) val=0;
			if (val>200) val=200;
			if (bipolar) {
				val -= 100;
			}
			el.val(val);
			el.trigger('change');
		}
		el.on('change', function() {
			var v = el.val();
			if (!labelpath) {
				dispval.text(v);
			}
			if (el.data("remoteupdate")!=true) {
				v = parseFloat(v);
				socket.sendValue(el, v);
			}
			if (hilitenon0) {
				if (v!=0) {
					scale.addClass('nonzero');
				}
				else {
					scale.removeClass('nonzero');
				}
			}
			updatePos(v);
		});
		let isDragging = false;
        scale.on('mousedown touchstart', function (ev) {
        	ev.preventDefault();
        	var pageY = ev.type==='mousedown'?ev.pageY:ev.originalEvent.touches[0].pageY;
            var clickedY = pageY - scale.offset().top - hheight/2;
            isDragging = true;
            handle.addClass("dragging");
            updateY(clickedY);
        });
        $(window).on('mousemove touchmove', function (ev) {
            if (isDragging) {
            	// ev.preventDefault();
            	var pageY = ev.type==='mousemove'?ev.pageY:ev.originalEvent.touches[0].pageY;
                var currentY = pageY - scale.offset().top - hheight/2;
                updateY(currentY);
            }
        })
            .on('mouseup touchend', function (ev) {
                if (isDragging) {
                    ev.preventDefault();
                    isDragging = false;
                    handle.removeClass("dragging");
                }
            });
		
		
		
		
		id++;
	});

	$(".box-medium-2x2").cleanWhitespace();
	$("#keyboard .keys").cleanWhitespace();
	$(".checkbox").each(function(index) {
		var el = $(this);
		var exclusive = el.parent().hasClass("exclusive");
		var exclusiveunstyled = el.parent().hasClass("exclusiveunstyled");
		var clickfn = function() {
			if (exclusive || exclusiveunstyled) {
				var waschecked = el.hasClass("checked");
				if (waschecked) {
					// do nothing
					return;
				}
				else {
					// uncheck others
					var checked = el.parents(".exclusive, .exclusiveunstyled").find(".checked");
					$(checked).each(function(index2) {
						$(this).removeClass("checked");
						socket.sendValue($(this));
					});
				}
			}
			el.toggleClass("checked");
			socket.sendValue(el);
		}; 
		el.click(clickfn);
		el.append("<span class='inner'></span>");
		if (exclusive) {
			el.wrap("<div class='exclusive-container'></div>");
			el.after("<br />"+el.attr("title"));
		}
		else {
			if (el.parents(".labelbelow").length>0) {
				el.after("<br /><label>"+el.attr("title")+"</label>");
			}
			else {
				el.after("<label>"+el.attr("title")+"</label>");
				// el.after("<label style='color:"+col+"'>"+el.attr("title")+"</label>");
				el.next().click(clickfn);
			}
		}

	});
	$(".wk, .bk").each(function(no) {
		var el = $(this).attr("data-osc","/play/note/"+(36+no));
	});
	$("a.push, .wk, .bk, .iconbutton").each(pushHandler);
	$("*[data-ui='togglecredits']").click(function() {
		var credits = $("#credits");
		if (credits.is(":visible")) {
			credits.hide();
			dimmer.hide();
		}
		else {
			credits.fadeIn();
			dimmer.show();
		}
	});
	$("#dimmer").click(function() {
		$("#savepatchwindow").hide();
		$("#patchlistwindow").hide();
		$("#credits").hide();
		$("#settings").hide();
		dimmer.hide();
	});
	$("*[data-osc-observe]").each(function(ndx){
		var el = $(this);
		var path = el.attr("data-osc-observe");
		observers.push({ path: path, element: el});
	});
	
	/*
	function blink(selector) {
		$(selector).animate({opacity:0}, 100, "linear", function(){
			$(this).delay(100);
			$(this).animate({opacity:1}, 50, function(){
				blink(this);
			});
			$(this).delay(800);
		});
	};
	blink(".blink");
	*/
	
	// setup limiter display
	var setupLimiter = function() {
		var onFader = $('body').hasClass('pi800');
		var fps = 12;
		var now;
		var then = Date.now();
		var interval = 1000/fps;
		var delta;
		var limCanvas;
		if (onFader) {
			limCanvas = $("<canvas id='limiterview' width='200' height='3'></canvas>");
			$('*[data-control-path="/amp/volume"]').parent().prepend(limCanvas);
		}
		else {
			limCanvas = $("<canvas id='limiterview' width='50' height='50'></canvas>");
			$('*[data-control-path="/amp/volume"]').prepend(limCanvas);
		}
		limCanvas = document.getElementById("limiterview");
		if (typeof limCanvas !== 'undefined' && limCanvas!=null) {
			var lctx = limCanvas.getContext("2d");
			lctx.strokeStyle = "#ff0000";
			lctx.fillStyle = "#ff0000";
			lctx.lineWidth = 3;
			lctx.lineCap = "round";
			
			var animateLimiter;
			
			if (onFader) {
				var limvalue = 0;
				var targetvalue = 0;
				animateLimiter = function() {
					window.requestAnimationFrame(animateLimiter);
					now = Date.now();
				    delta = now - then;
				    if (delta > interval) {
				    	if (limvalue!=targetvalue) {
				    		if (limvalue>targetvalue) {
				    			limvalue = targetvalue;
				    		}
				    		else {
				    			limvalue += (targetvalue+.1-limvalue)*.5
				    		}
				    	}
				    	lctx.clearRect(0, 0, 200, 3);
				    	lctx.fillRect(200-limvalue, 0, limvalue, 3);
				    }
				    then = now - (delta % interval);
				}
				animateLimiter();
				return function(value) {
					targetvalue = value*200;
				}
			}
			else {
				var startAngle = 2*Math.PI+0.25*Math.PI;
				var fullAngle = 1.5*Math.PI;
				var targetAngle = startAngle;
				var currAngle = targetAngle;
				
				animateLimiter = function() {
					window.requestAnimationFrame(animateLimiter);
					now = Date.now();
				    delta = now - then;
				    if (delta > interval) {
						if (currAngle!=targetAngle) {
							if (currAngle>targetAngle) {
								currAngle = targetAngle;
							}
							else {
								currAngle += (targetAngle+.1-currAngle)*.3;
							}
							lctx.clearRect(0, 0, 50, 50);
							if (currAngle<startAngle) {
								lctx.beginPath();
								lctx.arc(25, 25, 22, currAngle, startAngle);
								lctx.stroke();
							}
						}
				        then = now - (delta % interval);
				    }
				}
				animateLimiter();
				return function(value) {
					targetAngle = startAngle-fullAngle*Math.min(value, 1);
				}
			}
		}
		return function() {};
	};
	var renderLimiter = setupLimiter();
	
	// fast navigation
	
	$('nav.fastnav span').each(function(){
		var link = $(this);
		$(this).click(function(){
			$(link.data('href'))[0].scrollIntoView(true);
			window.scrollBy(0, -70);
			return false;
		});
	});
	
	// screen saver

	var screensaverTimer = undefined;
	var screensaverOn = false;
	function screensaverOff() {
		if (typeof screensaverTimer!=='undefined') {
			clearTimeout(screensaverTimer);
		}
		if (screensaverOn) {
			screensaverOn = false;
			$(document.body).removeClass('sleep');
			socket.sendValueDirectly("/command/sleep", "999");
		}
		screensaverTimer = setTimeout(function() {
			screensaverOn = true;
			$(document.body).addClass('sleep');
			socket.sendValueDirectly("/command/sleep", "1");
		}, 1000*60*10); 
	}
	$('body, #screensaver').on('mousemove mousedown touchstart', screensaverOff);
	screensaverOff();
	
	$('#loadpatch').focus();
	
	socket.createAndConnect();
});