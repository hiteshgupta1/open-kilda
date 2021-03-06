/*<![CDATA[*/

/** Below the javascript/ajax/jquery code to generate and display the switch and its corresponding details*/

/**
 * show switch details when page is loaded or when user is redirected to this
 * page
 */
$(document).ready(function() {

			var switchname = window.location.href.split("#")[1]	;
			var port_number = window.location.href.split("#")[2];
			var tmp_anchor = '<a href="/openkilda/switch">'+ 'Switch' + '</a>';
			$("#topologyId").parent().append(tmp_anchor);			
			var tmp_anchor_switch = '<a href="details#' + switchname + '">'+ switchname + '</a>';
			$("#kilda-switch-name").parent().append(tmp_anchor_switch);
			$("#kilda-port-name").parent().append(port_number);
			var portData = localStorage.getItem('port_'+common.toggleSwitchID(switchname)+"_"+port_number);
			
			if(!portData) {
				window.location =  APP_CONTEXT+ "/switch";
			}	
			var obj = JSON.parse(portData)
			$("#kilda-port-name").parent().append(obj.port_name)
			$('body').css('pointer-events', 'all');							
			showSwitchData(obj,switchname);
			getMetricDetails.getPortMetricData();
			
})

/**
 * function to retrieve and show switch details from the switch response json
 * object and display on the html page
 */
function showSwitchData(obj,switchname) {
	
	$(".graph_div").show();
	$(".port_details_div_status").html(obj.status);
	$('#edit_port_status').val(obj.status);
	$(".switchdetails_div_number").html(obj.port_number);
	$(".switchdetails_div_interface").html(obj.interfacetype);
	$(".switchportdetails_div_switch_id").html(switchname);
}
/* ]]> */