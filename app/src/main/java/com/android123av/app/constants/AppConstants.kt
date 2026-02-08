package com.android123av.app.constants

object AppConstants {
    const val PREFS_NAME = "app_prefs"
    const val MAX_USERNAME_LENGTH = 20
    const val MAX_PASSWORD_LENGTH = 30
    const val MAX_SEARCH_HISTORY = 20
    
    const val IMAGE_CACHE_DIR = "image_cache"
    const val IMAGE_CACHE_SIZE_MB = 200
    
    const val VIDEO_CACHE_EXPIRATION_MS = 30 * 60 * 1000L
    
    const val DEFAULT_DOWNLOAD_DIR = "123AV_Downloads"
    
    const val COOKIE_PREFS_NAME = "app_cookies"
    
    const val DOWNLOAD_PATH_PREFS_NAME = "download_path_prefs"
    const val THEME_PREFS_NAME = "theme_prefs"
    const val USER_PREFS_NAME = "user_prefs"
    const val SEARCH_HISTORY_PREFS_NAME = "search_history_prefs"
    
    const val KEY_CUSTOM_PATH = "custom_download_path"
    const val KEY_USE_CUSTOM_PATH = "use_custom_path"
    
    const val KEY_THEME_MODE = "theme_mode"
    const val KEY_DYNAMIC_COLOR = "dynamic_color"
    const val KEY_CUSTOM_COLOR_SEED = "custom_color_seed"
    const val THEME_LIGHT = 0
    const val THEME_DARK = 1
    const val THEME_SYSTEM = 2
    
    const val KEY_IS_LOGGED_IN = "is_logged_in"
    const val KEY_USER_ID = "user_id"
    const val KEY_USER_NAME = "user_name"
    const val KEY_USER_EMAIL = "user_email"
    const val KEY_REMEMBER_ME = "remember_me"
    const val KEY_SAVED_USERNAME = "saved_username"
    const val KEY_SAVED_PASSWORD = "saved_password"
    
    const val KEY_SEARCH_HISTORY = "search_history"
    
    const val WATCH_HISTORY_PREFS_NAME = "watch_history_prefs"
    const val KEY_WATCH_HISTORY = "watch_history"
    const val MAX_WATCH_HISTORY = 50
    
    const val KEY_SELECTED_SITE_ID = "selected_site_id"
    const val DEFAULT_SITE_ID = "123av_com"
    
    const val COOKIE_PREFIX = "cookie_"
    const val COOKIE_COUNT_KEY = "cookie_count"
    
    const val BYTES_IN_KB = 1024
    const val BYTES_IN_MB = 1024 * 1024
    const val BYTES_IN_GB = 1024 * 1024 * 1024
    
    const val SECONDS_IN_MINUTE = 60
    const val SECONDS_IN_HOUR = 3600
    
    const val DEFAULT_CACHE_SIZE = 50
    const val DEFAULT_HTTP_CACHE_SIZE_MB = 50
    const val DEFAULT_CONNECTION_POOL_SIZE = 10
    
    const val DEFAULT_TIMEOUT_MS = 15000L
    const val FAST_TIMEOUT_MS = 12000L
    const val SHORT_TIMEOUT_MS = 3000L
    const val VERY_SHORT_TIMEOUT_MS = 1000L
    const val LONG_TIMEOUT_MS = 30000L
    const val WEBVIEW_TIMEOUT_MS = 15000L
    const val HTTP_PARALLEL_TIMEOUT_MS = 12000L
    
    const val HTTP_STATUS_INTERNAL_ERROR = 500
    
    const val THUMBNAIL_RANDOM_MIN = 200
    const val THUMBNAIL_RANDOM_MAX = 300
}

object NetworkConstants {
    const val HTTP_CACHE_SIZE = 50L * 1024 * 1024
    const val CONNECTION_POOL_SIZE = 10
    const val CACHE_EXPIRATION_MS = 30 * 60 * 1000L
    const val VIDEO_URL_CACHE_SIZE = 50
}
