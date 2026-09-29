package com.example.data

import com.example.model.*

object MockData {
    private fun img(seed: String, w: Int = 400, h: Int = 600): String {
        return "https://picsum.photos/seed/$seed/$w/$h"
    }

    val defaultProjects: List<ProjectItem> = listOf(
        ProjectItem(
            id = "p1",
            title = "Viagem à Serra",
            duration = "01:24",
            date = "Hoje",
            thumbUrl = img("vid1"),
            aspectRatio = AspectRatio.RATIO_9_16,
            clips = listOf(
                MediaClip(id = "c1", title = "Mirante", uri = img("vid1_clip1"), durationMs = 24000L),
                MediaClip(id = "c2", title = "Trilha", uri = img("vid1_clip2"), durationMs = 32000L, transition = "Dissolver"),
                MediaClip(id = "c3", title = "Pôr do Sol", uri = img("vid1_clip3"), durationMs = 28000L, transition = "Fade")
            ),
            audios = listOf(
                AudioTrackItem(id = "a1", name = "Summer Vibes", category = "Happy", duration = "02:34", volume = 0.75f)
            ),
            texts = listOf(
                TextOverlayItem(id = "t1", text = "Serra Gaúcha 🌲", startTimeMs = 1000L, durationMs = 5000L, posY = 0.25f)
            ),
            subtitles = listOf(
                SubtitleSegmentItem(id = "s1", text = "Chegamos ao topo da montanha!", startTimeMs = 1200L, endTimeMs = 4500L),
                SubtitleSegmentItem(id = "s2", text = "A vista daqui é simplesmente incrível.", startTimeMs = 5000L, endTimeMs = 9200L)
            )
        ),
        ProjectItem(
            id = "p2",
            title = "Reels de Verão",
            duration = "00:32",
            date = "Ontem",
            thumbUrl = img("vid2"),
            aspectRatio = AspectRatio.RATIO_9_16,
            clips = listOf(
                MediaClip(id = "c4", title = "Praia 1", uri = img("vid2_clip1"), durationMs = 16000L),
                MediaClip(id = "c5", title = "Mergulho", uri = img("vid2_clip2"), durationMs = 16000L, transition = "Zoom")
            ),
            audios = listOf(
                AudioTrackItem(id = "a2", name = "Neon Nights", category = "Pop", duration = "03:12")
            )
        ),
        ProjectItem(
            id = "p3",
            title = "Aniversário Lu",
            duration = "02:10",
            date = "12 set",
            thumbUrl = img("vid3"),
            aspectRatio = AspectRatio.RATIO_16_9,
            clips = listOf(
                MediaClip(id = "c6", title = "Parabéns", uri = img("vid3_clip1"), durationMs = 60000L),
                MediaClip(id = "c7", title = "Bolo", uri = img("vid3_clip2"), durationMs = 70000L)
            )
        ),
        ProjectItem(
            id = "p4",
            title = "Tutorial Café Espresso",
            duration = "03:45",
            date = "08 set",
            thumbUrl = img("vid4"),
            aspectRatio = AspectRatio.RATIO_1_1,
            clips = listOf(
                MediaClip(id = "c8", title = "Moagem", uri = img("vid4_clip1"), durationMs = 45000L),
                MediaClip(id = "c9", title = "Extração", uri = img("vid4_clip2"), durationMs = 60000L)
            )
        )
    )

    val templates: List<VideoTemplateItem> = listOf(
        VideoTemplateItem(id = "t1", title = "Vlog Cinematográfico", category = "Em alta", clipsCount = 6, duration = "00:30", thumbUrl = img("tp1", 500, 700)),
        VideoTemplateItem(id = "t2", title = "Beat Sync Reels", category = "Redes", clipsCount = 8, duration = "00:15", thumbUrl = img("tp2", 500, 700), isPremium = true),
        VideoTemplateItem(id = "t3", title = "Diário de Viagem", category = "Vlog", clipsCount = 5, duration = "00:45", thumbUrl = img("tp3", 500, 700)),
        VideoTemplateItem(id = "t4", title = "Promo Produto", category = "Redes", clipsCount = 4, duration = "00:20", thumbUrl = img("tp4", 500, 700), isPremium = true),
        VideoTemplateItem(id = "t5", title = "Aftermovie Festa", category = "Em alta", clipsCount = 10, duration = "01:00", thumbUrl = img("tp5", 500, 700)),
        VideoTemplateItem(id = "t6", title = "Rotina Matinal", category = "Vlog", clipsCount = 7, duration = "00:35", thumbUrl = img("tp6", 500, 700))
    )

