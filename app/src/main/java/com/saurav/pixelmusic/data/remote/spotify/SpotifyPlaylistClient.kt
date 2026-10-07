package com.saurav.pixelmusic.data.remote.spotify

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

class SpotifyNotConfiguredException :
    Exception("Spotify import isn't set up yet (missing API credentials)")

/**
 * Minimal Spotify Web API client (Client Credentials flow) for importing public
 * playlists from pasted links. No user login required.
 */
class SpotifyPlaylistClient(
    private val http: OkHttpClient,
    private val clientId: String = SpotifyCredentials.CLIENT_ID,
    private val clientSecret: String = SpotifyCredentials.CLIENT_SECRET,
) {
    data class Track(val title: String, val artist: String, val durationMs: Long)

    private var cachedToken: String? = null
    private var tokenExpiresAtMs: Long = 0L

    private suspend fun accessToken(): String = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        cachedToken?.takeIf { now < tokenExpiresAtMs - 60_000 }?.let { return@withContext it }
        if (clientId.isBlank() || clientSecret.isBlank()) throw SpotifyNotConfiguredException()
        val body = FormBody.Builder().add("grant_type", "client_credentials").build()
        val request = Request.Builder()
            .url("https://accounts.spotify.com/api/token")
            .header("Authorization", Credentials.basic(clientId, clientSecret))
            .post(body)
            .build()
        http.newCall(request).execute().use { response ->
            val json = JSONObject(response.body.string())
            if (!response.isSuccessful) {
                throw Exception("Spotify auth failed (HTTP ${response.code})")
            }
            cachedToken = json.getString("access_token")
            tokenExpiresAtMs = now + json.getLong("expires_in") * 1000
            cachedToken!!
        }
    }

    /**
     * Returns (playlist name, tracks in order).
     */
    suspend fun fetchPlaylist(playlistId: String): Pair<String, List<Track>> =
        withContext(Dispatchers.IO) {
            val token = accessToken()

            fun get(url: String): JSONObject {
                val request = Request.Builder()
                    .url(url)
                    .header("Authorization", "Bearer $token")
                    .build()
                http.newCall(request).execute().use { response ->
                    val body = response.body.string()
                    if (!response.isSuccessful) {
                        throw Exception("Spotify request failed (HTTP ${response.code})")
                    }
                    return JSONObject(body)
                }
            }

            val name = get("https://api.spotify.com/v1/playlists/$playlistId?fields=name")
                .optString("name", "Spotify Playlist")

            val tracks = mutableListOf<Track>()
            var url: String? =
                "https://api.spotify.com/v1/playlists/$playlistId/tracks?limit=50&fields=items(track(name,artists(name),duration_ms)),next"
            while (url != null) {
                val page = get(url)
                val items = page.optJSONArray("items") ?: break
                for (i in 0 until items.length()) {
                    val track = items.optJSONObject(i)?.optJSONObject("track") ?: continue
                    val artistsJson = track.optJSONArray("artists")
                    val artist = buildList {
                        for (a in 0 until (artistsJson?.length() ?: 0)) {
                            artistsJson?.optJSONObject(a)?.optString("name")
                                ?.takeIf { it.isNotBlank() }?.let { add(it) }
                        }
                    }.joinToString(", ")
                    tracks += Track(
                        title = track.optString("name"),
                        artist = artist,
                        durationMs = track.optLong("duration_ms"),
                    )
                }
                url = page.optString("next").takeIf { it.isNotBlank() }
            }
            name to tracks
        }
}
