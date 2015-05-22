<?php
/**
 * Elgg web services API plugin
 */

elgg_register_event_handler('init', 'system', 'ws_init');

function ws_init() {
	$lib_dir = elgg_get_plugins_path() . "web_services/lib";
	elgg_register_library('elgg:ws', "$lib_dir/web_services.php");
	elgg_register_library('elgg:ws:api_user', "$lib_dir/api_user.php");
	elgg_register_library('elgg:ws:client', "$lib_dir/client.php");
	elgg_register_library('elgg:ws:tokens', "$lib_dir/tokens.php");

	elgg_load_library('elgg:ws:api_user');
	elgg_load_library('elgg:ws:tokens');

	elgg_register_page_handler('services', 'ws_page_handler');

	// Register a service handler for the default web services
	// The name rest is a misnomer as they are not RESTful
	elgg_ws_register_service_handler('rest', 'ws_rest_handler');

	// expose the list of api methods
	elgg_ws_expose_function("system.api.list", "list_all_apis", null,
		elgg_echo("system.api.list"), "GET", false, false);

	// The authentication token api
	elgg_ws_expose_function(
		"auth.gettoken",
		"auth_gettoken",
		array(
			'username' => array ('type' => 'string'),
			'password' => array ('type' => 'string'),
		),
		elgg_echo('auth.gettoken'),
		'POST',
		false,
		false
	);

	elgg_register_plugin_hook_handler('unit_test', 'system', 'ws_unit_test');
	
	//
	elgg_ws_expose_function("test.echo",
                "my_echo",
                 array("string" => array('type' => 'string')),
                 'A testing method which echos back a string',
                 'GET',
                 false,
                 false
                );
        elgg_ws_expose_function("thewire.get_posts",
                "wire_get_posts",
                 array("context" => array('type' => 'string')),
                 'Get Wire Posts',
                 'GET',
                 false,
                 true
                );       
	elgg_ws_expose_function("thewire.post",
                "my_post_to_wire",
                 array("text" => array('type' => 'string')),
                 'Post to the wire. 140 characters or less',
                 'POST',
                 false,
                 true
                );
	elgg_ws_expose_function('user.register',
               "user_register",
               array('name' => array ('type' => 'string'),
                       'email' => array ('type' => 'string'),
                       'username' => array ('type' => 'string'),
                       'password' => array ('type' => 'string'),
                   ),
               "Register user",
               'GET',
               false,
               false);
       expose_function('site.river_feed',
               "site_river_feed",
               array('limit' => array('type' => 'int', 'required' => 'no')),
               "Get river feed",
               'GET',
               false,
               false);
}

/**
 * Handle a web service request
 * 
 * Handles requests of format: http://site/services/api/handler/response_format/request
 * The first element after 'services/api/' is the service handler name as
 * registered by {@link register_service_handler()}.
 *
 * The remaining string is then passed to the {@link service_handler()}
 * which explodes by /, extracts the first element as the response format
 * (viewtype), and then passes the remaining array to the service handler
 * function registered by {@link register_service_handler()}.
 *
 * If a service handler isn't found, a 404 header is sent.
 * 
 * @param array $segments URL segments
 * @return bool
 */
function ws_page_handler($segments) {
	elgg_load_library('elgg:ws');

	if (!isset($segments[0]) || $segments[0] != 'api') {
		return false;
	}
	array_shift($segments);

	$handler = array_shift($segments);
	$request = implode('/', $segments);

	service_handler($handler, $request);

	return true;
}

/**
 * A global array holding API methods.
 * The structure of this is
 * 	$API_METHODS = array (
 * 		$method => array (
 * 			"description" => "Some human readable description"
 * 			"function" = 'my_function_callback'
 * 			"parameters" = array (
 * 				"variable" = array ( // the order should be the same as the function callback
 * 					type => 'int' | 'bool' | 'float' | 'string'
 * 					required => true (default) | false
 *					default => value // optional
 * 				)
 * 			)
 * 			"call_method" = 'GET' | 'POST'
 * 			"require_api_auth" => true | false (default)
 * 			"require_user_auth" => true | false (default)
 * 		)
 *  )
 */
global $API_METHODS;
$API_METHODS = array();

/** Define a global array of errors */
global $ERRORS;
$ERRORS = array();

