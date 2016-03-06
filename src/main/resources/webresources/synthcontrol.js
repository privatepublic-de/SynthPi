$(document).ready(function () {
	var warnRandomize = true;
	var dontreconnect = false;
	var observers = [];
	var Cookie = {
			create: function(name, value, days) {
				var expires;
				if (days) {
					var date = new Date();
					date.setTime(date.getTime() + (days * 24 * 60 * 60 * 1000));
					expires = "; expires=" + date.toGMTString();
				} else {
					expires = "";
				}
				document.cookie = encodeURIComponent(name) + "=" + encodeURIComponent(value) + expires + "; path=/";
			},

			read: function(name) {
				var nameEQ = encodeURIComponent(name) + "=";
				var ca = document.cookie.split(';');
				for (var i = 0; i < ca.length; i++) {
					var c = ca[i];
					while (c.charAt(0) === ' ') c = c.substring(1, c.length);
					if (c.indexOf(nameEQ) === 0) return decodeURIComponent(c.substring(nameEQ.length, c.length));
				}
				return null;
			},

			erase: function(name) {
				createCookie(name, "", -1);
			}	
	};
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
				this.mySocket.onmessage = function(event) {
					this.dispatchMessage(event.data);
					$("#datalight").stop().css("opacity", 1).animate({"opacity":0},300);
				}.bind(this);
				this.mySocket.onopen = function(event) {
					console.log("Socket opened "+event);
					this.setStatus(true);
				}.bind(this);
				this.mySocket.onclose = function(event) {
					console.log("Socket closed ", event);
					this.setStatus(false);
				}.bind(this);
				this.mySocket.onerror = function(event) {
					console.log("Socket exception ", event);
				}.bind(this);		
			},
			setStatus: function(connected) {
				if (connected) {
					$('#status').removeClass("connecting");
					$('#status').addClass("connected");
				}
				else {
					if (!dontreconnect) {
						$('#status').addClass("connecting");
						$('#status').removeClass("connected");
						if (this.reconnectTimer) {
							clearTimeout(this.reconnectTimer);
							this.reconectTimer = 0;
						}
						this.reconnectTimer = setTimeout(function() {
							console.log("Reconnecting ...");
							this.createAndConnect()
						}.bind(this), 2000);
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
						if (parts[0]=="/waveform/osc1" || parts[0]=="/waveform/osc2") {
							renderWaveform(parts[0], parts[1]);
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
	var sortOrdering = function(a,b) {
		if (a.category<b.category)
			return -1;
		if (a.category>b.category)
			return 1;
		if (a.name<b.name)
			return -1;
		if (a.name>b.name)
			return 1;
		return 0;
	};
	var renderPatchList = function(lists) {
		var addEntry = function(item, element) {
			element.append("<div class='"+item.category+"'><a href='#' class='push' data-osc='/command/loadpatch/"+item.id+"'><span class='category'>"+item.category+"</span>"+item.name+"</a></div>");
		}
		var columnsOut = function(list, element) {
			if (list && list.length>0) {
				list.sort(sortOrdering);
				var rows = parseInt(list.length/2)+list.length%2;
				for (var i=0;i<rows;i++) {
					addEntry(list[i], element);
					var j = i+rows;
					if (j<list.length) {
						addEntry(list[j], element);
					}
					else {
						element.append("<div></div>");
					}
				}
			}
			else {
				element.append("<div><em>(empty)</em></div><div></div>");	
			}
		}
		var elist = $("#loadpatchlist");
		elist.empty();
		elist.append("<div class='headline'>Your Patches</div>");
		columnsOut(lists.user, elist);
		elist.append("<div class='headline'>Factory Presets</div>");
		elist.append("<div><a href='#' class='push' data-osc='/command/initpatch' style='text-align:center'>INIT (start from scratch)</a></div><div></div>");
		columnsOut(lists.factory, elist);
		elist.append("<div class='headline'>Play Test Sequences</div>");
		elist.append("<div><a href='#' class='push seq' data-osc='/command/sequence/start/1'>&#9654; simple-sequence</a></div>");
		elist.append("<div><a href='#' class='push seq' data-osc='/command/sequence/start/2'>&#9654; pad-sequence</a></div>");
		elist.append("<div><a href='#' class='push seq' data-osc='/command/sequence/stop'>&#9642; Stop sequencer</a></div>");
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
		
		var existing = $("#existingpatches");
		existing.empty();
		if (info.existingPatches) {
			info.existingPatches.sort(sortOrdering);
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
	
	var renderWaveform = function(path, values) {
		var oscNumber = path.charAt(13);
		var values = values.split(',');
		var canvas = document.getElementById('waveformosc'+oscNumber);
		var ctx = canvas.getContext("2d");
		ctx.clearRect(0, 0, canvas.width, canvas.height);
		ctx.strokeStyle = "#999958";
		ctx.lineWidth = 1;
		ctx.beginPath();
		for (var x=0;x<values.length;x++) {
			var y = values[x];
			if (y>=0) {
				ctx.moveTo(x,20+4);
				ctx.lineTo(x,parseInt(y)+4);
			}
			else {
				ctx.moveTo(x,canvas.height);
				ctx.lineTo(x,parseInt(y)+canvas.height);
			}
		}
		ctx.stroke();
		var data = canvas.toDataURL();
		var displayElement = $('*[data-control-path="/osc/'+oscNumber+'/wave"]');
		displayElement.css('background-image','url('+data+')');
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
		$("#set_limiter").prop("checked",settings.limimterenabled);
		$("#set_polyphonyvoices").val(settings.polyphonyvoices);
		$("#set_buffersize").val(settings.audiobuffersize);
		$("#savesettings").unbind().click(function() {
			settings.midichannel = parseInt($("#set_midichannel").val());
			settings.pitchbendrange = parseInt($("#set_pitchbendrange").val());
			settings.pitchbendfix = $("#set_pitchbendfix").prop("checked");
			settings.polyphonyvoices = parseInt($("#set_polyphonyvoices").val());
			settings.audiobuffersize = parseInt($("#set_buffersize").val());
			settings.transferperformancedata = $("#set_liveperformance").prop("checked");
			settings.limimterenabled = $("#set_limiter").prop("checked");
			socket.sendValueDirectly("/command/settings/save", JSON.stringify(settings));
			$("#settings").fadeOut();
			dimmer.hide();
		});
	}

	jQuery.fn.cleanWhitespace = function() {
		textNodes = this.contents().filter(
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
	
	observers.push({ path:'/label/osc/mode', fn: function(path, value) {
			$("body").removeClass('VIRTUAL_ANALOG').removeClass('ADDITIVE').removeClass('EXITER').addClass(value);
			var attrkey = "data-label-"+value;
			$("*["+attrkey+"]").each(function() {
				var label = $(this).parent().parent().find("label");
				label.text($(this).attr(attrkey));
			}); 
		}
	});
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
		el.attr("disabled","disabled");
		var cclass = (el.hasClass("large")?"large":el.hasClass("small")?"small":"medium") + (el.hasClass("waveform")?" waveform":"");
		var container = $(document.createElement("div"));
		container.addClass("container "+cclass);
		container.attr("id","kn"+id);
		container.attr("data-control-path", el.attr("data-osc"));
		var fgcolor = el.css("color");
		if (!el.parents().hasClass("perfcontrols")) {  
			var bgcolor = $.Color(el, "color").lightness(0.2).saturation(0.3).toHexString();	
		}
		else {
			var bgcolor = $.Color(el, "color").lightness(0.2).saturation(0.0).toHexString();
		}
		var bgcolor2 = $.Color(el, "color").saturation(0.3).lightness(0.8).toHexString();
		var fgcolor2 = $.Color(el, "color").lightness(0.3).toHexString();
		var width = el.width();
		var cursor = el.attr("data-cursor");
		var labelpath = el.attr("data-osc-label");
		el.wrap(container);
		el.knob({
			'min': cursor?-100:0, 
			'max': cursor?100:200, 
			'step': 1,
			'angleArc': 240, 
			'angleOffset': -120,
			'width': width,
			'fgColor': fgcolor, 
			'inputColor': 'inherit', 
			'bgColor': bgcolor,
			'fontWeight': 'normal',
			'displayInput': labelpath?false:true,
			// handler
			'release': function(v) {
				if (el.data("remoteupdate")!=true) {
					socket.sendValue(el, v);
				} 
			},
			'change': function(v) {
				if (el.data("remoteupdate")!=true) {
					socket.sendValue(el, v);
				} 
			}
		});
		el.attr("data-bg-1", bgcolor);
		el.attr("data-fg-1", fgcolor);
		el.attr("data-bg-2", bgcolor2);
		el.attr("data-fg-2", fgcolor2);
		$("#kn"+id).append("<label class='static'>"+el.attr("title")+"</label>").dblclick(function() {
			socket.sendValue(el, 0);
			el.val(0).trigger("change");
		});
		if (labelpath) {
			$("#kn"+id).append("<label class='dynamic' data-osc='"+labelpath+"'>...</label>");
		}
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
	var learnmode = false;
	$("#learn").click(function() {
		learnmode = !learnmode;
		
		if (learnmode) {
			$("body").addClass("learnselected");
			var learnpath;
			$("#main").on("mouseover", function(ev) {
				$("#main *").removeClass("learnselected");
				var el = $(ev.target);
				var container = el.parents("*[data-control-path]").first();
				var detectedpath;
				if (container.length>0) {
					container.addClass("learnselected");
					detectedpath = container.attr("data-control-path");
				}
				else {
					if (el.prev().hasClass("checkbox")) {
						el.addClass("learnselected");
						el.prev().addClass("learnselected");
						detectedpath = el.prev().attr("data-osc");
					}
					else if (el.hasClass("checkbox")) {
						el.addClass("learnselected");
						el.next().addClass("learnselected");
						detectedpath = el.attr("data-osc");
					}
				}
				if (detectedpath) {
					if (learnpath!=detectedpath) {
						socket.sendValueDirectly("/command/learn/start", detectedpath);
						console.log("Start learning:", detectedpath);
					}
				}
				else {
					if (learnpath) {
						socket.sendValueDirectly("/command/learn/stop", "1");
						console.log("Stop learning");
					}
				}
				learnpath = detectedpath;
			});
		}
		else {
			$("#main").unbind();
			$("body").removeClass("learnselected");
			socket.sendValueDirectly("/command/learn/stop", "1");
		}
	});
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
	
	var updateDisplayColors = function() {
		var colorNo = $("body").hasClass("inverted")?2:1; 
		Cookie.create("synthpi_uistyle", colorNo, 30);
		$('.dial').each(function() {
			var el = $(this);
			var bgCol = el.attr("data-bg-"+colorNo);
			var fgCol = el.attr("data-fg-"+colorNo);
			el.trigger('configure', { "fgColor":fgCol, "bgColor":bgCol});				
		});
	}
	
	$("#toggledisplay").click(function() {
		$("body").toggleClass("inverted");
		updateDisplayColors();
	});
	
	// setup limiter display
	var setupLimiter = function() {
		var fps = 12;
		var now;
		var then = Date.now();
		var interval = 1000/fps;
		var delta;
		var limCanvas = $("<canvas id='limiterview' width='50' height='50'></canvas>");
		$('*[data-control-path="/amp/volume"]').prepend(limCanvas);
		limCanvas = document.getElementById("limiterview");
		var startAngle = 2*Math.PI+0.25*Math.PI;
		var fullAngle = 1.5*Math.PI;
		var targetAngle = startAngle;
		var currAngle = targetAngle;
		var lctx = limCanvas.getContext("2d");
		lctx.strokeStyle = "#ff0000";
		lctx.lineWidth = 3;
		lctx.lineCap = "round";
		var animateLimiter = function() {
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
	};
	var renderLimiter = setupLimiter();
	if (Cookie.read("synthpi_uistyle")==2) {
		$("body").addClass("inverted");
		updateDisplayColors();
	}
	socket.createAndConnect();
});