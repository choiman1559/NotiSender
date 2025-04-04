package com.noti.main.service.backend;

@SuppressWarnings("unused")
public class PacketConst {

    public final static String SERVICE_TYPE_LIVE_NOTIFICATION = "type_live_notification";
    public final static String SERVICE_TYPE_IMAGE_CACHE = "type_image_cache";
    public final static String SERVICE_TYPE_PACKET_PROXY = "type_packet_proxy";
    public final static String SERVICE_TYPE_FILE_TRANSFER = "type_file_transfer";
    public final static String SERVICE_TYPE_FILE_LIST = "type_file_list";
    public final static String SERVICE_TYPE_PACKET_BONDING = "type_packet_bonding";
    public final static String SERVICE_TYPE_PING_SERVER = "type_ping";

    public final static String REQUEST_POST_SHORT_TERM_DATA = "request_post_short_term_data";
    public final static String REQUEST_GET_SHORT_TERM_DATA = "request_get_short_term_data";
    public final static String REQUEST_POST_LONG_TERM_DATA = "request_post_long_term_data";
    public final static String REQUEST_GET_LONG_TERM_DATA = "request_get_long_term_data";

    public final static String STATUS_ERROR = "error";
    public final static String STATUS_OK = "ok";

    public final static String ERROR_NONE = "none";
    public final static String ERROR_NOT_FOUND = "not_found";
    public final static String ERROR_SERVICE_NOT_FOUND = "service_type_not_found";
    public final static String ERROR_SERVICE_NOT_IMPLEMENTED = "service_type_not_implemented";
    public final static String ERROR_INTERNAL_ERROR = "server_internal_error";
    public final static String ERROR_ILLEGAL_ARGUMENT = "server_illegal_argument";
    public final static String ERROR_FORBIDDEN = "server_forbidden";

    public final static String ERROR_DATA_NO_MATCHING_DATA = "No matching Data Available";
    public final static String ERROR_DATA_DEVICE_INFO_NOT_MATCH = "Device Information is invalid comparing from stored data";
    public final static String ERROR_DATA_DEVICE_INFO_NOT_AVAILABLE = "Device Information is not available";
    public final static String ERROR_DATA_DB_IO_FAILED_READ = "IO Failed while reading from stored db";
    public final static String ERROR_DATA_DB_IO_FAILED_WRITE = "IO Failed while writing to persistent db";

    public final static String KEY_DEVICE_ID = "device_id";
    public final static String KEY_DEVICE_NAME = "device_name";
    public final static String KEY_SEND_DEVICE_ID = "send_device_id";
    public final static String KEY_SEND_DEVICE_NAME = "send_device_name";
    public static final String KEY_IS_SUCCESS = "is_success";
    public final static String KEY_PACKET_BONDING_ARRAY = "packet_bonding_array";

    public final static String KEY_ACTION_TYPE = "action_type";
    public final static String KEY_UID = "uid";
    public final static String KEY_DATA_KEY = "data_key";
    public final static String KEY_EXTRA_DATA = "extra_data";

    public final static String contentType = "application/json";
    public final static String API_ROUTE_SCHEMA = "%s/%s/v1/service=%s";
    public final static String API_DOMAIN = "https://cuj1559.ddns.net";
    public final static String API_PUBLIC_ROUTE = "api";
    public final static String API_DEBUG_ROUTE = "api_test";
    public final static String API_PREFS_DOMAIN_KEY = "preferred_domain";
}
