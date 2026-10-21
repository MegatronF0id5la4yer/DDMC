package com.sonora.music

import android.content.Context
import android.graphics.*
import android.media.MediaPlayer
import android.util.LruCache
import android.view.MotionEvent
import android.view.View
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * Manages all DDSC `.webp` and `.png` character sprites from `assets/ddsc/`
 * AND all interactive PinkChan Chibi Shimeji skins & sounds from `assets/skins/` and `assets/sounds/`.
 *
 * Includes:
 * - Automatic opaque bounding-box cropping (`cropToOpaqueBounds`) so neither 960x960 DDSC sprites
 *   nor 128x128 PinkChan Chibi frames suffer from huge transparent borders or floating offsets.
 * - Head/face avatar extraction (`loadFaceAvatarBitmap`) so circular avatars center on the face.
 * - Full PinkChan Shimeji physics & behavior state machine (`WALK`, `RUN`, `STAND`, `SIT`/`SNUG`,
 *   `HAPPY_JUMP`, `CLIMB_LEFT`, `CLIMB_RIGHT`, `CEILING`, `FALL`, `DRAGGED`, `FLUNG`, `ANGRY`, `CUSTOM_ACTION`).
 */
object DdscEngine {

    data class OutfitSpriteSet(
        val key: String,
        val dokiId: String,
        val dokiName: String,
        val outfitLabel: String,
        val colorHex: String,
        val folder: String,
        val idle: String,
        val speak: String,
        val blink: String,
        val feliz: List<String>,
        val pensando: List<String>,
        val examen: List<String>,
        val molesta: List<String>
    )

    data class CustomActionDef(
        val id: String,
        val label: String,
        val emoji: String,
        val quote: String,
        val frameType: String // "jump", "snug", "stand", "walk", "guitar", "box", "clone"
    )

    data class PinkChanSkin(
        val dokiId: String,
        val skinFolder: String,
        val soundFolder: String,
        val displayName: String,
        val colorHex: String,
        val nativeFacesLeft: Boolean,
        val quotesIdle: List<String>,
        val quotesPoke: List<String>,
        val quotesPet: List<String>,
        val quotesAngry: List<String>,
        val quotesMusic: List<String>,
        val customActions: List<CustomActionDef>
    )

    val PINKCHAN_SKINS: Map<String, PinkChanSkin> = mapOf(
        "natsuki" to PinkChanSkin(
            dokiId = "natsuki",
            skinFolder = "Natsuki",
            soundFolder = "natsuki",
            displayName = "Natsuki",
            colorHex = "#F472B6",
            nativeFacesLeft = true,
            quotesIdle = listOf(
                "¡El manga TAMBIÉN es literatura!",
                "Hice cupcakes... pero no son para ti, ¿eh?",
                "¡Deja de mirarme así, tonto!",
                "¿Vamos a leer Parfait Girls o qué?"
            ),
            quotesPoke = listOf(
                "¡Oye! ¡No soy chiquita!",
                "¡B-Baka! ¡No me toques así!",
                "¡Te voy a dar un puñetazo si sigues!",
                "¡Hmph! Qué molesto eres..."
            ),
            quotesPet = listOf(
                "N-No es como si me gustara que me acaricies...",
                "Bueno... solo un poquito más...",
                "Hmph... tienes suerte de que hoy estoy de buenas 💖"
            ),
            quotesAngry = listOf(
                "¡¡YA BASTA!! ¡¡NO SOY TIERNA!! 💢",
                "¡¡TE DIJE QUE NO ME MOLESTES, BAKA!! 💢"
            ),
            quotesMusic = listOf(
                "¡Oye, este ritmo está bastante genial! ♪",
                "¡Súbele más a esta canción! ♫",
                "Mmm~ me dan ganas de hornear al ritmo de esto 🧁"
            ),
            customActions = listOf(
                CustomActionDef("cupcake", "Comer Cupcake", "🧁", "¡Los mejores cupcakes del club! 🧁✨", "snug"),
                CustomActionDef("manga", "Leer Parfait Girls", "📖", "No me interrumpas, estoy en la mejor parte 📖", "snug"),
                CustomActionDef("tsundere", "Berrinche Tsundere", "💢", "¡¡N-NO ESTOY HACIENDO PUCHERO!! 💢", "jump"),
                CustomActionDef("clone", "Invocar Clon", "✨", "¡Doble Natsuki en el escenario!", "clone")
            )
        ),
        "monika" to PinkChanSkin(
            dokiId = "monika",
            skinFolder = "Monika",
            soundFolder = "monika",
            displayName = "Monika",
            colorHex = "#34D399",
            nativeFacesLeft = true,
            quotesIdle = listOf(
                "¡Bienvenidos al Club de Literatura! 💚",
                "Estuve practicando esta canción en el piano~",
                "Siempre es un buen momento para escuchar música contigo.",
                "Just Monika~"
            ),
            quotesPoke = listOf(
                "Jeje~ ¿Necesitas mi atención?",
                "¡Aquí estoy, no me voy a ir a ningún lado!",
                "¿Qué pasa? ¿Cambiamos de pista?"
            ),
            quotesPet = listOf(
                "Aww, gracias~ Eres muy dulce conmigo 💚",
                "Podría quedarme aquí contigo para siempre~ 💖",
                "Jeje, me haces muy feliz~"
            ),
            quotesAngry = listOf(
                "Oye... ¿estás intentando borrar mi archivo? 💢",
                "Será mejor que te portes bien conmigo~ 💻"
            ),
            quotesMusic = listOf(
                "Every day, I imagine a future where I can be with you~ 🎹",
                "¡Qué buena armonía tiene este tema! ♪",
                "Me encanta tu gusto musical 💚"
            ),
            customActions = listOf(
                CustomActionDef("piano", "Tocar Piano", "🎹", "Tocando 'Your Reality' para ti~ 🎹💚", "snug"),
                CustomActionDef("glitch", "Glitch de Realidad", "💻", "J̵u̵s̵t̵ ̵M̵o̵n̵i̵k̵a̵.̵chr actualizado~ 💻", "jump"),
                CustomActionDef("poema", "Escribir Poema", "✒️", "Cada palabra es para ti~ ✒️", "stand"),
                CustomActionDef("clone", "Clon de Código", "✨", "¡Compilando otra instancia!", "clone")
            )
        ),
        "sayori" to PinkChanSkin(
            dokiId = "sayori",
            skinFolder = "Sayori",
            soundFolder = "sayori",
            displayName = "Sayori",
            colorHex = "#FB7185",
            nativeFacesLeft = true,
            quotesIdle = listOf(
                "Ehehe~ ¿Tienes algo de comer? 🍪",
                "¡Hoy va a ser un día súper divertido!",
                "Me quedé dormida otra vez... jeje~",
                "¡Escuchar música contigo me alegra el corazón!"
            ),
            quotesPoke = listOf(
                "¡Ay! ¡Me hiciste cosquillas! Ehehe~",
                "¿Eh? ¿Me vas a regalar una galleta?",
                "¡Oye, casi me tropiezo! Jeje~"
            ),
            quotesPet = listOf(
                "¡Yaaay! ¡Abrazos y cariñitos! 💖",
                "Ehehe~ ¡Eres el mejor amigo del mundo! ☀️",
                "Qué calentito se siente~"
            ),
            quotesAngry = listOf(
                "¡Buaaa! ¡Eres un malvado conmigo! 😭💢",
                "¡Me debes dos galletas gigantes por eso! 💢"
            ),
            quotesMusic = listOf(
                "¡Wiii! ¡Vamos a bailar con esta canción! 🎶",
                "¡Este ritmo me da mucha energía! ☀️",
                "¡La-la-la~ qué bonita melodía! ♪"
            ),
            customActions = listOf(
                CustomActionDef("cookie", "Galleta Gigante", "🍪", "¡¡ÑAM ÑAM!! ¡Deliciosa galleta! 🍪✨", "jump"),
                CustomActionDef("mrcow", "Abrazar a Mr. Cow", "🐄", "Mr. Cow también quiere escuchar música~ 🐄💖", "snug"),
                CustomActionDef("sunshine", "Salto de Alegría", "☀️", "¡¡Arriba ese ánimo!! ☀️", "jump"),
                CustomActionDef("clone", "Clon Feliz", "✨", "¡Más amigos para el club!", "clone")
            )
        ),
        "yuri" to PinkChanSkin(
            dokiId = "yuri",
            skinFolder = "Yuri",
            soundFolder = "yuri",
            displayName = "Yuri",
            colorHex = "#A855F7",
            nativeFacesLeft = true,
            quotesIdle = listOf(
                "¿Te gustaría una taza de té Oolong mientras escuchamos? ☕",
                "La atmósfera de esta canción es tan profunda...",
                "Estaba leyendo un capítulo fascinante...",
                "El sonido envolvente ayuda a concentrarse..."
            ),
            quotesPoke = listOf(
                "¡A-Ah! M-Me asustaste...",
                "¿D-Disculpa? ¿Dije algo extraño?",
                "Uuu... por favor no me mires tan de cerca..."
            ),
            quotesPet = listOf(
                "A-Ah... esto es muy reconfortante... 💜",
                "Mi corazón late muy rápido cuando haces eso... 💖",
                "G-Gracias... eres muy amable conmigo..."
            ),
            quotesAngry = listOf(
                "Por favor... estás invadiendo mi espacio personal... 👁️",
                "Quiero quedarme mirándote... para siempre... 👁️💜"
            ),
            quotesMusic = listOf(
                "Qué instrumentación tan exquisita y melancólica~ ♪",
                "Siento cada nota resonar en el ambiente... ♫",
                "Esta melodía combina perfecto con un buen libro 📖"
            ),
            customActions = listOf(
                CustomActionDef("tea", "Hora del Té Oolong", "☕", "Preparé té aromático para ambos~ ☕💜", "snug"),
                CustomActionDef("markov", "Retrato de Markov", "👁️", "Este libro tiene una historia hipnótica... 👁️📖", "snug"),
                CustomActionDef("shy", "Suspiro Tímido", "💜", "Uuu... qué momento tan agradable~", "stand"),
                CustomActionDef("clone", "Espejo Literario", "✨", "Una proyección paralela...", "clone")
            )
        ),
        "bocchi" to PinkChanSkin(
            dokiId = "bocchi",
            skinFolder = "Bocchi",
            soundFolder = "bocchi",
            displayName = "Bocchi",
            colorHex = "#EC4899",
            nativeFacesLeft = false,
            quotesIdle = listOf(
                "M-Mi nombre es Hitori Gotoh... 🎸",
                "Quiero esconderme en mi caja de mango... 📦",
                "Si subo un cover a YouTube tendré millones de visitas... jeje...",
                "La guitarra es mi única amiga..."
            ),
            quotesPoke = listOf(
                "¡¡GYAAAAH!! ¡Me hablaste! 🫠",
                "¡P-Perdón por existir en tu reproductor!",
                "¡Me voy a convertir en polvo espacial...!"
            ),
            quotesPet = listOf(
                "¿E-En serio te gusta cómo toco la guitarra...? 💖",
                "Ehehe... soy un monstruo sediento de aprobaciones~ ✨",
                "Qué cálido... ya no tengo tanto miedo..."
            ),
            quotesAngry = listOf(
                "¡¡MODO GLITCH DE ANSIEDAD SOCIAL ACTIVADO!! 🫠⚡",
                "¡¡NO ME OBLIGUES A TRABAJAR EN STARRY!! 😭"
            ),
            quotesMusic = listOf(
                "¡¡Ese riff de guitarra está brutal!! 🎸🔥",
                "¡Kessoku Band podría tocar este temazo! 🎶",
                "¡Vamos con el solo de guitarra! 🎸✨"
            ),
            customActions = listOf(
                CustomActionDef("guitar", "Solo de Guitarra", "🎸", "¡¡Tocando a máxima velocidad con Kessoku Band!! 🎸🔥", "guitar"),
                CustomActionDef("box", "Modo Caja de Mango", "📦", "Aquí adentro nadie puede juzgarme... 📦", "box"),
                CustomActionDef("panic", "Crisis Social", "🫠", "¡¡Aaaah demasiada gente mirándome!!", "jump")
            )
        ),
        "konata" to PinkChanSkin(
            dokiId = "konata",
            skinFolder = "Konata",
            soundFolder = "konata",
            displayName = "Konata",
            colorHex = "#38BDF8",
            nativeFacesLeft = false,
            quotesIdle = listOf(
                "¿De qué lado se empieza a comer el cuernito de chocolate? 🥐",
                "Estuve leveleando toda la noche en el MMO~ 🎮",
                "¡Los openings de anime en FLAC suenan de lujo!",
                "Timotei~ Timotei~ ✨"
            ),
            quotesPoke = listOf(
                "¿Qué onda? ¿Sale partida cooperativa? 🎮",
                "Jeje, tengo reflejos de gamer nivel 99~",
                "¡No me distraigas del combo!"
            ),
            quotesPet = listOf(
                "¡Subiste +100 puntos de amistad! 💖",
                "Jeje~ eres todo un hombre de cultura ✨",
                "¡Así sí dan ganas de estudiar... mañana! 😆"
            ),
            quotesAngry = listOf(
                "¡¡LAG EN EL SERVIDOR!! ¡¡NOOOO!! 💢",
                "¡Kagami me va a regañar si no termino la tarea! 💢"
            ),
            quotesMusic = listOf(
                "¡Este temazo parece opening de temporada nueva! 🎶",
                "¡Modo Hare Hare Yukai activado! 💃✨",
                "¡Súbele al bajo que retumbe! 🎧"
            ),
            customActions = listOf(
                CustomActionDef("dance", "Baile Hare Hare", "💃", "¡Bailando la coreografía completa! 💃🎶", "jump"),
                CustomActionDef("cornet", "Cuernito de Chocolate", "🥐", "Empezando por la punta... ¿o por la base? 🥐", "snug"),
                CustomActionDef("gamer", "Modo Gamer MMO", "🎮", "¡Farmear canciones legendarias! 🎮", "stand")
            )
        )
    )