/**
 * Expose a function as a web service.
 *
 * Limitations: Currently cannot expose functions which expect objects.
 * It also cannot handle arrays of bools or arrays of arrays.
 * Also, input will be filtered to protect against XSS attacks through the web services.
 *
 * @param string $method            The api name to expose - for example "myapi.dosomething"
 * @param string $function          Your function callback.
 * @param array  $parameters        (optional) List of parameters in the same order as in
 *                                  your function. Default values may be set for parameters which
 *                                  allow REST api users flexibility in what parameters are passed.
 *                                  Generally, optional parameters should be after required
 *                                  parameters.
 *
 *                                  This array should be in the format
 *                                    "variable" = array (
 *                                  					type => 'int' | 'bool' | 'float' | 'string' | 'array'
 *                                  					required => true (default) | false
 *                                  					default => value (optional)
 *                                  	 )
 * @param string $description       (optional) human readable description of the function.
 * @param string $call_method       (optional) Define what http method must be used for
 *                                  this function. Default: GET
 * @param bool   $require_api_auth  (optional) (default is false) Does this method
 *                                  require API authorization? (example: API key)
 * @param bool   $require_user_auth (optional) (default is false) Does this method
 *                                  require user authorization?
 *
 * @return bool
 * @throws InvalidParameterException
 */
function elgg_ws_expose_function($method, $function, array $parameters = NULL, $description = "",
		$call_method = "GET", $require_api_auth = false, $require_user_auth = false) {

	global $API_METHODS;

	if (($method == "") || ($function == "")) {
		$msg = elgg_echo('InvalidParameterException:APIMethodOrFunctionNotSet');
		throw new InvalidParameterException($msg);
	}

	// does not check whether this method has already been exposed - good idea?
	$API_METHODS[$method] = array();

	$API_METHODS[$method]["description"] = $description;

	// does not check whether callable - done in execute_method()
	$API_METHODS[$method]["function"] = $function;

	if ($parameters != NULL) {
		if (!is_array($parameters)) {
			$msg = elgg_echo('InvalidParameterException:APIParametersArrayStructure', array($method));
			throw new InvalidParameterException($msg);
		}

		// catch common mistake of not setting up param array correctly
		$first = current($parameters);
		if (!is_array($first)) {
			$msg = elgg_echo('InvalidParameterException:APIParametersArrayStructure', array($method));
			throw new InvalidParameterException($msg);
		}
	}

	if ($parameters != NULL) {
		// ensure the required flag is set correctly in default case for each parameter
		foreach ($parameters as $key => $value) {
			// check if 'required' was specified - if not, make it true
			if (!array_key_exists('required', $value)) {
				$parameters[$key]['required'] = true;
			}
		}

		$API_METHODS[$method]["parameters"] = $parameters;
	}

	$call_method = strtoupper($call_method);
	switch ($call_method) {
		case 'POST' :
			$API_METHODS[$method]["call_method"] = 'POST';
			break;
		case 'GET' :
			$API_METHODS[$method]["call_method"] = 'GET';
			break;
		default :
			$msg = elgg_echo('InvalidParameterException:UnrecognisedHttpMethod',
			array($call_method, $method));

			throw new InvalidParameterException($msg);
	}

	$API_METHODS[$method]["require_api_auth"] = $require_api_auth;

	$API_METHODS[$method]["require_user_auth"] = $require_user_auth;

	return true;
}

/**
 * Unregister a web services method
 *
 * @param string $method The api name that was exposed
 * @return void
 */
function elgg_ws_unexpose_function($method) {
	global $API_METHODS;

	if (isset($API_METHODS[$method])) {
		unset($API_METHODS[$method]);
	}
}

/**
 * Simple api to return a list of all api's installed on the system.
 *
 * @return array
 * @access private
 */
function list_all_apis() {
	global $API_METHODS;

	// sort first
	ksort($API_METHODS);

	return $API_METHODS;
}

/**
 * Registers a web services handler
 *
 * @param string $handler  Web services type
 * @param string $function Your function name
 *
 * @return bool Depending on success
 */
function elgg_ws_register_service_handler($handler, $function) {
	global $CONFIG;

	if (!isset($CONFIG->servicehandler)) {
		$CONFIG->servicehandler = array();
	}
	if (is_callable($function, true)) {
		$CONFIG->servicehandler[$handler] = $function;
		return true;
	}

	return false;
}

/**
 * Remove a web service
 * To replace a web service handler, register the desired handler over the old on
 * with register_service_handler().
 *
 * @param string $handler web services type
 * @return void
 */
function elgg_ws_unregister_service_handler($handler) {
	global $CONFIG;

	if (isset($CONFIG->servicehandler, $CONFIG->servicehandler[$handler])) {
		unset($CONFIG->servicehandler[$handler]);
	}
}

