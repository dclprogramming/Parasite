package com.liskovsoft.googlecommon.common.constants

import com.liskovsoft.googlecommon.common.constants.data.ConstantsResult

/**
 * Hardened build: upstream downloaded this file at runtime from the original author's GitHub release
 * (OAuth client id/secret and a YouTube Data API key). Config that a third party can change remotely is
 * exactly the kind of channel this fork removes, so nothing is fetched. Features that need these values
 * (Google sign-in for Drive backup, YouTube Data API v3) stay switched off; normal YouTube sign-in and
 * playback do not use them. To enable them, create your own Google Cloud OAuth client and put the values here.
 */
internal object ConstantsService {
    @JvmStatic
    val constants: ConstantsResult? = null
}
