package tw.dietbook

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory

/**
 * 體型階段與 WebP 貼圖載入（assets/pig/<stage>/…）。
 * 餘額（累計熱量）規則：
 *  surplus >1500 fat3 · >1000 fat2 · >500 fat1
 *  |bal|≤500 normal（理想）
 *  deficit >500 slim1 · >1000 slim2（太瘦）
 */
enum class BodyStage(val dir: String, val caption: String, val hint: String) {
    SLIM2("slim2", "小豬太瘦了", "吃太少了喔，溫柔地多補一點營養吧～"),
    SLIM1("slim1", "小豬有點瘦", "缺口剛好～記得均衡進食，別再刻意少吃。"),
    NORMAL("normal", "小豬狀態剛好", "理想體態，保持覺察就好。"),
    FAT1("fat1", "小豬微微圓", "有一點進帳，多動或慢慢還款即可。"),
    FAT2("fat2", "小豬圓圓的", "進帳偏多，動起來幫助還款～"),
    FAT3("fat3", "小豬吃太飽了", "進帳很多，慢慢還就好，別自責。"),
}

object PigSprites {
    const val WALK_1 = "walk_1"
    const val WALK_2 = "walk_2"
    const val WALK_3 = "walk_3"
    const val WALK_4 = "walk_4"
    const val LIFT_SURPRISE = "lift_surprise"
    const val LIFT_SHY = "lift_shy"
    const val LIFT_WIGGLE = "lift_wiggle"
    const val LAND_SQUASH = "land_squash"
    const val BOUNCE_HAPPY = "bounce_happy"
    const val DIZZY = "dizzy"
    const val BENTO_BOX = "bento_box"
    const val BENTO_HOLD = "bento_hold"
    const val BENTO_EAT = "bento_eat"
    const val BENTO_THUMB = "bento_thumb"
    const val LIFT_1 = "lift_1"
    const val LIFT_2 = "lift_2"
    const val LIFT_3 = "lift_3"
    const val RUN_1 = "run_1"
    const val RUN_2 = "run_2"
    const val RUN_3 = "run_3"

    val WALK = arrayOf(WALK_1, WALK_2, WALK_3, WALK_4)
    val PICKED = arrayOf(LIFT_SURPRISE, LIFT_SHY, LIFT_WIGGLE)
    val BENTO = arrayOf(BENTO_BOX, BENTO_HOLD, BENTO_EAT, BENTO_THUMB)
    val LIFT = arrayOf(LIFT_1, LIFT_2, LIFT_3)
    val RUN = arrayOf(RUN_1, RUN_2, RUN_3)

    private val cache = HashMap<String, Bitmap>()

    fun fromBalance(balance: Double): BodyStage = when {
        balance > 1500 -> BodyStage.FAT3
        balance > 1000 -> BodyStage.FAT2
        balance > 500 -> BodyStage.FAT1
        balance < -1000 -> BodyStage.SLIM2
        balance < -500 -> BodyStage.SLIM1
        else -> BodyStage.NORMAL
    }

    fun get(ctx: Context, stage: BodyStage, frame: String): Bitmap {
        val key = "${stage.dir}/$frame"
        cache[key]?.let { return it }
        val path = "pig/${stage.dir}/$frame.webp"
        val bm = try {
            ctx.assets.open(path).use { BitmapFactory.decodeStream(it) }
        } catch (_: Exception) {
            null
        } ?: Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
        cache[key] = bm
        return bm
    }

    fun idle(ctx: Context, stage: BodyStage): Bitmap = get(ctx, stage, WALK_1)

    fun clearCache() = cache.clear()
}