    val OUTFIT_SETS = listOf(
        OutfitSpriteSet(
            key = "natsuki_casual",
            dokiId = "natsuki",
            dokiName = "Natsuki",
            outfitLabel = "Ropa Casual",
            colorHex = "#F472B6",
            folder = "natsuki casual",
            idle = "natsuki casual/Casual1.webp",
            speak = "natsuki casual/Casual2.webp",
            blink = "natsuki casual/Casual5.webp",
            feliz = listOf("natsuki casual/Casual10.webp", "natsuki casual/Casual11.webp"),
            pensando = listOf("natsuki casual/Casual7.webp", "natsuki casual/Casual8.webp"),
            examen = listOf("natsuki casual/Casual14.webp", "natsuki casual/Casual15.webp"),
            molesta = listOf("natsuki casual/Casual13.webp", "natsuki casual/Casual18.webp")
        ),
        OutfitSpriteSet(
            key = "natsuki_escuela",
            dokiId = "natsuki",
            dokiName = "Natsuki",
            outfitLabel = "Uniforme Escolar",
            colorHex = "#F472B6",
            folder = "natsuki escuela",
            idle = "natsuki escuela/Schoola.webp",
            speak = "natsuki escuela/Schoolb.webp",
            blink = "natsuki escuela/Schoole.webp",
            feliz = listOf("natsuki escuela/Schoolj.webp", "natsuki escuela/Schoolk.webp"),
            pensando = listOf("natsuki escuela/NatsukiCrossedArms.webp", "natsuki escuela/Schoolp.webp"),
            examen = listOf("natsuki escuela/Natsuki_Grin.webp", "natsuki escuela/Schoolg.webp"),
            molesta = listOf("natsuki escuela/Natsukiheadturn.webp", "natsuki escuela/Natsukivomit.webp")
        ),
        OutfitSpriteSet(
            key = "monika_escuela",
            dokiId = "monika",
            dokiName = "Monika",
            outfitLabel = "Uniforme Club",
            colorHex = "#34D399",
            folder = "monika escuela",
            idle = "monika escuela/Mon1.webp",
            speak = "monika escuela/Mon2.webp",
            blink = "monika escuela/Mon5.webp",
            feliz = listOf("monika escuela/Mon3.webp", "monika escuela/Ika1.webp"),
            pensando = listOf("monika escuela/Mon8.webp", "monika escuela/3aa.webp"),
            examen = listOf("monika escuela/Mon12.webp", "monika escuela/Ika6.webp"),
            molesta = listOf("monika escuela/Mon15.webp", "monika escuela/Ika12.webp")
        ),
        OutfitSpriteSet(
            key = "sayori_casual",
            dokiId = "sayori",
            dokiName = "Sayori",
            outfitLabel = "Ropa Casual",
            colorHex = "#FB7185",
            folder = "sayori casual",
            idle = "sayori casual/Yori1.webp",
            speak = "sayori casual/Yori2.webp",
            blink = "sayori casual/Yori5.webp",
            feliz = listOf("sayori casual/Yori10.webp", "sayori casual/7.webp"),
            pensando = listOf("sayori casual/Z1.webp", "sayori casual/Yori8.webp"),
            examen = listOf("sayori casual/Yori16.webp", "sayori casual/Z10.webp"),
            molesta = listOf("sayori casual/Yori21.webp", "sayori casual/Z18.webp")
        ),
        OutfitSpriteSet(
            key = "sayori_escuela",
            dokiId = "sayori",
            dokiName = "Sayori",
            outfitLabel = "Uniforme Escolar",
            colorHex = "#FB7185",
            folder = "sayori escuela",
            idle = "sayori escuela/Ori1.webp",
            speak = "sayori escuela/Ori2.webp",
            blink = "sayori escuela/Ori5.webp",
            feliz = listOf("sayori escuela/Say4.webp", "sayori escuela/Ori10.webp"),
            pensando = listOf("sayori escuela/3a.webp", "sayori escuela/Ori14.webp"),
            examen = listOf("sayori escuela/Ori18.webp", "sayori escuela/Say12.webp"),
            molesta = listOf("sayori escuela/Ori22.webp", "sayori escuela/Say20.webp")
        ),
        OutfitSpriteSet(
            key = "yuri_escuela",
            dokiId = "yuri",
            dokiName = "Yuri",
            outfitLabel = "Uniforme Escolar",
            colorHex = "#A855F7",
            folder = "yuri escuela",
            idle = "yuri escuela/YBashful_Smile.webp",
            speak = "yuri escuela/YHappy2.webp",
            blink = "yuri escuela/Yuri_Exhale.webp",
            feliz = listOf("yuri escuela/YSHappy.webp", "yuri escuela/YBashful_Smile_2.webp"),
            pensando = listOf("yuri escuela/YLook.webp", "yuri escuela/13.webp"),
            examen = listOf("yuri escuela/Yuri_school_1.webp", "yuri escuela/YShy1.webp"),
            molesta = listOf("yuri escuela/YAngry.webp", "yuri escuela/YSLookaway.webp")
        ),
        OutfitSpriteSet(
            key = "yuri_casual",
            dokiId = "yuri",
            dokiName = "Yuri",
            outfitLabel = "Suéter Casual",
            colorHex = "#A855F7",
            folder = "yuri casual",
            idle = "yuri casual/Casualclothes1-0.webp",
            speak = "yuri casual/Casualclothes1-5.webp",
            blink = "yuri casual/Casualclothes1-4.webp",
            feliz = listOf("yuri casual/22.webp", "yuri casual/Casualclothes1-6.webp"),
            pensando = listOf("yuri casual/YOutSGlance.webp"),
            examen = listOf("yuri casual/YOutLookAway.webp"),
            molesta = listOf("yuri casual/YOutWorried.webp", "yuri casual/YOutSad.webp")
        )
    )

