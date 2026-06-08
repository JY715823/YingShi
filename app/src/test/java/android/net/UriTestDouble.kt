package android.net

import android.os.Parcel

class UriTestDouble(
    private val raw: String,
) : Uri() {

    override fun toString(): String = raw

    override fun isHierarchical(): Boolean = true

    override fun isRelative(): Boolean = false

    override fun getScheme(): String = raw.substringBefore("://", missingDelimiterValue = "")

    override fun getSchemeSpecificPart(): String = raw.substringAfter("://", missingDelimiterValue = raw)

    override fun getEncodedSchemeSpecificPart(): String = schemeSpecificPart

    override fun getAuthority(): String? = null

    override fun getEncodedAuthority(): String? = null

    override fun getUserInfo(): String? = null

    override fun getEncodedUserInfo(): String? = null

    override fun getHost(): String? = null

    override fun getPort(): Int = -1

    override fun getPath(): String? = null

    override fun getEncodedPath(): String? = null

    override fun getQuery(): String? = null

    override fun getEncodedQuery(): String? = null

    override fun getFragment(): String? = null

    override fun getEncodedFragment(): String? = null

    override fun getPathSegments(): MutableList<String> = mutableListOf()

    override fun getLastPathSegment(): String? = null

    override fun buildUpon(): Builder {
        throw UnsupportedOperationException("Not needed in unit tests.")
    }

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        throw UnsupportedOperationException("Not needed in unit tests.")
    }

    override fun describeContents(): Int = 0
}
