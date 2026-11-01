package tw.dietbook

import android.content.Context

/** Delete / restore logs and keep 總存款 consistent for today and past days. */
sealed class DeletedPayload {
    data class Food(val log: FoodLog) : DeletedPayload()
    data class Cardio(val log: CardioLog) : DeletedPayload()
    data class Strength(val log: StrengthLog) : DeletedPayload()
    data class HcIgnore(val day: String, val previousIgnored: Boolean) : DeletedPayload()
}

object LogActions {
    fun deleteFood(ctx: Context, day: String, id: Long): FoodLog? {
        val log = Store.logs(ctx, day).find { it.id == id } ?: return null
        Store.deleteLog(ctx, day, id)
        Balance.recomputeDay(ctx, day)
        return log
    }

    fun deleteCardio(ctx: Context, day: String, id: Long): CardioLog? {
        val log = Store.cardioLogs(ctx, day).find { it.id == id } ?: return null
        Store.deleteCardio(ctx, day, id)
        Balance.recomputeDay(ctx, day)
        return log
    }

    fun deleteStrength(ctx: Context, day: String, id: Long): StrengthLog? {
        val log = Store.strengthLogs(ctx, day).find { it.id == id } ?: return null
        Store.deleteStrength(ctx, day, id)
        Balance.recomputeDay(ctx, day)
        return log
    }

    fun setHcIgnored(ctx: Context, day: String, ignored: Boolean): Boolean {
        val prev = Store.hcIgnored(ctx, day)
        Store.setHcIgnored(ctx, day, ignored)
        Balance.recomputeDay(ctx, day)
        return prev
    }

    fun restore(ctx: Context, payload: DeletedPayload) {
        when (payload) {
            is DeletedPayload.Food -> {
                Store.addLog(ctx, payload.log)
                Balance.recomputeDay(ctx, payload.log.day)
            }
            is DeletedPayload.Cardio -> {
                Store.addCardio(ctx, payload.log)
                Balance.recomputeDay(ctx, payload.log.day)
            }
            is DeletedPayload.Strength -> {
                Store.addStrength(ctx, payload.log)
                Balance.recomputeDay(ctx, payload.log.day)
            }
            is DeletedPayload.HcIgnore -> {
                Store.setHcIgnored(ctx, payload.day, payload.previousIgnored)
                Balance.recomputeDay(ctx, payload.day)
            }
        }
    }

    fun label(payload: DeletedPayload): String = when (payload) {
        is DeletedPayload.Food -> "已刪除「${payload.log.foodName}」"
        is DeletedPayload.Cardio -> "已刪除「${payload.log.label}」"
        is DeletedPayload.Strength -> "已刪除「${payload.log.part}・${payload.log.exercise}」"
        is DeletedPayload.HcIgnore -> if (!payload.previousIgnored) "已忽略今日 Health Connect" else "已恢復 Health Connect"
    }
}