    val templateCategories = listOf("Tudo", "Em alta", "Vlog", "Redes", "Viagem", "Comida")

    val audioTracks: List<AudioTrackItem> = listOf(
        AudioTrackItem(id = "a1", name = "Summer Vibes", category = "Happy", duration = "02:34"),
        AudioTrackItem(id = "a2", name = "Neon Nights", category = "Pop", duration = "03:12"),
        AudioTrackItem(id = "a3", name = "Morning Coffee", category = "Vlog", duration = "02:05"),
        AudioTrackItem(id = "a4", name = "Urban Beat", category = "Pop", duration = "02:48"),
        AudioTrackItem(id = "a5", name = "Sunset Drive", category = "Happy", duration = "03:30"),
        AudioTrackItem(id = "a6", name = "Chill Lounge", category = "Vlog", duration = "02:20"),
        AudioTrackItem(id = "a7", name = "Cinematic Epic", category = "Cinemático", duration = "04:12")
    )

    val audioCategories = listOf("Tudo", "Happy", "Pop", "Vlog", "Cinemático")

    val effects: List<VFXEffectItem> = listOf(
        VFXEffectItem(id = "e_halo_blur", name = "Halo Blur", category = "Básico", thumbUrl = img("ef_halo", 200, 200)),
        VFXEffectItem(id = "e_diamond_zoom", name = "Diamond Zoom", category = "Básico", thumbUrl = img("ef_diamond", 200, 200)),
        VFXEffectItem(id = "e_motion_blur", name = "Motion Blur", category = "Básico", thumbUrl = img("ef_motion", 200, 200)),
        VFXEffectItem(id = "e_fisheye_2", name = "Fisheye II", category = "Básico", thumbUrl = img("ef_fish2", 200, 200)),
        VFXEffectItem(id = "e_fisheye_4", name = "Fisheye 4", category = "Lente", thumbUrl = img("ef_fish4", 200, 200)),
        VFXEffectItem(id = "e_trans_fall", name = "Transform to Fall", category = "Movimento", thumbUrl = img("ef_fall", 200, 200)),
        VFXEffectItem(id = "e_wide_angle", name = "Wide Angle", category = "Lente", thumbUrl = img("ef_wide", 200, 200)),
        VFXEffectItem(id = "e1", name = "Desfoque", category = "Básico", thumbUrl = img("ef1", 200, 200)),
        VFXEffectItem(id = "e2", name = "Glitch", category = "Distorção", thumbUrl = img("ef2", 200, 200), isPremium = true),
        VFXEffectItem(id = "e3", name = "RGB Split", category = "Distorção", thumbUrl = img("ef3", 200, 200), isPremium = true),
        VFXEffectItem(id = "e4", name = "Shake", category = "Básico", thumbUrl = img("ef4", 200, 200)),
        VFXEffectItem(id = "e7", name = "VHS 90s", category = "Retrô", thumbUrl = img("ef7", 200, 200), isPremium = true)
    )

    val transitions: List<TransitionItem> = listOf(
        TransitionItem(id = "tr1", name = "Dissolver", iconName = "contrast"),
        TransitionItem(id = "tr2", name = "Fade", iconName = "wb_sunny"),
        TransitionItem(id = "tr3", name = "Zoom", iconName = "zoom_in"),
        TransitionItem(id = "tr4", name = "Girar", iconName = "sync"),
        TransitionItem(id = "tr5", name = "Flash", iconName = "flash_on"),
        TransitionItem(id = "tr6", name = "Ondular", iconName = "waves")
    )

    val filtersList = listOf("Original", "Vívido", "Frio", "Quente", "P&B", "Filme", "Retrô", "Suave", "Cyberpunk", "Golden Hour")

    val textColors = listOf("#FFFFFF", "#000000", "#7C3AED", "#EF4444", "#22C55E", "#F59E0B", "#3B82F6", "#EC4899", "#A855F7")

    val captionLanguages = listOf("Português (BR)", "English (US)", "Español", "Français", "日本語", "Italiano", "Deutsch")

    val demoGalleryMedia = List(12) { i ->
        MediaClip(
            id = "demo_clip_$i",
            title = if (i % 2 == 0) "Clipe ${i + 1}" else "Foto ${i + 1}",
            uri = img("gallery_$i", 600, 800),
            type = if (i % 2 == 0) MediaType.VIDEO else MediaType.PHOTO,
            durationMs = ((i + 2) * 3500L)
        )
    }
}