    private val bitmapCache = object : LruCache<String, Bitmap>(64) {}
    private val shadowCache = object : LruCache<String, Bitmap>(36) {}
    private val avatarCache = object : LruCache<String, Bitmap>(16) {}
    private val folderBoundsCache = HashMap<String, Rect>()
    private var cachedManifest: Map<String, List<String>>? = null
    private var soundPlayer: MediaPlayer? = null

    val EXPRESSIONS = listOf(
        "shimeji" to "Chibi PinkChan (Interactivo)",
        "idle" to "Doki HD (Lip-Sync)",
        "feliz" to "Doki HD • Feliz",
        "pensando" to "Doki HD • Pensativa",
        "examen" to "Doki HD • Sorprendida",
        "molesta" to "Doki HD • Puchero"
    )

    fun getSkin(dokiId: String): PinkChanSkin =
        PINKCHAN_SKINS[dokiId.lowercase()] ?: PINKCHAN_SKINS["natsuki"]!!

    /** Plays a character voice/sound effect from `assets/sounds/{character}/` when poked, petted, or performing actions. */
    fun playCharacterSound(context: Context, dokiId: String) {
        // Silenced per user request: no doki voice audio
        return
    }

    /**
     * Scans the non-transparent bounding box `(minX, minY, maxX, maxY)` of a sprite bitmap.
     * Eliminates huge empty transparent margins around 960x960 DDSC sprites and 128x128 Chibi sprites.
     */
    fun computeOpaqueBounds(bmp: Bitmap, keepBottomAnchored: Boolean): Rect {
        val w = bmp.width
        val h = bmp.height
        if (w <= 4 || h <= 4) return Rect(0, 0, w, h)
        val pixels = IntArray(w * h)
        bmp.getPixels(pixels, 0, w, 0, 0, w, h)

        var minX = w
        var minY = h
        var maxX = 0
        var maxY = 0
        val step = if (w >= 400) 2 else 1

        var y = 0
        while (y < h) {
            val rowOff = y * w
            var x = 0
            while (x < w) {
                val alpha = (pixels[rowOff + x] ushr 24) and 0xFF
                if (alpha > 14) {
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                }
                x += step
            }
            y += step
        }

        if (minX >= maxX || minY >= maxY) return Rect(0, 0, w, h)
        val pad = if (w >= 400) 6 else 1
        val left = (minX - pad).coerceAtLeast(0)
        val top = (minY - pad).coerceAtLeast(0)
        val right = (maxX + pad + 1).coerceAtMost(w)
        val bottom = if (keepBottomAnchored) h else (maxY + pad + 1).coerceAtMost(h)
        return Rect(left, top, right, bottom)
    }

    fun cropToBounds(bmp: Bitmap, rect: Rect): Bitmap {
        val cw = (rect.right - rect.left).coerceIn(1, bmp.width - rect.left)
        val ch = (rect.bottom - rect.top).coerceIn(1, bmp.height - rect.top)
        if (rect.left == 0 && rect.top == 0 && cw == bmp.width && ch == bmp.height) return bmp
        return try {
            Bitmap.createBitmap(bmp, rect.left, rect.top, cw, ch)
        } catch (_: Exception) {
            bmp
        }
    }

    /**
     * Loads a PinkChan Chibi frame from `assets/skins/{skinFolder}/{frameName}` (with fallback to `assets/ddsc/`)
     * and crops it tightly to its opaque bounding box so floor/wall/ceiling anchoring is pixel-exact!
     */
    fun loadChibiFrame(context: Context, skinFolder: String, frameName: String): Bitmap? {
        val cacheKey = "chibi:$skinFolder/$frameName"
        bitmapCache.get(cacheKey)?.let { if (!it.isRecycled) return it }

        val pathsToTry = listOf(
            "skins/$skinFolder/$frameName",
            "ddsc/$skinFolder/$frameName",
            "skins/$skinFolder/stand.png",
            "skins/$skinFolder/shime1.png"
        )
        for (p in pathsToTry) {
            try {
                context.assets.open(p).use { stream ->
                    val raw = BitmapFactory.decodeStream(stream)
                    if (raw != null) {
                        val bounds = computeOpaqueBounds(raw, keepBottomAnchored = false)
                        val cropped = cropToBounds(raw, bounds)
                        bitmapCache.put(cacheKey, cropped)
                        return cropped
                    }
                }
            } catch (_: Exception) {}
        }
        return null
    }

    /**
     * Loads a close-up face/head avatar bitmap for the top bar and character selector chips.
     */
    fun loadFaceAvatarBitmap(context: Context, dokiId: String, pose: String = "casual"): Bitmap? {
        val key = "face:$dokiId:$pose"
        avatarCache.get(key)?.let { if (!it.isRecycled) return it }

        val lower = dokiId.lowercase()
        if (lower == "bocchi" || lower == "konata") {
            val skin = getSkin(lower)
            val chibi = loadChibiFrame(context, skin.skinFolder, "stand.png")
                ?: loadChibiFrame(context, skin.skinFolder, "walk1.png")
            if (chibi != null) {
                avatarCache.put(key, chibi)
                return chibi
            }
        }

        val set = resolveOutfitSet(lower, pose)
        val full = loadSpriteBitmap(context, set.idle) ?: return null
        val w = full.width
        val h = full.height
        // The cropped DDSC sprite has the head in the top ~36% of the height
        val cropSize = min(w, (h * 0.42f).toInt()).coerceAtLeast(32)
        val left = ((w - cropSize) / 2).coerceAtLeast(0)
        val top = 0
        val headBmp = try {
            Bitmap.createBitmap(full, left, top, cropSize.coerceAtMost(w - left), cropSize.coerceAtMost(h))
        } catch (_: Exception) {
            full
        }
        avatarCache.put(key, headBmp)
        return headBmp
    }

