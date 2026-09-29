package com.example.transition

enum class TransitionType(val displayName: String, val iconName: String) {
    CUT("Corte Seco", "content_cut"),
    DISSOLVE("Dissolver", "contrast"),
    FADE("Fade (Esmaecer)", "wb_sunny"),
    SLIDE_LEFT("Deslizar Esquerda", "chevron_left"),
    SLIDE_RIGHT("Deslizar Direita", "chevron_right"),
    SLIDE_UP("Deslizar Cima", "keyboard_arrow_up"),
    SLIDE_DOWN("Deslizar Baixo", "keyboard_arrow_down"),
    ZOOM("Zoom", "zoom_in"),
    WIPE("Cortina (Wipe)", "waves");

    companion object {
        fun fromName(name: String?): TransitionType {
            if (name == null) return CUT
            val clean = name.trim().lowercase()
            return when {
                clean == "dissolver" || clean == "dissolve" -> DISSOLVE
                clean == "fade" || clean.contains("esmaecer") -> FADE
                clean.contains("esquerda") || clean.contains("left") -> SLIDE_LEFT
                clean.contains("direita") || clean.contains("right") -> SLIDE_RIGHT
                clean.contains("cima") || clean.contains("up") -> SLIDE_UP
                clean.contains("baixo") || clean.contains("down") -> SLIDE_DOWN
                clean.contains("zoom") -> ZOOM
                clean.contains("wipe") || clean.contains("ondular") || clean.contains("cortina") -> WIPE
                clean == "corte seco" || clean == "cut" || clean == "nenhuma" -> CUT
                else -> DISSOLVE
            }
        }
    }
}