/**
 * REST API handler
 *
 * @return void
 * @access private
 *
 * @throws SecurityException|APIException
 */
function ws_rest_handler() {

	elgg_load_library('elgg:ws');

	// Register the error handler
	error_reporting(E_ALL);
	set_error_handler('_php_api_error_handler');

	// Register a default exception handler
	set_exception_handler('_php_api_exception_handler');

	// plugins should return true to control what API and user authentication handlers are registered
	if (elgg_trigger_plugin_hook('rest', 'init', null, false) == false) {
		// for testing from a web browser, you can use the session PAM
		// do not use for production sites!!
		//register_pam_handler('pam_auth_session');

		// user token can also be used for user authentication
		register_pam_handler('pam_auth_usertoken');

		// simple API key check
		register_pam_handler('api_auth_key', "sufficient", "api");
		// hmac
		register_pam_handler('api_auth_hmac', "sufficient", "api");
	}

	// Get parameter variables
	$method = get_input('method');
	$result = null;

	// this will throw an exception if authentication fails
	authenticate_method($method);

	$result = execute_method($method);


	if (!($result instanceof GenericResult)) {
		throw new APIException(elgg_echo('APIException:ApiResultUnknown'));
	}

	// Output the result
	echo elgg_view_page($method, elgg_view("api/output", array("result" => $result)));
}

/**
 * Unit tests for web services
 *
 * @param string $hook   unit_test
 * @param string $type   system
 * @param mixed  $value  Array of tests
 * @param mixed  $params Params
 *
 * @return array
 * @access private
 */
function ws_unit_test($hook, $type, $value, $params) {
	elgg_load_library('elgg:ws');
	elgg_load_library('elgg:ws:client');
	$value[] = dirname(__FILE__) . '/tests/ElggCoreWebServicesApiTest.php';
	return $value;
}
function my_echo($string) {
    return $string;
}
function wire_get_posts($context, $limit = 10, $offset = 0, $username) {

	if(!$username) {
		$user = get_loggedin_user();
	} else {
		$user = get_user_by_username($username);
		if (!$user) {
			throw new InvalidParameterException('registration:usernamenotvalid');
		}
	}
		
	if($context == "all"){
		$params = array(
			'types' => 'object',
			'subtypes' => 'thewire',
			'limit' => $limit,
			'full_view' => FALSE,
		);
		}
		if($context == "mine" || $context == "user"){
		$params = array(
			'types' => 'object',
			'subtypes' => 'thewire',
			'owner_guid' => $user->guid,
			'limit' => $limit,
			'full_view' => FALSE,
		);
		}
		$latest_wire = elgg_get_entities($params);
		
		if($context == "friends"){
		$latest_wire = get_user_friends_objects($user->guid, 'thewire', $limit, $offset);
		}

	if($latest_wire){
		foreach($latest_wire as $single ) {
			$wire['guid'] = $single->guid;
			
			$owner = get_entity($single->owner_guid);
			$wire['owner']['guid'] = $owner->guid;
			$wire['owner']['name'] = $owner->name;
			$wire['owner']['avatar_url'] = get_entity_icon_url($owner,'small');
				
			$wire['time_created'] = (int)$single->time_created;
			$wire['description'] = $single->description;
			$return[] = $wire;
		} 
	} else {
		$msg = elgg_echo('thewire:noposts');
		throw new InvalidParameterException($msg);
	}
	
	return $return;
} 

function my_post_to_wire($text) {

   $text = substr($text, 0, 140);

   $access = ACCESS_PUBLIC;

   if(!$username) {
       $user = get_loggedin_user();
   } else {
       $user = get_user_by_username($username);
       if (!$user) {
           throw new InvalidParameterException('registration:usernamenotvalid');
       }
   }

   // returns guid of wire post
   return thewire_save_post($text, $user->guid, $access, "api");
}

function user_register($name, $email, $username, $password) {
   $user = get_user_by_username($username);
   if (!$user) {
       $return['success'] = true;
       $return['guid'] = register_user($username, $password, $name, $email);
   } else {
       $return['success'] = false;
       $return['message'] = elgg_echo('registration:userexists');
   }
   return $return;
}

/**
* Retrive river feed
*
* @return array $river_feed contains all information for river
*/
function site_river_feed($limit){

       global $jsonexport;

       elgg_view_river_items();

       return $jsonexport['activity'];

}