    fun getManifest(context: Context): Map<String, List<String>> {
        cachedManifest?.let { return it }
        val result = LinkedHashMap<String, List<String>>()
        try {
            val jsonStr = context.assets.open("ddsc/manifest.json").bufferedReader().use { it.readText() }
            val obj = JSONObject(jsonStr)
            val keys = obj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                if (k.equals("bg", ignoreCase = true)) continue // Never use bg backgrounds
                val arr = obj.getJSONArray(k)
                val list = ArrayList<String>(arr.length())
                for (i in 0 until arr.length()) {
                    list.add(arr.getString(i))
                }
                if (list.isNotEmpty()) {
                    result[k] = list
                }
            }
        } catch (_: Exception) {}
        cachedManifest = result
        return result
    }

    fun loadSpriteBitmap(context: Context, relativePath: String): Bitmap? {
        val clean = relativePath.removePrefix("img/").removePrefix("ddsc/")
        bitmapCache.get(clean)?.let { if (!it.isRecycled) return it }
        return try {
            val stream = try {
                context.assets.open("ddsc/$clean")
            } catch (_: Exception) {
                context.assets.open("skins/$clean")
            }
            stream.use { s ->
                val raw = BitmapFactory.decodeStream(s) ?: return null
                // Crop transparent margins consistently per outfit folder so blinking/speaking never jitters
                val folder = clean.substringBefore('/', "")
                val isChibi = raw.width <= 256 && raw.height <= 256
                val cropped = if (isChibi) {
                    val b = computeOpaqueBounds(raw, keepBottomAnchored = false)
                    cropToBounds(raw, b)
                } else {
                    val b = computeOpaqueBounds(raw, keepBottomAnchored = true)
                    val union = folderBoundsCache[folder]?.let { prev ->
                        Rect(
                            min(prev.left, b.left),
                            min(prev.top, b.top),
                            max(prev.right, b.right),
                            max(prev.bottom, b.bottom)
                        )
                    } ?: b
                    if (folder.isNotEmpty()) folderBoundsCache[folder] = union
                    cropToBounds(raw, union)
                }
                bitmapCache.put(clean, cropped)
                cropped
            }
        } catch (_: Exception) {
            null
        }
    }

    /** Builds a soft blurred contour drop-shadow bitmap from the transparent sprite's alpha channel. */
    fun getOrBuildShadow(bmp: Bitmap, key: String): Bitmap? {
        shadowCache.get(key)?.let { if (!it.isRecycled) return it }
        return try {
            val sw = (bmp.width / 2).coerceIn(24, 240)
            val sh = (bmp.height / 2).coerceIn(24, 240)
            val pad = 14
            val out = Bitmap.createBitmap(sw + pad * 2, sh + pad * 2, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(out)
            val scaled = Bitmap.createScaledBitmap(bmp, sw, sh, true)
            val alphaMask = scaled.extractAlpha()
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(175, 0, 0, 0)
                maskFilter = BlurMaskFilter(10f, BlurMaskFilter.Blur.NORMAL)
            }
            canvas.drawBitmap(alphaMask, pad.toFloat(), pad.toFloat(), paint)
            alphaMask.recycle()
            shadowCache.put(key, out)
            out
        } catch (_: Exception) {
            null
        }
    }

    fun loadSprite(context: Context, relativePath: String): Bitmap? =
        loadSpriteBitmap(context, relativePath)

    fun spritesForCharacter(context: Context, dokiId: String): List<String> {
        val manifest = getManifest(context)
        val prefix = dokiId.lowercase()
        val matched = ArrayList<String>()
        for ((folder, files) in manifest) {
            if (folder.equals("bg", ignoreCase = true)) continue
            if (folder.lowercase().startsWith(prefix)) {
                for (f in files) {
                    val clean = f.removePrefix("img/").removePrefix("ddsc/")
                    matched.add(clean)
                }
            }
        }
        // Also append PinkChan skin frames if available
        val skin = getSkin(dokiId)
        try {
            val skinFiles = context.assets.list("skins/${skin.skinFolder}")?.filter { it.endsWith(".png", true) } ?: emptyList()
            for (sf in skinFiles) {
                val rel = "${skin.skinFolder}/$sf"
                if (!matched.contains(rel)) matched.add(rel)
            }
        } catch (_: Exception) {}
        return matched
    }

    fun resolveOutfitSet(dokiId: String, pose: String): OutfitSpriteSet {
        return when (dokiId.lowercase()) {
            "natsuki" -> if (pose == "school") OUTFIT_SETS.first { it.key == "natsuki_escuela" }
                         else OUTFIT_SETS.first { it.key == "natsuki_casual" }
            "monika" -> OUTFIT_SETS.first { it.key == "monika_escuela" }
            "sayori" -> if (pose == "school") OUTFIT_SETS.first { it.key == "sayori_escuela" }
                        else OUTFIT_SETS.first { it.key == "sayori_casual" }
            "yuri" -> if (pose == "casual") OUTFIT_SETS.first { it.key == "yuri_casual" }
                      else OUTFIT_SETS.first { it.key == "yuri_escuela" }
            else -> OUTFIT_SETS.first()
        }
    }
}

/**
 * Dual-Mode Interactive Doki & PinkChan Shimeji Stage View:
 *
 * 1. **PinkChan Chibi Shimeji Mode (`isChibiShimejiMode == true` or `emotion == "shimeji"`)**:
 *    - Authentic 60fps PinkChan Shimeji physics & behaviors inside the stage:
 *      `WALK`, `RUN`, `STAND`, `SIT`/`SNUG`, `HAPPY_JUMP`, `CLIMB_LEFT`, `CLIMB_RIGHT`,
 *      `CEILING` (using `climb_top.png`), `FALL`, `DRAGGED` (using `air_swing_l.png` / `air_swing_r.png`),
 *      `FLUNG` (inertia + wall/floor bounce), `ANGRY`, and `CUSTOM_ACTION` (Cupcake, Manga, Piano, Galleta, Té, Guitarra, Caja, Clon).
 *    - Touch Interactions:
 *      - **Tap (Poke)**: Plays voice audio from `assets/sounds/`, shows comic speech bubble with Spanish dialogue, and triggers Tsundere/Angry mode if poked 5 times rapidly.
 *      - **Hold / Rub (Pet)**: Heals annoyance, spawns floating `💖` heart particles, and triggers Happy Jump.
 *      - **Drag & Fling**: Grab the Chibi with your finger, swing her in mid-air, and fling her across the stage!
 *
 * 2. **Doki HD Mode (`isChibiShimejiMode == false`)**:
 *    - Renders full-body transparent `.webp` DDSC sprites cropped to their opaque bounding box so they fill ~95% of the stage height cleanly.
 *    - Features soft contour drop-shadow, natural blinking, music-reactive lip-sync, breathing sway, AND interactive tap speech bubbles + voice audio!
 */
class DokiStageSpriteView(context: Context) : View(context) {

    enum class ShimejiState {
        STAND, WALK, RUN, SIT, HAPPY_JUMP,
        CLIMB_LEFT, CLIMB_RIGHT, CEILING,
        FALL, DRAGGED, FLUNG, ANGRY, CUSTOM_ACTION
    }

    private data class FloatingParticle(
        var x: Float,
        var y: Float,
        var vx: Float,
        var vy: Float,
        val symbol: String,
        val color: Int,
        var alpha: Float = 1f
    )

    private var activeDokiId: String = "natsuki"
    private var activePose: String = "casual"
    private var activeEmotion: String = "shimeji"

    private var currentPath: String = "natsuki casual/Casual1.webp"
    private var prevPath: String? = null
    private var crossfadeStartMs: Long = 0L
    private val crossfadeDurMs: Long = 150L

