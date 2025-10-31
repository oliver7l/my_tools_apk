package com.example.aliyunapp

import com.google.gson.annotations.SerializedName

/**
 * 哔哩哔哩收藏数据模型
 */

data class BilibiliFavoritesResponse(
    @SerializedName("favorites")
    val favorites: List<BilibiliFavorite>
)

data class BilibiliFavorite(
    @SerializedName("id")
    val id: Long,
    @SerializedName("title")
    val title: String,
    @SerializedName("cover")
    val cover: String,
    @SerializedName("upper")
    val upper: BilibiliUpper,
    @SerializedName("intro")
    val intro: String,
    @SerializedName("media_count")
    val mediaCount: Int,
    @SerializedName("ctime")
    val ctime: Long,
    @SerializedName("mtime")
    val mtime: Long,
    @SerializedName("medias")
    val medias: List<BilibiliMedia>
)

data class BilibiliUpper(
    @SerializedName("mid")
    val mid: Long,
    @SerializedName("name")
    val name: String,
    @SerializedName("face")
    val face: String,
    @SerializedName("followed")
    val followed: Boolean,
    @SerializedName("vip_type")
    val vipType: Int,
    @SerializedName("vip_statue")
    val vipStatue: Int
)

data class BilibiliMedia(
    @SerializedName("id")
    val id: Long,
    @SerializedName("type")
    val type: Int,
    @SerializedName("title")
    val title: String,
    @SerializedName("cover")
    val cover: String,
    @SerializedName("intro")
    val intro: String,
    @SerializedName("page")
    val page: Int,
    @SerializedName("duration")
    val duration: Int,
    @SerializedName("upper")
    val upper: BilibiliMediaUpper,
    @SerializedName("attr")
    val attr: Int,
    @SerializedName("cnt_info")
    val cntInfo: BilibiliCountInfo?,
    @SerializedName("link")
    val link: String,
    @SerializedName("ctime")
    val ctime: Long,
    @SerializedName("pubtime")
    val pubtime: Long,
    @SerializedName("fav_time")
    val favTime: Long,
    @SerializedName("bv_id")
    val bvId: String,
    @SerializedName("bvid")
    val bvid: String,
    @SerializedName("season")
    val season: String?,
    @SerializedName("ogv")
    val ogv: BilibiliOgv?,
    @SerializedName("ugc")
    val ugc: BilibiliUgc,
    @SerializedName("media_list_link")
    val mediaListLink: String,
    @SerializedName("invalid")
    val invalid: Boolean
)

data class BilibiliMediaUpper(
    @SerializedName("mid")
    val mid: Long,
    @SerializedName("name")
    val name: String,
    @SerializedName("face")
    val face: String,
    @SerializedName("jump_link")
    val jumpLink: String
)

data class BilibiliCountInfo(
    @SerializedName("collect")
    val collect: Int,
    @SerializedName("play")
    val play: Int,
    @SerializedName("danmaku")
    val danmaku: Int,
    @SerializedName("vt")
    val vt: Int,
    @SerializedName("play_switch")
    val playSwitch: Int,
    @SerializedName("reply")
    val reply: Int,
    @SerializedName("view_text_1")
    val viewText1: String
)

data class BilibiliUgc(
    @SerializedName("first_cid")
    val firstCid: Long
)

data class BilibiliOgv(
    @SerializedName("type_name")
    val typeName: String,
    @SerializedName("type_id")
    val typeId: Int,
    @SerializedName("season_id")
    val seasonId: Long
)

/**
 * 简化后的哔哩哔哩收藏项，用于在UI中显示
 */
data class BilibiliFavoriteItem(
    val title: String,
    val link: String,
    val bvid: String,
    val cover: String?,
    val duration: Int,
    val favTime: Long,
    val isFavorite: Boolean = false,  // 是否喜欢
    val isDeleted: Boolean = false     // 是否已删除
)