    private var idlePath: String = "natsuki casual/Casual1.webp"
    private var speakPath: String = "natsuki casual/Casual2.webp"
    private var blinkPath: String = "natsuki casual/Casual5.webp"
    private var customLockedPath: String? = null

    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val chibiPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = false }
    private val prevBitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val floorShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(110, 0, 0, 0)
    }
    private val bubbleBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(235, 20, 22, 34)
        style = Paint.Style.FILL
    }
    private val bubbleStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#F472B6")
        style = Paint.Style.STROKE
        strokeWidth = context.dpf(1.6f)
    }
    private val bubbleTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = context.dpf(11.5f)
        typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    private val particlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = context.dpf(14f)
        textAlign = Paint.Align.CENTER
    }

    private val destRect = RectF()
    private val shadowRect = RectF()
    private val bubbleRect = RectF()

    var isMusicPlaying: Boolean = false
    var isPlayingMusic: Boolean
        get() = isMusicPlaying
        set(value) {
            if (isMusicPlaying != value) {
                isMusicPlaying = value
                if (value && Random.nextFloat() < 0.65f) {
                    val skin = DdscEngine.getSkin(activeDokiId)
                    say(skin.quotesMusic.random(), 3200L)
                }
                if (isShown) postInvalidateOnAnimation()
            }
        }
    var audioEnergy: Float = 0f
    var currentSongBpm: Float = 120f
    private var lastDrawMs: Long = 0L
    private var breathPhase: Float = 0f
    private var lastBlinkCheckMs: Long = 0L
    private var blinkEndMs: Long = 0L

    // ---- PinkChan Shimeji Physics & State ----
    var shimejiState: ShimejiState = ShimejiState.WALK
        private set
    private var posX: Float = -1f
    private var posY: Float = -1f
    private var velX: Float = 0f
    private var velY: Float = 0f
    private var facingRight: Boolean = true
    private var stateTicks: Int = 0
    private var animTick: Int = 0
    private var annoyanceCount: Int = 0
    private var lastPokeMs: Long = 0L
    private var customActionEmoji: String = ""
    private var customActionFrameType: String = "snug"
    private var cloneActiveUntilMs: Long = 0L

    // Speech bubble state
    private var speechText: String = ""
    private var speechUntilMs: Long = 0L

    // Touch / Drag / Pet tracking
    private var downX: Float = 0f
    private var downY: Float = 0f
    private var lastTouchX: Float = 0f
    private var lastTouchY: Float = 0f
    private var touchDownMs: Long = 0L
    private var isDraggingChibi: Boolean = false
    private var petRubAccum: Float = 0f
    private val particles = ArrayList<FloatingParticle>()
    private var lastMusicNoteMs: Long = 0L

    var dokiScale: Float = 1.0f
        set(value) {
            field = value.coerceIn(0.5f, 2.5f)
            invalidate()
        }

    var isVoiceMuted: Boolean = true
        set(value) {
            field = value
            if (value) {
                speechText = ""
                speechUntilMs = 0L
            }
            invalidate()
        }

    var onDokiAngryAndSwitch: (() -> Unit)? = null

    val isChibiMode: Boolean
        get() = activeEmotion == "shimeji" || activeDokiId == "bocchi" || activeDokiId == "konata"

    fun say(text: String, durationMs: Long = 3000L) {
        // Silenced per user request: no speech bubbles / quotes
        return
    }

    fun triggerCustomAction(action: DdscEngine.CustomActionDef? = null) {
        val skin = DdscEngine.getSkin(activeDokiId)
        val chosen = action ?: skin.customActions.randomOrNull() ?: return
        shimejiState = ShimejiState.CUSTOM_ACTION
        stateTicks = 180
        customActionEmoji = chosen.emoji
        customActionFrameType = chosen.frameType
        if (chosen.id == "clone") {
            cloneActiveUntilMs = System.currentTimeMillis() + 6500L
        }
        if (!isVoiceMuted) {
            say(chosen.quote, 3600L)
            DdscEngine.playCharacterSound(context, activeDokiId)
        }
        spawnParticles(chosen.emoji, 5)
        postInvalidateOnAnimation()
    }

    fun triggerBehavior(type: String) {
        val skin = DdscEngine.getSkin(activeDokiId)
        when (type.lowercase()) {
            "jump" -> {
                shimejiState = ShimejiState.HAPPY_JUMP
                stateTicks = 80
                velY = -context.dpf(10.5f)
                say(skin.quotesPet.random(), 2800L)
                DdscEngine.playCharacterSound(context, activeDokiId)
                spawnParticles("💖", 5)
            }
            "custom" -> triggerCustomAction()
            "speak" -> {
                say(skin.quotesPoke.random(), 3200L)
                DdscEngine.playCharacterSound(context, activeDokiId)
                spawnParticles("🎵", 4)
            }
            "climb" -> {
                shimejiState = if (facingRight) ShimejiState.CLIMB_RIGHT else ShimejiState.CLIMB_LEFT
                stateTicks = 120
                say("¡A escalar!", 2400L)
            }
            "sit" -> {
                shimejiState = ShimejiState.SIT
                stateTicks = 160
                say(skin.quotesIdle.random(), 2800L)
            }
        }
        postInvalidateOnAnimation()
    }

    private fun spawnParticles(symbol: String, count: Int) {
        val cx = if (posX > 0f) posX else width / 2f
        val cy = if (posY > 0f) posY - context.dpf(60f) else height * 0.5f
        for (i in 0 until count) {
            particles.add(
                FloatingParticle(
                    x = cx + (Random.nextFloat() - 0.5f) * context.dpf(50f),
                    y = cy + (Random.nextFloat() - 0.5f) * context.dpf(30f),
                    vx = (Random.nextFloat() - 0.5f) * context.dpf(1.5f),
                    vy = -context.dpf(1.2f + Random.nextFloat() * 1.4f),
                    symbol = symbol,
                    color = Color.WHITE
                )
            )
        }
    }

    fun setDoki(dokiId: String, pose: String, emotion: String) {
        val changedChar = activeDokiId != dokiId
        activeDokiId = dokiId
        activePose = pose
        activeEmotion = emotion
        val skin = DdscEngine.getSkin(dokiId)
        bubbleStrokePaint.color = try { Color.parseColor(skin.colorHex) } catch (_: Exception) { Color.WHITE }

        val set = DdscEngine.resolveOutfitSet(dokiId, pose)
        configureSet(set, emotion, null)
        if (changedChar) {
            say(skin.quotesIdle.random(), 2800L)
        }
    }

    fun setCustomAsset(assetRel: String) {
        val set = DdscEngine.resolveOutfitSet(activeDokiId, activePose)
        val isSkinFrame = assetRel.startsWith("Monika/") || assetRel.startsWith("Natsuki/") ||
            assetRel.startsWith("Sayori/") || assetRel.startsWith("Yuri/") ||
            assetRel.startsWith("Bocchi/") || assetRel.startsWith("Konata/")
        if (isSkinFrame) {
            activeEmotion = "idle"
        }
        configureSet(set, activeEmotion, assetRel)
    }

    fun configureSet(set: DdscEngine.OutfitSpriteSet, emotion: String, customSprite: String?) {
        val oldTarget = currentPath
        idlePath = set.idle
        speakPath = set.speak
        blinkPath = set.blink
        customLockedPath = when {
            !customSprite.isNullOrEmpty() -> customSprite
            emotion == "feliz" -> set.feliz.firstOrNull()
            emotion == "pensando" -> set.pensando.firstOrNull()
            emotion == "examen" -> set.examen.firstOrNull()
            emotion == "molesta" -> set.molesta.firstOrNull()
            else -> null
        }
        val nextTarget = customLockedPath ?: idlePath
        if (nextTarget != oldTarget && oldTarget.isNotEmpty()) {
            prevPath = oldTarget
            crossfadeStartMs = System.currentTimeMillis()
        }
        currentPath = nextTarget
        if (!isChibiMode) {
            DdscEngine.loadSpriteBitmap(context, currentPath)
            if (customLockedPath == null) {
                DdscEngine.loadSpriteBitmap(context, speakPath)
                DdscEngine.loadSpriteBitmap(context, blinkPath)
            }
        }
        postInvalidateOnAnimation()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return super.onTouchEvent(event)

        val x = event.x
        val y = event.y
        val now = System.currentTimeMillis()
        val skin = DdscEngine.getSkin(activeDokiId)

        val isFullScreen = w > context.dpf(200f) && h > context.dpf(340f)
        if (isFullScreen && isChibiMode) {
            val dist = kotlin.math.hypot((x - posX).toDouble(), (y - (posY - context.dpf(55f) * dokiScale)).toDouble()).toFloat()
            if (event.actionMasked == MotionEvent.ACTION_DOWN && dist > context.dpf(85f) * dokiScale) {
                return false
            }
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = x
                downY = y
                lastTouchX = x
                lastTouchY = y
                touchDownMs = now
                petRubAccum = 0f
                isDraggingChibi = false
                parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = x - lastTouchX
                val dy = y - lastTouchY
                val distFromStart = kotlin.math.hypot((x - downX).toDouble(), (y - downY).toDouble()).toFloat()

                if (isChibiMode) {
                    // Check if user is gently rubbing horizontally to pet, or dragging across the stage
                    if (!isDraggingChibi && now - touchDownMs > 220L && abs(dy) < context.dpf(18f) && abs(dx) > context.dpf(2f)) {
                        petRubAccum += abs(dx)
                        if (petRubAccum > context.dpf(42f)) {
                            petRubAccum = 0f
                            annoyanceCount = 0
                            shimejiState = ShimejiState.HAPPY_JUMP
                            stateTicks = 55
                            say(skin.quotesPet.random(), 2800L)
                            spawnParticles("💖", 4)
                            DdscEngine.playCharacterSound(context, activeDokiId)
                        }
                    } else if (distFromStart > context.dpf(14f)) {
                        isDraggingChibi = true
                        shimejiState = ShimejiState.DRAGGED
                        posX = x.coerceIn(context.dpf(28f), w - context.dpf(28f))
                        posY = (y + context.dpf(36f)).coerceIn(context.dpf(50f), h)
                        velX = dx * 0.65f
                        velY = dy * 0.65f
                    }
                } else {
                    if (distFromStart > context.dpf(22f) && now - touchDownMs > 200L) {
                        petRubAccum += abs(dx) + abs(dy)
                        if (petRubAccum > context.dpf(70f)) {
                            petRubAccum = 0f
                            say(skin.quotesPet.random(), 2800L)
                            spawnParticles("💖", 4)
                            DdscEngine.playCharacterSound(context, activeDokiId)
                        }
                    }
                }
                lastTouchX = x
                lastTouchY = y
                postInvalidateOnAnimation()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                val totalMove = kotlin.math.hypot((x - downX).toDouble(), (y - downY).toDouble()).toFloat()
                val elapsed = now - touchDownMs

                if (isDraggingChibi && isChibiMode) {
                    isDraggingChibi = false
                    shimejiState = ShimejiState.FLUNG
                    velX = velX.coerceIn(-context.dpf(38f), context.dpf(38f))
                    velY = velY.coerceIn(-context.dpf(36f), context.dpf(25f))
                } else if (totalMove < context.dpf(14f) && elapsed < 380L) {
                    // Tap -> Poke interaction!
                    handlePokeInteraction(now, skin)
                    performClick()
                } else if (totalMove < context.dpf(14f) && elapsed >= 380L) {
                    // Long press -> Trigger a PinkChan Custom Action!
                    triggerCustomAction()
                }
                postInvalidateOnAnimation()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun handlePokeInteraction(now: Long, skin: DdscEngine.PinkChanSkin) {
        lastPokeMs = now
        // Animate comical angry reaction
        if (isChibiMode) {
            shimejiState = ShimejiState.ANGRY
            stateTicks = 70
            // Absurd physics launch on poke: tiny rage hop
            velY = -context.dpf(7.5f)
            velX = if (Random.nextBoolean()) context.dpf(5f) else -context.dpf(5f)
        } else {
            val outfit = DdscEngine.resolveOutfitSet(activeDokiId, activePose)
            val reactionSprites = if (outfit.molesta.isNotEmpty()) outfit.molesta else outfit.feliz
            if (reactionSprites.isNotEmpty()) {
                val chosen = reactionSprites.random()
                val folder = outfit.folder.ifEmpty { "$activeDokiId $activePose" }
                customLockedPath = "$folder/$chosen"
                postDelayed({
                    customLockedPath = null
                    postInvalidateOnAnimation()
                }, 1600L)
            }
            breathPhase += 1.2f
        }
        spawnParticles("💢", 5)
        postInvalidateOnAnimation()

        // Trigger character switch immediately as requested!
        onDokiAngryAndSwitch?.invoke()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (visibility == VISIBLE && isShown) {
            postInvalidateOnAnimation()
        }
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility == VISIBLE && isShown) {
            postInvalidateOnAnimation()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val now = System.currentTimeMillis()
        if (isChibiMode && customLockedPath == null) {
            drawPinkChanShimeji(canvas, w, h, now)
        } else {
            drawHdDdscSprite(canvas, w, h, now)
        }
        drawParticlesAndSpeechBubble(canvas, w, h, now)

        if (isShown && windowVisibility == VISIBLE) {
            postInvalidateOnAnimation()
        }
    }

    /**
     * Full PinkChan Shimeji 60fps behavior & physics engine inside the stage.
     */
    private fun drawPinkChanShimeji(canvas: Canvas, w: Float, h: Float, now: Long) {
        val skin = DdscEngine.getSkin(activeDokiId)
        val isFullScreen = w > context.dpf(200f) && h > context.dpf(340f)
        val floorY = if (isFullScreen) h - context.dpf(60f) else h - context.dpf(6f)
        val ceilingY = if (isFullScreen) context.dpf(52f) else context.dpf(8f)
        val marginX = context.dpf(20f)
        val leftWallX = marginX
        val rightWallX = (w - marginX).coerceAtLeast(leftWallX + 10f)

        if (posX < 0f || posY < 0f) {
            posX = w * 0.5f
            posY = floorY
        }

        animTick++
        stateTicks--

        // Spawn musical notes automatically when music is playing
        if (isMusicPlaying && now - lastMusicNoteMs > 1100L) {
            lastMusicNoteMs = now
            val note = if (Random.nextBoolean()) "♪" else "♫"
            particles.add(
                FloatingParticle(
                    x = posX + (Random.nextFloat() - 0.5f) * context.dpf(36f),
                    y = posY - context.dpf(75f),
                    vx = (Random.nextFloat() - 0.5f) * context.dpf(0.9f),
                    vy = -context.dpf(1.1f),
                    symbol = note,
                    color = Color.WHITE
                )
            )
        }

        val speedWalk = context.dpf(if (isMusicPlaying) 1.45f else 1.05f)
        val speedRun = context.dpf(2.35f)
        val speedClimb = context.dpf(1.25f)
        val gravity = context.dpf(0.48f)

        // State machine transitions & physics
        when (shimejiState) {
            ShimejiState.DRAGGED -> {
                // Controlled directly by touch move
            }
            ShimejiState.FLUNG -> {
                posX += velX
                posY += velY
                velY += gravity * 0.85f // slightly floatier gravity
                velX *= 0.992f // low air drag for absurd flight

                // Left wall superball pinball ricochet
                if (posX <= leftWallX) {
                    posX = leftWallX
                    velX = -velX * 1.05f // Absurd elastic amplification!
                    facingRight = true
                    spawnParticles("💥", 2)
                } else if (posX >= rightWallX) {
                    posX = rightWallX
                    velX = -velX * 1.05f
                    facingRight = false
                    spawnParticles("💥", 2)
                }

                // Ceiling ricochet
                if (posY <= ceilingY + context.dpf(50f)) {
                    posY = ceilingY + context.dpf(50f)
                    velY = -velY * 0.95f
                    spawnParticles("✨", 2)
                } else if (posY >= floorY) {
                    posY = floorY
                    // Absurd superball floor bounce: if moving with speed, bounce back up!
                    if (abs(velY) > context.dpf(3f)) {
                        velY = -velY * 0.88f // Bounce high!
                        velX *= 0.92f
                        spawnParticles("💨", 2)
                    } else {
                        velY = 0f
                        velX = 0f
                        shimejiState = ShimejiState.HAPPY_JUMP
                        stateTicks = 25
                    }
                }
            }
            ShimejiState.FALL -> {
                posY += velY
                velY += gravity
                if (posY >= floorY) {
                    posY = floorY
                    velY = 0f
                    shimejiState = ShimejiState.SIT
                    stateTicks = Random.nextInt(50, 95)
                }
            }
            ShimejiState.WALK, ShimejiState.RUN -> {
                val spd = if (shimejiState == ShimejiState.RUN) speedRun else speedWalk
                posX += if (facingRight) spd else -spd
                posY = floorY
                if (posX <= leftWallX) {
                    posX = leftWallX
                    if (Random.nextFloat() < 0.55f) {
                        shimejiState = ShimejiState.CLIMB_LEFT
                        stateTicks = Random.nextInt(85, 160)
                    } else {
                        facingRight = true
                    }
                } else if (posX >= rightWallX) {
                    posX = rightWallX
                    if (Random.nextFloat() < 0.55f) {
                        shimejiState = ShimejiState.CLIMB_RIGHT
                        stateTicks = Random.nextInt(85, 160)
                    } else {
                        facingRight = false
                    }
                }
                if (stateTicks <= 0) pickNextFloorBehavior()
            }
            ShimejiState.CLIMB_LEFT -> {
                posX = leftWallX
                facingRight = false
                posY -= speedClimb
                if (posY <= ceilingY + context.dpf(64f)) {
                    posY = ceilingY + context.dpf(64f)
                    shimejiState = ShimejiState.CEILING
                    facingRight = true
                    stateTicks = Random.nextInt(90, 170)
                } else if (stateTicks <= 0) {
                    shimejiState = ShimejiState.FALL
                    velY = 0f
                }
            }
            ShimejiState.CLIMB_RIGHT -> {
                posX = rightWallX
                facingRight = true
                posY -= speedClimb
                if (posY <= ceilingY + context.dpf(64f)) {
                    posY = ceilingY + context.dpf(64f)
                    shimejiState = ShimejiState.CEILING
                    facingRight = false
                    stateTicks = Random.nextInt(90, 170)
                } else if (stateTicks <= 0) {
                    shimejiState = ShimejiState.FALL
                    velY = 0f
                }
            }
            ShimejiState.CEILING -> {
                posY = ceilingY + context.dpf(64f)
                posX += if (facingRight) speedClimb * 0.85f else -speedClimb * 0.85f
                if (posX <= leftWallX) {
                    posX = leftWallX
                    facingRight = true
                } else if (posX >= rightWallX) {
                    posX = rightWallX
                    facingRight = false
                }
                if (stateTicks <= 0) {
                    shimejiState = ShimejiState.FALL
                    velY = 0f
                }
            }
            ShimejiState.STAND, ShimejiState.SIT, ShimejiState.HAPPY_JUMP, ShimejiState.ANGRY, ShimejiState.CUSTOM_ACTION -> {
                posY = floorY
                if (stateTicks <= 0) {
                    customActionEmoji = ""
                    pickNextFloorBehavior()
                }
            }
        }

        // Select the exact PinkChan frame for the current state
        val frameName = selectChibiFrameName(skin)
        val bmp = DdscEngine.loadChibiFrame(context, skin.skinFolder, frameName) ?: return

        // Target height: generous, crisp Chibi size with user-customizable dokiScale
        val baseH = if (isFullScreen) context.dpf(125f) else (h * 0.44f).coerceIn(context.dpf(96f), context.dpf(152f))
        val targetH = baseH * dokiScale
        val aspect = bmp.width.toFloat() / bmp.height.toFloat().coerceAtLeast(1f)
        val drawH = targetH
        val drawW = (drawH * aspect).coerceAtMost(w * 0.90f)

        // Music rhythm-locked beat bounce (calibrated to song BPM and delta time)
        val bpm = currentSongBpm.coerceIn(60f, 200f)
        val beatPeriodMs = (60000.0 / bpm).coerceIn(300.0, 1000.0)
        val beatBounceY = if (isMusicPlaying && shimejiState in listOf(ShimejiState.STAND, ShimejiState.WALK, ShimejiState.SIT, ShimejiState.HAPPY_JUMP, ShimejiState.CUSTOM_ACTION)) {
            val beatPhase = (now.toDouble() / beatPeriodMs) * 2.0 * Math.PI
            (abs(sin(beatPhase)).toFloat() * context.dpf(3.2f + audioEnergy * 6.5f)).coerceIn(0f, context.dpf(12f))
        } else 0f

        val jumpOffsetY = if (shimejiState == ShimejiState.HAPPY_JUMP) {
            abs(sin(animTick * 0.22f)) * context.dpf(18f)
        } else 0f

        val drawCenterX = posX.coerceIn(drawW * 0.45f, (w - drawW * 0.45f).coerceAtLeast(drawW * 0.45f))
        val drawBottomY = (posY - beatBounceY - jumpOffsetY).coerceIn(drawH, h)

        // Draw soft oval floor shadow when near the floor
        if (posY >= floorY - context.dpf(30f)) {
            val shadowW = drawW * 0.65f
            val shadowH = context.dpf(8f)
            canvas.drawOval(
                drawCenterX - shadowW / 2f,
                floorY - shadowH / 2f,
                drawCenterX + shadowW / 2f,
                floorY + shadowH / 2f,
                floorShadowPaint
            )
        }

        // If clone action is active, draw a companion clone next to the main Chibi!
        if (now < cloneActiveUntilMs) {
            val cloneX = (drawCenterX + (if (facingRight) -drawW * 0.65f else drawW * 0.65f))
                .coerceIn(drawW * 0.4f, (w - drawW * 0.4f).coerceAtLeast(drawW * 0.4f))
            destRect.set(cloneX - drawW / 2f, drawBottomY - drawH, cloneX + drawW / 2f, drawBottomY)
            canvas.save()
            chibiPaint.alpha = 215
            canvas.drawBitmap(bmp, null, destRect, chibiPaint)
            chibiPaint.alpha = 255
            canvas.restore()
        }

        destRect.set(
            drawCenterX - drawW / 2f,
            drawBottomY - drawH,
            drawCenterX + drawW / 2f,
            drawBottomY
        )

        // Flip horizontally according to native facing direction in PinkChan's actions.xml
        val needFlip = if (skin.nativeFacesLeft) facingRight else !facingRight
        canvas.save()
        if (needFlip && shimejiState != ShimejiState.DRAGGED && shimejiState != ShimejiState.CEILING) {
            canvas.scale(-1f, 1f, drawCenterX, drawBottomY - drawH / 2f)
        }
        canvas.drawBitmap(bmp, null, destRect, chibiPaint)
        canvas.restore()

        // Draw custom action prop badge next to the Chibi if active
        if (shimejiState == ShimejiState.CUSTOM_ACTION && customActionEmoji.isNotEmpty()) {
            particlePaint.textSize = context.dpf(20f)
            canvas.drawText(
                customActionEmoji,
                drawCenterX + (if (facingRight) drawW * 0.38f else -drawW * 0.38f),
                drawBottomY - drawH * 0.35f + sin(animTick * 0.2f) * context.dpf(4f),
                particlePaint
            )
            particlePaint.textSize = context.dpf(14f)
        }
    }

    private fun pickNextFloorBehavior() {
        val r = Random.nextFloat()
        when {
            r < 0.38f -> {
                shimejiState = ShimejiState.WALK
                facingRight = Random.nextBoolean()
                stateTicks = Random.nextInt(90, 210)
            }
            r < 0.54f -> {
                shimejiState = ShimejiState.SIT
                stateTicks = Random.nextInt(80, 160)
            }
            r < 0.70f -> {
                shimejiState = ShimejiState.STAND
                stateTicks = Random.nextInt(60, 120)
            }
            r < 0.85f -> {
                shimejiState = ShimejiState.HAPPY_JUMP
                stateTicks = Random.nextInt(40, 75)
            }
            else -> {
                shimejiState = ShimejiState.RUN
                facingRight = Random.nextBoolean()
                stateTicks = Random.nextInt(65, 130)
            }
        }
    }

    private fun selectChibiFrameName(skin: DdscEngine.PinkChanSkin): String {
        val isBocchiOrKonata = !skin.nativeFacesLeft
        val now = System.currentTimeMillis()
        val bpm = currentSongBpm.coerceIn(60f, 200f)
        val beatPeriodMs = (60000.0 / bpm).coerceIn(300.0, 1000.0)
        // Dance & walking steps sync to song tempo (every half-beat = 2 steps per beat):
        val danceStep = if (isMusicPlaying) (now / (beatPeriodMs * 0.5).toLong()).toLong() else (now / 350L)
        val walkStep = if (isMusicPlaying) (now / (beatPeriodMs * 0.5).toLong()).toLong() else (now / 240L)
        val runStep = (now / 150L)

        return when (shimejiState) {
            ShimejiState.DRAGGED -> {
                if (velX < -context.dpf(1.2f)) "air_swing_r.png"
                else if (velX > context.dpf(1.2f)) "air_swing_l.png"
                else if ((now / 200L) % 2L == 0L) "air_swing_l.png" else "air_swing_r.png"
            }
            ShimejiState.FALL, ShimejiState.FLUNG -> {
                if (isBocchiOrKonata) "fall1.png" else "air.png"
            }
            ShimejiState.CLIMB_LEFT, ShimejiState.CLIMB_RIGHT -> {
                if (isBocchiOrKonata) {
                    if ((now / 220L) % 2L == 0L) "climb1.png" else "climb2.png"
                } else {
                    "climb.png"
                }
            }
            ShimejiState.CEILING -> {
                if (isBocchiOrKonata) {
                    if ((now / 260L) % 2L == 0L) "ceiling1.png" else "ceiling2.png"
                } else {
                    "climb_top.png"
                }
            }
            ShimejiState.SIT -> {
                if (isBocchiOrKonata) {
                    val idx = ((now / 400L) % 4) + 1
                    "sit$idx.png"
                } else {
                    "snug.png"
                }
            }
            ShimejiState.HAPPY_JUMP, ShimejiState.ANGRY -> {
                val jumpStep = (now / 220L)
                if (isBocchiOrKonata) {
                    if (jumpStep % 2L == 0L) "jump1.png" else "land1.png"
                } else {
                    val seq = arrayOf("jump_2.png", "jump_3.png", "jump_2.png", "jump_1.png")
                    seq[(jumpStep % seq.size).toInt()]
                }
            }
            ShimejiState.WALK -> {
                if (isBocchiOrKonata) {
                    val idx = ((walkStep % 4) + 1).toInt()
                    "walk$idx.png"
                } else {
                    val seq = arrayOf("stand.png", "stand_walk1.png", "stand_walk2.png", "stand_walk1.png")
                    seq[(walkStep % seq.size).toInt()]
                }
            }
            ShimejiState.RUN -> {
                if (isBocchiOrKonata) {
                    val idx = ((runStep % 4) + 1).toInt()
                    "walk$idx.png"
                } else {
                    val seq = arrayOf("stand.png", "stand_walk1.png", "stand_walk2.png", "stand_walk1.png")
                    seq[(runStep % seq.size).toInt()]
                }
            }
            ShimejiState.CUSTOM_ACTION -> {
                val actionStep = if (isMusicPlaying) (now / (beatPeriodMs * 0.5).toLong()).toLong() else (now / 200L)
                when (customActionFrameType) {
                    "guitar" -> "guitar${((actionStep % 3) + 1)}.png"
                    "box" -> "box${(((now / 500L) % 2) + 1)}.png"
                    "clone" -> "clone_${((actionStep % 4) + 1)}.png"
                    "jump" -> if (isBocchiOrKonata) "jump1.png" else "jump_2.png"
                    "snug" -> if (isBocchiOrKonata) "sit1.png" else "snug.png"
                    else -> "stand.png"
                }
            }
            ShimejiState.STAND -> {
                if (isMusicPlaying) {
                    // Groove in perfect rhythm with the song beat!
                    if (isBocchiOrKonata) {
                        val seq = arrayOf("stand1.png", "stand2.png", "stand3.png", "stand2.png")
                        seq[(danceStep % seq.size).toInt()]
                    } else {
                        val seq = arrayOf("stand.png", "jump_1.png", "stand.png", "snug.png")
                        seq[(danceStep % seq.size).toInt()]
                    }
                } else {
                    if (isBocchiOrKonata) "stand1.png" else "stand.png"
                }
            }
        }
    }

    /**
     * Renders full-body HD `.webp` DDSC sprites cropped to their opaque bounding box.
     */
    private fun drawHdDdscSprite(canvas: Canvas, w: Float, h: Float, now: Long) {
        val reducedMotion = MotionTokens.isReducedMotion(context)

        if (customLockedPath == null) {
            if (now - lastBlinkCheckMs > 3400L) {
                lastBlinkCheckMs = now
                blinkEndMs = now + 155L
            }
            val isBlinking = now < blinkEndMs
            val isSinging = isMusicPlaying && audioEnergy > 0.22f
            currentPath = when {
                isBlinking -> blinkPath
                isSinging -> speakPath
                else -> idlePath
            }
        } else {
            currentPath = customLockedPath!!
        }

        val bmp = DdscEngine.loadSpriteBitmap(context, currentPath) ?: return
        val shadowBmp = DdscEngine.getOrBuildShadow(bmp, currentPath)

        if (!reducedMotion) {
            breathPhase += if (isMusicPlaying) 0.045f else 0.018f
        }

        val bw = bmp.width.toFloat().coerceAtLeast(1f)
        val bh = bmp.height.toFloat().coerceAtLeast(1f)

        // Allow slight horizontal breathing room (w * 1.15f) so tall sprites fill ~95% of stage height!
        val scale = min((w * 1.16f) / bw, (h * 0.95f) / bh) * dokiScale
        val drawW = bw * scale
        val drawH = bh * scale

        val left = (w - drawW) / 2f
        val top = h - drawH
        destRect.set(left, top, left + drawW, h)

        posX = w / 2f
        posY = top + drawH * 0.22f

        val swayDeg = if (reducedMotion) 0f else sin(breathPhase) * 0.58f
        val beatBounce = if (reducedMotion || !isMusicPlaying) 0f else (audioEnergy * context.dpf(10f)).coerceIn(0f, context.dpf(15f))
        val bounceScaleY = if (reducedMotion) 1.0f else {
            1.0f + sin(breathPhase * 1.4f) * 0.007f + (if (isMusicPlaying) audioEnergy * 0.035f else 0f)
        }
        val bounceScaleX = 1.0f - (bounceScaleY - 1.0f) * 0.35f

        canvas.save()
        if (!reducedMotion) {
            canvas.translate(0f, -beatBounce)
            canvas.rotate(swayDeg, w / 2f, h)
            canvas.scale(bounceScaleX, bounceScaleY, w / 2f, h)
        }

        if (shadowBmp != null) {
            val padRatioX = drawW * 0.07f
            val padRatioY = drawH * 0.05f
            val offsetX = context.dpf(3f)
            val offsetY = context.dpf(5f)
            shadowRect.set(
                destRect.left - padRatioX + offsetX,
                destRect.top - padRatioY + offsetY,
                destRect.right + padRatioX + offsetX,
                destRect.bottom + padRatioY + offsetY
            )
            canvas.drawBitmap(shadowBmp, null, shadowRect, shadowPaint)
        }

        val elapsedCrossfade = now - crossfadeStartMs
        val isCrossfading = prevPath != null && elapsedCrossfade in 0 until crossfadeDurMs
        if (isCrossfading) {
            val t = (elapsedCrossfade.toFloat() / crossfadeDurMs.toFloat()).coerceIn(0f, 1f)
            val eased = MotionTokens.EASE_OUT.getInterpolation(t)
            val oldBmp = prevPath?.let { DdscEngine.loadSpriteBitmap(context, it) }
            if (oldBmp != null && oldBmp != bmp) {
                prevBitmapPaint.alpha = ((1f - eased) * 255f).toInt().coerceIn(0, 255)
                bitmapPaint.alpha = (eased * 255f).toInt().coerceIn(0, 255)
                canvas.drawBitmap(oldBmp, null, destRect, prevBitmapPaint)
                canvas.drawBitmap(bmp, null, destRect, bitmapPaint)
                bitmapPaint.alpha = 255
            } else {
                bitmapPaint.alpha = 255
                canvas.drawBitmap(bmp, null, destRect, bitmapPaint)
            }
        } else {
            prevPath = null
            bitmapPaint.alpha = 255
            canvas.drawBitmap(bmp, null, destRect, bitmapPaint)
        }
        canvas.restore()
    }

    private fun drawParticlesAndSpeechBubble(canvas: Canvas, w: Float, h: Float, now: Long) {
        // 1. Update & draw floating heart/note/sparkle particles
        val it = particles.iterator()
        while (it.hasNext()) {
            val p = it.next()
            p.x += p.vx
            p.y += p.vy
            p.alpha -= 0.022f
            if (p.alpha <= 0f) {
                it.remove()
            } else {
                particlePaint.alpha = (p.alpha * 255f).toInt().coerceIn(0, 255)
                canvas.drawText(p.symbol, p.x, p.y, particlePaint)
            }
        }
        particlePaint.alpha = 255

        // 2. Draw comic speech bubble if active
        if (now < speechUntilMs && speechText.isNotEmpty()) {
            val padH = context.dpf(8f)
            val padV = context.dpf(6f)
            val maxBubbleW = (w - context.dpf(8f)).coerceAtLeast(context.dpf(80f))

            // Wrap into 1 or 2 lines if needed
            val words = speechText.split(" ")
            var line1 = ""
            var line2 = ""
            for (word in words) {
                val test = if (line1.isEmpty()) word else "$line1 $word"
                if (bubbleTextPaint.measureText(test) <= maxBubbleW - padH * 2f || line1.isEmpty()) {
                    line1 = test
                } else {
                    line2 = if (line2.isEmpty()) word else "$line2 $word"
                }
            }

            val textW = max(bubbleTextPaint.measureText(line1), bubbleTextPaint.measureText(line2))
            val bw = (textW + padH * 2f).coerceAtMost(maxBubbleW)
            val lineH = context.dpf(14f)
            val bh = if (line2.isEmpty()) lineH + padV * 2f else lineH * 2f + padV * 2f

            val anchorX = (if (isChibiMode) posX else w / 2f).coerceIn(bw / 2f + context.dpf(2f), (w - bw / 2f - context.dpf(2f)).coerceAtLeast(bw / 2f + context.dpf(2f)))
            val rawTop = if (isChibiMode) (posY - context.dpf(128f) - bh) else context.dpf(8f)
            val topY = rawTop.coerceIn(context.dpf(4f), (h - bh - context.dpf(48f)).coerceAtLeast(context.dpf(4f)))

            bubbleRect.set(anchorX - bw / 2f, topY, anchorX + bw / 2f, topY + bh)
            val corner = context.dpf(10f)
            canvas.drawRoundRect(bubbleRect, corner, corner, bubbleBgPaint)
            canvas.drawRoundRect(bubbleRect, corner, corner, bubbleStrokePaint)

            if (line2.isEmpty()) {
                canvas.drawText(line1, bubbleRect.centerX(), bubbleRect.centerY() + context.dpf(4f), bubbleTextPaint)
            } else {
                canvas.drawText(line1, bubbleRect.centerX(), bubbleRect.top + padV + context.dpf(10f), bubbleTextPaint)
                canvas.drawText(line2, bubbleRect.centerX(), bubbleRect.top + padV + lineH + context.dpf(10f), bubbleTextPaint)
            }
        }
    }
}

/**
 * Generates a valid looping multi-frame animated GIF file in cache so the user can test
 * animated GIF album covers and first-frame palette extraction at any time.
 */
object DemoGifGenerator {
    fun ensureSampleGifFile(context: Context): File = getOrCreateDemoGif(context, 0)

    fun getOrCreateDemoGif(context: Context, variant: Int = 0): File {
        val file = File(context.cacheDir, "demo_cover_anim_$variant.gif")
        if (file.exists() && file.length() > 200) return file
        val bytes = buildAnimatedGif64(variant)
        file.writeBytes(bytes)
        return file
    }

    private fun buildAnimatedGif64(variant: Int): ByteArray {
        val out = ByteArrayOutputStream()
        fun writeStr(s: String) = out.write(s.toByteArray(Charsets.US_ASCII))
        fun writeShort(v: Int) { out.write(v and 0xFF); out.write((v shr 8) and 0xFF) }

        val w = 32
        val h = 32
        writeStr("GIF89a")
        writeShort(w)
        writeShort(h)
        out.write(0xF1)
        out.write(0)
        out.write(0)

        val palettes = arrayOf(
            intArrayOf(0xEC4899, 0x8B5CF6, 0x38BDF8, 0x1E1B4B),
            intArrayOf(0xF59E0B, 0xEF4444, 0xEC4899, 0x27121B),
            intArrayOf(0x06B6D4, 0x3B82F6, 0x6366F1, 0x0F172A)
        )
        val pal = palettes[variant % palettes.size]
        for (c in pal) {
            out.write((c shr 16) and 0xFF)
            out.write((c shr 8) and 0xFF)
            out.write(c and 0xFF)
        }

        out.write(intArrayOf(0x21, 0xFF, 0x0B).map { it.toByte() }.toByteArray())
        writeStr("NETSCAPE2.0")
        out.write(intArrayOf(0x03, 0x01, 0x00, 0x00, 0x00).map { it.toByte() }.toByteArray())

        for (frame in 0 until 8) {
            out.write(intArrayOf(0x21, 0xF9, 0x04, 0x00, 10, 0x00, 0x00, 0x00).map { it.toByte() }.toByteArray())
            out.write(0x2C)
            writeShort(0); writeShort(0); writeShort(w); writeShort(h)
            out.write(0x00)

            out.write(2)
            val bitPacker = BitPacker()
            val clearCode = 4
            val eoiCode = 5
            bitPacker.writeBits(clearCode, 3)
            var countSinceClear = 0
            for (y in 0 until h) {
                for (x in 0 until w) {
                    val dx = x - w / 2
                    val dy = y - h / 2
                    val dist = kotlin.math.sqrt((dx * dx + dy * dy).toDouble()).toInt()
                    val colorIdx = ((dist + frame) / 3) and 0x03
                    bitPacker.writeBits(colorIdx, 3)
                    countSinceClear++
                    if (countSinceClear == 2) {
                        bitPacker.writeBits(clearCode, 3)
                        countSinceClear = 0
                    }
                }
            }
            bitPacker.writeBits(eoiCode, 3)
            val lzwBytes = bitPacker.toByteArray()
            var offset = 0
            while (offset < lzwBytes.size) {
                val chunk = minOf(254, lzwBytes.size - offset)
                out.write(chunk)
                out.write(lzwBytes, offset, chunk)
                offset += chunk
            }
            out.write(0x00)
        }
        out.write(0x3B)
        return out.toByteArray()
    }

    private class BitPacker {
        private val out = ByteArrayOutputStream()
        private var curByte = 0
        private var bitPos = 0

        fun writeBits(value: Int, numBits: Int) {
            for (i in 0 until numBits) {
                val bit = (value shr i) and 1
                curByte = curByte or (bit shl bitPos)
                bitPos++
                if (bitPos == 8) {
                    out.write(curByte)
                    curByte = 0
                    bitPos = 0
                }
            }
        }

        fun toByteArray(): ByteArray {
            if (bitPos > 0) {
                out.write(curByte)
                curByte = 0
                bitPos = 0
            }
            return out.toByteArray()
        }
    }
}
